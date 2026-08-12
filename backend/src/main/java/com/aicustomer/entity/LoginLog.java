package com.aicustomer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 登录日志（表 login_logs，见 db-design.md）
 * M7.9：每次成功登录插入一条，供系统登录次数统计（累计 / 今日 / 今日人数）
 */
@Entity
@Table(name = "login_logs", indexes = {
        @Index(name = "idx_ll_login_at", columnList = "login_at")
})
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    /** M8.1：登录用户所属租户（系统管理员为 NULL）；租户级登录统计/地理分布按此过滤 */
    @Column(name = "tenant_id")
    private Long tenantId;

    /** M7.13：登录客户端真实 IP（nginx 透传 X-Forwarded-For / X-Real-IP），历史记录为 NULL */
    @Column(length = 64)
    private String ip;

    /** M7.13：ip2region 解析的地理归属（国家|省|市|ISP|国家码），登录时一次性固化 */
    @Column(length = 128)
    private String geo;

    @Column(name = "login_at", nullable = false)
    private LocalDateTime loginAt = LocalDateTime.now();

    public LoginLog() {
    }

    public LoginLog(String username) {
        this.username = username;
    }

    public LoginLog(String username, String ip, String geo) {
        this.username = username;
        this.ip = ip;
        this.geo = geo;
    }

    public LoginLog(String username, Long tenantId, String ip, String geo) {
        this.username = username;
        this.tenantId = tenantId;
        this.ip = ip;
        this.geo = geo;
    }

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDateTime getLoginAt() {
        return loginAt;
    }

    public void setLoginAt(LocalDateTime loginAt) {
        this.loginAt = loginAt;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getGeo() {
        return geo;
    }

    public void setGeo(String geo) {
        this.geo = geo;
    }
}
