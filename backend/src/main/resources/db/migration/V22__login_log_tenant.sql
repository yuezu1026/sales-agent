-- V22: 登录日志增加租户归属（租户级登录统计/地理分布隔离，2026-08-12）
-- tenant_id：登录用户所属租户 id；系统管理员（平台级）登录为 NULL（统计时不归属任何租户）
-- 用途：系统管理员看全平台统计；租户管理员/普通用户只看自己租户的统计
-- 历史记录（V22 之前）按 username 回填（users.username 唯一）

ALTER TABLE login_logs ADD COLUMN tenant_id BIGINT;
CREATE INDEX idx_ll_tenant_login_at ON login_logs(tenant_id, login_at);

UPDATE login_logs ll
SET tenant_id = u.tenant_id
FROM users u
WHERE u.username = ll.username;
