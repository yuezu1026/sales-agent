package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.entity.FollowUp;
import com.aicustomer.service.FollowUpService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 跟进记录接口（客户跟踪留痕）：GET/POST/PUT/DELETE /api/leads/{leadId}/follow-ups
 * 需登录（JWT 拦截器统一校验）
 */
@RestController
@RequestMapping("/api/leads/{leadId}/follow-ups")
public class FollowUpController {

    private final FollowUpService followUpService;

    public FollowUpController(FollowUpService followUpService) {
        this.followUpService = followUpService;
    }

    /** 某客户跟进记录列表（按跟进时间倒序） */
    @GetMapping
    public ApiResponse<List<FollowUp>> list(@PathVariable Long leadId) {
        return ApiResponse.ok(followUpService.listByLead(leadId));
    }

    /** 新增跟进记录（content 必填） */
    @PostMapping
    public ApiResponse<FollowUp> create(@PathVariable Long leadId, @RequestBody FollowUp followUp) {
        return ApiResponse.ok(followUpService.create(leadId, followUp));
    }

    /** 编辑跟进记录 */
    @PutMapping("/{id}")
    public ApiResponse<FollowUp> update(@PathVariable Long leadId, @PathVariable Long id,
                                        @RequestBody FollowUp followUp) {
        return ApiResponse.ok(followUpService.update(leadId, id, followUp));
    }

    /** 删除跟进记录 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long leadId, @PathVariable Long id) {
        followUpService.delete(leadId, id);
        return ApiResponse.ok();
    }
}
