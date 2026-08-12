package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.repository.EmailSendLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件效果统计（M4-6 打开率追踪看板）：发送数 / 打开数 / 打开率 / 点击数 / 点击率
 * 基于 email_send_log：sent 为实际投递成功的邮件数；打开/点击以追踪像素与链接回传为准。
 */
@RestController
@RequestMapping("/api/email-stats")
public class EmailStatsController {

    private final EmailSendLogRepository emailSendLogRepository;

    public EmailStatsController(EmailSendLogRepository emailSendLogRepository) {
        this.emailSendLogRepository = emailSendLogRepository;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> stats() {
        long sent = emailSendLogRepository.countByStatus("sent");
        long opened = emailSendLogRepository.countByOpenedAtIsNotNull();
        long clicked = emailSendLogRepository.countByClickedAtIsNotNull();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sent", sent);
        result.put("opened", opened);
        result.put("openRate", rate(opened, sent));
        result.put("clicked", clicked);
        result.put("clickRate", rate(clicked, sent));
        return ApiResponse.ok(result);
    }

    /** 百分比（一位小数），分母为 0 时返回 0 */
    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }
}
