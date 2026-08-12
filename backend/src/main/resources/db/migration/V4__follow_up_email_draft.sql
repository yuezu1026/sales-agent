-- V4: 跟进记录 follow_up + 邮件草稿 email_draft
-- 需求：客户跟踪记录管理 + AI 邮件内容保存记录管理
-- follow_up.method: phone / email / wechat / visit / other
-- email_draft.status: draft / confirmed（confirmed_at 记录确认时间）

CREATE TABLE follow_up (
    id          BIGSERIAL PRIMARY KEY,
    lead_id     BIGINT NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    method      VARCHAR(32) NOT NULL DEFAULT 'other',
    content     TEXT NOT NULL,
    happened_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_fu_lead ON follow_up(lead_id);

CREATE TABLE email_draft (
    id           BIGSERIAL PRIMARY KEY,
    lead_id      BIGINT NOT NULL REFERENCES lead(id) ON DELETE CASCADE,
    subject      VARCHAR(255) NOT NULL,
    body         TEXT NOT NULL,
    tone         VARCHAR(32) NOT NULL DEFAULT 'neutral',
    status       VARCHAR(20) NOT NULL DEFAULT 'draft',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    confirmed_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ed_lead ON email_draft(lead_id);
