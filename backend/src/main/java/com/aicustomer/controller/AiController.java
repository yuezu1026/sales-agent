package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.service.AiService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI 能力接口：用量看板（需登录）
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    /** 用量看板（D6）：今日/累计/按场景 */
    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage() {
        return ApiResponse.ok(aiService.usageSummary());
    }
}
