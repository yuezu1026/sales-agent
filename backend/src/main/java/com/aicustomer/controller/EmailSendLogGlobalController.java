package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.service.EmailSendLogService;
import com.aicustomer.service.EmailSendLogService.EmailSendLogView;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * 邮件发送记录全局管理接口（M6 发件箱）：跨客户查看/筛选所有 SMTP 发送记录
 * 需登录（/api/** 由 AuthInterceptor 保护）
 */
@RestController
@RequestMapping("/api/email-send-logs")
public class EmailSendLogGlobalController {

    private final EmailSendLogService emailSendLogService;

    public EmailSendLogGlobalController(EmailSendLogService emailSendLogService) {
        this.emailSendLogService = emailSendLogService;
    }

    /** 全局发送记录分页列表（关键词 / 状态筛选） */
    @GetMapping
    public ApiResponse<Page<EmailSendLogView>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(emailSendLogService.listAll(keyword, status, page, size));
    }

    /** 删除一条发送记录（发件箱管理） */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        emailSendLogService.delete(id);
        return ApiResponse.ok(null);
    }
}
