package com.aicustomer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 客户回复邮件（表 email_inbox，见 db-design.md §2.15）
 * M2-1.6：MCP Client 抓取邮箱中的客户回复邮件落库，统一管理
 * lead_id 可空：发件人自动匹配到客户则关联，否则留空
 * ai_intent: inquiry / quote / objection / followup / positive / other
 * ai_analysis_status: pending / analyzed / failed
 */
@Entity
@Table(name = "email_inbox", indexes = {
        @Index(name = "idx_inbox_lead", columnList = "lead_id"),
        @Index(name = "idx_inbox_from", columnList = "from_address"),
        @Index(name = "idx_inbox_received", columnList = "received_at"),
        @Index(name = "idx_inbox_read", columnList = "is_read")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_inbox_uid", columnNames = {"mailbox", "uid"})
})
public class EmailInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(nullable = false, length = 32)
    private String mailbox = "INBOX";

    /** IMAP UID（同一邮箱内唯一，用于去重） */
    @Column(nullable = false)
    private Long uid;

    @Column(name = "message_id", length = 512)
    private String messageId;

    @Column(name = "from_address", nullable = false, length = 255)
    private String fromAddress;

    @Column(name = "from_name", length = 255)
    private String fromName;

    @Column(name = "to_address", length = 255)
    private String toAddress;

    @Column(length = 255)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Column(name = "ai_intent", length = 32)
    private String aiIntent;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_analysis_status", nullable = false, length = 20)
    private String aiAnalysisStatus = "pending";

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

    public String getMailbox() {
        return mailbox;
    }

    public void setMailbox(String mailbox) {
        this.mailbox = mailbox;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(LocalDateTime receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Boolean getIsRead() {
        return isRead;
    }

    public void setIsRead(Boolean isRead) {
        this.isRead = isRead;
    }

    public String getAiIntent() {
        return aiIntent;
    }

    public void setAiIntent(String aiIntent) {
        this.aiIntent = aiIntent;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public String getAiAnalysisStatus() {
        return aiAnalysisStatus;
    }

    public void setAiAnalysisStatus(String aiAnalysisStatus) {
        this.aiAnalysisStatus = aiAnalysisStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
