package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.entity.EmailSendLog;
import com.aicustomer.repository.EmailSendLogRepository;
import com.aicustomer.repository.LeadRepository;
import com.aicustomer.service.EmailSendService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邮件发送记录接口：/api/leads/{leadId}/email-send-logs
 * M3-2 补充：发送历史查询（客户维度）+ 失败记录重试
 * 需登录（JWT 拦截器统一校验）
 */
@RestController
@RequestMapping("/api/leads/{leadId}/email-send-logs")
public class EmailSendLogController {

    private final EmailSendLogRepository emailSendLogRepository;
    private final LeadRepository leadRepository;
    private final EmailSendService emailSendService;

    public EmailSendLogController(EmailSendLogRepository emailSendLogRepository,
                                  LeadRepository leadRepository,
                                  EmailSendService emailSendService) {
        this.emailSendLogRepository = emailSendLogRepository;
        this.leadRepository = leadRepository;
        this.emailSendService = emailSendService;
    }

    private void requireLead(Long leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw BizException.notFound("客户不存在");
        }
    }

    /** 某客户邮件发送记录（按创建时间倒序：最新在前） */
    @GetMapping
    public ApiResponse<List<EmailSendLog>> list(@PathVariable Long leadId) {
        requireLead(leadId);
        return ApiResponse.ok(emailSendLogRepository.findByLeadIdOrderByCreatedAtDesc(leadId));
    }

    /** 重试失败的发送记录（仅 failed 可重试，重新走 SMTP 投递并新落一条记录） */
    @PostMapping("/{id}/retry")
    public ApiResponse<EmailSendService.SendResult> retry(@PathVariable Long leadId,
                                                          @PathVariable Long id) {
        return ApiResponse.ok(emailSendService.retry(leadId, id));
    }
}
