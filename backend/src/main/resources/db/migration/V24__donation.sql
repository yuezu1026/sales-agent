-- 捐助记录（捐助拾客 Shike 开源项目）
CREATE TABLE IF NOT EXISTS donations (
    id          BIGSERIAL PRIMARY KEY,
    donor       VARCHAR(64)  NOT NULL DEFAULT '匿名用户',
    amount_cents BIGINT      NOT NULL,
    channel     VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
