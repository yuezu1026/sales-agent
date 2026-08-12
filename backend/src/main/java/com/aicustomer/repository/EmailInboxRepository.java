package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * 客户回复邮件仓储：分页检索 + 同步去重（按租户隔离）
 */
public interface EmailInboxRepository extends JpaRepository<EmailInbox, Long>,
        JpaSpecificationExecutor<EmailInbox> {

    /** 去重：同一邮箱同一 UID 是否已入库（租户内） */
    default boolean existsByMailboxAndUid(String mailbox, Long uid) {
        return existsByTenantIdAndMailboxAndUid(TenantContext.require(), mailbox, uid);
    }

    /** 批量去重：一次查出已入库的 uid 集合（避免逐封查询） */
    default List<Long> findUidsByMailboxAndUidIn(String mailbox, Collection<Long> uids) {
        return findUidsByTenantIdAndMailboxAndUidIn(TenantContext.require(), mailbox, uids);
    }

    /** 某客户回复的邮件，按接收时间正序 → 沟通时间线 */
    default List<EmailInbox> findByLeadIdOrderByReceivedAtAsc(Long leadId) {
        return findByTenantIdAndLeadIdOrderByReceivedAtAsc(TenantContext.require(), leadId);
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default java.util.Optional<EmailInbox> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    /** 全量计数（租户内） */
    @Override
    default long count() {
        return countByTenantId(TenantContext.require());
    }

    java.util.Optional<EmailInbox> findByTenantIdAndId(Long tenantId, Long id);

    long countByTenantId(Long tenantId);

    boolean existsByTenantIdAndMailboxAndUid(Long tenantId, String mailbox, Long uid);

    @Query("select e.uid from EmailInbox e where e.tenantId = :tenantId and e.mailbox = :mailbox and e.uid in :uids")
    List<Long> findUidsByTenantIdAndMailboxAndUidIn(@Param("tenantId") Long tenantId, @Param("mailbox") String mailbox,
                                                    @Param("uids") Collection<Long> uids);

    List<EmailInbox> findByTenantIdAndLeadIdOrderByReceivedAtAsc(Long tenantId, Long leadId);
}
