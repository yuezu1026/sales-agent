#!/bin/bash
echo "=== system_config 全貌（tenant_id 分布 + 租户2的 key） ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select tenant_id, count(*) from system_config group by tenant_id order by tenant_id nulls first;"
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select id, config_key, tenant_id, left(coalesce(description,''), 40) as descr from system_config where tenant_id=2 order by id;"
echo
echo "=== data_source 租户2 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select id, tenant_id, name from data_source where tenant_id=2;"
echo
echo "=== 4 账号 login_logs 分布 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select username, count(*) from login_logs where username in ('op_e2e','admin2_e2e','tadmin_e2e','op_t_e2e') group by username;"
