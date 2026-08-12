package com.aicustomer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 退订黑名单（表 email_unsubscribe，见 db-design.md）
 * M3-2 合规闭环：收件人点击邮件内退订链接后落库，后续不再向其发送营销邮件。
 * 复合主键 (tenant_id, email)：SaaS 下按租户隔离，邮箱维度拉黑（一个邮箱可能对应多个 lead）。
 */
@Entity
@Table(name = "email_unsubscribe")
@IdClass(EmailUnsubscribeId.class)
public class EmailUnsubscribe {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Id
    @Column(name = "email", nullable = false, length = 128)
    private String email;

    /** 来源：link（邮件内退订链接）/ manual（人工）等 */
    @Column(name = "source", nullable = false, length = 20)
    private String source = "link";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
