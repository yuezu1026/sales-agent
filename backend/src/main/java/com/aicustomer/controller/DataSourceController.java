package com.aicustomer.controller;

import com.aicustomer.common.ApiResponse;
import com.aicustomer.common.BizException;
import com.aicustomer.entity.DataSource;
import com.aicustomer.service.DataSourceService;
import com.aicustomer.util.AesUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 潜客数据源接口（M2-2）：CRUD + 启用/禁用
 * api_key 落库加密（AES），回显脱敏（占位符）
 */
@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {

    /** 密钥回显占位符（与 ConfigController 敏感项同策略，提交占位符表示保持原值） */
    private static final String MASK = "••••••";

    private final DataSourceService dataSourceService;
    private final AesUtil aesUtil;

    public DataSourceController(DataSourceService dataSourceService, AesUtil aesUtil) {
        this.dataSourceService = dataSourceService;
        this.aesUtil = aesUtil;
    }

    /** 数据源列表（api_key 脱敏） */
    @GetMapping
    public ApiResponse<List<DataSourceView>> list() {
        List<DataSourceView> views = new ArrayList<>();
        for (DataSource ds : dataSourceService.list()) {
            views.add(toView(ds));
        }
        return ApiResponse.ok(views);
    }

    /** 新增数据源 */
    @PostMapping
    public ApiResponse<DataSourceView> create(@RequestBody DataSourceRequest req) {
        DataSource ds = new DataSource();
        ds.setName(req.name());
        ds.setType(req.type());
        ds.setApiBaseUrl(req.apiBaseUrl());
        ds.setApiKeyEncrypted(StringUtils.hasText(req.apiKey()) ? aesUtil.encrypt(req.apiKey().trim()) : null);
        ds.setEnabled(req.enabled() != null ? req.enabled() : false);
        return ApiResponse.ok(toView(dataSourceService.create(ds)));
    }

    /** 编辑数据源（type 不可改；apiKey 传占位符表示保持原值） */
    @PutMapping("/{id}")
    public ApiResponse<DataSourceView> update(@PathVariable Long id, @RequestBody DataSourceRequest req) {
        DataSource patch = new DataSource();
        patch.setName(req.name());
        patch.setApiBaseUrl(req.apiBaseUrl());
        if (StringUtils.hasText(req.apiKey()) && !MASK.equals(req.apiKey())) {
            patch.setApiKeyEncrypted(aesUtil.encrypt(req.apiKey().trim()));
        } else {
            // null → 保持原值
            patch.setApiKeyEncrypted(null);
        }
        patch.setEnabled(req.enabled());
        return ApiResponse.ok(toView(dataSourceService.update(id, patch)));
    }

    /** 删除数据源 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id);
        return ApiResponse.ok();
    }

    /** 启用/禁用 */
    @PutMapping("/{id}/enabled")
    public ApiResponse<DataSourceView> setEnabled(@PathVariable Long id, @RequestBody EnableRequest req) {
        return ApiResponse.ok(toView(dataSourceService.setEnabled(id, req.enabled())));
    }

    private DataSourceView toView(DataSource ds) {
        boolean hasKey = StringUtils.hasText(ds.getApiKeyEncrypted());
        return new DataSourceView(ds.getId(), ds.getName(), ds.getType(), ds.getApiBaseUrl(),
                hasKey ? MASK : null, ds.getEnabled(), ds.getCreatedAt());
    }

    /** 列表/回显视图（api_key 脱敏） */
    public record DataSourceView(Long id, String name, String type, String apiBaseUrl,
                                 String apiKeyMasked, Boolean enabled, java.time.LocalDateTime createdAt) {
    }

    /** 新增/编辑请求 */
    public record DataSourceRequest(String name, String type, String apiBaseUrl, String apiKey, Boolean enabled) {
    }

    /** 启用/禁用请求 */
    public record EnableRequest(boolean enabled) {
    }
}
