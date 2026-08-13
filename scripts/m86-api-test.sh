#!/bin/bash
# M8.6 E2E API 验证脚本（在服务器上执行）
set -e
BASE=http://localhost:8080/api

login() {
  curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])'
}

TOKEN_T=$(login tadmin_e2e Tadmin@12345)
TOKEN_O=$(login op_t_e2e OpT@54321)
TOKEN_A=$(login admin2_e2e Admin2@54321)

echo "=== 1. tadmin_e2e /users（期望：只有 op_t_e2e，不含自己） ==="
curl -s $BASE/users -H "Authorization: Bearer $TOKEN_T" | python3 -c '
import sys,json
data=json.load(sys.stdin)["data"]
print("数量:", len(data))
for u in data: print(" -", u["username"], u["role"], "tenant:", u["tenantName"])
names=[u["username"] for u in data]
assert "tadmin_e2e" not in names, "FAIL: 租户管理员列表包含自己!"
assert names == ["op_t_e2e"], "FAIL: 租户管理员应只看到 op_t_e2e!"
print("PASS: 租户管理员列表不含自己，只含 op_t_e2e")
'

echo "=== 2. op_t_e2e /users（期望：只有自己一行） ==="
curl -s $BASE/users -H "Authorization: Bearer $TOKEN_O" | python3 -c '
import sys,json
data=json.load(sys.stdin)["data"]
print("数量:", len(data))
for u in data: print(" -", u["username"], u["role"])
assert len(data)==1 and data[0]["username"]=="op_t_e2e", "FAIL: operator 应只看到自己"
print("PASS: operator 只看到自己")
'

echo "=== 3. op_t_e2e /users/all（期望 403） ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE/users/all -H "Authorization: Bearer $TOKEN_O")
echo "HTTP $CODE"
[ "$CODE" = "403" ] && echo "PASS: operator 访问 /users/all 被拒" || echo "FAIL: 期望 403 得到 $CODE"

echo "=== 4. op_t_e2e POST /users（期望 403） ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST $BASE/users -H "Authorization: Bearer $TOKEN_O" -H 'Content-Type: application/json' -d '{"username":"hacker1","password":"Hacker@123"}')
echo "HTTP $CODE"
[ "$CODE" = "403" ] && echo "PASS: operator 创建用户被拒" || echo "FAIL: 期望 403 得到 $CODE"

echo "=== 5. op_t_e2e 尝试重置他人密码（期望 403/404） ==="
# 用 tadmin_e2e 的 id（先查 tadmin_e2e 的 id）
TADMIN_ID=$(curl -s $BASE/users -H "Authorization: Bearer $TOKEN_A" | python3 -c 'import sys,json;data=json.load(sys.stdin)["data"];print([u["id"] for u in data if u["username"]=="tadmin_e2e"][0])')
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT $BASE/users/$TADMIN_ID/password -H "Authorization: Bearer $TOKEN_O" -H 'Content-Type: application/json' -d '{"newPassword":"Hacked@123"}')
echo "HTTP $CODE"
[ "$CODE" = "404" ] || [ "$CODE" = "403" ] && echo "PASS: operator 重置他人密码被拒" || echo "FAIL: 期望 403/404 得到 $CODE"

echo "=== 6. admin2_e2e /users（系统管理员，期望 2 个系统管理员） ==="
curl -s $BASE/users -H "Authorization: Bearer $TOKEN_A" | python3 -c '
import sys,json
data=json.load(sys.stdin)["data"]
print("数量:", len(data))
for u in data: print(" -", u["username"], u["role"])
assert len(data)>=2, "FAIL: 系统管理员列表应含 admin+admin2_e2e"
print("PASS: 系统管理员列表正常")
'

echo "=== ALL API TESTS DONE ==="
