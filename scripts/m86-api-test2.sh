#!/bin/bash
# M8.6 API 验证 5-6 项
BASE=http://localhost:8080/api
TOKEN_O=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"op_t_e2e","password":"OpT@54321"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
TOKEN_A=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"admin2_e2e","password":"Admin2@54321"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')

# 从 tadmin_e2e 自己视角拿自己的 id（GET /users 现在只返回 op_t_e2e，改用 /auth/me）
TADMIN_ID=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"tadmin_e2e","password":"Tadmin@12345"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["id"])' 2>/dev/null || echo "unknown")
echo "tadmin_e2e id (login): $TADMIN_ID"
if [ "$TADMIN_ID" = "unknown" ]; then
  TADMIN_ID=$(curl -s $BASE/auth/me -H "Authorization: Bearer $TOKEN_A" 2>/dev/null | python3 -c 'import sys,json;print("")' )
fi

echo "=== 5. op_t_e2e 尝试重置 tadmin_e2e 密码（期望 403/404） ==="
# tadmin_e2e 的 id 从数据库查（通过 admin2 的所有用户接口 /users/all）
TADMIN_ID=$(curl -s $BASE/users/all -H "Authorization: Bearer $TOKEN_A" | python3 -c 'import sys,json;data=json.load(sys.stdin)["data"];print([u["id"] for u in data if u["username"]=="tadmin_e2e"][0])')
echo "tadmin_e2e id: $TADMIN_ID"
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT $BASE/users/$TADMIN_ID/password -H "Authorization: Bearer $TOKEN_O" -H 'Content-Type: application/json' -d '{"newPassword":"Hacked@123"}')
echo "HTTP $CODE"
if [ "$CODE" = "404" ] || [ "$CODE" = "403" ]; then echo "PASS: operator 重置他人密码被拒"; else echo "FAIL: 期望 403/404 得到 $CODE"; fi

echo "=== 6. admin2_e2e /users（系统管理员，期望 2 个系统管理员） ==="
curl -s $BASE/users -H "Authorization: Bearer $TOKEN_A" | python3 -c '
import sys,json
data=json.load(sys.stdin)["data"]
print("数量:", len(data))
for u in data: print(" -", u["username"], u["role"])
assert len(data)>=2, "FAIL: 系统管理员列表应含 admin+admin2_e2e"
print("PASS: 系统管理员列表正常")
'

echo "=== 7. admin2_e2e 重置 tadmin_e2e 密码（期望 403，租户用户由租户管理员管理） ==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT $BASE/users/$TADMIN_ID/password -H "Authorization: Bearer $TOKEN_A" -H 'Content-Type: application/json' -d '{"newPassword":"Hacked@123"}')
echo "HTTP $CODE"
if [ "$CODE" = "403" ]; then echo "PASS: 系统管理员重置租户用户被拒"; else echo "FAIL: 期望 403 得到 $CODE"; fi

echo "=== 8. tadmin_e2e 重置 op_t_e2e 密码（期望 200，租户管理员可重置本租户） ==="
OP_ID=$(curl -s $BASE/users -H "Authorization: Bearer $TOKEN_A" | python3 -c 'import sys,json;data=json.load(sys.stdin)["data"];print("")' 2>/dev/null)
# 从 /users/all 拿 op_t_e2e id
OP_ID=$(curl -s $BASE/users/all -H "Authorization: Bearer $TOKEN_A" | python3 -c 'import sys,json;data=json.load(sys.stdin)["data"];print([u["id"] for u in data if u["username"]=="op_t_e2e"][0])')
TOKEN_T=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"tadmin_e2e","password":"Tadmin@12345"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT $BASE/users/$OP_ID/password -H "Authorization: Bearer $TOKEN_T" -H 'Content-Type: application/json' -d '{"newPassword":"OpT@54321"}')
echo "HTTP $CODE"
if [ "$CODE" = "200" ]; then echo "PASS: 租户管理员重置本租户 operator 成功"; else echo "FAIL: 期望 200 得到 $CODE"; fi

echo "=== ALL EXTRA API TESTS DONE ==="
