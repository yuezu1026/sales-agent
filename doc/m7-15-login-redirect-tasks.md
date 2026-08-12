# M7.15 打开 /login 自动跳转到根目录首页（宣传站）修复

## 状态

✅ 已完成（2026-08-11）

## 需求原文

> 服务器上，怎么打开登录页面，会自动跳转到根目录首页？

## 根因分析

- **现象**：访问 `https://sales-agent.top/login` → 返回的是**宣传站首页**（不是登录页），看起来像"打开登录页面自动跳转到首页"
- **复现**：
  - `curl https://sales-agent.top/login` → 200，返回宣传站 `/var/www/sales-agent/index.html` 的 HTML
  - `curl https://sales-agent.top/app/login` → 200，返回 React 应用登录页（正常）
- **根因 1（nginx）**：宿主机 nginx `location /` 是宣传站（`root /var/www/sales-agent` + `try_files $uri $uri/ /index.html`）。访问 `/login` 时宣传站目录无 `login` 文件 → `try_files` 兜底返回宣传站首页
- **根因 2（前端硬编码）**：`frontend/src/api/client.ts:46` 的 401 跳转是硬编码 `window.location.href = "/login"`（**不带 `/app` 前缀**）。浏览器原生跳转不走 React Router，不会自动加 basename → 落到宣传站首页。而其他页面 `navigate("/login")` 走 React Router 会自动加 `/app` 前缀，是正确的

## 设计决策

1. **前端修复（根本）**：`client.ts` 硬跳转改为 `/app/login`（与部署路径一致）
2. **nginx 兜底（防御）**：宣传站 server 块加 `location = /login { return 301 /app/login; }`，保护直接访问旧 URL 的用户

## 改动清单

- [x] `frontend/src/api/client.ts`：`window.location.href = "/login"` → `"/app/login"`
- [x] 服务器 `/etc/nginx/sites-enabled/sales-agent`：加 `location = /login` 301 跳转
- [x] 前端重新构建部署 + nginx reload
- [x] 验证：`/login` → 301 → `/app/login` 登录页正常

## 验证记录

- [x] 修复前：`curl /login` → 200 宣传站首页（复现）
- [x] 修复后：`curl /login` → 301 → `/app/login` 登录页（curl 确认 redirect_url）
- [x] 浏览器：直接访问 `/login` 新 tab → 重定向到 `/app` 登录页
- [x] 401 跳转：清 token 访问 `/app/customers` → 落到 `/app/login`（修复前会落到宣传站）
- [x] 登录闭环：admin/Admin@123456 登录成功进入工作台
- [x] 错误密码：显示"用户名或密码错误"，不跳转不登出
- [x] E2E DOM 测量：登录页无元素越界、无横向滚动、无异常重叠
- [x] 新 bundle `index-BBJEZo5C.js` 已上线，含 `location.href="/app/login"`

## 交付

- 前端修复：`frontend/src/api/client.ts`（唯一硬编码跳转改 `/app/login`）
- nginx 兜底：`/etc/nginx/sites-enabled/sales-agent` 加 `location = /login { return 301 /app/login; }`（备份 `.bak-20260811-login` 移出 sites-enabled）
- 前端镜像重建：ai-customer-deploy-frontend:latest 重新构建并重启 aic-frontend
- 服务器配置留档：`scripts/sales-agent.nginx`（本仓库，含兜底注释）
