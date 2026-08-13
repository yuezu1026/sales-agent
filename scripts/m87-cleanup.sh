#!/bin/bash
# M8.7 清理测试数据：删除 tadmin_m87 + op_m87 及其 login_logs
set -e
echo "=== 1. 删除测试账号 login_logs ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "DELETE FROM login_logs WHERE username IN ('tadmin_m87','op_m87');"

echo "=== 2. 删除测试账号 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "DELETE FROM users WHERE username IN ('tadmin_m87','op_m87');"

echo "=== 3. 验证：剩余用户 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "SELECT id, username, role, tenant_id, status FROM users ORDER BY id;"

echo "=== 4. 验证：测试账号登录应失败 ==="
for u in tadmin_m87 op_m87; do
  echo -n "$u: "
  curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$u\",\"password\":\"Admin@123456\"}" | head -c 80
  echo
done

echo "=== 5. admin 登录仍正常 ==="
curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"Admin@123456"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print("code:",d["code"],"username:",d["data"]["username"] if d.get("data") else None)'
