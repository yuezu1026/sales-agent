package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.WechatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 微信沟通消息仓储（M2-1.8，按租户隔离）
 */
public interface WechatMessageRepository extends JpaRepository<WechatMessage, Long> {

    /** 某客户微信会话消息（按发生时间正序，会话阅读顺序） */
    default List<WechatMessage> findByLeadIdOrderBySentAtAsc(Long leadId) {
        return findByTenantIdAndLeadIdOrderBySentAtAsc(TenantContext.require(), leadId);
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default java.util.Optional<WechatMessage> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    java.util.Optional<WechatMessage> findByTenantIdAndId(Long tenantId, Long id);

    List<WechatMessage> findByTenantIdAndLeadIdOrderBySentAtAsc(Long tenantId, Long leadId);
}
