# 修复任务：登录/激活/退订页 logo 裂图（子路径部署绝对路径问题）

> 状态：✅ 已完成（2026-08-09）
> 类型：线上 Bug 修复（部署路径）

---

## 一、问题现象（用户反馈）

服务器上登录界面的产品图标（logo）显示为裂图。

## 二、根因分析（已登录服务器 43.153.229.106 实测确认）

服务器前端为**双站结构**：

| 路径    | 内容                       | 说明                                                                 |
| :------ | :------------------------- | :------------------------------------------------------------------- |
| `/`     | 营销落地页（Landing Page） | 外层 nginx `sites-enabled/sales-agent`                               |
| `/app/` | React 应用                 | `vite.config.ts` 设 `base: "/app/"`，`main.tsx` 设 `basename="/app"` |

React 应用部署在 `/app` 子路径，`logo.svg` 实际位于 `/app/logo.svg`。
但源码中 3 处写死**绝对路径** `<img src="/logo.svg">`，浏览器请求根路径 `/logo.svg`：

- 该位置无文件 → nginx `try_files $uri $uri/ /index.html` 回退为落地页 `index.html`
- 返回 `HTTP 200 + text/html`（20949B，与 `/` 完全一致）→ `<img>` 拿到 HTML → **裂图**

### 实测证据

```
/app/logo.svg  → HTTP 200  size=1171B   type=image/svg+xml   ✅ 文件在
/logo.svg      → HTTP 200  size=20949B  type=text/html       ❌ 回退成 index.html
/              → HTTP 200  size=20949B                        （大小一致，坐实回退）
```

### 同类隐患（一并修复）

除 3 处 logo 外，还有 2 处写死绝对路径的路由链接，在 `/app` 子路径下同样会跳到落地页：

- `Activate.tsx`：`<a href="/login">`（已激活？去登录）
- `Drafts.tsx`：`<a href="/customers">`（有 preventDefault，但 href 本身错误）

## 三、修复方案

将写死的绝对路径改为 Vite 的 `import.meta.env.BASE_URL` 动态前缀：

- 本地开发 `base=/` → `BASE_URL="/"`，行为不变
- 服务器 `base=/app/` → `BASE_URL="/app/"`，正确解析到 `/app/logo.svg`、`/app/login`

## 四、改动清单

### 前端（本地仓库 + 服务器副本各一份，需保持一致）

- [x] `src/pages/Login.tsx`：`src="/logo.svg"` → `src={`${import.meta.env.BASE_URL}logo.svg`}`
- [x] `src/pages/Activate.tsx`：同上（logo）
- [x] `src/pages/Unsubscribe.tsx`：同上（logo）
- [x] `src/pages/Activate.tsx`：`href="/login"` → `href={`${import.meta.env.BASE_URL}login`}`
- [x] `src/pages/Drafts.tsx`：`href="/customers"` → `href={`${import.meta.env.BASE_URL}customers`}`
- [x] `src/vite-env.d.ts`：新增 `/// <reference types="vite/client" />`（修复 `import.meta.env` TS2339）

### 服务器部署

- [x] 同步上述 5 处修改到 `/home/ubuntu/ai-customer-deploy/frontend/src/pages/` + `src/vite-env.d.ts`（覆盖前已 diff 确认仅含预期改动）
- [x] 重建前端镜像：`sudo docker compose build frontend` + `sudo docker compose up -d --no-deps frontend`
  - ⚠️ 不能用 `up -d --build frontend`：服务器上 `./backend` 构建上下文不存在（backend 以镜像方式部署）
- [x] **部署踩坑（网络断裂）**：重建后 frontend 被放入新网络 `ai-customer-deploy_default`，而 backend/db 仍在旧网络 `ai-customer_default`（历史部署目录不同所致）→ 容器内 nginx 启动即报 `host not found in upstream "backend"`，外层 nginx 502
  - 修复：`sudo docker network connect ai-customer_default aic-frontend` + `sudo docker restart aic-frontend`（不动 backend/db，数据库零风险）

## 五、验证记录

- [x] 本地 `npm run build` 通过（dist：index-A8oLIn1V.css / index-JHLsPe6J.js，270.63 kB）
- [x] 服务器 `/app/logo.svg` → `HTTP 200 image/svg+xml 1171B`
- [x] 服务器 `/app/api/health`（经前端 nginx 代理）→ `HTTP 200`，backend 连通恢复
- [x] 新构建产物 `index-Cx2-JWVd.js` 中 3 处 `<img>` 均为 `src:"/app/logo.svg`，旧绝对路径 `"/logo.svg"` 已清零
- [x] 浏览器 E2E（DOM 测量，非截图）：
  - `/app/login`：logo `naturalWidth=150`、`complete=true`（真实加载），显示 48×48，全页无元素溢出视口，`scrollWidth==clientWidth`
  - `/app/activate`：logo 加载正常；"已激活？去登录"链接正确指向 `/app/login`；无溢出
  - `/app/unsubscribe`：logo 加载正常；无 token 时正确提示"退订链接无效"；无溢出

## 六、交付

- [x] Git commit + push（本文件一并提交）
- [ ] ⚠️ 提醒：服务器 `frontend/` 为独立副本（含 `base:/app/`、`basename:/app`、落地页），与本地仓库有差异，后续需建立同步机制
- [ ] ⚠️ 提醒：服务器 compose 网络分裂为临时修复（frontend 双网络挂载），后续统一部署目录后应 `docker compose down && up -d` 重建归一
