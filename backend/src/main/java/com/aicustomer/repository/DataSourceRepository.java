package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.DataSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataSourceRepository extends JpaRepository<DataSource, Long> {

    /** 启用的数据源（挖掘时按优先级取，租户内） */
    default List<DataSource> findByEnabledTrueOrderByIdAsc() {
        return findByTenantIdAndEnabledTrueOrderByIdAsc(TenantContext.require());
    }

    /** 按类型查（唯一，租户内） */
    default Optional<DataSource> findByType(String type) {
        return findByTenantIdAndType(TenantContext.require(), type);
    }

    default boolean existsByType(String type) {
        return existsByTenantIdAndType(TenantContext.require(), type);
    }

    /** 全部数据源（含禁用，租户内） */
    default List<DataSource> findAll() {
        return findAllByTenantIdOrderByIdAsc(TenantContext.require());
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default Optional<DataSource> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    Optional<DataSource> findByTenantIdAndId(Long tenantId, Long id);

    List<DataSource> findByTenantIdAndEnabledTrueOrderByIdAsc(Long tenantId);

    Optional<DataSource> findByTenantIdAndType(Long tenantId, String type);

    boolean existsByTenantIdAndType(Long tenantId, String type);

    List<DataSource> findAllByTenantIdOrderByIdAsc(Long tenantId);
}
