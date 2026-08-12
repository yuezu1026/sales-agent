-- V19: 登录日志 login_logs
-- M7.9：系统登录次数统计。每次成功登录插入一条，供累计/今日统计聚合。
-- 注：仅记录成功登录（密码校验通过后），登录失败不记录。

CREATE TABLE login_logs (
    id        BIGSERIAL PRIMARY KEY,
    username  VARCHAR(64)  NOT NULL,
    login_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ll_login_at ON login_logs(login_at);
