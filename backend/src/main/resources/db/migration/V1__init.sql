-- ============================================================
-- AI智能获客助手 MVP - 初始表结构（PostgreSQL）
-- 对应 doc/db-design.md，仅 M1 里程碑需要的表
-- ============================================================

-- 用户账号（db-design §2.1）
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    display_name  VARCHAR(64),
    role          VARCHAR(20)  NOT NULL DEFAULT 'admin',
    status        VARCHAR(20)  NOT NULL DEFAULT 'active',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ
);

-- License 授权（db-design §2.2，MVP 单设备简化：指纹直接挂在 license 上）
CREATE TABLE IF NOT EXISTS license (
    id               BIGSERIAL PRIMARY KEY,
    license_key      VARCHAR(64)  NOT NULL UNIQUE,
    edition          VARCHAR(20)  NOT NULL,
    activated_at     TIMESTAMPTZ,
    expire_at        TIMESTAMPTZ,
    status           VARCHAR(20)  NOT NULL DEFAULT 'inactive',
    max_devices      INTEGER      NOT NULL DEFAULT 1,
    fingerprint_hash VARCHAR(128),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- AI 用量记录（db-design §2.11）
CREATE TABLE IF NOT EXISTS ai_usage_log (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT,
    scene             VARCHAR(32)    NOT NULL,
    model             VARCHAR(32)    NOT NULL,
    prompt_tokens     INTEGER        NOT NULL DEFAULT 0,
    completion_tokens INTEGER        NOT NULL DEFAULT 0,
    total_tokens      INTEGER        NOT NULL DEFAULT 0,
    cost              NUMERIC(10,6)  NOT NULL DEFAULT 0,
    status            VARCHAR(20)    NOT NULL DEFAULT 'success',
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_aul_scene ON ai_usage_log (scene);
CREATE INDEX IF NOT EXISTS idx_aul_created ON ai_usage_log (created_at);

-- 系统配置（db-design §2.13）
CREATE TABLE IF NOT EXISTS system_config (
    id           BIGSERIAL PRIMARY KEY,
    config_key   VARCHAR(64) NOT NULL UNIQUE,
    config_value TEXT,
    description  VARCHAR(255),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 初始管理员账号：admin / Admin@123456（首次登录后请修改）
-- 密码为 BCrypt 哈希（由启动时 CommandLineRunner 幂等写入，见 InitDataConfig）
