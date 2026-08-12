package com.aicustomer.repository;

import com.aicustomer.common.TenantContext;
import com.aicustomer.entity.AiCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiCacheRepository extends JpaRepository<AiCache, Long> {

    /** 命中查询：tenant + kind + cache_key 唯一 */
    default Optional<AiCache> findByKindAndCacheKey(String kind, String cacheKey) {
        return findByTenantIdAndKindAndCacheKey(TenantContext.require(), kind, cacheKey);
    }

    /** 命中且未过期（created_at >= since） */
    default Optional<AiCache> findByKindAndCacheKeyAndCreatedAtGreaterThanEqual(String kind, String cacheKey,
                                                                                LocalDateTime since) {
        return findByTenantIdAndKindAndCacheKeyAndCreatedAtGreaterThanEqual(TenantContext.require(), kind, cacheKey, since);
    }

    Optional<AiCache> findByTenantIdAndKindAndCacheKey(Long tenantId, String kind, String cacheKey);

    Optional<AiCache> findByTenantIdAndKindAndCacheKeyAndCreatedAtGreaterThanEqual(Long tenantId, String kind,
                                                                                  String cacheKey, LocalDateTime since);
}
