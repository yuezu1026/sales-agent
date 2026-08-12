#!/usr/bin/env bash
# ============================================================
# 安装腾讯云 SSL 证书到 ./certs/（供 docker-compose.prod.yml 挂载）
# 用法:  ./scripts/setup-cert.sh <腾讯云nginx包目录> [域名]
# 示例:  ./scripts/setup-cert.sh ~/Downloads/sales-agent.top_nginx
#
# 腾讯云 SSL 证书控制台 → 申请证书 → 下载 → 选择「Nginx」格式，
# 解压后目录内应有 xxx_bundle.pem（证书链）与 xxx.key（私钥）。
# 本脚本将其复制为 nginx 标准文件名 fullchain.pem / privkey.pem。
# ============================================================
set -euo pipefail

SRC_DIR="${1:?用法: ./scripts/setup-cert.sh <腾讯云nginx包目录> [域名]}"
DOMAIN="${2:-sales-agent.top}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

if [ ! -d "$SRC_DIR" ]; then
  echo "❌ 目录不存在: $SRC_DIR" >&2
  exit 1
fi

# 证书链 *_bundle.pem（腾讯云 nginx 包固定命名）
BUNDLE="$(find "$SRC_DIR" -maxdepth 1 -name "*_bundle.pem" | head -1)"
# 私钥 *.key（排除 .csr）
KEY="$(find "$SRC_DIR" -maxdepth 1 -name "*.key" | head -1)"

if [ -z "$BUNDLE" ] || [ -z "$KEY" ]; then
  echo "❌ 未找到证书文件，请确认是腾讯云「Nginx」格式的证书包" >&2
  echo "   需要: *_bundle.pem（证书链）+ *.key（私钥）" >&2
  exit 1
fi

mkdir -p "$PROJECT_DIR/certs"
cp "$BUNDLE" "$PROJECT_DIR/certs/fullchain.pem"
cp "$KEY" "$PROJECT_DIR/certs/privkey.pem"
chmod 600 "$PROJECT_DIR/certs/privkey.pem"

echo "✅ 证书已安装到 ./certs/"
echo "   fullchain.pem <- $BUNDLE"
echo "   privkey.pem   <- $KEY"
echo ""
echo "下一步（服务器）:"
echo "   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build"
echo "验证: curl -I https://$DOMAIN/app/"
