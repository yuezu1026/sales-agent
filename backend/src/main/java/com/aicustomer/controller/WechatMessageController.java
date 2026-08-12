package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.WechatMessage;
import com.aicustomer.service.WechatMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 微信沟通接口（M2-1.8 记录式工作台）：/api/leads/{leadId}/wechat-messages
 * 需登录（JWT 拦截器统一校验）
 */
@RestController
@RequestMapping("/api/leads/{leadId}/wechat-messages")
public class WechatMessageController {

    private final WechatMessageService wechatMessageService;

    public WechatMessageController(WechatMessageService wechatMessageService) {
        this.wechatMessageService = wechatMessageService;
    }

    /** 某客户微信会话消息列表（按发生时间正序） */
    @GetMapping
    public ApiResponse<List<WechatMessage>> list(@PathVariable Long leadId) {
        return ApiResponse.ok(wechatMessageService.listByLead(leadId));
    }

    /** 记录一条微信消息（direction=in/out，content 必填） */
    @PostMapping
    public ApiResponse<WechatMessage> create(@PathVariable Long leadId, @RequestBody WechatMessage message) {
        return ApiResponse.ok(wechatMessageService.create(leadId, message));
    }

    /** AI 生成微信回复建议（基于客户画像 + 沟通时间线），返回 { reply } */
    @PostMapping("/suggest")
    public ApiResponse<Map<String, String>> suggest(@PathVariable Long leadId,
                                                    @Valid @RequestBody SuggestRequest request,
                                                    HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute(AuthController.ATTR_USERNAME);
        String reply = wechatMessageService.suggestReply(leadId, request.goal(), request.tone(), username);
        return ApiResponse.ok(Map.of("reply", reply == null ? "" : reply.trim()));
    }

    /** 编辑消息 */
    @PutMapping("/{id}")
    public ApiResponse<WechatMessage> update(@PathVariable Long leadId, @PathVariable Long id,
                                             @RequestBody WechatMessage message) {
        return ApiResponse.ok(wechatMessageService.update(leadId, id, message));
    }

    /** 删除消息 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long leadId, @PathVariable Long id) {
        wechatMessageService.delete(leadId, id);
        return ApiResponse.ok();
    }

    /** AI 生成回复：goal/tone 均可选（tone: formal / friendly / neutral） */
    public record SuggestRequest(String goal, String tone) {
    }
}
