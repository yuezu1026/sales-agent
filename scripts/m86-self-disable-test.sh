#!/bin/bash
BASE=http://localhost:8080/api
echo "=== 0. 查 tadmin_e2e id（DB） ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -t -c "select id, username, role, status, tenant_id from users where username in ('tadmin_e2e','op_t_e2e') order by id;"
echo
echo "=== 1. tadmin_e2e 尝试禁用自己（应 400 不能禁用当前登录账号） ==="
TOKEN_T=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' -d '{"username":"tadmin_e2e","password":"Tadmin@12345"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["token"])')
TID=$(sudo docker exec aic-db psql -U ai_customer -d ai_customer -t -c "select id from users where username='tadmin_e2e';" | tr -d ' \n')
echo "tadmin_e2e id=$TID"
curl -s -X PUT $BASE/users/$TID/status -H "Authorization: Bearer $TOKEN_T" -H 'Content-Type: application/json' -d '{"status":"disabled"}'
echo
echo "=== 2. tadmin_e2e 禁用本租户操作员 op_t_e2e（应 200 允许） ==="
OID=$(sudo docker exec aic-db psql -U ai_customer -d ai_customer -t -c "select id from users where username='op_t_e2e';" | tr -d ' \n')
echo "op_t_e2e id=$OID"
curl -s -X PUT $BASE/users/$OID/status -H "Authorization: Bearer $TOKEN_T" -H 'Content-Type: application/json' -d '{"status":"disabled"}'
echo
echo "=== 3. 恢复 op_t_e2e ==="
curl -s -X PUT $BASE/users/$OID/status -H "Authorization: Bearer $TOKEN_T" -H 'Content-Type: application/json' -d '{"status":"active"}'
echo
echo "=== 4. tadmin_e2e 禁用平台系统管理员 admin2_e2e（应 403/400 越权） ==="
AID=$(sudo docker exec aic-db psql -U ai_customer -d ai_customer -t -c "select id from users where username='admin2_e2e';" | tr -d ' \n')
echo "admin2_e2e id=$AID"
curl -s -X PUT $BASE/users/$AID/status -H "Authorization: Bearer $TOKEN_T" -H 'Content-Type: application/json' -d '{"status":"disabled"}'
echo
echo "=== 5. tadmin_e2e 禁用其他租户用户（应 404 租户隔离） ==="
curl -s -X PUT $BASE/users/1/status -H "Authorization: Bearer $TOKEN_T" -H 'Content-Type: application/json' -d '{"status":"disabled"}'
echo
