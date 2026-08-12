package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.service.EmailDraftService;
import com.aicustomer.service.EmailDraftService.EmailDraftView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 邮件草稿全局管理接口（M2-1.7 补充）：跨客户查看/筛选所有邮件草稿
 * 需登录（/api/** 由 AuthInterceptor 保护）
 */
@RestController
@RequestMapping("/api/email-drafts")
public class EmailDraftGlobalController {

    private final EmailDraftService emailDraftService;

    public EmailDraftGlobalController(EmailDraftService emailDraftService) {
        this.emailDraftService = emailDraftService;
    }

    /** 全局草稿分页列表（关键词 / 状态筛选） */
    @GetMapping
    public ApiResponse<Page<EmailDraftView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(emailDraftService.listAll(keyword, status, page, size));
    }
}
