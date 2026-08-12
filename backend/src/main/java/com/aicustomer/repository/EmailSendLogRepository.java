package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.EmailSendLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface EmailSendLogRepository extends JpaRepository<EmailSendLog, Long>,
        JpaSpecificationExecutor<EmailSendLog> {

    /** 某客户发送记录（倒序） */
    default List<EmailSendLog> findByLeadIdOrderByCreatedAtDesc(Long leadId) {
        return findByTenantIdAndLeadIdOrderByCreatedAtDesc(TenantContext.require(), leadId);
    }

    /** 某草稿发送记录（倒序） */
    default List<EmailSendLog> findByDraftIdOrderByCreatedAtDesc(Long draftId) {
        return findByTenantIdAndDraftIdOrderByCreatedAtDesc(TenantContext.require(), draftId);
    }

    /** 今日成功发送数（限频用）：status=sent 且 sent_at >= 当日零点 */
    default long countByStatusAndSentAtGreaterThanEqual(String status, LocalDateTime since) {
        return countByTenantIdAndStatusAndSentAtGreaterThanEqual(TenantContext.require(), status, since);
    }

    /** 全部发送记录（倒序，工作台/看板用） */
    default List<EmailSendLog> findAllByOrderByCreatedAtDesc() {
        return findByTenantIdOrderByCreatedAtDesc(TenantContext.require());
    }

    // ==================== M4-6 打开率追踪统计 ====================

    /** 某状态记录数（sent 为实际投递成功的邮件数） */
    default long countByStatus(String status) {
        return countByTenantIdAndStatus(TenantContext.require(), status);
    }

    /** 已打开的邮件数 */
    default long countByOpenedAtIsNotNull() {
        return countByTenantIdAndOpenedAtIsNotNull(TenantContext.require());
    }

    /** 已点击链接的邮件数 */
    default long countByClickedAtIsNotNull() {
        return countByTenantIdAndClickedAtIsNotNull(TenantContext.require());
    }

    /** 租户内按 id 查询（防跨租户读取；公开追踪端点请用 findByTenantIdAndId 显式传参） */
    @Override
    default java.util.Optional<EmailSendLog> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    java.util.Optional<EmailSendLog> findByTenantIdAndId(Long tenantId, Long id);

    List<EmailSendLog> findByTenantIdAndLeadIdOrderByCreatedAtDesc(Long tenantId, Long leadId);

    List<EmailSendLog> findByTenantIdAndDraftIdOrderByCreatedAtDesc(Long tenantId, Long draftId);

    long countByTenantIdAndStatusAndSentAtGreaterThanEqual(Long tenantId, String status, LocalDateTime since);

    List<EmailSendLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    long countByTenantIdAndStatus(Long tenantId, String status);

    long countByTenantIdAndOpenedAtIsNotNull(Long tenantId);

    long countByTenantIdAndClickedAtIsNotNull(Long tenantId);
}
