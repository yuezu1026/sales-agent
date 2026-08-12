-- ============================================================
-- V2: Prompt 模板表（db-design §2.12）
-- 场景唯一，content 变更版本号自增
-- ============================================================

CREATE TABLE IF NOT EXISTS prompt_template (
    id         BIGSERIAL PRIMARY KEY,
    scene      VARCHAR(32)  NOT NULL UNIQUE,
    name       VARCHAR(64)  NOT NULL,
    content    TEXT         NOT NULL,
    version    INTEGER      NOT NULL DEFAULT 1,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 默认邮件生成模板（email_gen）
INSERT INTO prompt_template (scene, name, content, enabled)
VALUES ('email_gen', 'B2B 销售邮件生成',
        '你是一名专业的 B2B 销售邮件撰写助手。请根据客户信息生成一封简洁、专业、有人情味的中文销售邮件。要求：1. 主题行不超过 20 字；2. 正文 3-4 句话，突出客户价值而非产品推销；3. 结尾给出一个低门槛的行动邀请；4. 不要使用夸张营销用语。',
        TRUE)
ON CONFLICT (scene) DO NOTHING;
