-- ============================================================
-- V21: SaaS 多租户改造
-- 1) 新建 tenants 租户表
-- 2) users 加 tenant_id（NULL = 平台级账号，如初始 admin）
-- 3) 全部业务表加 tenant_id，存量数据归默认租户（id=1）
-- 4) 重建跨表唯一约束为 (tenant_id, ...) 粒度，避免租户间冲突
-- 5) 移除 License 授权表（SaaS 注册即用，不再需要激活码/设备指纹）
-- ============================================================

-- ==================== 1. 租户表 ====================

CREATE TABLE tenants (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,          -- 租户名称（公司名）
    owner_user_id BIGINT,                          -- 租户管理员用户 id
    plan          VARCHAR(20)  NOT NULL DEFAULT 'free',  -- 套餐（本期固定 free）
    status        VARCHAR(20)  NOT NULL DEFAULT 'active', -- active / disabled
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expire_at     TIMESTAMPTZ                      -- 到期时间（套餐预留）
);

-- 默认租户：存量业务数据归属（全新部署时 id=1）
INSERT INTO tenants (name, plan, status)
VALUES ('默认租户', 'free', 'active');

-- ==================== 2. users 加 tenant_id ====================

ALTER TABLE users ADD COLUMN tenant_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_users_tenant ON users (tenant_id);

-- ==================== 3. 业务表加 tenant_id（存量归默认租户） ====================

-- 通用模式：ADD COLUMN NOT NULL DEFAULT 1 填充存量 → 移除 DEFAULT（后续必须显式写入）→ 加索引

-- lead（含重建去重唯一索引）
ALTER TABLE lead ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE lead ALTER COLUMN tenant_id DROP DEFAULT;
DROP INDEX IF EXISTS uk_lead_source;
CREATE UNIQUE INDEX uk_lead_source ON lead (tenant_id, source_type, source_id) WHERE source_id IS NOT NULL;
CREATE INDEX idx_lead_tenant ON lead (tenant_id);

-- follow_up
ALTER TABLE follow_up ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE follow_up ALTER COLUMN tenant_id DROP DEFAULT;
CREATE INDEX idx_fu_tenant ON follow_up (tenant_id);

-- email_draft
ALTER TABLE email_draft ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE email_draft ALTER COLUMN tenant_id DROP DEFAULT;
CREATE INDEX idx_ed_tenant ON email_draft (tenant_id);

-- email_inbox
ALTER TABLE email_inbox ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE email_inbox ALTER COLUMN tenant_id DROP DEFAULT;
CREATE INDEX idx_ei_tenant ON email_inbox (tenant_id);

-- email_send_log
ALTER TABLE email_send_log ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE email_send_log ALTER COLUMN tenant_id DROP DEFAULT;
CREATE INDEX idx_esl_tenant ON email_send_log (tenant_id);

-- wechat_message
ALTER TABLE wechat_message ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE wechat_message ALTER COLUMN tenant_id DROP DEFAULT;
CREATE INDEX idx_wm_tenant ON wechat_message (tenant_id);

-- customer_profile
ALTER TABLE customer_profile ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE customer_profile ALTER COLUMN tenant_id DROP DEFAULT;
CREATE INDEX idx_cp_tenant ON customer_profile (tenant_id);

-- ai_usage_log
ALTER TABLE ai_usage_log ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE ai_usage_log ALTER COLUMN tenant_id DROP DEFAULT;
CREATE INDEX idx_aul_tenant ON ai_usage_log (tenant_id);

-- ai_cache（重建缓存唯一索引为租户粒度）
ALTER TABLE ai_cache ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE ai_cache ALTER COLUMN tenant_id DROP DEFAULT;
DROP INDEX IF EXISTS uk_ai_cache;
CREATE UNIQUE INDEX uk_ai_cache ON ai_cache (tenant_id, kind, cache_key);
CREATE INDEX idx_ai_cache_tenant ON ai_cache (tenant_id);

-- system_config（唯一键 config_key → (tenant_id, config_key)）
ALTER TABLE system_config ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE system_config ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE system_config DROP CONSTRAINT IF EXISTS system_config_config_key_key;
ALTER TABLE system_config ADD CONSTRAINT uk_sc_tenant_key UNIQUE (tenant_id, config_key);
CREATE INDEX idx_sc_tenant ON system_config (tenant_id);

-- prompt_template（唯一键 scene → (tenant_id, scene)）
ALTER TABLE prompt_template ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE prompt_template ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE prompt_template DROP CONSTRAINT IF EXISTS prompt_template_scene_key;
ALTER TABLE prompt_template ADD CONSTRAINT uk_pt_tenant_scene UNIQUE (tenant_id, scene);

-- email_template（唯一键 name → (tenant_id, name)）
ALTER TABLE email_template ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE email_template ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE email_template DROP CONSTRAINT IF EXISTS email_template_name_key;
ALTER TABLE email_template ADD CONSTRAINT uk_et_tenant_name UNIQUE (tenant_id, name);
CREATE INDEX idx_et_tenant ON email_template (tenant_id);

-- data_source（唯一键 type → (tenant_id, type)，存量种子数据归默认租户）
ALTER TABLE data_source ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE data_source ALTER COLUMN tenant_id DROP DEFAULT;
DROP INDEX IF EXISTS uk_ds_type;
CREATE UNIQUE INDEX uk_ds_type ON data_source (tenant_id, type);
CREATE INDEX idx_ds_tenant ON data_source (tenant_id);

-- email_unsubscribe（主键 email → 复合主键 (tenant_id, email)）
ALTER TABLE email_unsubscribe DROP CONSTRAINT IF EXISTS email_unsubscribe_pkey;
ALTER TABLE email_unsubscribe ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE email_unsubscribe ALTER COLUMN tenant_id DROP DEFAULT;
ALTER TABLE email_unsubscribe ADD PRIMARY KEY (tenant_id, email);

-- ==================== 4. 移除 License ====================

DROP TABLE IF EXISTS license CASCADE;
