package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /** 按当前租户查配置（请求上下文内使用） */
    default Optional<SystemConfig> findByConfigKey(String configKey) {
        return findByTenantIdAndConfigKey(TenantContext.require(), configKey);
    }

    Optional<SystemConfig> findByTenantIdAndConfigKey(Long tenantId, String configKey);

    List<SystemConfig> findByTenantIdOrderByConfigKeyAsc(Long tenantId);
}
