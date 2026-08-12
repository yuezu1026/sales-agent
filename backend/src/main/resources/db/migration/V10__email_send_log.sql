-- V10: 邮件发送记录 email_send_log
-- M3-2：SMTP 发送闭环，记录每次发送的状态/失败原因/发送时间
-- status: queued（排队）/ sent（成功）/ failed（失败）/ bounced（退信）

CREATE TABLE email_send_log (
    id          BIGSERIAL PRIMARY KEY,
    lead_id     BIGINT REFERENCES lead(id) ON DELETE SET NULL,
    draft_id    BIGINT REFERENCES email_draft(id) ON DELETE SET NULL,
    from_email  VARCHAR(128) NOT NULL,
    to_email    VARCHAR(128) NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    body        TEXT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'queued',
    error_msg   VARCHAR(255),
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_esl_lead ON email_send_log(lead_id);
CREATE INDEX idx_esl_status ON email_send_log(status);
CREATE INDEX idx_esl_sent_at ON email_send_log(sent_at);
