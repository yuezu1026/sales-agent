package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailUnsubscribe;
import com.aicustomer.entity.EmailUnsubscribeId;
import com.aicustomer.repository.EmailUnsubscribeRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 退订接口（SaaS 化：退订按租户隔离，公开链接携带 tenantId 参数定位租户）：
 * - GET /api/unsubscribe?email=xxx&tenantId=yyy   公开（JWT 拦截器 exclude，见 WebConfig）——收件人无登录态
 * - GET /api/unsubscribe/list                      需登录——后台查看本租户退订黑名单
 * - DELETE /api/unsubscribe/{email}                需登录——恢复邮箱（移除黑名单，误操作恢复）
 * M3-2 合规闭环：点击退订链接即生效，后续不再向该邮箱发送营销邮件（sendDraft 前置拦截）。
 */
@RestController
@RequestMapping("/api/unsubscribe")
public class UnsubscribeController {

    private final EmailUnsubscribeRepository unsubscribeRepository;

    public UnsubscribeController(EmailUnsubscribeRepository unsubscribeRepository) {
        this.unsubscribeRepository = unsubscribeRepository;
    }

    /**
     * 退订（幂等，按租户）：email + tenantId 定位记录。
     * tenantId 缺失时回退 1（默认租户，兼容旧版链接）。
     * 返回结构供前端落地页渲染：{ email, status: "unsubscribed" | "already", message }
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> unsubscribe(@RequestParam("email") String email,
                                                        @RequestParam(value = "tenantId", required = false) Long tenantId) {
        String normalized = email == null ? null : email.trim().toLowerCase();
        if (!StringUtils.hasText(normalized) || !normalized.contains("@")) {
            return ApiResponse.ok(Map.of(
                    "status", "invalid",
                    "message", "退订链接无效，请检查邮箱参数"
            ));
        }
        Long tid = tenantId == null ? 1L : tenantId;
        boolean already = unsubscribeRepository.existsByTenantIdAndEmail(tid, normalized);
        if (!already) {
            EmailUnsubscribe record = new EmailUnsubscribe();
            record.setTenantId(tid);
            record.setEmail(normalized);
            record.setSource("link");
            unsubscribeRepository.save(record);
        }
        return ApiResponse.ok(Map.of(
                "email", normalized,
                "status", already ? "already" : "unsubscribed",
                "message", already ? "该邮箱已退订过" : "退订成功，将不再向该邮箱发送营销邮件"
        ));
    }

    /** 退订黑名单列表（后台管理，需登录，按租户）：按退订时间倒序 */
    @GetMapping("/list")
    public ApiResponse<List<EmailUnsubscribe>> list() {
        return ApiResponse.ok(unsubscribeRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.require()));
    }

    /** 恢复邮箱：从黑名单移除，该邮箱可继续接收营销邮件（需登录，按租户） */
    @DeleteMapping("/{email}")
    public ApiResponse<Map<String, String>> restore(@PathVariable String email) {
        String normalized = email == null ? null : email.trim().toLowerCase();
        if (!StringUtils.hasText(normalized) || !normalized.contains("@")) {
            throw BizException.badRequest("邮箱格式不正确");
        }
        Long tenantId = TenantContext.require();
        if (!unsubscribeRepository.existsByTenantIdAndEmail(tenantId, normalized)) {
            throw BizException.badRequest("该邮箱不在退订名单中");
        }
        unsubscribeRepository.deleteById(new EmailUnsubscribeId(tenantId, normalized));
        return ApiResponse.ok(Map.of("email", normalized, "message", "已恢复，该邮箱可继续接收邮件"));
    }
}
