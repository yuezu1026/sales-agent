# M7.6 本地 Docker 部署 + 结构对齐服务器

## 状态

✅ 已完成

## 需求原文

1. 把最新代码部署到本地 Docker Desktop（M7.1~M7.5 全部改动）
2. 本地 Docker 镜像结构与服务器保持一致

## 服务器结构（参照基准）

```
外层 nginx :80 (宿主机 /etc/nginx/sites-enabled/default)
  ├─ /app/  → proxy_pass http://127.0.0.1:8081/  （剥离 /app/ 前缀）
  ├─ /api/  → proxy_pass http://127.0.0.1:8080   （后端）
  └─ /      → try_files → /var/www/sales-agent    （营销首页）
frontend 容器 :8081:80（容器内 nginx 无 /app/ 规则，靠外层剥离）
backend 容器 :8080
db 容器 :5432
```

## 设计决策

- 本地增加 **gateway 服务**（nginx:1.27-alpine，映射 80:80），nginx.conf 与服务器外层 nginx 逐条对齐，仅 proxy_pass 目标改为 compose 服务名（frontend:80 / backend:8080）
- **frontend 端口 8081:80**（与服务器一致），容器内 nginx.conf 保持原始版本（无 /app/ 特判）
- gateway 容器内置**营销首页占位页** index.html（对应服务器 /var/www/sales-agent，本地无该站点，用占位页对齐 location / 行为）
- 撤销先前本地临时的 frontend nginx /app/ rewrite 规则（该方案导致本地与服务器行为不一致，弃用）

## 改动清单

| 文件                  | 改动                                                                |
| --------------------- | ------------------------------------------------------------------- |
| `gateway/Dockerfile`  | 新增：nginx:1.27-alpine + COPY nginx.conf + index.html              |
| `gateway/nginx.conf`  | 新增：与服务器外层 nginx 一致（/app/ 剥离、/api/ 转发、/ 营销首页） |
| `gateway/index.html`  | 新增：营销首页占位页（「进入系统」→ /app/）                         |
| `docker-compose.yml`  | frontend 端口 80→8081；新增 gateway 服务（80:80）                   |
| `frontend/nginx.conf` | 恢复原始版本（撤销临时 /app/ rewrite 规则）                         |

## 验证记录

### 1. 镜像构建

- ai-customer-backend：重建（含最新代码，M7.1 后后端无改动）
- ai-customer-frontend：重建（bundle index-N5H_Y9jK.js，与服务器一致）
- ai-customer-gateway：新建

### 2. 容器状态

```
aic-gateway   | Up | 0.0.0.0:80->80/tcp
aic-frontend  | Up | 0.0.0.0:8081->80/tcp
aic-backend   | Up | 0.0.0.0:8080->8080/tcp
aic-db        | Up (healthy) | 0.0.0.0:5432->5432/tcp
```

### 3. 全链路健康检查（与服务器逐项一致）

| 请求                                | 结果                                    |
| ----------------------------------- | --------------------------------------- |
| `GET /`                             | 200 营销首页占位                        |
| `GET /app/`                         | 200 前端应用                            |
| `GET /app/assets/index-N5H_Y9jK.js` | 200 application/javascript（MIME 正确） |
| `GET /api/health`                   | 200 `{"code":0,"data":{"status":"UP"}}` |

### 4. 浏览器 E2E（http://localhost/app/login）

- 登录页正常渲染（用户名/密码/登录/重置/返回首页）✅
- admin/Admin@123456 登录成功 → 工作台 ✅
- 客户管理页完整渲染（4 条数据、状态下拉、操作按钮齐全）✅
- DOM 测量：无元素超出视口、无重叠 ✅
- Dashboard 无激活内联提示（M7.5 生效）✅
- 无「系统未激活」横幅（本地 license 已激活，行为正确）✅
- 未登录/401 → 跳 /login → 营销首页占位（与服务器行为一致）✅

## 交付

- 本地 Docker Desktop 三容器 + 网关层全部运行，结构与服务器完全一致
- 后续新代码部署：`docker compose build && docker compose up -d` 即可
- 访问入口：http://localhost/ （营销首页）→ 进入系统 → /app/
