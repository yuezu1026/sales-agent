package com.aicustomer.service;

import com.aicustomer.common.BizException;
import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.DataSource;
import com.aicustomer.repository.DataSourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 潜客数据源管理（M2-2）：CRUD + 启用/禁用
 * api_key 加密与脱敏由 Controller 层处理（对齐 ConfigController 敏感项策略）
 */
@Service
public class DataSourceService {

    private final DataSourceRepository dataSourceRepository;

    public DataSourceService(DataSourceRepository dataSourceRepository) {
        this.dataSourceRepository = dataSourceRepository;
    }

    public List<DataSource> list() {
        return dataSourceRepository.findAll();
    }

    /** 新增：name/type 必填，type 唯一 */
    @Transactional
    public DataSource create(DataSource ds) {
        if (!StringUtils.hasText(ds.getName())) {
            throw BizException.badRequest("数据源名称不能为空");
        }
        if (!StringUtils.hasText(ds.getType())) {
            throw BizException.badRequest("数据源类型不能为空");
        }
        String type = ds.getType().trim().toLowerCase();
        if (dataSourceRepository.existsByType(type)) {
            throw BizException.badRequest("数据源类型「" + type + "」已存在");
        }
        ds.setName(ds.getName().trim());
        ds.setType(type);
        if (ds.getEnabled() == null) {
            ds.setEnabled(false);
        }
        ds.setTenantId(TenantContext.require());
        return dataSourceRepository.save(ds);
    }

    /** 编辑：更新名称/接口地址/密钥/启用状态；type 不可改 */
    @Transactional
    public DataSource update(Long id, DataSource patch) {
        DataSource ds = dataSourceRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("数据源不存在"));
        if (StringUtils.hasText(patch.getName())) {
            ds.setName(patch.getName().trim());
        }
        if (patch.getApiBaseUrl() != null) {
            ds.setApiBaseUrl(StringUtils.hasText(patch.getApiBaseUrl())
                    ? patch.getApiBaseUrl().trim() : null);
        }
        // apiKeyEncrypted 传入 null 表示保持原值（Controller 已处理占位符）
        if (patch.getApiKeyEncrypted() != null) {
            ds.setApiKeyEncrypted(StringUtils.hasText(patch.getApiKeyEncrypted())
                    ? patch.getApiKeyEncrypted() : null);
        }
        if (patch.getEnabled() != null) {
            ds.setEnabled(patch.getEnabled());
        }
        return dataSourceRepository.save(ds);
    }

    @Transactional
    public void delete(Long id) {
        if (!dataSourceRepository.existsById(id)) {
            throw BizException.notFound("数据源不存在");
        }
        dataSourceRepository.deleteById(id);
    }

    @Transactional
    public DataSource setEnabled(Long id, boolean enabled) {
        DataSource ds = dataSourceRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("数据源不存在"));
        ds.setEnabled(enabled);
        return dataSourceRepository.save(ds);
    }
}
