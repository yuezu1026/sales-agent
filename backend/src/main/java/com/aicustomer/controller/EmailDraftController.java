package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.EmailDraft;
import com.aicustomer.service.EmailDraftService;
import com.aicustomer.service.EmailSendService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 邮件草稿接口（AI 邮件内容保存记录管理）：/api/leads/{leadId}/email-drafts
 * 需登录（JWT 拦截器统一校验）
 */
@RestController
@RequestMapping("/api/leads/{leadId}/email-drafts")
public class EmailDraftController {

    private final EmailDraftService emailDraftService;
    private final EmailSendService emailSendService;

    public EmailDraftController(EmailDraftService emailDraftService, EmailSendService emailSendService) {
        this.emailDraftService = emailDraftService;
        this.emailSendService = emailSendService;
    }

    /** 基于沟通记录生成邮件（M2-1.7/M3-2）：聚合跟进/已发邮件/客户回复续写，返回 主题+正文；
     * templateId 可选：以模板为风格参考，结合沟通历史个性化编写（HTML 美化正文） */
    @PostMapping("/generate")
    public ApiResponse<Map<String, String>> generate(@PathVariable Long leadId,
                                                     @Valid @RequestBody GenerateRequest request,
                                                     HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute(AuthController.ATTR_USERNAME);
        EmailDraftService.GenerateResult result = emailDraftService.generateWithContext(
                leadId, request.goal(), request.tone(), request.templateId(), username);
        return ApiResponse.ok(Map.of("subject", result.subject(), "body", result.body()));
    }

    /** 某客户邮件草稿列表（按创建时间倒序） */
    @GetMapping
    public ApiResponse<List<EmailDraft>> list(@PathVariable Long leadId) {
        return ApiResponse.ok(emailDraftService.listByLead(leadId));
    }

    /** 保存草稿（AI 生成结果落库：subject/body 必填） */
    @PostMapping
    public ApiResponse<EmailDraft> create(@PathVariable Long leadId, @RequestBody EmailDraft draft) {
        return ApiResponse.ok(emailDraftService.create(leadId, draft));
    }

    /** 编辑草稿 */
    @PutMapping("/{id}")
    public ApiResponse<EmailDraft> update(@PathVariable Long leadId, @PathVariable Long id,
                                          @RequestBody EmailDraft draft) {
        return ApiResponse.ok(emailDraftService.update(leadId, id, draft));
    }

    /** 状态流转：draft ↔ confirmed */
    @PutMapping("/{id}/status")
    public ApiResponse<EmailDraft> changeStatus(@PathVariable Long leadId, @PathVariable Long id,
                                                @RequestBody StatusRequest request) {
        return ApiResponse.ok(emailDraftService.changeStatus(leadId, id, request.status()));
    }

    /** 发送草稿（须 confirmed）：SMTP 发送 + 发送记录 + 每日限频，成功/失败均落库 */
    @PostMapping("/{id}/send")
    public ApiResponse<EmailSendService.SendResult> send(@PathVariable Long leadId, @PathVariable Long id) {
        return ApiResponse.ok(emailSendService.sendDraft(leadId, id));
    }

    /** 删除草稿 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long leadId, @PathVariable Long id) {
        emailDraftService.delete(leadId, id);
        return ApiResponse.ok();
    }

    public record StatusRequest(@NotBlank(message = "状态不能为空") String status) {
    }

    /** 基于沟通记录生成：目标必填，语气可选（formal / friendly / neutral），模板 ID 可选 */
    public record GenerateRequest(
            @NotBlank(message = "触达目标不能为空") String goal,
            String tone,
            Long templateId) {
    }
}
