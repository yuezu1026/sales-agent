-- V5: 客户回复邮件收件箱 email_inbox（M2-1.6）
-- MCP Client 通过 MCP 协议抓取邮箱（IMAP/mock）→ 落库统一管理
-- lead_id 可空：发件人匹配到客户时关联，否则留空待人工关联
-- uid: 同一邮箱内唯一（IMAP UID），用于同步去重
-- ai_intent: inquiry(询价)/quote(报价要求)/objection(异议)/followup(跟进请求)/positive(积极意向)/other
-- ai_analysis_status: pending(未分析)/analyzed(已分析)/failed(分析失败)

CREATE TABLE email_inbox (
    id                  BIGSERIAL PRIMARY KEY,
    lead_id             BIGINT REFERENCES lead(id) ON DELETE SET NULL,
    mailbox             VARCHAR(32) NOT NULL DEFAULT 'INBOX',
    uid                 BIGINT NOT NULL,
    message_id          VARCHAR(512),
    from_address        VARCHAR(255) NOT NULL,
    from_name           VARCHAR(255),
    to_address          VARCHAR(255),
    subject             VARCHAR(255),
    body                TEXT,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_read             BOOLEAN NOT NULL DEFAULT FALSE,
    ai_intent           VARCHAR(32),
    ai_summary          TEXT,
    ai_analysis_status  VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 去重：同一邮箱同一 UID 唯一
CREATE UNIQUE INDEX uk_inbox_uid ON email_inbox(mailbox, uid);
CREATE INDEX idx_inbox_lead ON email_inbox(lead_id);
CREATE INDEX idx_inbox_from ON email_inbox(from_address);
CREATE INDEX idx_inbox_received ON email_inbox(received_at);
CREATE INDEX idx_inbox_read ON email_inbox(is_read);
