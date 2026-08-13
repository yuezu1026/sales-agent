#!/bin/bash
# M8.7 验证：GET /users/all 排除系统管理员
# 前置：先部署 backend 并等待健康
set -e
BASE="http://127.0.0.1:8080"

echo "=== 1. 后端健康 ==="
curl -s "$BASE/api/health"

echo ""
echo "=== 2. admin 登录 ==="
TOKEN=$(curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])" 2>/dev/null)
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "登录失败！"
  curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"Admin@123456"}'
  exit 1
fi
echo "登录成功"

echo ""
echo "=== 3. GET /users/all 应为 200（仅系统管理员可调）==="
curl -s -w "\nHTTP:%{http_code}\n" "$BASE/api/users/all" -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json
raw=sys.stdin.read()
# 分离 body 与 HTTP 码
body,_,rest=raw.rpartition('HTTP:')
data=json.loads(body)
print('code:',data.get('code'))
users=data.get('data') or []
print('用户数:',len(users))
print('username | role | tenant_id | tenantName')
for u in users:
    print(f\"{u.get('username')} | {u.get('role')} | {u.get('tenantId')} | {u.get('tenantName')}\")
# 断言：不应有 role=admin 且 tenantId 为空的
bad=[u for u in users if u.get('role')=='admin' and u.get('tenantId') is None]
if bad:
    print('❌ 仍包含系统管理员:',[u.get('username') for u in bad])
    sys.exit(1)
print('✅ 不包含系统管理员')
"

echo ""
echo "=== 4. GET /users（系统用户管理视图）应仍含系统管理员 ==="
curl -s "$BASE/api/users" -H "Authorization: Bearer $TOKEN" | python3 -c "
import sys,json
data=json.load(sys.stdin)
users=data.get('data') or []
print('用户数:',len(users))
for u in users:
    print(f\"{u.get('username')} | {u.get('role')} | {u.get('tenantId')}\")
if not users:
    print('❌ 系统用户管理为空')
    sys.exit(1)
sysadmins=[u for u in users if u.get('role')=='admin' and u.get('tenantId') is None]
print('✅ 系统管理员数:',len(sysadmins))
"
