#!/bin/bash
# M8.2 带宽排查脚本：分析 nginx access log
LOG=/var/log/nginx/access.log
echo "=== 今天的请求(按路径 top15) ==="
awk '{print $7}' $LOG | sort | uniq -c | sort -rn | head -15
echo
echo "=== 今天的请求(按状态码) ==="
awk '{print $9}' $LOG | sort | uniq -c | sort -rn | head -8
echo
echo "=== 今天总传输字节 ==="
awk '{s+=$10} END {print s, "bytes"}' $LOG
echo
echo "=== 最大的10个响应 (bytes, path, ip) ==="
awk '{print $10, $7, $1}' $LOG | sort -rn | head -10
echo
echo "=== 最近15条日志 ==="
tail -15 $LOG
