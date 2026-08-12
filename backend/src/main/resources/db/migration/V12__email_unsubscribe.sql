-- V12: 退订黑名单 email_unsubscribe
-- M3-2 合规闭环：收件人点击邮件内退订链接后在此落库，后续不再向其发送营销邮件
-- email 为主键（一个邮箱维度拉黑，可能对应多个 lead）

CREATE TABLE email_unsubscribe (
    email       VARCHAR(128) PRIMARY KEY,
    source      VARCHAR(20) NOT NULL DEFAULT 'link',   -- link（邮件内退订链接）
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
