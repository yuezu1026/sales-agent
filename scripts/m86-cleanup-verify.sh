#!/bin/bash
echo "=== 1. 后端健康 ==="
curl -s http://localhost:8080/api/health
echo
echo "=== 2. 测试账号登录应全部失败 ==="
for u in admin2_e2e tadmin_e2e op_t_e2e op_e2e; do
  echo -n "$u: "
  curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$u\",\"password\":\"x\"}" | head -c 120
  echo
done
echo
echo "=== 3. admin 正常登录 ==="
curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"Admin@123456"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print("code:",d["code"],"username:",d["data"]["username"] if d.get("data") else None)' 2>/dev/null || echo "(登录失败，密码可能已改)"
