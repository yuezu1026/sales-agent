#!/bin/bash
# M8.10 修复：外层 nginx 正则 location 抢占 /app/ 与 /api/ 静态请求导致 404
# 方案：给 /app/ 与 /api/ 前缀 location 加 ^~ 修饰符
set -e

CONF=/etc/nginx/sites-enabled/sales-agent
BAK=${CONF}.bak-20260814

# 1. 备份（若已存在则不覆盖，保留最早的原始版本）
if [ ! -f "$BAK" ]; then
  sudo cp "$CONF" "$BAK"
  echo "[1] 已备份 -> $BAK"
else
  echo "[1] 备份已存在，跳过: $BAK"
fi

# 2. 替换（幂等：已有 ^~ 则不重复添加）
sudo sed -i 's|^\([[:space:]]*\)location /app/ {|\1location ^~ /app/ {|' "$CONF"
sudo sed -i 's|^\([[:space:]]*\)location /api/ {|\1location ^~ /api/ {|' "$CONF"
echo "[2] 替换完成，当前 location 行："
grep -n 'location' "$CONF"

# 3. 语法校验
sudo nginx -t
echo "[3] nginx -t 通过"

# 4. reload
sudo nginx -s reload
echo "[4] nginx 已 reload"

# 5. 实测验证
echo "[5] 验证结果："
curl -sk -o /dev/null -w '  /app/logo.svg      -> %{http_code} %{size_download}B %{content_type}\n' https://sales-agent.top/app/logo.svg
curl -sk -o /dev/null -w '  /app/favicon.svg   -> %{http_code} %{size_download}B %{content_type}\n' https://sales-agent.top/app/favicon.svg
curl -sk -o /dev/null -w '  /app/ (index)      -> %{http_code} %{size_download}B %{content_type}\n' https://sales-agent.top/app/
curl -sk -o /dev/null -w '  /api/health        -> %{http_code}\n' https://sales-agent.top/api/health
curl -sk -o /dev/null -w '  宣传站 /           -> %{http_code} %{size_download}B\n' https://sales-agent.top/
