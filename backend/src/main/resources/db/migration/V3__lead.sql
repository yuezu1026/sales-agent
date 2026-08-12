-- ============================================================
-- V3: lead 潜客表（db-design §2.5）
-- M2-1 客户管理 CRM：人工录入 / CSV 导入 / 后续 API 挖掘共用
-- ============================================================

CREATE TABLE IF NOT EXISTS lead (
    id              BIGSERIAL PRIMARY KEY,
    company_name    VARCHAR(128) NOT NULL,
    contact_name    VARCHAR(64),
    contact_email   VARCHAR(128),
    contact_phone   VARCHAR(32),
    industry        VARCHAR(64),
    region          VARCHAR(64),
    scale           VARCHAR(32),
    website         VARCHAR(255),
    source_type     VARCHAR(32)  NOT NULL DEFAULT 'manual',
    source_id       VARCHAR(64),
    profile_score   INTEGER      NOT NULL DEFAULT 0,
    profile_summary TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'new',
    notes           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 状态筛选索引
CREATE INDEX IF NOT EXISTS idx_lead_status ON lead (status);

-- 邮箱检索索引
CREATE INDEX IF NOT EXISTS idx_lead_email ON lead (contact_email);

-- 数据源去重（仅对带 source_id 的来源生效；manual 来源 source_id 为空，
-- 由业务层按 company_name 判重）
CREATE UNIQUE INDEX IF NOT EXISTS uk_lead_source
    ON lead (source_type, source_id)
    WHERE source_id IS NOT NULL;
