package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailDraft;
import com.aicustomer.entity.EmailSendLog;
import com.aicustomer.entity.Lead;
import com.aicustomer.entity.SystemConfig;
import com.aicustomer.repository.EmailDraftRepository;
import com.aicustomer.repository.EmailSendLogRepository;
import com.aicustomer.repository.EmailUnsubscribeRepository;
import com.aicustomer.repository.LeadRepository;
import com.aicustomer.repository.SystemConfigRepository;
import com.aicustomer.util.AesUtil;
import com.aicustomer.util.TemplateRenderer;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 邮件发送服务（M3-2 核心）：
 * - SMTP 发送（angus-mail 实现，适配任意邮箱服务商：465 SSL / 587 STARTTLS / 25 明文）
 * - 发送记录 email_send_log 落库（queued → sent / failed）
 * - mail.daily_limit 每日发送限频
 * - 发送成功后草稿 status → sent
 * 前端 REST 直接调本服务；MCP email_send_email 工具薄封装（外部 AI Agent 入口）。
 * 注意：sendDraft 不标 @Transactional —— 发送失败时 failed 记录独立提交，草稿保持 confirmed 可重试。
 */
@Service
public class EmailSendService {

    private static final Logger log = LoggerFactory.getLogger(EmailSendService.class);

    private final EmailDraftRepository emailDraftRepository;
    private final EmailSendLogRepository emailSendLogRepository;
    private final EmailUnsubscribeRepository emailUnsubscribeRepository;
    private final LeadRepository leadRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final AesUtil aesUtil;

    public EmailSendService(EmailDraftRepository emailDraftRepository,
                            EmailSendLogRepository emailSendLogRepository,
                            EmailUnsubscribeRepository emailUnsubscribeRepository,
                            LeadRepository leadRepository,
                            SystemConfigRepository systemConfigRepository,
                            AesUtil aesUtil) {
        this.emailDraftRepository = emailDraftRepository;
        this.emailSendLogRepository = emailSendLogRepository;
        this.emailUnsubscribeRepository = emailUnsubscribeRepository;
        this.leadRepository = leadRepository;
        this.systemConfigRepository = systemConfigRepository;
        this.aesUtil = aesUtil;
    }

    /** 发送结果 */
    public record SendResult(Long sendLogId, String status, String toEmail, String errorMsg) {
    }

    /**
     * 发送草稿（须 status=confirmed）：
     * 校验（草稿/客户邮箱/SMTP 配置/每日限频）→ 落 queued 记录 → SMTP 投递 → sent/failed。
     */
    public SendResult sendDraft(Long leadId, Long draftId) {
        EmailDraft draft = emailDraftRepository.findById(draftId)
                .orElseThrow(() -> BizException.notFound("邮件草稿不存在"));
        if (!draft.getLeadId().equals(leadId)) {
            throw BizException.notFound("邮件草稿不存在");
        }
        if (!"confirmed".equals(draft.getStatus())) {
            throw BizException.badRequest("草稿需先标记待发（confirmed）才能发送");
        }
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> BizException.notFound("客户不存在"));
        if (!StringUtils.hasText(lead.getContactEmail())) {
            throw BizException.badRequest("客户邮箱未填写，无法发送邮件");
        }

        // 退订黑名单拦截（M3-2 合规闭环）：收件人已点击退订链接 → 不再发送
        String toEmail = lead.getContactEmail().trim().toLowerCase();
        if (emailUnsubscribeRepository.existsByEmail(toEmail)) {
            throw BizException.badRequest("该邮箱已退订，不再发送营销邮件");
        }

        SmtpConfig smtp = loadSmtpConfig();
        checkDailyLimit(smtp.dailyLimit());

        // 邮件模板变量替换（M3-2 补充）：subject/body 中的 {companyName} {contactName} 等占位符
        // 按 Lead 实际字段替换（空字段 → 空串）。草稿保存时已替换（草稿箱即真实内容），
        // 此处兜底幂等替换（用户手改草稿加入的新占位符也会生效），再追加退订链接。
        String finalSubject = TemplateRenderer.render(draft.getSubject(), lead);
        String finalBody = TemplateRenderer.render(draft.getBody(), lead);
        finalBody = appendUnsubscribe(finalBody, lead.getContactEmail());

        // 先落 queued 记录
        EmailSendLog entry = new EmailSendLog();
        entry.setTenantId(TenantContext.require());
        entry.setLeadId(leadId);
        entry.setDraftId(draftId);
        entry.setFromEmail(smtp.username());
        entry.setToEmail(lead.getContactEmail());
        entry.setSubject(finalSubject);
        entry.setBody(finalBody);
        entry.setStatus("queued");
        emailSendLogRepository.save(entry);

        // M4-6 打开率追踪：mail.track_url 配置后，HTML 正文注入 1px 追踪像素 + 链接包装为点击追踪（纯文本不埋点）
        String sendBody = finalBody;
        String trackBase = trackBaseUrl();
        if (StringUtils.hasText(trackBase)) {
            sendBody = injectTracking(finalBody, trackBase, entry.getId());
            entry.setBody(sendBody);
        }

        try {
            sendSmtp(smtp, lead.getContactEmail(), finalSubject, sendBody);
            entry.setStatus("sent");
            entry.setSentAt(LocalDateTime.now());
            entry.setErrorMsg(null);
            // 草稿标记已发送（不再可编辑）
            draft.setStatus("sent");
            emailDraftRepository.save(draft);
            log.info("邮件发送成功: leadId={}, draftId={}, to={}", leadId, draftId, lead.getContactEmail());
        } catch (Exception e) {
            entry.setStatus("failed");
            entry.setErrorMsg(truncate(e.getMessage(), 255));
            log.warn("邮件发送失败: leadId={}, draftId={}, err={}", leadId, draftId, e.getMessage());
        }
        emailSendLogRepository.save(entry);
        return new SendResult(entry.getId(), entry.getStatus(), lead.getContactEmail(), entry.getErrorMsg());
    }

    /**
     * 重试失败的发送记录（M3-2 补充）：仅 failed 且有关联草稿的记录可重试，
     * 重新走 sendDraft（草稿须仍为 confirmed，否则由 sendDraft 校验拦截）。
     */
    public SendResult retry(Long leadId, Long logId) {
        EmailSendLog log = emailSendLogRepository.findById(logId)
                .orElseThrow(() -> BizException.notFound("发送记录不存在"));
        if (!log.getLeadId().equals(leadId)) {
            throw BizException.notFound("发送记录不存在");
        }
        if (!"failed".equals(log.getStatus())) {
            throw BizException.badRequest("仅发送失败的记录可重试");
        }
        if (log.getDraftId() == null) {
            throw BizException.badRequest("该发送记录无关联草稿，无法重试");
        }
        return sendDraft(leadId, log.getDraftId());
    }

    // ==================== SMTP ====================

    private void sendSmtp(SmtpConfig cfg, String to, String subject, String body) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.smtp.host", cfg.host());
        props.put("mail.smtp.port", String.valueOf(cfg.port()));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        if (cfg.port() == 465) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", "*");
        } else if (cfg.port() == 587) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(cfg.username()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject == null ? "" : subject, "UTF-8");
        // M3-2 增强：正文为 HTML（模板/AI 生成）时按 text/html 发送，支持美化排版
        if (isHtml(body)) {
            message.setContent(body == null ? "" : body, "text/html; charset=UTF-8");
        } else {
            message.setText(body == null ? "" : body, "UTF-8");
        }
        Transport transport = session.getTransport("smtp");
        try {
            transport.connect(cfg.host(), cfg.port(), cfg.username(), cfg.password());
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            transport.close();
        }
    }

    // ==================== 退订链接 ====================

    /**
     * 正文末尾追加退订链接（M3-2 遗留，营销邮件合规）：
     * mail.unsubscribe_url 未配置 → 原样返回；配置填域名前缀（如 https://www.example.com）时
     * 自动补全内置退订落地页路径 /unsubscribe；若已含 {email} 占位符或 /unsubscribe 路径则按原样
     * 使用（兼容自定义落地页），{email} 占位符 URL 编码替换，无占位符时自动拼 ?email=xxx
     * （URL 已含查询参数则用 &）。
     */
    private String appendUnsubscribe(String body, String toEmail) {
        String base = cfg("mail.unsubscribe_url");
        if (!StringUtils.hasText(base)) {
            return body;
        }
        // 域名前缀自动补全退订路径；已含 {email} 或 /unsubscribe 则尊重用户配置
        if (!base.contains("{email}") && !base.contains("/unsubscribe")) {
            base = base.replaceAll("/+$", "") + "/unsubscribe";
        }
        String encoded = URLEncoder.encode(toEmail, StandardCharsets.UTF_8);
        // SaaS：退订链接携带租户 id，公开落地页据此定位退订记录（无上下文时回退默认租户 1）
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            tenantId = 1L;
        }
        String url = base.contains("{email}")
                ? base.replace("{email}", encoded)
                : base + (base.contains("?") ? "&" : "?") + "email=" + encoded + "&tenantId=" + tenantId;
        boolean html = isHtml(body);
        // HTML 邮件用 HTML 退订块（与正文排版一致）；纯文本邮件用原有纯文本块
        String block = html
                ? "<br><div style=\"margin-top:16px;padding-top:8px;border-top:1px solid #eee;color:#999;font-size:12px;\">如不希望收到此类邮件，请点击<a href=\"" + url + "\" style=\"color:#1677ff;\">退订</a></div>"
                : "\n————————————\n如不希望收到此类邮件，请点击退订：" + url + "\n";
        if (body == null || body.isBlank()) {
            return block;
        }
        return body + (html || body.endsWith("\n") ? "" : "\n") + block;
    }

    /** 判断正文是否为 HTML：包含常见 HTML 标签即按 HTML 邮件处理（模板/AI 生成的正文） */
    private static boolean isHtml(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String t = body.toLowerCase();
        return t.contains("<!doctype html") || t.contains("<html") || t.contains("<p")
                || t.contains("<br") || t.contains("<div") || t.contains("<span") || t.contains("<table")
                || t.contains("<ul") || t.contains("<li") || t.contains("<strong") || t.contains("<b>")
                || t.contains("<h1") || t.contains("<h2") || t.contains("<h3") || t.contains("<a ");
    }

    // ==================== M4-6 打开率追踪 ====================

    /** 追踪域名前缀：mail.track_url 未配置/空白 → 返回空串（不追踪） */
    private String trackBaseUrl() {
        String base = cfg("mail.track_url");
        if (!StringUtils.hasText(base)) {
            return "";
        }
        return base.trim().replaceAll("/+$", "");
    }

    /**
     * 注入打开/点击追踪：仅 HTML 邮件生效（客户端不渲染纯文本里的 img/链接包装会显示乱码）。
     * 1) 全部 http(s) 外链（含退订链接）包装为 /api/track/click/{logId}?url=xxx 追踪跳转；
     * 2) 正文末尾追加 1px 透明像素 /api/track/open/{logId}，加载即记打开。
     * SaaS：追踪链接携带 tenantId（公开端点据此定位记录，无上下文时回退默认租户 1）。
     */
    private String injectTracking(String body, String trackBase, Long logId) {
        if (!isHtml(body)) {
            return body;
        }
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            tenantId = 1L;
        }
        return appendOpenPixel(wrapLinks(body, trackBase, logId, tenantId), trackBase, logId, tenantId);
    }

    /** 链接点击追踪：<a href> 仅包装 http/https 外链（mailto:/#/javascript: 等保持原样） */
    private String wrapLinks(String body, String trackBase, Long logId, Long tenantId) {
        Pattern pattern = Pattern.compile("(?i)(<a\\s+[^>]*?href\\s*=\\s*\")([^\"]+)(\")");
        Matcher m = pattern.matcher(body);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String href = m.group(2).trim();
            String target = href;
            if (href.startsWith("http://") || href.startsWith("https://")) {
                String encoded = URLEncoder.encode(href, StandardCharsets.UTF_8);
                target = trackBase + "/api/track/click/" + logId + "?url=" + encoded + "&tenantId=" + tenantId;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + target + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 打开追踪像素：HTML 末尾（</body> 前）追加 1x1 透明 GIF img */
    private String appendOpenPixel(String body, String trackBase, Long logId, Long tenantId) {
        String pixel = "<img src=\"" + trackBase + "/api/track/open/" + logId + "?tenantId=" + tenantId
                + "\" width=\"1\" height=\"1\" style=\"display:none\" alt=\"\" />";
        int idx = body.toLowerCase().indexOf("</body>");
        if (idx >= 0) {
            return body.substring(0, idx) + pixel + body.substring(idx);
        }
        return body + pixel;
    }

    // ==================== 配置 / 限频 ====================

    /** SMTP 配置（system_config 读取，password 解密） */
    private record SmtpConfig(String host, int port, String username, String password, int dailyLimit) {
    }

    private SmtpConfig loadSmtpConfig() {
        String host = cfg("smtp.host");
        String portStr = cfg("smtp.port");
        String username = cfg("smtp.username");
        String passwordRaw = cfg("smtp.password");
        String dailyLimitStr = cfg("mail.daily_limit");
        if (!StringUtils.hasText(host)) {
            throw BizException.badRequest("未配置 SMTP 服务器，请先在系统设置中配置 smtp.host");
        }
        if (!StringUtils.hasText(username)) {
            throw BizException.badRequest("未配置发件邮箱，请先在系统设置中配置 smtp.username");
        }
        if (!StringUtils.hasText(passwordRaw)) {
            throw BizException.badRequest("未配置 SMTP 授权码，请先在系统设置中配置 smtp.password");
        }
        int port = 465;
        if (StringUtils.hasText(portStr)) {
            try {
                port = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException ignored) {
                // 非法端口回退 465
            }
        }
        // 密码在系统设置保存时已 AES 加密，解密失败按明文兼容
        String password;
        try {
            password = aesUtil.decrypt(passwordRaw);
        } catch (Exception e) {
            password = passwordRaw;
        }
        int dailyLimit = 50;
        if (StringUtils.hasText(dailyLimitStr)) {
            try {
                dailyLimit = Integer.parseInt(dailyLimitStr.trim());
            } catch (NumberFormatException ignored) {
                // 非法上限回退 50
            }
        }
        return new SmtpConfig(host, port, username, password, dailyLimit);
    }

    /** 当日已成功发送数 ≥ 上限则拒绝（mail.daily_limit，默认 50） */
    private void checkDailyLimit(int dailyLimit) {
        // M7.12：按中国时区取“今日”，避免容器 UTC 导致限频跨天不重置
        long sentToday = emailSendLogRepository.countByStatusAndSentAtGreaterThanEqual(
                "sent", LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay());
        if (sentToday >= dailyLimit) {
            throw BizException.badRequest("今日发送已达上限（" + dailyLimit + " 封），请明天再试或调高 mail.daily_limit");
        }
    }

    private String cfg(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(null);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
