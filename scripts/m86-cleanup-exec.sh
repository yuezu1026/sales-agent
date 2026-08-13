#!/bin/bash
echo "=== 1. 删除租户2业务数据 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "
DELETE FROM prompt_template WHERE tenant_id=2;
DELETE FROM data_source WHERE tenant_id=2;
DELETE FROM system_config WHERE tenant_id=2;
DELETE FROM ai_cache WHERE tenant_id=2;
DELETE FROM ai_usage_log WHERE tenant_id=2;
DELETE FROM customer_profile WHERE tenant_id=2;
DELETE FROM email_draft WHERE tenant_id=2;
DELETE FROM email_inbox WHERE tenant_id=2;
DELETE FROM email_send_log WHERE tenant_id=2;
DELETE FROM email_template WHERE tenant_id=2;
DELETE FROM email_unsubscribe WHERE tenant_id=2;
DELETE FROM follow_up WHERE tenant_id=2;
DELETE FROM lead WHERE tenant_id=2;
DELETE FROM wechat_message WHERE tenant_id=2;"
echo
echo "=== 2. 删除测试账号 login_logs ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "DELETE FROM login_logs WHERE username IN ('op_e2e','admin2_e2e','tadmin_e2e','op_t_e2e');"
echo
echo "=== 3. 删除测试租户 E2E测试公司 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "DELETE FROM tenants WHERE id=2;"
echo
echo "=== 4. 删除测试账号 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "DELETE FROM users WHERE username IN ('op_e2e','admin2_e2e','tadmin_e2e','op_t_e2e');"
echo
echo "=== 5. 验证：剩余 users / tenants ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select id, username, role, tenant_id, status from users order by id;"
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select id, name, owner_user_id, status from tenants order by id;"
echo
echo "=== 6. 验证：租户1数据完好 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -t -c "
  select 'lead' as t, count(*) from lead
  union all select 'customer_profile', count(*) from customer_profile
  union all select 'email_inbox', count(*) from email_inbox
  union all select 'email_send_log', count(*) from email_send_log
  union all select 'system_config', count(*) from system_config
  union all select 'prompt_template', count(*) from prompt_template
  union all select 'email_template', count(*) from email_template
  union all select 'donations', count(*) from donations;" 2>&1 | grep -v '^$'
