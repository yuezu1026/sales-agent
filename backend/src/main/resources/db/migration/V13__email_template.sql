-- V13: 邮件模板 email_template
-- M3-2 补充：可复用的邮件主题/正文模板，subject/body 支持占位符变量
-- （{companyName} {contactName} {date} 等），保存草稿/发送时按客户实际字段替换。
-- 模板管理页可随时编辑美化（前端实时预览）。

CREATE TABLE email_template (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(64) NOT NULL UNIQUE,      -- 模板名称（唯一）
    subject     VARCHAR(255) NOT NULL,            -- 邮件主题（可含占位符）
    body        TEXT NOT NULL,                    -- 邮件正文（可含占位符）
    description VARCHAR(255),                     -- 适用场景说明
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
