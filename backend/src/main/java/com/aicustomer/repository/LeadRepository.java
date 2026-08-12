package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.Lead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeadRepository extends JpaRepository<Lead, Long>, JpaSpecificationExecutor<Lead> {

    /** 数据源去重：source_type + source_id 唯一（source_id 非空时，租户内） */
    default boolean existsBySourceTypeAndSourceId(String sourceType, String sourceId) {
        return existsByTenantIdAndSourceTypeAndSourceId(TenantContext.require(), sourceType, sourceId);
    }

    /** manual 来源去重：公司名忽略大小写 */
    default boolean existsByCompanyNameIgnoreCase(String companyName) {
        return existsByTenantIdAndCompanyNameIgnoreCase(TenantContext.require(), companyName);
    }

    /** 编辑时去重（排除自身 id） */
    default boolean existsBySourceTypeAndSourceIdAndIdNot(String sourceType, String sourceId, Long id) {
        return existsByTenantIdAndSourceTypeAndSourceIdAndIdNot(TenantContext.require(), sourceType, sourceId, id);
    }

    default boolean existsByCompanyNameIgnoreCaseAndIdNot(String companyName, Long id) {
        return existsByTenantIdAndCompanyNameIgnoreCaseAndIdNot(TenantContext.require(), companyName, id);
    }

    /** 状态分布统计（供看板，租户内） */
    default List<StatusStat> statsByStatus() {
        return statsByStatus(TenantContext.require());
    }

    /** 收件箱自动关联：按发件人邮箱匹配客户（忽略大小写） */
    default Optional<Lead> findFirstByContactEmailIgnoreCase(String contactEmail) {
        return findFirstByTenantIdAndContactEmailIgnoreCase(TenantContext.require(), contactEmail);
    }

    /** 收件箱自动关联（批量）：按邮箱集合一次查出客户，避免逐封查询 */
    default List<Lead> findByContactEmailInIgnoreCase(Collection<String> emails) {
        return findByTenantIdAndContactEmailInIgnoreCase(TenantContext.require(), emails);
    }

    /** 收件箱同步过滤：所有已配置邮箱的客户（去空值、去重、转小写） */
    default List<String> findDistinctContactEmails() {
        return findDistinctContactEmailsByTenant(TenantContext.require());
    }

    /** 租户内按 id 查询（防跨租户读取） */
    @Override
    default Optional<Lead> findById(Long id) {
        return findByTenantIdAndId(TenantContext.require(), id);
    }

    /** 全量列表（租户内；用于全量重算画像分等） */
    @Override
    default List<Lead> findAll() {
        return findByTenantId(TenantContext.require());
    }

    /** 全量计数（租户内） */
    @Override
    default long count() {
        return countByTenantId(TenantContext.require());
    }

    Optional<Lead> findByTenantIdAndId(Long tenantId, Long id);

    List<Lead> findByTenantId(Long tenantId);

    long countByTenantId(Long tenantId);

    boolean existsByTenantIdAndSourceTypeAndSourceId(Long tenantId, String sourceType, String sourceId);

    boolean existsByTenantIdAndCompanyNameIgnoreCase(Long tenantId, String companyName);

    boolean existsByTenantIdAndSourceTypeAndSourceIdAndIdNot(Long tenantId, String sourceType, String sourceId, Long id);

    boolean existsByTenantIdAndCompanyNameIgnoreCaseAndIdNot(Long tenantId, String companyName, Long id);

    /** 状态分布统计（供看板） */
    @Query("select l.status as status, count(l) as count from Lead l where l.tenantId = :tenantId group by l.status")
    List<StatusStat> statsByStatus(@Param("tenantId") Long tenantId);

    Optional<Lead> findFirstByTenantIdAndContactEmailIgnoreCase(Long tenantId, String contactEmail);

    @Query("select l from Lead l where l.tenantId = :tenantId and lower(trim(l.contactEmail)) in :emails")
    List<Lead> findByTenantIdAndContactEmailInIgnoreCase(@Param("tenantId") Long tenantId,
                                                         @Param("emails") Collection<String> emails);

    @Query("select distinct lower(l.contactEmail) from Lead l where l.tenantId = :tenantId "
            + "and l.contactEmail is not null and trim(l.contactEmail) <> ''")
    List<String> findDistinctContactEmailsByTenant(@Param("tenantId") Long tenantId);

    interface StatusStat {
        String getStatus();

        long getCount();
    }
}
