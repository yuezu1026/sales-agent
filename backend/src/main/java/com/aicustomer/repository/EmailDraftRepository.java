package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface EmailDraftRepository extends JpaRepository<EmailDraft, Long>,
        JpaSpecificationExecutor<EmailDraft> {

    /** 某客户未发送的草稿（draft/confirmed），按创建时间倒序。M7.1：已发送的 sent 归发件箱展示 */
    default List<EmailDraft> findByLeadIdAndStatusInOrderByCreatedAtDesc(Long leadId, List<String> statuses) {
        return findByTenantIdAndLeadIdAndStatusInOrderByCreatedAtDesc(TenantContext.require(), leadId, statuses);
    }

    /** 某客户已确认（已发出）的草稿，按创建时间正序 → 沟通时间线 */
    default List<EmailDraft> findByLeadIdAndStatusOrderByCreatedAtAsc(Long leadId, String status) {
        return findByTenantIdAndLeadIdAndStatusOrderByCreatedAtAsc(TenantContext.require(), leadId, status);
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default java.util.Optional<EmailDraft> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    java.util.Optional<EmailDraft> findByTenantIdAndId(Long tenantId, Long id);

    List<EmailDraft> findByTenantIdAndLeadIdAndStatusInOrderByCreatedAtDesc(Long tenantId, Long leadId,
                                                                           List<String> statuses);

    List<EmailDraft> findByTenantIdAndLeadIdAndStatusOrderByCreatedAtAsc(Long tenantId, Long leadId, String status);
}
