FROM nginx:1.27-alpine
# 默认纯 HTTP 配置（本地开发）；服务器 HTTPS 由 docker-compose.prod.yml
# 以只读挂载 nginx.ssl.conf 覆盖 /etc/nginx/conf.d/default.conf 启用
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html /usr/share/nginx/html/index.html
