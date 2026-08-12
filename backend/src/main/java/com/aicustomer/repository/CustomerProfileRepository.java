package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 客户画像仓库：按公司名去重、按 id 倒序列表（按租户隔离）
 */
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    default Optional<CustomerProfile> findByCompanyNameIgnoreCase(String companyName) {
        return findByTenantIdAndCompanyNameIgnoreCase(TenantContext.require(), companyName);
    }

    default boolean existsByCompanyNameIgnoreCase(String companyName) {
        return existsByTenantIdAndCompanyNameIgnoreCase(TenantContext.require(), companyName);
    }

    default List<CustomerProfile> findAllByOrderByIdDesc() {
        return findByTenantIdOrderByIdDesc(TenantContext.require());
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default Optional<CustomerProfile> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    /** 全量画像（租户内；语义检索/相似匹配用） */
    @Override
    default List<CustomerProfile> findAll() {
        return findByTenantIdOrderByIdDesc(TenantContext.require());
    }

    Optional<CustomerProfile> findByTenantIdAndId(Long tenantId, Long id);

    Optional<CustomerProfile> findByTenantIdAndCompanyNameIgnoreCase(Long tenantId, String companyName);

    boolean existsByTenantIdAndCompanyNameIgnoreCase(Long tenantId, String companyName);

    List<CustomerProfile> findByTenantIdOrderByIdDesc(Long tenantId);
}
