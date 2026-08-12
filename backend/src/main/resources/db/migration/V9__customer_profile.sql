-- ============================================================
-- V9: customer_profile 自有客户画像表（db-design §2.7）
-- M2-3 RAG 客户画像：CSV 导入向量化 + 检索打分（profile_score）
-- embedding 存 JSON 文本（MVP 不引入 pgvector；本地 TF-IDF 稀疏向量）
-- ============================================================

CREATE TABLE IF NOT EXISTS customer_profile (
    id            BIGSERIAL PRIMARY KEY,
    company_name  VARCHAR(128)  NOT NULL,
    industry      VARCHAR(64),
    contact_name  VARCHAR(64),
    contact_email VARCHAR(128),
    deal_value    NUMERIC(12,2),
    tags          VARCHAR(255),
    description   TEXT,
    embedding     TEXT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- 画像去重（同一公司只保留一份语料）
CREATE UNIQUE INDEX IF NOT EXISTS uk_cp_company ON customer_profile (LOWER(company_name));

-- 邮箱检索索引
CREATE INDEX IF NOT EXISTS idx_cp_email ON customer_profile (contact_email);
