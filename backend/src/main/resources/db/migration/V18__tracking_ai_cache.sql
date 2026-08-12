-- V18: 邮件打开/点击追踪 + AI 缓存
-- M4-6：
-- 1) email_send_log 加 opened_at / clicked_at（首次打开/点击时间，幂等只记一次）
-- 2) 新建 ai_cache 表（AI 重复请求缓存：chat 生成 + embedding 向量）

-- ==================== 1. 打开率追踪 ====================

ALTER TABLE email_send_log
    ADD COLUMN opened_at  TIMESTAMPTZ,
    ADD COLUMN clicked_at TIMESTAMPTZ;

CREATE INDEX idx_esl_opened ON email_send_log(opened_at);
CREATE INDEX idx_esl_clicked ON email_send_log(clicked_at);

-- ==================== 2. AI 缓存 ====================

-- kind: chat（大模型生成）/ embedding（向量化）
-- cache_key: SHA-256 hex（chat = scene+system+user 组合哈希；embedding = 原文哈希）
-- response: chat 存生成文本；embedding 存 JSON 向量 {"dim":N,"data":[...]}
CREATE TABLE ai_cache (
    id           BIGSERIAL PRIMARY KEY,
    kind         VARCHAR(20)  NOT NULL,
    scene        VARCHAR(50),
    cache_key    VARCHAR(64)  NOT NULL,
    response     TEXT         NOT NULL,
    total_tokens INT          NOT NULL DEFAULT 0,
    hit_count    INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_ai_cache ON ai_cache(kind, cache_key);
CREATE INDEX idx_ai_cache_created ON ai_cache(created_at);
