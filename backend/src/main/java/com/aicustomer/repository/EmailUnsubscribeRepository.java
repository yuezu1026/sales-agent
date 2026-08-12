package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailUnsubscribe;
import com.aicustomer.entity.EmailUnsubscribeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailUnsubscribeRepository extends JpaRepository<EmailUnsubscribe, EmailUnsubscribeId> {

    /** 邮箱是否已退订（租户内） */
    default boolean existsByEmail(String email) {
        return existsByTenantIdAndEmail(TenantContext.require(), email);
    }

    /** 黑名单列表（后台管理用）：按退订时间倒序 */
    default List<EmailUnsubscribe> findAllByOrderByCreatedAtDesc() {
        return findByTenantIdOrderByCreatedAtDesc(TenantContext.require());
    }

    boolean existsByTenantIdAndEmail(Long tenantId, String email);

    List<EmailUnsubscribe> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
