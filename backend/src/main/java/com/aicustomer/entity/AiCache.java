package com.aicustomer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * AI 重复请求缓存（表 ai_cache，M4-6）：
 * 相同请求（场景 + Prompt 组合 / 相同 embedding 文本）命中后直接返回，不重复调用模型、不扣 token 额度。
 * kind: chat（大模型生成）/ embedding（向量化）
 * cache_key: SHA-256 hex；response: chat 存文本 / embedding 存 JSON 向量 {"dim":N,"data":[...]}
 */
@Entity
@Table(name = "ai_cache", indexes = {
        @Index(name = "uk_ai_cache", columnList = "kind, cache_key", unique = true),
        @Index(name = "idx_ai_cache_created", columnList = "created_at")
})
public class AiCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(length = 50)
    private String scene;

    @Column(name = "cache_key", nullable = false, length = 64)
    private String cacheKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String response;

    @Column(name = "total_tokens", nullable = false)
    private int totalTokens;

    @Column(name = "hit_count", nullable = false)
    private int hitCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
