package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailInbox;
import com.aicustomer.entity.FollowUp;
import com.aicustomer.entity.Lead;
import com.aicustomer.entity.Tenant;
import com.aicustomer.mcp.EmailMailboxService;
import com.aicustomer.mcp.EmailMailboxService.MailItem;
import com.aicustomer.repository.EmailInboxRepository;
import com.aicustomer.repository.FollowUpRepository;
import com.aicustomer.repository.LeadRepository;
import com.aicustomer.repository.TenantRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收件箱服务（M2-1.6）：MCP 同步客户回复邮件 → 落库管理
 * 能力：同步 / 检索 / 详情 / 已读 / 转跟进 / AI 意图分析 / 删除
 */
@Service
public class EmailInboxService {

    private static final Logger log = LoggerFactory.getLogger(EmailInboxService.class);

    private static final String MAILBOX = "INBOX";
    private static final int SYNC_LIMIT = 100;
    private static final int FOLLOWUP_BODY_SNIPPET = 500;
    private static final Set<String> VALID_INTENTS =
            Set.of("inquiry", "quote", "objection", "followup", "positive", "other");
    private static final Set<String> VALID_METHODS =
            Set.of("phone", "email", "wechat", "visit", "other");

    private final EmailInboxRepository emailInboxRepository;
    private final LeadRepository leadRepository;
    private final FollowUpRepository followUpRepository;
    private final EmailMailboxService mailboxService;
    private final AiService aiService;
    private final ObjectMapper objectMapper;
    private final TenantRepository tenantRepository;

    public EmailInboxService(EmailInboxRepository emailInboxRepository,
                             LeadRepository leadRepository,
                             FollowUpRepository followUpRepository,
                             EmailMailboxService mailboxService,
                             AiService aiService,
                             ObjectMapper objectMapper,
                             TenantRepository tenantRepository) {
        this.emailInboxRepository = emailInboxRepository;
        this.leadRepository = leadRepository;
        this.followUpRepository = followUpRepository;
        this.mailboxService = mailboxService;
        this.aiService = aiService;
        this.objectMapper = objectMapper;
        this.tenantRepository = tenantRepository;
    }

    /** 收件箱视图（含关联客户信息） */
    public record EmailInboxView(Long id, Long leadId, String leadCompanyName, String leadContactName,
                                 String fromAddress, String fromName, String subject, String body,
                                 LocalDateTime receivedAt, boolean isRead, String aiIntent, String aiSummary,
                                 String aiAnalysisStatus, LocalDateTime createdAt) {
    }

    /** 通过 MCP 抓取邮箱邮件并入库（按 uid 去重，自动关联客户） */
    public Map<String, Object> sync() {
        // 只同步客户管理中已配置邮箱的客户发来的邮件（发件人白名单过滤）
        List<String> customerEmails = leadRepository.findDistinctContactEmails();
        if (customerEmails.isEmpty()) {
            log.info("收件箱同步跳过：客户管理中没有已配置邮箱的客户");
            return Map.of("added", 0, "total", emailInboxRepository.count(), "filteredBy", List.of());
        }
        // 第一步：只拉元数据（不含正文，列表快）
        List<MailItem> items = mailboxService.listEmails(MAILBOX, SYNC_LIMIT, false, null, customerEmails, false);
        if (items.isEmpty()) {
            return Map.of("added", 0, "total", emailInboxRepository.count(), "filteredBy", customerEmails);
        }
        // 第二步：批量查已入库 uid，只保留新邮件（避免对已入库邮件重复取正文）
        List<Long> allUids = items.stream().map(MailItem::uid).toList();
        Set<Long> existingUids = new HashSet<>(emailInboxRepository.findUidsByMailboxAndUidIn(MAILBOX, allUids));
        List<MailItem> newItems = items.stream().filter(i -> !existingUids.contains(i.uid())).toList();
        if (newItems.isEmpty()) {
            log.info("收件箱同步完成：无新邮件（白名单 {}）", customerEmails);
            return Map.of("added", 0, "total", emailInboxRepository.count(), "filteredBy", customerEmails);
        }
        // 第三步：只对新邮件批量取正文（一次 MCP 调用 + 一次 IMAP 连接）
        List<Long> newUids = newItems.stream().map(MailItem::uid).toList();
        Map<Long, MailItem> byUid = mailboxService.readEmails(newUids).stream()
                .collect(Collectors.toMap(MailItem::uid, Function.identity(), (a, b) -> a));
        // 第四步：批量查发件人邮箱 → 客户 id 映射（避免逐封 findFirstByContactEmailIgnoreCase）
        Map<String, Long> leadIdByEmail = leadRepository.findByContactEmailInIgnoreCase(customerEmails).stream()
                .filter(l -> StringUtils.hasText(l.getContactEmail()))
                .collect(Collectors.toMap(l -> l.getContactEmail().trim().toLowerCase(),
                        Lead::getId, (a, b) -> a));
        // 第五步：组装 + 批量入库（saveAll 一次事务；并发撞唯一约束时回退逐封跳过）
        List<EmailInbox> toSave = new ArrayList<>(newItems.size());
        for (MailItem item : newItems) {
            MailItem full = byUid.getOrDefault(item.uid(), item);
            EmailInbox inbox = new EmailInbox();
            inbox.setTenantId(TenantContext.require());
            inbox.setMailbox(MAILBOX);
            inbox.setUid(full.uid());
            inbox.setMessageId(full.messageId());
            inbox.setFromAddress(full.fromAddress());
            inbox.setFromName(full.fromName());
            inbox.setToAddress(full.toAddress());
            inbox.setSubject(full.subject());
            inbox.setBody(full.body());
            inbox.setReceivedAt(full.receivedAt());
            inbox.setIsRead(full.isRead());
            if (StringUtils.hasText(full.fromAddress())) {
                inbox.setLeadId(leadIdByEmail.get(full.fromAddress().trim().toLowerCase()));
            }
            toSave.add(inbox);
        }
        int added = 0;
        try {
            emailInboxRepository.saveAll(toSave);
            added = toSave.size();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 定时任务与手动同步并发时可能同时插入同一 uid → 回退逐封保存，冲突的跳过
            log.debug("批量入库撞唯一约束，回退逐封保存（并发）");
            for (EmailInbox in : toSave) {
                try {
                    emailInboxRepository.save(in);
                    added++;
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    log.debug("邮件已存在（并发跳过）uid={}", in.getUid());
                }
            }
        }
        long total = emailInboxRepository.count();
        log.info("收件箱同步完成：新增 {} 封，累计 {}（发件人白名单 {}）", added, total, customerEmails);
        return Map.of("added", added, "total", total, "filteredBy", customerEmails);
    }

    /**
     * 定时同步（M2-1.6）：按 app.email.sync-cron 周期抓取新邮件。
     * SaaS：定时任务无请求上下文 → 遍历所有租户逐个设置租户上下文后同步。
     * 失败仅记录日志，不影响主流程；cron 留空则禁用。
     */
    @Scheduled(cron = "${app.email.sync-cron:}")
    public void scheduledSync() {
        List<Long> tenantIds = tenantRepository.findAll().stream().map(Tenant::getId).toList();
        if (tenantIds.isEmpty()) {
            return;
        }
        for (Long tenantId : tenantIds) {
            TenantContext.set(tenantId);
            try {
                Map<String, Object> result = sync();
                log.info("定时收件箱同步[租户{}]：新增 {} 封，累计 {}", tenantId, result.get("added"), result.get("total"));
            } catch (Exception e) {
                log.error("定时收件箱同步[租户{}]失败", tenantId, e);
            } finally {
                TenantContext.clear();
            }
        }
    }

    /** 收件箱分页检索（关键词 / 未读 / 客户），按接收时间倒序 */
    public Page<EmailInboxView> list(String keyword, boolean unreadOnly, Long leadId, int page, int size) {
        Specification<EmailInbox> spec = buildSpec(keyword, unreadOnly, leadId);
        Page<EmailInbox> result = emailInboxRepository.findAll(spec,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "receivedAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Set<Long> leadIds = result.getContent().stream()
                .map(EmailInbox::getLeadId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // 注意：leadIds 为空（如演示模式 mock 邮件全部无关联客户）时不能用 Map.of()，
        // 其 get(null) 会抛 NPE（Map.of 不允许 null key）→ 用 HashMap 允许 null key
        Map<Long, Lead> leads = new HashMap<>();
        if (!leadIds.isEmpty()) {
            leadRepository.findAllById(leadIds)
                    .forEach(l -> leads.put(l.getId(), l));
        }
        return result.map(e -> toView(e, leads.get(e.getLeadId())));
    }

    /** 动态拼接检索条件：避免 NULL 参数进入 SQL 被推断为 bytea */
    private Specification<EmailInbox> buildSpec(String keyword, boolean unreadOnly, Long leadId) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("tenantId"), TenantContext.require()));
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fromAddress")), pattern),
                        cb.like(cb.lower(root.get("fromName")), pattern),
                        cb.like(cb.lower(root.get("subject")), pattern)));
            }
            if (unreadOnly) {
                predicates.add(cb.isFalse(root.get("isRead")));
            }
            if (leadId != null) {
                predicates.add(cb.equal(root.get("leadId"), leadId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** 邮件详情 */
    public EmailInboxView get(Long id) {
        EmailInbox inbox = require(id);
        Lead lead = inbox.getLeadId() == null ? null : leadRepository.findById(inbox.getLeadId()).orElse(null);
        return toView(inbox, lead);
    }

    /** 标记已读 / 未读（同步更新邮箱端与本地） */
    public EmailInbox markRead(Long id, boolean read) {
        EmailInbox inbox = require(id);
        inbox.setIsRead(read);
        EmailInbox saved = emailInboxRepository.save(inbox);
        try {
            mailboxService.markRead(saved.getUid(), read);
        } catch (Exception e) {
            log.warn("同步邮箱端已读状态失败 uid={}: {}", saved.getUid(), e.getMessage());
        }
        return saved;
    }

    /** 一键转跟进记录（需已关联客户） */
    @Transactional
    public FollowUp convertToFollowUp(Long id, String method) {
        EmailInbox inbox = require(id);
        if (inbox.getLeadId() == null) {
            throw BizException.badRequest("该邮件尚未关联客户，无法转为跟进记录");
        }
        String m = StringUtils.hasText(method) ? method : "email";
        if (!VALID_METHODS.contains(m)) {
            throw BizException.badRequest("跟进方式仅支持 phone / email / wechat / visit / other");
        }
        FollowUp followUp = new FollowUp();
        followUp.setTenantId(TenantContext.require());
        followUp.setLeadId(inbox.getLeadId());
        followUp.setMethod(m);
        String bodySnippet = inbox.getBody();
        if (bodySnippet != null && bodySnippet.length() > FOLLOWUP_BODY_SNIPPET) {
            bodySnippet = bodySnippet.substring(0, FOLLOWUP_BODY_SNIPPET) + "…";
        }
        followUp.setContent("邮件回复：《" + (inbox.getSubject() == null ? "" : inbox.getSubject()) + "》\n"
                + "发件人：" + (inbox.getFromName() == null ? "" : inbox.getFromName())
                + " <" + inbox.getFromAddress() + ">\n\n" + (bodySnippet == null ? "" : bodySnippet));
        followUp.setHappenedAt(LocalDateTime.now());
        return followUpRepository.save(followUp);
    }

    /** AI 意图分析 + 回复建议（结果回写邮件记录） */
    public Map<String, String> analyze(Long id, String username) {
        EmailInbox inbox = require(id);
        String system = """
                你是一名专业的 B2B 销售邮件分析助手。
                请分析客户发来的回复邮件，识别其意图并给出回复建议，只输出一个 JSON 对象（不要 markdown 围栏、不要其他文字）：
                {"intent":"inquiry|quote|objection|followup|positive|other","summary":"一句话中文摘要","replySubject":"建议回复主题（不超过20字）","replyBody":"建议回复正文（2-3句中文，专业且有温度）"}
                intent 取值说明：inquiry=询价咨询；quote=要求报价；objection=异议或价格顾虑；followup=约定后续跟进；positive=积极意向；other=其他
                """;
        String user = "发件人：" + (inbox.getFromName() == null ? "" : inbox.getFromName())
                + " <" + inbox.getFromAddress() + ">\n主题：" + (inbox.getSubject() == null ? "" : inbox.getSubject())
                + "\n正文：\n" + (inbox.getBody() == null ? "" : inbox.getBody());
        try {
            String content = aiService.generate("email_analyze", system, user, null, username);
            JsonNode node = objectMapper.readTree(cleanJson(content));
            String intent = node.path("intent").asText("other").trim().toLowerCase();
            if (!VALID_INTENTS.contains(intent)) {
                intent = "other";
            }
            String summary = node.path("summary").asText("").trim();
            String replySubject = node.path("replySubject").asText("").trim();
            String replyBody = node.path("replyBody").asText("").trim();
            inbox.setAiIntent(intent);
            inbox.setAiSummary(summary);
            inbox.setAiAnalysisStatus("analyzed");
            emailInboxRepository.save(inbox);
            return Map.of("intent", intent, "summary", summary,
                    "replySubject", replySubject, "replyBody", replyBody, "status", "analyzed");
        } catch (Exception e) {
            inbox.setAiAnalysisStatus("failed");
            emailInboxRepository.save(inbox);
            if (e instanceof BizException biz) {
                throw biz;
            }
            throw new IllegalStateException("AI 分析失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** 删除邮件 */
    @Transactional
    public void delete(Long id) {
        emailInboxRepository.delete(require(id));
    }

    private EmailInbox require(Long id) {
        return emailInboxRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("邮件不存在"));
    }

    private EmailInboxView toView(EmailInbox e, Lead lead) {
        return new EmailInboxView(
                e.getId(), e.getLeadId(),
                lead == null ? null : lead.getCompanyName(),
                lead == null ? null : lead.getContactName(),
                e.getFromAddress(), e.getFromName(), e.getSubject(), e.getBody(),
                e.getReceivedAt(), Boolean.TRUE.equals(e.getIsRead()),
                e.getAiIntent(), e.getAiSummary(), e.getAiAnalysisStatus(), e.getCreatedAt());
    }

    private String cleanJson(String s) {
        String t = s == null ? "" : s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\n?", "").trim();
            t = t.replaceAll("```$", "").trim();
        }
        return t;
    }
}
