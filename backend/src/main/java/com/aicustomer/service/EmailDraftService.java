package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.entity.EmailDraft;
import com.aicustomer.entity.EmailInbox;
import com.aicustomer.entity.EmailTemplate;
import com.aicustomer.entity.FollowUp;
import com.aicustomer.entity.Lead;
import com.aicustomer.repository.EmailDraftRepository;
import com.aicustomer.repository.EmailInboxRepository;
import com.aicustomer.repository.EmailTemplateRepository;
import com.aicustomer.repository.FollowUpRepository;
import com.aicustomer.repository.LeadRepository;
import com.aicustomer.util.TemplateRenderer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 邮件草稿服务：AI 生成的邮件内容保存记录管理（M3 邮件闭环前半段）
 * status: draft → confirmed（确认后待发送，SMTP 发送属 M3 后半段）
 * M2-1.7：基于该客户沟通记录（跟进/已发邮件/客户回复）生成延续性邮件
 */
@Service
public class EmailDraftService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 跟进方式 → 中文 */
    private static final Map<String, String> METHOD_LABELS = Map.of(
            "phone", "电话", "email", "邮件", "wechat", "微信", "visit", "拜访", "other", "其他");

    private final EmailDraftRepository emailDraftRepository;
    private final LeadRepository leadRepository;
    private final FollowUpRepository followUpRepository;
    private final EmailInboxRepository emailInboxRepository;
    private final EmailTemplateRepository emailTemplateRepository;
    private final AiService aiService;

    public EmailDraftService(EmailDraftRepository emailDraftRepository,
                             LeadRepository leadRepository,
                             FollowUpRepository followUpRepository,
                             EmailInboxRepository emailInboxRepository,
                             EmailTemplateRepository emailTemplateRepository,
                             AiService aiService) {
        this.emailDraftRepository = emailDraftRepository;
        this.leadRepository = leadRepository;
        this.followUpRepository = followUpRepository;
        this.emailInboxRepository = emailInboxRepository;
        this.emailTemplateRepository = emailTemplateRepository;
        this.aiService = aiService;
    }

    /** 生成结果：主题 + 正文 */
    public record GenerateResult(String subject, String body) {
    }

    private void requireLead(Long leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw BizException.notFound("客户不存在");
        }
    }

    /**
     * 基于沟通记录生成邮件（M2-1.7，M3-2 增强）：
     * 聚合该客户的跟进记录 + 已确认草稿（=已发出邮件）+ 客户回复邮件，拼成时间线注入 Prompt，
     * 让大模型"记得聊过什么"续写邮件 —— 业务数据即记忆，无需 RAG / ChatMemory。
     * templateId 可选：提供邮件模板作为视觉风格/结构参考，但内容必须结合沟通时间线
     * 重新个性化编写（每次生成措辞、结构、引用事实组合均不同），并输出 HTML 美化正文。
     */
    public GenerateResult generateWithContext(Long leadId, String goal, String tone, Long templateId, String username) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> BizException.notFound("客户不存在"));
        String timeline = buildTimeline(leadId);
        String toneHint = switch (tone == null || tone.isBlank() ? "neutral" : tone) {
            case "formal" -> "语气正式、商务严谨";
            case "friendly" -> "语气亲切、口语化但保持专业";
            default -> "语气中性、自然专业";
        };

        // 可选参考模板：注入模板作为风格参考，AI 需结合沟通历史重新编写而非复制
        String templateHint = "";
        if (templateId != null) {
            EmailTemplate tpl = emailTemplateRepository.findById(templateId)
                    .orElseThrow(() -> BizException.notFound("邮件模板不存在"));
            templateHint = "\n\n【参考模板（仅参考其结构/视觉风格，正文必须重新编写）】\n模板名称：" + tpl.getName() +
                    "\n模板主题示例：" + nz(tpl.getSubject()) +
                    "\n模板正文示例（其中 {companyName}/{contactName} 等 {占位符} 表示客户字段，生成时请替换为上面的真实客户信息）：\n" + nz(tpl.getBody());
        }

        String systemPrompt = """
                你是一名专业的 B2B 销售邮件撰写助手，擅长根据与客户的历史沟通记录延续对话。
                请基于提供的沟通时间线，围绕本次触达目标，撰写一封"延续性"销售邮件。
                要求：
                1. 必须引用沟通记录中的关键事实（客户关注点、上次讨论内容、客户提出的问题），让客户感到"你记得我们聊过什么"
                2. 主题行不超过 20 字，且不得与历史邮件主题重复
                3. 正文用简洁美观的 HTML 输出：用 <p> 分段、<b> 强调关键点、可适当用 <ul><li> 列表与 <span style="color:#1677ff"> 高亮，全部使用内联样式，禁止外部 CSS/JS/图片
                4. 正文 3-6 句话，突出客户价值而非产品推销，自然承接上次沟通，结尾给出一个低门槛的行动邀请（如回复邮件约时间）
                5. 不要重复客户已明确拒绝的内容，不要使用夸张营销用语
                6. 每次生成都必须与历史邮件明显不同：换一种措辞、句子结构与引用事实的组合，即使反复使用同一参考模板也要避免雷同
                7. 客户信息与沟通时间线已提供，正文中的客户名称/公司等直接写真实值，不要输出 {占位符}
                8. 严格按以下两行格式输出，不要输出代码块标记或其它内容：
                主题：<邮件主题>
                正文：<HTML 正文>
                """;
        String userPrompt = "客户信息：\n" + leadInfo(lead) +
                "\n本次触达目标：" + goal +
                "\n语气要求：" + toneHint +
                templateHint +
                "\n\n【与该客户的沟通时间线】\n" + timeline +
                "\n\n请生成邮件。";

        String content = aiService.generate("email_gen_context", systemPrompt, userPrompt, null, username);
        return parseResult(content);
    }

    /** 客户基本盘信息 */
    private String leadInfo(Lead lead) {
        return "- 公司：" + lead.getCompanyName() +
                "\n- 行业：" + nz(lead.getIndustry()) +
                "\n- 联系人：" + nz(lead.getContactName()) +
                "\n- 邮箱：" + nz(lead.getContactEmail()) +
                "\n- 地区：" + nz(lead.getRegion()) +
                "\n- 规模：" + nz(lead.getScale()) +
                "\n- 当前状态：" + statusLabel(lead.getStatus());
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "新线索";
        }
        return switch (status) {
            case "contacted" -> "已触达";
            case "interested" -> "有意向";
            case "converted" -> "已转化";
            case "invalid" -> "无效";
            default -> "新线索";
        };
    }

    /** 聚合三类沟通记录 → 时间线文本 */
    private String buildTimeline(Long leadId) {
        StringBuilder sb = new StringBuilder();
        List<FollowUp> followUps = followUpRepository.findByLeadIdOrderByHappenedAtAsc(leadId);
        List<EmailDraft> drafts = emailDraftRepository.findByLeadIdAndStatusOrderByCreatedAtAsc(leadId, "confirmed");
        List<EmailInbox> inbox = emailInboxRepository.findByLeadIdOrderByReceivedAtAsc(leadId);

        if (followUps.isEmpty() && drafts.isEmpty() && inbox.isEmpty()) {
            return "（暂无沟通记录，按首次触达邮件撰写）";
        }

        followUps.forEach(fu -> sb.append("[")
                .append(fu.getHappenedAt() == null ? "-" : fu.getHappenedAt().format(TS))
                .append("] 我方跟进（").append(METHOD_LABELS.getOrDefault(fu.getMethod(), "其他"))
                .append("）：").append(nz(fu.getContent())).append("\n"));

        drafts.forEach(d -> sb.append("[")
                .append(d.getCreatedAt() == null ? "-" : d.getCreatedAt().format(TS))
                .append("] 我方发出邮件，主题「").append(nz(d.getSubject()))
                .append("」，正文：").append(nz(d.getBody())).append("\n"));

        inbox.forEach(m -> sb.append("[")
                .append(m.getReceivedAt() == null ? "-" : m.getReceivedAt().format(TS))
                .append("] 客户回复，主题「").append(nz(m.getSubject()))
                .append("」，正文：").append(nz(m.getBody())).append("\n"));

        return sb.toString();
    }

    /** 解析 "主题：xxx\n正文：yyy" 格式（正文可为多行 HTML）；异常时主题留空、全文作正文 */
    private GenerateResult parseResult(String content) {
        if (!StringUtils.hasText(content)) {
            throw BizException.badRequest("AI 生成结果为空");
        }
        String text = content.trim();
        // 剥离可能出现的代码块标记（```html ... ```）
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        }
        String subject = "";
        String body = text;
        String[] lines = text.split("\n", 2);
        if (lines.length == 2 && lines[0].startsWith("主题：")) {
            subject = lines[0].substring("主题：".length()).trim();
            String rest = lines[1].trim();
            if (rest.startsWith("正文：")) {
                rest = rest.substring("正文：".length()).trim();
            }
            body = rest;
        }
        if (!StringUtils.hasText(subject)) {
            subject = "";
        }
        return new GenerateResult(subject, body);
    }

    private String nz(String s) {
        return StringUtils.hasText(s) ? s : "（未填写）";
    }

    /**
     * 某客户的邮件草稿列表（按创建时间倒序）。
     * M7.1：与全局草稿箱 listAll 对齐 —— 草稿 = 未发送，只返回 draft + confirmed；
     * 已发送（sent）由发件箱 email_send_log 展示，不再出现在客户详情弹窗的「邮件草稿」区。
     */
    public List<EmailDraft> listByLead(Long leadId) {
        requireLead(leadId);
        return emailDraftRepository.findByLeadIdAndStatusInOrderByCreatedAtDesc(leadId, List.of("draft", "confirmed"));
    }

    /** 全局草稿视图（含关联客户信息） */
    public record EmailDraftView(Long id, Long leadId, String leadCompanyName, String leadContactName,
                                 String subject, String body, String tone, String status,
                                 LocalDateTime createdAt, LocalDateTime confirmedAt) {
    }

    /**
     * 全局邮件草稿分页检索（关键词匹配主题/正文，状态筛选），按创建时间倒序。
     * M6：草稿箱只管理未发送内容 —— 未指定状态时默认仅查 draft + confirmed（已发送的 sent 由发件箱 email_send_log 展示）。
     * 动态拼接条件：避免 NULL 参数进入 SQL 被推断为 bytea。
     */
    public Page<EmailDraftView> listAll(String keyword, String status, int page, int size) {
        Specification<EmailDraft> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), com.aicustomer.common.TenantContext.require()));
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), pattern),
                        cb.like(cb.lower(root.get("body")), pattern)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim()));
            } else {
                predicates.add(root.get("status").in("draft", "confirmed"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<EmailDraft> result = emailDraftRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Set<Long> leadIds = result.getContent().stream()
                .map(EmailDraft::getLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Lead> leads = leadIds.isEmpty() ? Map.of()
                : leadRepository.findAllById(leadIds).stream()
                        .collect(Collectors.toMap(Lead::getId, Function.identity()));
        return result.map(d -> toView(d, leads.get(d.getLeadId())));
    }

    private EmailDraftView toView(EmailDraft d, Lead lead) {
        return new EmailDraftView(d.getId(), d.getLeadId(),
                lead == null ? null : lead.getCompanyName(),
                lead == null ? null : lead.getContactName(),
                d.getSubject(), d.getBody(), d.getTone(), d.getStatus(),
                d.getCreatedAt(), d.getConfirmedAt());
    }

    /**
     * 保存草稿（AI 生成结果落库）：保存时即按客户字段替换占位符（{companyName} {contactName} 等），
     * 草稿箱看到的就是替换后的真实内容；发送时的再次替换为幂等兜底。
     */
    public EmailDraft create(Long leadId, EmailDraft draft) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> BizException.notFound("客户不存在"));
        if (!StringUtils.hasText(draft.getSubject())) {
            throw BizException.badRequest("邮件主题不能为空");
        }
        if (!StringUtils.hasText(draft.getBody())) {
            throw BizException.badRequest("邮件正文不能为空");
        }
        draft.setId(null);
        draft.setLeadId(leadId);
        draft.setTenantId(com.aicustomer.common.TenantContext.require());
        draft.setSubject(TemplateRenderer.render(draft.getSubject(), lead));
        draft.setBody(TemplateRenderer.render(draft.getBody(), lead));
        draft.setStatus(StringUtils.hasText(draft.getStatus()) ? draft.getStatus() : "draft");
        if (!StringUtils.hasText(draft.getTone())) {
            draft.setTone("neutral");
        }
        if ("confirmed".equals(draft.getStatus()) && draft.getConfirmedAt() == null) {
            draft.setConfirmedAt(LocalDateTime.now());
        }
        return emailDraftRepository.save(draft);
    }

    /**
     * 编辑草稿（正文/主题/语气）：保存时同样按客户字段替换占位符，
     * 保证草稿箱内容始终是替换后的真实内容。
     */
    public EmailDraft update(Long leadId, Long id, EmailDraft patch) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> BizException.notFound("客户不存在"));
        EmailDraft exist = emailDraftRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("邮件草稿不存在"));
        if (!exist.getLeadId().equals(leadId)) {
            throw BizException.notFound("邮件草稿不存在");
        }
        if (StringUtils.hasText(patch.getSubject())) {
            exist.setSubject(TemplateRenderer.render(patch.getSubject(), lead));
        }
        if (patch.getBody() != null) {
            if (!StringUtils.hasText(patch.getBody())) {
                throw BizException.badRequest("邮件正文不能为空");
            }
            exist.setBody(TemplateRenderer.render(patch.getBody(), lead));
        }
        if (StringUtils.hasText(patch.getTone())) {
            exist.setTone(patch.getTone());
        }
        return emailDraftRepository.save(exist);
    }

    /** 状态流转：draft ↔ confirmed（confirmed_at 记录确认时间） */
    public EmailDraft changeStatus(Long leadId, Long id, String status) {
        requireLead(leadId);
        EmailDraft exist = emailDraftRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("邮件草稿不存在"));
        if (!exist.getLeadId().equals(leadId)) {
            throw BizException.notFound("邮件草稿不存在");
        }
        if (!"draft".equals(status) && !"confirmed".equals(status)) {
            throw BizException.badRequest("状态仅支持 draft / confirmed");
        }
        exist.setStatus(status);
        exist.setConfirmedAt("confirmed".equals(status) ? LocalDateTime.now() : null);
        return emailDraftRepository.save(exist);
    }

    /** 删除草稿 */
    @Transactional
    public void delete(Long leadId, Long id) {
        EmailDraft exist = emailDraftRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("邮件草稿不存在"));
        if (!exist.getLeadId().equals(leadId)) {
            throw BizException.notFound("邮件草稿不存在");
        }
        emailDraftRepository.delete(exist);
    }
}
