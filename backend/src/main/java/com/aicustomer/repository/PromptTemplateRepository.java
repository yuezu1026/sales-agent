package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    default Optional<PromptTemplate> findByScene(String scene) {
        return findByTenantIdAndScene(TenantContext.require(), scene);
    }

    default List<PromptTemplate> findByEnabledTrueOrderBySceneAsc() {
        return findByTenantIdAndEnabledTrueOrderBySceneAsc(TenantContext.require());
    }

    /** 全部模板（含禁用，租户内） */
    default List<PromptTemplate> findAll() {
        return findByTenantIdOrderBySceneAsc(TenantContext.require());
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default Optional<PromptTemplate> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    Optional<PromptTemplate> findByTenantIdAndId(Long tenantId, Long id);

    List<PromptTemplate> findByTenantIdOrderBySceneAsc(Long tenantId);

    Optional<PromptTemplate> findByTenantIdAndScene(Long tenantId, String scene);

    List<PromptTemplate> findByTenantIdAndEnabledTrueOrderBySceneAsc(Long tenantId);
}
