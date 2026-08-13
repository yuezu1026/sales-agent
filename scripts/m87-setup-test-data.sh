#!/bin/bash
# M8.7 创建测试数据：默认租户(1)下插入 租户管理员 + 普通操作员
# 密码统一用 admin 的 BCrypt 哈希（即 Admin@123456）
# 用 python3 生成 SQL（避开 $ 转义问题），subprocess 直接传 stdin 给 psql
set -e

echo "=== 1. 查询 admin 的 password_hash（BCrypt）==="
HASH=$(sudo docker exec aic-db psql -U ai_customer -d ai_customer -tAc "SELECT password_hash FROM users WHERE username='admin'")
echo "hash: ${HASH:0:20}..."

echo "=== 2. 插入测试账号（租户1：tadmin_m87 租户管理员 / op_m87 操作员）==="
python3 - "$HASH" <<'PY'
import subprocess, sys
h = sys.argv[1]
sql = (
    "INSERT INTO users (username, password_hash, display_name, role, status, tenant_id, created_at, last_login_at) VALUES\n"
    f" ('tadmin_m87', '{h}', 'M87租户管理员', 'admin', 'active', 1, now(), NULL),\n"
    f" ('op_m87',     '{h}', 'M87操作员',     'operator', 'active', 1, now(), NULL)\n"
    "ON CONFLICT (username) DO NOTHING;"
)
p = subprocess.run(
    ["sudo", "docker", "exec", "-i", "aic-db", "psql", "-U", "ai_customer",
     "-d", "ai_customer", "-v", "ON_ERROR_STOP=1"],
    input=sql.encode(), capture_output=True)
print(p.stdout.decode(), end="")
if p.returncode != 0:
    print(p.stderr.decode(), end="")
    sys.exit(1)
PY

echo "=== 3. 验证插入 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "SELECT id, username, role, tenant_id, status FROM users ORDER BY id;"
