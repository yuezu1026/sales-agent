#!/bin/bash
echo "=== 表清单 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -t -c "select tablename from pg_tables where schemaname='public' order by tablename;"
echo
echo "=== 含 user_id / operator_id / 相关引用列的表 ==="
sudo docker exec aic-db psql -U ai_customer -d ai_customer -c "select table_name, column_name from information_schema.columns where table_schema='public' and (column_name like '%user_id%' or column_name like '%operator%' or column_name='username' or column_name like '%created_by%') order by table_name, column_name;"
