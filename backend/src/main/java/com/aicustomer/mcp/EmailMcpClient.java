package com.aicustomer.mcp;

import com.aicustomer.common.BizException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP Client（M2-1.6）
 * 用 MCP Java SDK 手动构建 McpSyncClient，通过 Streamable HTTP 调用本服务
 * 的 /mcp 端点上的邮箱工具（email_list_emails / email_read_email / email_mark_read）。
 *
 * 说明：每次调用创建短连接（initialize → callTool → closeGracefully），
 * 避免与自身 Server 保持长连接占用资源，也规避应用启动时自连未就绪的问题。
 */
@Service
public class EmailMcpClient {

    private static final Logger log = LoggerFactory.getLogger(EmailMcpClient.class);

    private static final String ENDPOINT = "/mcp";

    private final String serverUrl;
    private final ObjectMapper objectMapper;

    public EmailMcpClient(@Value("${app.email.mcp-server-url:http://localhost:8080/mcp}") String serverUrl,
                          ObjectMapper objectMapper) {
        this.serverUrl = serverUrl;
        this.objectMapper = objectMapper;
    }

    /** 抓取邮件列表（元数据；includeBody=true 时同时返回正文） */
    public List<EmailMailboxService.MailItem> listEmails(int limit, boolean unreadOnly, List<String> fromEmails,
                                                         boolean includeBody) {
        String fromStr = fromEmails == null || fromEmails.isEmpty() ? "" : String.join(",", fromEmails);
        String json = callText("email_list_emails",
                Map.of("folder", "INBOX", "limit", limit, "unread_only", unreadOnly,
                        "since", "", "from_emails", fromStr, "include_body", includeBody));
        try {
            return objectMapper.readValue(json, new TypeReference<List<EmailMailboxService.MailItem>>() {
            });
        } catch (Exception e) {
            throw BizException.badRequest("解析邮件列表失败：" + e.getMessage());
        }
    }

    /** 读取单封邮件（含正文） */
    public EmailMailboxService.MailItem readEmail(long uid) {
        String json = callText("email_read_email", Map.of("uid", uid));
        try {
            return objectMapper.readValue(json, EmailMailboxService.MailItem.class);
        } catch (Exception e) {
            throw BizException.badRequest("解析邮件失败：" + e.getMessage());
        }
    }

    /** 批量读取邮件（含正文）：一次 MCP 调用 + 一次 IMAP 连接 */
    public List<EmailMailboxService.MailItem> readEmails(List<Long> uids) {
        if (uids == null || uids.isEmpty()) {
            return List.of();
        }
        String uidStr = uids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String json = callText("email_read_emails", Map.of("uids", uidStr));
        try {
            return objectMapper.readValue(json, new TypeReference<List<EmailMailboxService.MailItem>>() {
            });
        } catch (Exception e) {
            throw BizException.badRequest("解析邮件失败：" + e.getMessage());
        }
    }

    /** 标记已读 / 未读 */
    public void markRead(long uid, boolean read) {
        callText("email_mark_read", Map.of("uid", uid, "is_read", read));
    }

    // ==================== 内部：MCP 调用封装 ====================

    private String callText(String tool, Map<String, Object> arguments) {
        McpSyncClient client = newClient();
        try {
            client.initialize();
            CallToolResult result = client.callTool(
                    CallToolRequest.builder(tool).arguments(arguments).build());
            if (result.isError()) {
                throw BizException.badRequest("MCP 工具调用失败：" + extractText(result));
            }
            return extractText(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("MCP 调用 {} 失败: {}", tool, e.getMessage());
            throw BizException.badRequest("MCP 邮箱服务调用失败：" + e.getMessage());
        } finally {
            closeQuietly(client);
        }
    }

    private McpSyncClient newClient() {
        String baseUrl = serverUrl;
        if (serverUrl.endsWith(ENDPOINT)) {
            baseUrl = serverUrl.substring(0, serverUrl.length() - ENDPOINT.length());
        }
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(baseUrl)
                .endpoint(ENDPOINT)
                .build();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(20))
                .build();
    }

    private String extractText(CallToolResult result) {
        if (result.content() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Content c : result.content()) {
            if (c instanceof TextContent t && t.text() != null) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    private void closeQuietly(McpSyncClient client) {
        try {
            if (client != null) {
                client.closeGracefully();
            }
        } catch (Exception ignored) {
        }
    }
}
