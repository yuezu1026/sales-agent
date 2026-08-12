package com.aicustomer.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 邮件发送记录（表 email_send_log，见 db-design.md §2.10）
 * M3-2：SMTP 发送闭环，记录每次发送的状态与失败原因
 * status: queued / sent / failed / bounced
 */
@Entity
@Table(name = "email_send_log", indexes = {
        @Index(name = "idx_esl_lead", columnList = "lead_id"),
        @Index(name = "idx_esl_status", columnList = "status")
})
public class EmailSendLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "lead_id")
    private Long leadId;

    @Column(name = "draft_id")
    private Long draftId;

    @Column(name = "from_email", nullable = false, length = 128)
    private String fromEmail;

    @Column(name = "to_email", nullable = false, length = 128)
    private String toEmail;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private String status = "queued";

    @Column(name = "error_msg", length = 255)
    private String errorMsg;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /** 首次打开时间（1px 追踪像素，M4-6；null = 未打开） */
    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    /** 首次点击链接时间（M4-6；null = 未点击） */
    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

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

    public Long getLeadId() {
        return leadId;
    }

    public void setLeadId(Long leadId) {
        this.leadId = leadId;
    }

    public Long getDraftId() {
        return draftId;
    }

    public void setDraftId(Long draftId) {
        this.draftId = draftId;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getToEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(LocalDateTime openedAt) {
        this.openedAt = openedAt;
    }

    public LocalDateTime getClickedAt() {
        return clickedAt;
    }

    public void setClickedAt(LocalDateTime clickedAt) {
        this.clickedAt = clickedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
