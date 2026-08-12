package com.aicustomer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 微信沟通消息（表 wechat_message，见 db-design.md §2.16）
 * M2-1.8：记录式微信工作台 —— 记录与客户的微信往来消息；
 * AI 根据客户画像 + 沟通时间线生成回复建议（ai_reply），人工确认后复制到微信发送。
 * direction: in（客户发来）/ out（我方发出）
 * status: recorded（直接记录）/ ai_confirmed（AI 建议确认后发出）
 */
@Entity
@Table(name = "wechat_message", indexes = {
        @Index(name = "idx_wm_lead", columnList = "lead_id")
})
public class WechatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "lead_id", nullable = false)
    private Long leadId;

    @Column(nullable = false, length = 8)
    private String direction = "in";

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "ai_reply", columnDefinition = "TEXT")
    private String aiReply;

    @Column(nullable = false, length = 20)
    private String status = "recorded";

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

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

    public Long getLeadId() {
        return leadId;
    }

    public void setLeadId(Long leadId) {
        this.leadId = leadId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAiReply() {
        return aiReply;
    }

    public void setAiReply(String aiReply) {
        this.aiReply = aiReply;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
