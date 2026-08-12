package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

    default List<FollowUp> findByLeadIdOrderByHappenedAtDesc(Long leadId) {
        return findByTenantIdAndLeadIdOrderByHappenedAtDesc(TenantContext.require(), leadId);
    }

    /** 按发生时间正序 → 沟通时间线 */
    default List<FollowUp> findByLeadIdOrderByHappenedAtAsc(Long leadId) {
        return findByTenantIdAndLeadIdOrderByHappenedAtAsc(TenantContext.require(), leadId);
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default java.util.Optional<FollowUp> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    java.util.Optional<FollowUp> findByTenantIdAndId(Long tenantId, Long id);

    List<FollowUp> findByTenantIdAndLeadIdOrderByHappenedAtDesc(Long tenantId, Long leadId);

    List<FollowUp> findByTenantIdAndLeadIdOrderByHappenedAtAsc(Long tenantId, Long leadId);
}
