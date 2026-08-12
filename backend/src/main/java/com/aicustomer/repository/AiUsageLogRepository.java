package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    default long countByCreatedAtAfter(LocalDateTime after) {
        return countByTenantIdAndCreatedAtAfter(TenantContext.require(), after);
    }

    default long sumTotalTokensAfter(LocalDateTime after) {
        return sumTotalTokensAfter(TenantContext.require(), after);
    }

    default BigDecimal sumCostAfter(LocalDateTime after) {
        return sumCostAfter(TenantContext.require(), after);
    }

    default List<SceneStat> statsByScene() {
        return statsByScene(TenantContext.require());
    }

    long countByTenantIdAndCreatedAtAfter(Long tenantId, LocalDateTime after);

    @Query("select coalesce(sum(log.totalTokens), 0) from AiUsageLog log where log.tenantId = :tenantId and log.createdAt >= :after")
    long sumTotalTokensAfter(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after);

    @Query("select coalesce(sum(log.cost), 0) from AiUsageLog log where log.tenantId = :tenantId and log.createdAt >= :after")
    BigDecimal sumCostAfter(@Param("tenantId") Long tenantId, @Param("after") LocalDateTime after);

    @Query("""
            select log.scene as scene, count(log) as calls,
                   coalesce(sum(log.totalTokens), 0) as tokens,
                   coalesce(sum(log.cost), 0) as cost
            from AiUsageLog log
            where log.tenantId = :tenantId
            group by log.scene
            order by calls desc
            """)
    List<SceneStat> statsByScene(@Param("tenantId") Long tenantId);

    interface SceneStat {
        String getScene();

        long getCalls();

        long getTokens();

        BigDecimal getCost();
    }
}
