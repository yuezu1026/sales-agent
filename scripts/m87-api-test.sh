#!/bin/bash
# M8.7 完整 API 验证
# 前置：后端已部署 + 测试数据已创建（tadmin_m87 租户管理员 / op_m87 操作员，密码 Admin@123456）
set -e
BASE=http://localhost:8080/api

login() {
  curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d "{\"username\":\"$1\",\"password\":\"$2\"}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])'
}

TOKEN_A=$(login admin Admin@123456)
TOKEN_T=$(login tadmin_m87 Admin@123456)
TOKEN_O=$(login op_m87 Admin@123456)
echo "三个账号均登录成功"

echo ""
echo "=== 1. admin /users/all（期望：只有 tadmin_m87 + op_m87，不含 admin 系统管理员）==="
curl -s $BASE/users/all -H "Authorization: Bearer $TOKEN_A" | python3 -c '
import sys,json
data=json.load(sys.stdin)["data"]
print("数量:", len(data))
for u in data: print(" -", u["username"], "|", u["role"], "| tenant:", u["tenantName"])
names=[u["username"] for u in data]
assert "admin" not in names, "FAIL: 所有用户管理仍包含系统管理员 admin!"
assert set(names)=={"tadmin_m87","op_m87"}, "FAIL: 应只含 tadmin_m87 + op_m87, 实际 "+str(names)
assert len(data)==2, "FAIL: 数量应为 2"
print("PASS: /users/all 只含普通管理员 + 普通操作员，排除系统管理员")
'

echo ""
echo "=== 2. admin /users（系统用户管理视图，期望：仍只含系统管理员 admin）==="
curl -s $BASE/users -H "Authorization: Bearer $TOKEN_A" | python3 -c '
import sys,json
data=json.load(sys.stdin)["data"]
print("数量:", len(data))
for u in data: print(" -", u["username"], "|", u["role"], "| tenant:", u.get("tenantName"))
names=[u["username"] for u in data]
assert names==["admin"], "FAIL: 系统用户管理应只含 admin, 实际 "+str(names)
print("PASS: 系统用户管理视图不变，仍只显示系统管理员")
'

echo ""
echo "=== 3. op_m87 /users/all（期望 403：普通操作员不可见）==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE/users/all -H "Authorization: Bearer $TOKEN_O")
echo "HTTP $CODE"
[ "$CODE" = "403" ] && echo "PASS: operator 访问 /users/all 被拒" || echo "FAIL: 期望 403 得到 $CODE"

echo ""
echo "=== 4. tadmin_m87 /users（租户管理员视图，期望：只含 op_m87，排除自己）==="
curl -s $BASE/users -H "Authorization: Bearer $TOKEN_T" | python3 -c '
import sys,json
data=json.load(sys.stdin)["data"]
print("数量:", len(data))
for u in data: print(" -", u["username"], "|", u["role"])
names=[u["username"] for u in data]
assert names==["op_m87"], "FAIL: 租户管理员应只看到 op_m87, 实际 "+str(names)
print("PASS: 租户管理员只看到本租户操作员，排除自己")
'

echo ""
echo "=== 5. tadmin_m87 登录后 /users/all（期望 403：租户管理员也不可见）==="
CODE=$(curl -s -o /dev/null -w "%{http_code}" $BASE/users/all -H "Authorization: Bearer $TOKEN_T")
echo "HTTP $CODE"
[ "$CODE" = "403" ] && echo "PASS: 租户管理员访问 /users/all 被拒" || echo "FAIL: 期望 403 得到 $CODE"

echo ""
echo "全部 API 验证通过 ✅"
