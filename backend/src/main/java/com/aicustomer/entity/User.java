package com.aicustomer.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户账号（表 users，见 db-design.md §2.1）
 * 角色：admin=超级管理员（可管理用户/系统设置），operator=普通操作员（仅业务功能）
 */
@Entity
@Table(name = "users")
public class User {

    /** 超级管理员 */
    public static final String ROLE_ADMIN = "admin";
    /** 普通操作员 */
    public static final String ROLE_OPERATOR = "operator";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 128)
    @JsonIgnore
    private String passwordHash;

    @Column(name = "display_name", length = 64)
    private String displayName;

    /** 所属租户 id；NULL = 平台级账号（如初始平台管理员） */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 20)
    private String role = "admin";

    @Column(nullable = false, length = 20)
    private String status = "active";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /** 系统管理员（平台级）：role=admin 且无租户 */
    public boolean isSystemAdmin() {
        return ROLE_ADMIN.equals(role) && tenantId == null;
    }

    /** 普通管理员（租户级）：role=admin 且属于某租户 */
    public boolean isTenantAdmin() {
        return ROLE_ADMIN.equals(role) && tenantId != null;
    }
}
