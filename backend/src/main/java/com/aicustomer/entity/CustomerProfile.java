package com.aicustomer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自有客户画像（RAG 语料，db-design §2.7）
 * M2-3：CSV 导入历史成交客户 → 生成向量 → 与潜客特征做相似度打分
 */
@Entity
@Table(name = "customer_profile")
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 公司名称（唯一，忽略大小写） */
    @Column(name = "company_name", nullable = false, length = 128)
    private String companyName;

    /** 行业 */
    @Column(name = "industry", length = 64)
    private String industry;

    /** 联系人 */
    @Column(name = "contact_name", length = 64)
    private String contactName;

    /** 邮箱 */
    @Column(name = "contact_email", length = 128)
    private String contactEmail;

    /** 成交金额 */
    @Column(name = "deal_value", precision = 12, scale = 2)
    private BigDecimal dealValue;

    /** 标签（逗号分隔） */
    @Column(name = "tags", length = 255)
    private String tags;

    /** 描述/画像文本（用于向量化） */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** 向量（JSON 文本，MVP 存本地 TF-IDF 稀疏向量；配置 embedding_model 后存远程向量） */
    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public BigDecimal getDealValue() {
        return dealValue;
    }

    public void setDealValue(BigDecimal dealValue) {
        this.dealValue = dealValue;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
