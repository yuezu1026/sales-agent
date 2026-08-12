package com.aicustomer.mcp;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aicustomer.service.EmailSendService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * MCP 邮箱工具（M2-1.6）
 * 通过 Spring AI @McpTool 注解注册，由 spring-ai-starter-mcp-server-webmvc
 * 自动扫描并暴露在 Streamable HTTP /mcp 端点，供 MCP Client 调用。
 * 工具返回 JSON 字符串（sync 模式，非 reactive）。
 */
@Component
public class EmailMcpTools {

    private static final Logger log = LoggerFactory.getLogger(EmailMcpTools.class);

    private final EmailMailboxService mailboxService;
    private final EmailSendService emailSendService;
    private final ObjectMapper objectMapper;

    public EmailMcpTools(EmailMailboxService mailboxService, EmailSendService emailSendService,
                         ObjectMapper objectMapper) {
        this.mailboxService = mailboxService;
        this.emailSendService = emailSendService;
        this.objectMapper = objectMapper;
    }

    /** 列出邮件（返回邮件元数据列表，默认不含正文，按接收时间倒序） */
    @McpTool(name = "email_list_emails",
            description = "列出邮箱收件箱中的邮件（含发件人、主题、时间、已读状态）。"
                    + "参数：folder 文件夹（默认 INBOX）；limit 最大条数（默认 20）；"
                    + "unread_only 是否只看未读；since 起始时间（ISO-8601，只返回该时间之后的邮件）；"
                    + "from_emails 发件人白名单（逗号分隔邮箱，只返回这些发件人的邮件）；"
                    + "include_body 是否同时返回正文（默认 false）")
    public String listEmails(
            @McpToolParam(description = "文件夹名称，默认 INBOX", required = false) String folder,
            @McpToolParam(description = "最大返回条数，默认 20", required = false) Integer limit,
            @McpToolParam(description = "是否只看未读邮件", required = false) Boolean unread_only,
            @McpToolParam(description = "起始时间（ISO-8601，如 2026-08-01T00:00:00），只返回该时间之后的邮件", required = false) String since,
            @McpToolParam(description = "发件人白名单（逗号分隔的邮箱地址），只返回这些发件人的邮件；空=不过滤", required = false) String from_emails,
            @McpToolParam(description = "是否同时返回邮件正文，默认 false", required = false) Boolean include_body) {
        try {
            // SaaS：MCP 工具无请求上下文（外部 AI Agent 经 /mcp 调用）→ 绑定默认租户 1
            com.aicustomer.common.TenantContext.set(1L);
            LocalDateTime sinceTime = parseSince(since);
            List<EmailMailboxService.MailItem> items = mailboxService.listEmails(
                    folder != null ? folder : "INBOX",
                    limit != null ? limit : 20,
                    Boolean.TRUE.equals(unread_only),
                    sinceTime,
                    parseFromEmails(from_emails),
                    Boolean.TRUE.equals(include_body));
            return toJson(items);
        } catch (Exception e) {
            log.warn("email_list_emails 调用失败: {}", e.getMessage());
            return toError(e);
        } finally {
            com.aicustomer.common.TenantContext.clear();
        }
    }

    /** 读取单封邮件完整内容（含正文） */
    @McpTool(name = "email_read_email",
            description = "读取单封邮件的完整内容（含正文）。参数：uid（邮件 UID）")
    public String readEmail(
            @McpToolParam(description = "邮件 UID（来自 email_list_emails 返回值）", required = true) Long uid) {
        try {
            com.aicustomer.common.TenantContext.set(1L);
            return toJson(mailboxService.readEmail(uid));
        } catch (Exception e) {
            log.warn("email_read_email 调用失败: {}", e.getMessage());
            return toError(e);
        } finally {
            com.aicustomer.common.TenantContext.clear();
        }
    }

    /** 批量读取多封邮件完整内容（含正文）：一次 IMAP 连接批量取，供同步优化使用 */
    @McpTool(name = "email_read_emails",
            description = "批量读取多封邮件的完整内容（含正文）。参数：uids（逗号分隔的邮件 UID 列表）")
    public String readEmails(
            @McpToolParam(description = "邮件 UID 列表（逗号分隔），来自 email_list_emails 返回值", required = true) String uids) {
        try {
            com.aicustomer.common.TenantContext.set(1L);
            List<Long> uidList = parseUids(uids);
            return toJson(mailboxService.readEmails(uidList));
        } catch (Exception e) {
            log.warn("email_read_emails 调用失败: {}", e.getMessage());
            return toError(e);
        } finally {
            com.aicustomer.common.TenantContext.clear();
        }
    }

    /** 发送邮件（SMTP）：薄封装 EmailSendService，含发送记录与每日限频 */
    @McpTool(name = "email_send_email",
            description = "发送一封已确认的邮件草稿（SMTP）。参数：lead_id（客户 ID）；draft_id（邮件草稿 ID，须为 confirmed 待发状态）。"
                    + "发送成功/失败均写入发送记录，发送成功后草稿状态变为 sent，重复发送会返回失败。")
    public String sendEmail(
            @McpToolParam(description = "客户 ID", required = true) Long lead_id,
            @McpToolParam(description = "邮件草稿 ID（须已标记待发 confirmed）", required = true) Long draft_id) {
        try {
            com.aicustomer.common.TenantContext.set(1L);
            EmailSendService.SendResult result = emailSendService.sendDraft(lead_id, draft_id);
            return toJson(result);
        } catch (Exception e) {
            log.warn("email_send_email 调用失败: {}", e.getMessage());
            return toError(e);
        } finally {
            com.aicustomer.common.TenantContext.clear();
        }
    }

    /** 标记已读 / 未读 */
    @McpTool(name = "email_mark_read",
            description = "标记邮件为已读或未读。参数：uid（邮件 UID）；is_read（true=已读，false=未读）")
    public String markRead(
            @McpToolParam(description = "邮件 UID", required = true) Long uid,
            @McpToolParam(description = "是否已读，默认 true", required = false) Boolean is_read) {
        try {
            com.aicustomer.common.TenantContext.set(1L);
            mailboxService.markRead(uid, !Boolean.FALSE.equals(is_read));
            return toJson(Map.of("ok", true));
        } catch (Exception e) {
            log.warn("email_mark_read 调用失败: {}", e.getMessage());
            return toError(e);
        } finally {
            com.aicustomer.common.TenantContext.clear();
        }
    }

    private LocalDateTime parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(since);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            return "{}";
        }
    }

    private String toError(Exception e) {
        return toJson(Map.of("error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }

    /** 解析发件人白名单（逗号分隔邮箱，去空去重） */
    private List<String> parseFromEmails(String fromEmails) {
        if (fromEmails == null || fromEmails.isBlank()) {
            return null;
        }
        return java.util.Arrays.stream(fromEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /** 解析 uid 列表（逗号分隔，去空去重） */
    private List<Long> parseUids(String uids) {
        if (uids == null || uids.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(uids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .distinct()
                .toList();
    }
}
