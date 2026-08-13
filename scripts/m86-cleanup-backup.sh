#!/bin/bash
set -e
echo "=== 1. 备份数据库 ==="
sudo docker exec aic-db pg_dump -U ai_customer -d ai_customer -f /tmp/pre_cleanup_20260813.sql
sudo docker cp aic-db:/tmp/pre_cleanup_20260813.sql /home/ubuntu/ai-customer-deploy/backup_pre_cleanup_20260813.sql
ls -la /home/ubuntu/ai-customer-deploy/backup_pre_cleanup_20260813.sql
echo
echo "=== 2. 检查 donations 结构（确认无测试账号关联） ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select column_name from information_schema.columns where table_schema='public' and table_name='donations' order by ordinal_position;"
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select count(*) as total from donations;"
