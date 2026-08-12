package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.TenantContext;
import com.aicustomer.common.TenantDefaults;
import com.aicustomer.entity.SystemConfig;
import com.aicustomer.repository.SystemConfigRepository;
import com.aicustomer.util.AesUtil;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统配置接口：查看 / 保存（需登录，按租户隔离）
 * 敏感项（ai.api_key、smtp.password）落库加密，回显时脱敏
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    /** 敏感配置项（回显时显示占位符，不暴露明文） */
    private static final List<String> SENSITIVE_KEYS = List.of("ai.api_key", "smtp.password", "imap.password");

    private final SystemConfigRepository repository;
    private final AesUtil aesUtil;

    public ConfigController(SystemConfigRepository repository, AesUtil aesUtil) {
        this.repository = repository;
        this.aesUtil = aesUtil;
    }

    @GetMapping
    public ApiResponse<List<ConfigItem>> list() {
        ensureDefaults();
        List<ConfigItem> result = new ArrayList<>();
        for (SystemConfig cfg : repository.findByTenantIdOrderByConfigKeyAsc(TenantContext.require())) {
            String value = SENSITIVE_KEYS.contains(cfg.getConfigKey())
                    ? (cfg.getConfigValue() == null ? "" : "••••••")
                    : cfg.getConfigValue();
            result.add(new ConfigItem(cfg.getConfigKey(), value, cfg.getDescription()));
        }
        return ApiResponse.ok(result);
    }

    @PutMapping
    public ApiResponse<Void> update(@RequestBody List<ConfigItem> items) {
        Long tenantId = TenantContext.require();
        for (ConfigItem item : items) {
            SystemConfig cfg = repository.findByTenantIdAndConfigKey(tenantId, item.key()).orElseGet(() -> {
                SystemConfig c = new SystemConfig();
                c.setTenantId(tenantId);
                c.setConfigKey(item.key());
                return c;
            });
            String value = item.value();
            // 密码框回显的是占位符，保持原值不覆盖
            if (SENSITIVE_KEYS.contains(item.key()) && "••••••".equals(value)) {
                continue;
            }
            if (SENSITIVE_KEYS.contains(item.key()) && value != null && !value.isBlank()) {
                value = aesUtil.encrypt(value);
            }
            cfg.setConfigValue(value);
            cfg.setDescription(item.description());
            cfg.setUpdatedAt(LocalDateTime.now());
            repository.save(cfg);
        }
        return ApiResponse.ok();
    }

    /** 当前租户缺省配置补齐（首次访问或注册时调用） */
    public void ensureDefaults() {
        Long tenantId = TenantContext.require();
        for (var def : TenantDefaults.DEFAULT_CONFIGS) {
            repository.findByTenantIdAndConfigKey(tenantId, def.get("key")).orElseGet(() -> {
                SystemConfig c = new SystemConfig();
                c.setTenantId(tenantId);
                c.setConfigKey(def.get("key"));
                c.setDescription(def.get("description"));
                return repository.save(c);
            });
        }
    }

    public record ConfigItem(String key, String value, String description) {
    }
}
