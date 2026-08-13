#!/bin/bash
BASE=http://localhost:8080/api
echo "=== 1. op_t_e2e 登录（应被拒 403，账号已禁用） ==="
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"op_t_e2e","password":"OpT@54321"}'
echo
echo "=== 2. tadmin_e2e 启用 op_t_e2e（恢复） ==="
TOKEN_T=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"tadmin_e2e","password":"Tadmin@12345"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
curl -s -X PUT $BASE/users/5/status -H "Authorization: Bearer $TOKEN_T" -H 'Content-Type: application/json' -d '{"status":"active"}'
echo
echo "=== 3. op_t_e2e 登录（应恢复成功） ==="
curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"op_t_e2e","password":"OpT@54321"}' | python3 -c 'import sys,json;d=json.load(sys.stdin);print("code:",d["code"],"username:",d["data"]["username"] if d["data"] else None)'
