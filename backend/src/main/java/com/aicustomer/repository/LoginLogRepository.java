package com.aicustomer.repository;

import com.aicustomer.entity.LoginLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 登录日志仓库：系统登录次数统计（累计 / 今日 / 今日人数 / 趋势曲线）
 */
public interface LoginLogRepository extends JpaRepository<LoginLog, Long> {

    /** 某时间点之后（含）的登录次数，如今日登录次数 */
    long countByLoginAtGreaterThanEqual(LocalDateTime after);

    /** 某时间点之后（含）的全部登录记录（M7.10 趋势曲线：按桶聚合） */
    List<LoginLog> findByLoginAtGreaterThanEqual(LocalDateTime after);

    /** 某时间点之后（含）登录过的不同用户数，如今日登录人数 */
    @Query("select count(distinct log.username) from LoginLog log where log.loginAt >= :after")
    long countDistinctUsernameAfter(@Param("after") LocalDateTime after);

    /** 最近一次登录记录 */
    Optional<LoginLog> findTopByOrderByLoginAtDesc();

    /** M7.13：按地理归属分组计数（geo 非空），返回 [geo, count] 对 */
    @Query("select log.geo, count(log) from LoginLog log where log.geo is not null group by log.geo")
    List<Object[]> countByGeo();

    // ---- M8.1 租户级统计：租户管理员/普通用户只看自己租户 ----

    /** 某租户的登录总次数 */
    long countByTenantId(Long tenantId);

    /** 某租户某时间点之后（含）的登录次数 */
    long countByTenantIdAndLoginAtGreaterThanEqual(Long tenantId, LocalDateTime after);

    /** 某租户某时间点之后（含）的全部登录记录（趋势曲线聚合） */
    List<LoginLog> findByTenantIdAndLoginAtGreaterThanEqual(Long tenantId, LocalDateTime after);

    /** 某租户某时间点之后（含）登录过的不同用户数 */
    @Query("select count(distinct log.username) from LoginLog log where log.tenantId = :tid and log.loginAt >= :after")
    long countDistinctUsernameAfter(@Param("tid") Long tenantId, @Param("after") LocalDateTime after);

    /** 某租户按地理归属分组计数（geo 非空） */
    @Query("select log.geo, count(log) from LoginLog log where log.tenantId = :tid and log.geo is not null group by log.geo")
    List<Object[]> countByGeo(@Param("tid") Long tenantId);
}
