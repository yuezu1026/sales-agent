-- ============================================================
-- V6: 潜客数据源 data_source（M2-2 潜客挖掘 · db-design §2.4）
-- 第三方企业数据 API 配置（企查查/天眼查等），Function Calling 挖掘时按需调用
-- api_key_encrypted：AES 加密存储（与 system_config 敏感项同策略）
-- 种子数据：mock 内置演示数据源（默认启用，无 API 即可验证全链路）
--           qichacha 企查查（预留，需配置 base_url + api_key 后启用）
-- ============================================================

CREATE TABLE IF NOT EXISTS data_source (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(64)  NOT NULL,
    type              VARCHAR(32)  NOT NULL,
    api_base_url      VARCHAR(255),
    api_key_encrypted VARCHAR(255),
    enabled           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 类型检索索引
CREATE INDEX IF NOT EXISTS idx_ds_type ON data_source (type);

-- 类型唯一（同一数据源只配一份）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ds_type ON data_source (type);

-- 种子数据：内置演示 + 企查查预留
INSERT INTO data_source (name, type, api_base_url, enabled)
VALUES ('内置演示数据源', 'mock', NULL, TRUE)
ON CONFLICT (type) DO NOTHING;

INSERT INTO data_source (name, type, api_base_url, enabled)
VALUES ('企查查（预留）', 'qichacha', 'https://api.qcc.com', FALSE)
ON CONFLICT (type) DO NOTHING;
