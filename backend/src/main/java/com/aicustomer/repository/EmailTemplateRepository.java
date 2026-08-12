package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    default List<EmailTemplate> findAllByOrderByUpdatedAtDesc() {
        return findByTenantIdOrderByUpdatedAtDesc(TenantContext.require());
    }

    default boolean existsByName(String name) {
        return existsByTenantIdAndName(TenantContext.require(), name);
    }

    default boolean existsByNameAndIdNot(String name, Long id) {
        return existsByTenantIdAndNameAndIdNot(TenantContext.require(), name, id);
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default java.util.Optional<EmailTemplate> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    java.util.Optional<EmailTemplate> findByTenantIdAndId(Long tenantId, Long id);

    List<EmailTemplate> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);

    boolean existsByTenantIdAndName(Long tenantId, String name);

    boolean existsByTenantIdAndNameAndIdNot(Long tenantId, String name, Long id);
}
