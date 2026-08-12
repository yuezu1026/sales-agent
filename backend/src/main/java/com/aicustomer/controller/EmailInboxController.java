package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.EmailInbox;
import com.aicustomer.entity.FollowUp;
import com.aicustomer.service.EmailInboxService;
import com.aicustomer.service.EmailInboxService.EmailInboxView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 收件箱接口（M2-1.6）：客户回复邮件抓取与管理
 * 需登录（/api/** 由 AuthInterceptor 保护）
 */
@RestController
@RequestMapping("/api/emails/inbox")
public class EmailInboxController {

    private final EmailInboxService emailInboxService;

    public EmailInboxController(EmailInboxService emailInboxService) {
        this.emailInboxService = emailInboxService;
    }

    /** 收件箱分页列表（关键词 / 未读筛选 / 客户筛选） */
    @GetMapping
    public ApiResponse<Page<EmailInboxView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Long leadId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(emailInboxService.list(keyword, unreadOnly, leadId, page, size));
    }

    /** 邮件详情（含正文） */
    @GetMapping("/{id}")
    public ApiResponse<EmailInboxView> get(@PathVariable Long id) {
        return ApiResponse.ok(emailInboxService.get(id));
    }

    /** 手动触发 MCP 同步（返回新增数） */
    @PostMapping("/sync")
    public ApiResponse<Map<String, Object>> sync() {
        return ApiResponse.ok(emailInboxService.sync());
    }

    /** 标记已读 / 未读 */
    @PutMapping("/{id}/read")
    public ApiResponse<EmailInbox> markRead(@PathVariable Long id,
                                            @RequestBody MarkReadRequest request) {
        return ApiResponse.ok(emailInboxService.markRead(id, request.read()));
    }

    /** 一键转跟进记录 */
    @PostMapping("/{id}/convert-follow-up")
    public ApiResponse<FollowUp> convertToFollowUp(@PathVariable Long id,
                                                   @RequestBody(required = false) ConvertRequest request) {
        return ApiResponse.ok(emailInboxService.convertToFollowUp(id,
                request == null ? null : request.method()));
    }

    /** AI 意图分析 + 回复建议 */
    @PostMapping("/{id}/analyze")
    public ApiResponse<Map<String, String>> analyze(@PathVariable Long id,
                                                    HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute(AuthController.ATTR_USERNAME);
        return ApiResponse.ok(emailInboxService.analyze(id, username));
    }

    /** 删除邮件 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        emailInboxService.delete(id);
        return ApiResponse.ok();
    }

    public record MarkReadRequest(boolean read) {
    }

    public record ConvertRequest(String method) {
    }
}
