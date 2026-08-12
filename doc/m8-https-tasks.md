# M8 任务清单：服务器 HTTPS 部署（腾讯云证书）

> 状态：✅ 已完成（2026-08-10）
> 目标：为生产服务器启用 HTTPS（腾讯云 SSL 证书），网关层 TLS 终结，HTTP 自动跳转 HTTPS；本地开发保持纯 HTTP

---

## 一、用户需求（原文）

> 能否帮我在服务器上部署 https 的支持？
> （后续补充）请修改服务器使用 https，本地部署不需要
> 请用腾讯云给我创建的证书：C:\Users\39002\Downloads\sales-agent.top_nginx，让服务器支持 https

补充确认（2026-08-10 问答）：

- 部署方式：`docker compose` 源码构建（`docker-compose.yml`）
- 域名：`sales-agent.top`（已有，腾讯云证书包已下载到本地）
- 证书：**腾讯云 SSL 证书**（非 Let's Encrypt），nginx 格式包
- 范围：**仅服务器启用 HTTPS，本地保持纯 80**

## 二、设计决策

| 维度        | 决策                                                                                                    |
| :---------- | :------------------------------------------------------------------------------------------------------ |
| TLS 终结    | 网关层 `gateway` 容器（nginx）终结 TLS：443 承担业务，80 一律 301 跳转 HTTPS                            |
| 证书来源    | 腾讯云 SSL 证书（`sales-agent.top_bundle.pem` 证书链 + `sales-agent.top.key` 私钥），有效期 90 天手动续 |
| 证书挂载    | `certs/fullchain.pem`、`certs/privkey.pem` 由 `docker-compose.prod.yml` 只读挂载进网关容器              |
| 本地/服务器 | `docker-compose.yml` 保持纯 80（本地默认）；服务器叠加 `docker-compose.prod.yml` 启用 443               |
| 配置覆盖    | prod override 把 `gateway/nginx.ssl.conf` 只读挂载覆盖容器内 `default.conf`（不重建镜像即可切换）       |
| 后端适配    | 前端 API 走相对路径 `/api`（client.ts），HTTPS 下无需改动；nginx 已传 X-Forwarded-Proto                 |

## 三、改动清单

| 文件                                             | 改动                                                                                                                                 |
| :----------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------- |
| `gateway/nginx.conf`                             | 恢复纯 HTTP 配置（本地默认，与原来一致）                                                                                             |
| `gateway/nginx.ssl.conf`                         | 新增：80→301 跳转 + 443 TLS 终结 + /app/ /api/ 代理（docker gateway 备用方案）                                                       |
| `gateway/nginx.server-ssl.conf`                  | 新增：**服务器宿主机 nginx 实际使用的 HTTPS 配置**（80→301 + 443 TLS 终结 + /app/ /api/ 代理，nginx 1.24 兼容 listen 443 ssl http2） |
| `gateway/Dockerfile`                             | 改回 COPY 静态 `nginx.conf`（不再用模板）                                                                                            |
| `gateway/nginx.conf.template`                    | 删除（上一版方案遗留）                                                                                                               |
| `docker-compose.yml`                             | gateway 还原纯 80（去掉 443/证书卷/DOMAIN）                                                                                          |
| `docker-compose.prod.yml`                        | 新增：服务器 override（443 端口 + ssl 配置覆盖 + 证书挂载）                                                                          |
| `scripts/setup-cert.sh`                          | 新增：腾讯云 nginx 包 → `certs/` 安装脚本                                                                                            |
| `scripts/issue-cert.sh`、`scripts/renew-cert.sh` | 删除（Let's Encrypt 方案废弃）                                                                                                       |
| `certs/`                                         | 证书目录（gitignore，含 fullchain.pem + privkey.pem）                                                                                |
| `.gitignore`                                     | `certbot/` → `certs/`                                                                                                                |
| `.env.example`                                   | 去掉 DOMAIN                                                                                                                          |
| `doc/deploy.md`                                  | 架构图 + FAQ 8 更新为腾讯云证书方案                                                                                                  |

## 四、服务器部署记录（2026-08-10 已执行，宿主机 nginx 方案）

```bash
# 1. 上传证书（本地 → 服务器）
scp certs/fullchain.pem certs/privkey.pem ubuntu@43.153.229.106:/tmp/certs/
# 2. 安装证书到 /etc/nginx/certs/（chmod 644 证书 / 600 私钥）
# 3. 上传 nginx.server-ssl.conf → /etc/nginx/sites-enabled/sales-agent（原配置已备份为 /home/ubuntu/sales-agent.bak-20260810）
# 4. nginx -t && sudo nginx -s reload
# 5. 验证
curl -I https://sales-agent.top/app/   # 200（无告警）
curl -I http://sales-agent.top/        # 301 → https://

# （90 天后）腾讯云重新签发 → 下载 nginx 包 → 替换 /etc/nginx/certs/ 两个文件 → nginx -s reload
```

> 踩坑记录：
>
> - 服务器 nginx 为 1.24.0，**不支持** `http2 on;` 独立指令（需 1.25.1+），必须用 `listen 443 ssl http2;` 旧语法
> - 备份文件不能放 `/etc/nginx/sites-enabled/` 内（会被加载导致 duplicate default server 报错），应移到 `/home/ubuntu/`
> - nginx 配置 `/etc/nginx/nginx.conf` 默认 `http { include /etc/nginx/sites-enabled/*; }` 加载全部站点文件

## 五、验证记录

- [x] 证书文件复制为 nginx 标准命名（fullchain.pem 4459B / privkey.pem 1704B）
- [x] 证书与私钥匹配（Python ssl load_cert_chain 验证通过，CN=sales-agent.top，有效期 2026-08-10 → 2027-02-24）
- [x] `nginx.ssl.conf` 证书路径与 prod override 挂载路径一致
- [x] 本地 `docker compose build gateway` 成功
- [x] HTTP 配置 nginx -t 通过
- [x] HTTPS 配置 nginx -t 通过（含证书加载，修正 http2 弃用警告）
- [x] 本地纯 80 正常：compose up 后 gateway 仅映射 80，/app/ 200、/api/license 200
- [x] **服务器部署完成**：证书安装到 /etc/nginx/certs/、宿主机 nginx 站点配置替换、nginx -t 通过、reload 后 443 监听
- [x] 服务器 80 → 443 跳转（http://sales-agent.top/ → 301 Location: https://sales-agent.top/，公网实测）
- [x] 服务器 443 证书加载：https://sales-agent.top 无告警（TLSv1.3，证书 CN=sales-agent.top，TrustAsia DV TLS RSA CA 2025，有效期 2026-08-10 → 2027-02-24，SAN 含 sales-agent.top + www.sales-agent.top）
- [x] 公网全路径验证：`/` 200（营销首页 20949B）、`/app/` 200、`/app/login` 200（登录页正常渲染）、`/api/license` 200
- [x] 浏览器实测：https://sales-agent.top/app/login 登录页、https://sales-agent.top/ 营销首页均正常渲染、无证书告警

## 六、交付

- **服务器 HTTPS 已上线**：https://sales-agent.top 公网可访问，HTTP 自动 301 跳转 HTTPS
- 服务器配置：`gateway/nginx.server-ssl.conf`（宿主机 nginx，实际使用）+ `/etc/nginx/certs/`
- 本地保持纯 80；docker prod override（`nginx.ssl.conf` + `docker-compose.prod.yml`）作为备用方案保留
- `scripts/setup-cert.sh` 证书安装脚本；deploy.md FAQ 8 文档更新
