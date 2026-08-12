# M7.3 任务清单：登录页增加「返回首页」链接

> 状态：✅ 已完成（2026-08-09，含 M7.3.1 微调）
> 需求来源：用户「能否在登录页面上，增加一个返回到首页的连接的功能？现在无法返回到首页。」

---

## 〇、M7.3.1 追加调整（2026-08-09）

**用户反馈**：「这个连接能否放在右对齐？且跟重置按钮上下间隔要稍微隔开一点。否则容易点到按钮，而不是链接。」

- [x] `frontend/src/styles.css`：`.auth-home-link` `text-align: center` → `right`；`margin-top: 16px` → `24px`（拉开与重置按钮距离，防误点）
- [x] 构建 + 部署前端
- [x] E2E：链接右对齐且与按钮间距增大；零重叠零溢出；跳转 + 登录回归

### M7.3.1 验证记录

1. ✅ TS 编译：`npx tsc --noEmit` exit=0
2. ✅ DOM 测量：链接右缘 585 与 auth-box 右缘 609 间距 24px（**右对齐**）；链接 top 503 - 按钮 bottom 479 = **24px 间隔**（原 16px）；零重叠零溢出
3. ✅ 跳转：点击链接整页跳转 `/` 首页正常；登录回归正常
4. ✅ 新 bundle：`index-DOC-c7Zu.js`

---

## 一、需求原文

1. 登录页（`/app/login`）无法返回首页。
2. 需要增加一个「返回首页」链接，点击后回到外层营销 landing 首页（`/`）。

---

## 二、排查结论

- 外层 nginx（sites-available/sales-agent）：`location /` → root `/var/www/sales-agent`（营销 landing，实测 200）；`location /app/` → proxy_pass 127.0.0.1:8081（前端应用）。
- 登录页是前端应用内路由（BrowserRouter basename `/app`），**无任何返回外层首页的入口**（header 只有 logo+标题，按钮行只有登录/重置）。
- 首页地址确定：`/`（整页跳转即可，跨 nginx location，不能用 react-router navigate）。

---

## 三、设计决策

| 项   | 决策                                                                          |
| :--- | :---------------------------------------------------------------------------- |
| 方案 | auth-box 底部加「← 返回首页」链接，`<a href="/">` 整页跳转到营销 landing 首页 |
| 样式 | 居中、灰色小字、hover 变主色；新样式 `.auth-home-link`（或复用现有链接样式）  |
| 后端 | 零改动（纯前端）                                                              |

---

## 三、改动清单

- [ ] `frontend/src/pages/Login.tsx`：auth-box 内 msg 下方加「← 返回首页」链接
- [ ] `frontend/src/styles.css`：`.auth-home-link` 样式（居中、hover 变蓝、间距）
- [ ] 构建 + 部署前端（服务器 node:22-alpine 构建镜像，rm -f aic-frontend → up -d）
- [ ] E2E：链接存在且可点 → 整页跳转到 `/` 首页（title/内容验证）；登录功能回归；DOM 无溢出无重叠

---

## 四、验证记录

1. ✅ TS 编译：`npx tsc --noEmit` exit=0
2. ✅ 页面级（浏览器 E2E）：登录页底部显示「← 返回首页」链接（href=`/`），位于按钮行下方 16px，无重叠
3. ✅ 功能：点击链接成功整页跳转到 `http://43.153.229.106/` 营销 landing 首页（title「AI智能获客助手 - 端到端 AI 销售智能体」，hero/免费试用 CTA/应用入口齐全）
4. ✅ DOM 测量：链接（top 499-516）与按钮（bottom 483）间距 16px 零重叠；无横向/纵向溢出；auth-box 在视口内
5. ✅ 登录回归：正确密码正常登录进入工作台

---

## 五、交付

- 线上已部署：frontend 镜像 `ai-customer-deploy-frontend:latest` 重建，`aic-frontend` 容器重启，`/app/login` 200
- 新 bundle：`index-Xfeyslw0.js`
- 后端零改动
- Git：待提交推送
