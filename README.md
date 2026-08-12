# 拾客 Shike（AI 智能获客助手 · SaaS 多租户版）

端到端 AI 销售智能体：**潜客挖掘 → 个性化触达 → 转化**。SaaS 开放注册 + 多租户数据隔离，云端 AI 混合模式。

> 详细规划见 `doc/plan.md`（总体方案）、`doc/mvp-plan.md`（MVP 功能规划）、`doc/db-design.md`（表设计）、`doc/saas-migration-tasks.md`（SaaS 改造任务）
>
> 完整改动历史见下方「改动记录」章节，后续每次迭代持续补充。

## 技术栈

| 层   | 选型                                             |
| :--- | :----------------------------------------------- |
| 后端 | Spring Boot 4.1.0 + Spring AI 2.0.0 + PostgreSQL |
| 前端 | React 18 + Vite + TypeScript                     |
| 部署 | Docker Compose（PostgreSQL + 后端 + Nginx 前端） |

## 快速开始

### 方式一：Docker 一键部署（推荐）

```bash
cp .env.example .env
# 编辑 .env 填写 AI_API_KEY 等
docker compose up -d --build
```

- 前端：http://localhost
- 后端健康检查：http://localhost:8080/api/health
- 登录页点击「免费注册，立即开始」即可注册使用（注册即创建独立租户，数据互相隔离）
- 平台管理员：`admin / Admin@123456`（首次登录后请修改；平台账号可查看全部租户用户，不参与租户业务数据）

### 方式二：本地开发

**后端**（需本地 PostgreSQL，或 `docker compose up -d db`）：

```bash
cd backend
mvn spring-boot:run
```

**前端**：

```bash
cd frontend
npm install
npm run dev   # http://localhost:5173，已配置 /api 代理到 8080
```

## 已实现功能（按里程碑）

### M1 基础平台

- ✅ SaaS 开放注册 + 多租户数据隔离（`/api/auth/register` 注册即创建独立租户；14 张业务表按 tenant_id 隔离，JWT 携带租户上下文）
- ✅ 登录认证（JWT，`/api/auth/login`、`/api/auth/me`、修改密码）
- ✅ 系统配置管理（敏感项 AES 加密落库，`/api/config`）
- ✅ 统一响应体 `{code, message, data}` + 全局异常处理
- ✅ Flyway 数据库迁移 + admin 账号自动初始化
- ✅ AI 用量统计（真实 token 与成本计算，单价可在系统设置调整）
- ✅ Prompt 模板管理 + 接口限流

### M2 客户管理 CRM

- ✅ 潜客挖掘（M2-2）：数据源配置 + Function Calling 挖掘入库，人工确认后入库、按公司名去重
- ✅ RAG 客户画像（M2-3）：CSV 导入向量化 + 检索打分（profile_score），客户列表画像分列
- ✅ 客户管理（M2-1）：lead 表 / CRUD / 状态流转（新线索→已触达→有意向→已转化/无效）/ CSV 导入导出 / 搜索（名称/公司/手机号）/ 渠道来源下拉 / 性别 / 地址 / 股票代码
- ✅ 客户跟进记录与邮件草稿（M2-1.5）：follow_up / email_draft 表 + 子资源接口 + 跟进弹窗
- ✅ 收件箱（M2-1.6）：IMAP 拉取客户回复邮件、已读标记、转跟进记录、AI 分析
- ✅ AI 生成邮件（M2-1.7）：基于沟通记录（跟进/已发邮件/客户回复时间线）续写，保存为草稿
- ✅ 草稿箱：跨客户搜索 / 状态筛选（草稿/待发/已发送）/ 标记待发 / 发送 / 删除
- ✅ 微信沟通工作台（M2-1.8）：Lead 挂微信号/昵称，消息记录（in/out + AI 回复建议确认落库）

### M3 邮件触达闭环

- ✅ SMTP 邮件发送（M3-2）：EmailSendService + 发送记录 + 每日限频（mail.daily_limit），草稿箱/客户详情发送按钮，MCP `email_send_email` 工具
- ✅ 发送记录查询页：客户详情发送历史（状态/失败原因/重试）
- ✅ 邮件正文自动追加退订链接（合规）：`mail.unsubscribe_url` 支持 `{email}` 占位符；留空则不追加
- ✅ 退订 endpoint（点击即生效）：`GET /api/unsubscribe?email=xxx` 公开接口 + `email_unsubscribe` 黑名单表（V12）+ 发送前拦截（已退订邮箱不再发送）+ 免登录落地页 `/unsubscribe`
- ✅ 退订管理（后台）：设置页「退订管理」区块查看黑名单 + 恢复邮箱（`GET /api/unsubscribe/list` / `DELETE /api/unsubscribe/{email}`，JWT 保护）
- ✅ 帮助中心：快速开始 / 模块使用指南 / 常见问题 / 关于

## 目录结构

```
ai-customer/
├── backend/                  # Spring Boot 后端
│   └── src/main/
│       ├── java/com/aicustomer/
│       │   ├── common/       # 统一响应体、业务异常、全局异常处理
│       │   ├── config/       # 拦截器、CORS、初始化数据
│       │   ├── controller/   # REST 接口
│       │   ├── entity/       # JPA 实体
│       │   ├── mcp/          # MCP 工具（邮件发送等）
│       │   ├── repository/   # 数据访问
│       │   ├── service/      # 业务逻辑
│       │   └── util/         # JWT / AES / 指纹工具
│       └── resources/
│           ├── application.yml
│           └── db/migration/ # Flyway 迁移脚本（V1~V11）
├── frontend/                 # React 前端
│   └── src/
│       ├── api/              # API 封装（自动带 JWT）
│       └── pages/            # 工作台 / 客户管理 / 潜客挖掘 / 客户画像 / 收件箱 / 草稿箱 / 系统设置 / 帮助
├── doc/                      # 产品与技术文档
├── docker-compose.yml        # 一键部署
└── .env.example              # 环境变量模板
```

## 改动记录（Change Log）

> 按时间倒序，每次迭代（功能开发 + 文档 + 提交）后持续补充。

### 2026-08-12（SaaS 多租户改造 · 开放注册 + 数据隔离）

- ✅ **迁移**：`ai-customer` → `sales-agent`，排除构建产物（target/node_modules/dist/tar 包）
- ✅ **开放注册**（`AuthController /api/auth/register` + `Register.tsx`）：注册即创建独立租户（`tenants` 表）+ 租户管理员（role=admin）+ 初始化该租户默认配置（system_config 21 项 / 默认数据源 / Prompt 模板），注册即登录直达工作台
- ✅ **多租户数据隔离**：14 张业务表加 `tenant_id`（V21 迁移，NOT NULL DEFAULT 1）；`TenantContext`（ThreadLocal）贯穿请求；JWT claims 携带 `tenantId`，AuthInterceptor 注入；仓库层默认方法（findById/findAll/count）全部改为按租户查询；spec 动态查询加租户 predicate；实体创建处统一 setTenantId
- ✅ **公开端点租户定位**：退订 `/api/unsubscribe` 与邮件追踪 `/api/track/**`（收件人无登录态）通过链接 `tenantId` 参数定位租户，缺失回退租户 1
- ✅ **定时任务与 MCP 适配**：收件箱定时同步遍历所有租户逐个执行；邮件同步改为本地调用 `EmailMailboxService`（继承调用线程租户上下文，不再经 MCP HTTP）；MCP 工具入口绑定默认租户 1
- ✅ **License 机制移除**：删除拦截器/控制器/服务/实体/仓库/激活码签发工具 license-tool/ 与前端激活页，`/api/ai/**` 不再拦截；compose 与 .env 去掉 HW_SALT
- ✅ **平台管理员**：初始 `admin`（tenant_id=NULL）为平台级账号，可管理全部租户用户，不参与租户业务数据
- ✅ 验证：后端 `mvn -q compile` + 前端 `npm run build` 通过（详见 `doc/saas-migration-tasks.md`）

- ✅ **邮件支持 HTML**（`EmailSendService.isHtml` 启发式检测）：body 含 `<!doctype html`/`<html`/`<p`/`<br`/`<div`/`<span`/`<table`/`<ul`/`<li`/`<strong`/`<b>`/`<h1-3`/`<a ` 任一即按 `text/html; charset=UTF-8` 发送，否则纯文本 `setText(body,"UTF-8")`；HTML 退订块自动换为内联样式 `<div style="border-top:1px solid #eee...">…<a href style="color:#1677ff">退订</a></div>`
- ✅ **AI 生成个性化 HTML 邮件**（`EmailDraftService.generateWithContext`）：systemPrompt 强制「简洁美观 HTML（`<p>` 分段/`<b>` 强调/`<ul><li>` 列表/`<span style="color:#1677ff">` 高亮，**仅内联样式，禁外部 CSS/JS/图片**）；主题 ≤20 字且不与历史邮件主题重复；正文 3-6 句；**每次生成必须与历史邮件明显不同（换措辞/句子结构/引用事实组合）**；写真实客户值不输出 {占位符}」；严格「主题：xxx\n正文：xxx」两行格式，`parseResult` 增强剥离 ``` 代码块标记
- ✅ **快捷模板套用升级**（`Customers.tsx` 生成弹窗）：快捷模板下拉选择后 AI 生成时把模板注入为 `templateHint`「参考模板（仅参考其结构/视觉风格，正文必须重新编写）」——模板只影响风格、正文由 AI 结合沟通历史重新个性化编写
- ✅ **前端 HTML 渲染**：模板页预览区/客户详情草稿列表/草稿箱展开行按 `isHtmlText` 检测后 `dangerouslySetInnerHTML` 渲染富文本；生成弹窗新增「✏️ 源码 / 👁 预览」toggle（预览渲染 HTML，源码可编辑）
- ✅ Docker E2E 全过：AI 生成两次均返回 HTML（`<p>`/`<b>`/`<span style>`/`<ul>`）且内容明显不同（个性化+多样性）；HTML 草稿保存/发送成功，发送记录落库含 HTML 退订块；前端模板页新建 HTML 模板（预览富文本渲染+占位符替换）/删除、生成弹窗模板套用+源码/预览 toggle、草稿箱展开行富文本渲染；DOM 测量（视口 809×885）modal 无溢出、fixed 元素 0 溢出、overlapCount=0
- ✅ 环境已恢复（测试模板/测试草稿/发送记录已清，daily_limit=1、unsubscribe_url 空）

### 2026-08-09（邮件模板 HTML 美化 + AI 个性化生成 · 每次生成基本不同）

- ✅ **邮件支持 HTML**（`EmailSendService.isHtml` 启发式检测）：body 含 `<!doctype html`/`<html`/`<p`/`<br`/`<div`/`<span`/`<table`/`<ul`/`<li`/`<strong`/`<b>`/`<h1-3`/`<a ` 任一即按 `text/html; charset=UTF-8` 发送，否则纯文本 `setText(body,"UTF-8")`；HTML 退订块自动换为内联样式 `<div style="border-top:1px solid #eee...">…<a href style="color:#1677ff">退订</a></div>`
- ✅ **AI 生成个性化 HTML 邮件**（`EmailDraftService.generateWithContext`）：systemPrompt 强制「简洁美观 HTML（`<p>` 分段/`<b>` 强调/`<ul><li>` 列表/`<span style="color:#1677ff">` 高亮，**仅内联样式，禁外部 CSS/JS/图片**）；主题 ≤20 字且不与历史邮件主题重复；正文 3-6 句；**每次生成必须与历史邮件明显不同（换措辞/句子结构/引用事实组合）**；写真实客户值不输出 {占位符}」；严格「主题：xxx\n正文：xxx」两行格式，`parseResult` 增强剥离 ``` 代码块标记
- ✅ **快捷模板套用升级**（`Customers.tsx` 生成弹窗）：快捷模板下拉选择后 AI 生成时把模板注入为 `templateHint`「参考模板（仅参考其结构/视觉风格，正文必须重新编写）」——模板只影响风格、正文由 AI 结合沟通历史重新个性化编写
- ✅ **前端 HTML 渲染**：模板页预览区/客户详情草稿列表/草稿箱展开行按 `isHtmlText` 检测后 `dangerouslySetInnerHTML` 渲染富文本；生成弹窗新增「✏️ 源码 / 👁 预览」toggle（预览渲染 HTML，源码可编辑）
- ✅ Docker E2E 全过：AI 生成两次均返回 HTML（`<p>`/`<b>`/`<span style>`/`<ul>`）且内容明显不同（个性化+多样性）；HTML 草稿保存/发送成功，发送记录落库含 HTML 退订块；前端模板页新建 HTML 模板（预览富文本渲染+占位符替换）/删除、生成弹窗模板套用+源码/预览 toggle、草稿箱展开行富文本渲染；DOM 测量（视口 809×885）modal 无溢出、fixed 元素 0 溢出、overlapCount=0
- ✅ 环境已恢复（测试模板/测试草稿/发送记录已清，daily_limit=1、unsubscribe_url 空）

### 2026-08-09（邮件模板管理 · 模板中心化 + 草稿箱即真实内容）

- ✅ **独立邮件模板管理**（`V13__email_template.sql` 新表 + `EmailTemplate` 实体/仓库/服务/控制器）：模板 CRUD 接口 `GET/POST/PUT/DELETE /api/email-templates`，名称唯一（重名 400「模板名称已存在」）、空名称/主题/正文 400、不存在 404
- ✅ **渲染逻辑抽公共类** `util/TemplateRenderer`：`EmailSendService` 与 `EmailDraftService` 共用，避免重复实现
- ✅ **保存草稿时即替换占位符**（`EmailDraftService.create/update`）：按客户 Lead 字段替换 subject/body 后落库，**草稿箱看到的就是替换后的真实内容**（所见即所得）；发送时再次替换为**幂等兜底**（用户手改草稿新加的占位符也生效），随后 appendUnsubscribe 追加退订块
- ✅ **模板管理页**（`Templates.tsx` + 导航「邮件模板」）：列表（名称/主题占位符/说明/更新时间/操作）+ 新建/编辑 modal（13 个变量提示 code 标签 + 👁 实时预览「主题/正文」，示例客户 示例科技/张三）+ 删除二次确认
- ✅ **客户邮件弹窗一键套用**（`Customers.tsx`）：「AI 生成邮件」弹窗新增「快捷模板」下拉，选择后自动填入主题/正文（可再编辑）
- ✅ Docker E2E 全过：模板 CRUD 全分支（重名 400/空 body 400/PUT 与 DELETE 404）；草稿保存替换（`SUBJECT-{companyName}-{contactName}-{date}` → `SUBJECT-数澜科技-林晓岚-2026-08-09`，phone/contactPhone 双别名、`{email}`/未知变量原样保留、空字段 `{gender}` → 空串）；前端新建/编辑/删除/预览/快捷模板套用/草稿箱真实内容全验证；DOM 测量（视口 809×885）modal 无溢出、overlapCount=0
- ✅ 环境已恢复（lead18 草稿 0 条，模板保留示例「方案跟进」）

### 2026-08-09（邮件模板占位符 · 一封草稿适配多个客户）

- ✅ **发送时自动替换占位符**（`EmailSendService.renderTemplate`）：subject/body 中的 `{companyName}` `{contactName}` `{contactEmail}` `{phone}` `{contactPhone}` `{gender}` `{industry}` `{region}` `{scale}` `{website}` `{address}` `{date}`（当天日期）`{year}`（年份）等占位符按 Lead 实际字段替换；**空字段 → 空串**；**未识别占位符原样保留**（如 `{email}` 留给退订链接逻辑处理）
- ✅ 替换发生在校验通过后、落库前：落库 body/subject 保存的是替换后的真实投递内容，随后再追加退订链接
- ✅ 前端提示：客户管理「AI 生成邮件」弹窗「生成结果（可编辑）」下方新增变量提示文案；帮助中心 FAQ 新增「邮件正文支持哪些变量？」
- ✅ Docker 验证全过：`【{companyName}】{contactName}，{date}合作沟通` → `【云启软件】陈启明，2026-08-09合作沟通`；空字段 `{gender}` → 空串；`{contactPhone}` → 真实电话；`{email}`/未知变量原样保留；`{year}` → 2026；发送记录落库内容确认 ✅
- ✅ 环境已恢复（unsubscribe_url 空、daily_limit=1，测试数据已清理）

### 2026-08-09（退订配置简化 · 域名前缀自动补全路径）

- ✅ `mail.unsubscribe_url` 使用简化（用户反馈「退订 endpoint 是固定的，还要填完整前缀吗」）：退订路径 `/unsubscribe?email=xxx` 固定，**只需填网站域名前缀**（如 `https://www.example.com`），系统自动补全 `/unsubscribe?email=xxx` 生成退订链接
- ✅ 兼容旧写法：配置已含 `{email}` 占位符或 `/unsubscribe` 路径时按原样使用（支持自定义落地页/路径）
- ✅ 描述更新：「网站域名前缀（如 https://www.example.com），系统自动拼 /unsubscribe?email=xxx 生成退订链接；留空则不追加退订块」
- ✅ Docker 验证：① 域名前缀 → 落库链接 `http://localhost:8080/unsubscribe?email=zhang%40test.com`（自动补全）✅ ② 完整路径 → 不重复补 `/unsubscribe` ✅ ③ 前端设置页描述渲染 ✅
- ✅ 配置已恢复原状（unsubscribe_url 空、daily_limit=1）

### 2026-08-09（退订管理 UI · 后台查看/恢复黑名单）

- ✅ **后端管理接口**（`UnsubscribeController` 新增，受 JWT 保护，需登录）：
  - `GET /api/unsubscribe/list`：`findAllByOrderByCreatedAtDesc()` 返回退订黑名单（邮箱 + 来源 + 退订时间）
  - `DELETE /api/unsubscribe/{email}`：恢复邮箱 —— 格式非法 → `400`；不在名单 → `400「该邮箱不在退订名单中」`；成功 → 移除并返回「已恢复，该邮箱可继续接收邮件」
  - 仅精确路径 `/api/unsubscribe` 公开，`/list` 与 `/{email}` 自动受 JWT 保护（无 token → 401）
- ✅ **设置页退订管理区块（前端）**：`Settings.tsx` 新增「退订管理」卡片 —— 表格列出黑名单（邮箱/来源/退订时间）+「恢复」按钮（confirm 二次确认 → 成功 toast → 列表刷新）；空态「暂无退订邮箱」
- ✅ 布局：`.table.unsub-table` 专属紧凑列宽（覆盖全局 1080px min-width），809px 视口下无横向滚动、无溢出/重叠（DOM 测量：horizontalOverflow=0 / btnOverlap=0）
- ✅ Docker 验证全过：无 token 调 list → 401；登录后 list/恢复闭环（退订 → 列表出现 → 恢复 → 清空）；恢复不存在邮箱 → 400；前端渲染/confirm/toast/空态全通
- ✅ 单邮箱隔离（用户要求）：退订只拦截目标收件人邮箱，不影响其它客户收信

### 2026-08-09（退订 endpoint · 点击即生效，合规闭环）

- ✅ **退订接口（后端，公开免登录）**：
  - `GET /api/unsubscribe?email=xxx`（`UnsubscribeController`）：邮箱非法 → `invalid`；幂等（已退订过 → `already`，不重复插入）；首次 → `unsubscribed`
  - `WebConfig` 放行 `/api/unsubscribe`（JWT 拦截器 exclude）——收件人从邮件链接进入，无登录态
  - 数据库 `email_unsubscribe` 表（V12 Flyway 迁移）：`email` 主键 + `source`（link）+ `created_at`，按邮箱维度拉黑（一个邮箱可对应多个 lead）
- ✅ **发送前黑名单拦截**：`EmailSendService.sendDraft()` 在 SMTP 投递前检查收件邮箱是否已退订，命中 → `400「该邮箱已退订，不再发送营销邮件」`（不发、不落 queued 记录）
- ✅ **免登录退订落地页（前端）**：
  - `frontend/src/pages/Unsubscribe.tsx`：读 `?email=` 参数调公开接口，三态展示 —— ✅ 退订成功 / 已退订过 / 链接无效（附误操作恢复提示）
  - 路由 `/unsubscribe`（无守卫，独立于登录态）+ `App.tsx` 注册
- ✅ **配置提示更新**：`mail.unsubscribe_url` 描述建议填 `http://你的域名/unsubscribe`（内置落地页，点击即生效不再发送）
- ✅ Docker 部署验证 6 分支全通过：① 无 token 公开访问 → 成功 ② 重复点击 → 已退订 ③ 非法邮箱 → 链接无效 ④ V12 迁移应用 ⑤ 黑名单邮箱发送 → 400 拦截 ⑥ 前端落地页三态（DOM 测量无溢出/重叠）

### 2026-08-09（Docker 部署验证 · 退订链接 + 帮助中心）

- ✅ **邮件正文追加退订链接（合规要求，M3-2 遗留项）**：
  - 后端 `EmailSendService.appendUnsubscribe()`：发送时正文末尾自动追加退订块；`mail.unsubscribe_url` 未配置则原样返回
  - 支持 `{email}` 占位符（URL 编码替换）；无占位符时自动拼接 `?email=xxx`（URL 已含查询参数则用 `&`）
  - 落库发送记录 body 保存真实投递内容（追加后），接口验证 5 分支全通过：
    1. 未配置 → 正文不含退订块
    2. 前缀无占位符 → 自动追加 `?email=zhang%40test.com`
    3. 前缀含 `{email}` → 正确替换为编码邮箱
    4. 测试后恢复配置（unsubscribe_url 空 / daily_limit=1 / SMTP 不变）
    5. 系统设置页动态渲染新描述「退订链接前缀，支持 {email} 占位符；留空则不追加」
  - 前端无需改动（Settings.tsx 动态渲染 description）
- ✅ **帮助中心页面（前端纯静态）**：
  - `frontend/src/pages/Help.tsx`：4 区块 —— 快速开始（4 步）、模块使用指南（7 个模块含 tips）、常见问题（8 条 FAQ 手风琴）、关于
  - 路由 `/help` + 导航「帮助」入口 + `styles.css` help-\* 样式
  - 前端 E2E DOM 测量通过：无横向溢出、FAQ 8 按钮零重叠、展开/切换/收起全过；Docker 环境 `/help` 复验正常
- ✅ Docker 部署验证：backend/frontend 镜像重建（腾讯云 Maven 镜像加速，依赖下载约 5 分钟），`docker compose up -d` 后健康检查通过

### 2026-08-09（M3-2 补充 · 发送记录查询页）

- ✅ 客户详情发送记录：历史列表（状态/收件人/时间/失败原因）+ 失败重试按钮
- ✅ Dockerfile 加腾讯云 Maven 镜像加速（`https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`）
- ✅ Docker 部署 E2E 验证

### 2026-08-09（M2-1.8 微信沟通工作台）

- ✅ Lead 挂微信号/昵称（V11 迁移）
- ✅ 微信消息记录：in/out 双向 + AI 回复建议 → 人工确认落库（自动标记 AI 辅助徽标）
- ✅ 客户列表微信列 + 沟通 Modal

### 2026-08-09（M3-2 SMTP 邮件发送）

- ✅ `EmailSendService`：SMTP 配置（smtp.\*）读取 + 每日限频（mail.daily_limit）+ 发送记录（queued→sent/failed）
- ✅ 草稿箱 / 客户详情「✉ 发送」按钮；MCP `email_send_email` 工具
- ✅ 失败重试（POST `/email-send-logs/{id}/retry`）

### 2026-08-09（M2-3 RAG 客户画像）

- ✅ CSV 导入历史成交客户 → 向量化（embedding 模型或本地向量）
- ✅ 潜客画像相似度打分（profile_score，0-100 分）+ 一键重算
- ✅ 客户列表新增画像分列

### 2026-08-09（M2-2 潜客挖掘）

- ✅ 数据源配置 + Function Calling 挖掘入库（人工确认、按公司名去重）

### 2026-08-09（M1 体验优化）

- ✅ AI 用量统计接入真实 token 与成本（ai.input_price / ai.output_price 可调）
- ✅ api() 统一加 `cache:no-store` 修复用量偶发无值
- ✅ 移除工作台冗余 AI 生成区块 + 删除死接口 `/api/ai/generate/email`
- ✅ 登录页用户名 trim；登录/激活页产品图标；favicon 翡翠绿
- ✅ 客户表格列挤压修复（横向滚动容器 + 最小宽度）；渠道来源下拉；性别/手机号/地址/股票代码字段
- ✅ 系统更名为「AI智能获客助手」（全局同步）

### 2026-08-08（M1 + M2-1 主体）

- ✅ M1 骨架：登录/激活/系统设置/AI 调用 + 部署脚本；激活码生成器/修改密码/用量看板/限流/Prompt 模板管理
- ✅ M2-1 客户管理 CRM：lead 表 / CRUD / 状态流转 / CSV 导入导出 + 工作台统计
- ✅ M2-1.5 跟进记录 + 邮件草稿保存管理（V4 迁移）
- ✅ M2-1.6 收件箱：MCP 抓取客户回复邮件与管理
- ✅ M2-1.7 AI 生成邮件基于沟通记录续写（时间线注入，无需 RAG）
- ✅ 草稿管理页：跨客户搜索/筛选/确认/删除；草稿箱更名；「确认」→「标记待发」
- ✅ 系统更名、favicon、产品图标等品牌化

## 注意事项

1. **生产必须修改** `.env` 中的 `JWT_SECRET` 与 `CONFIG_ENC_KEY`（当前为开发默认值）
2. 硬件指纹绑定 License：换机器激活会被拒绝（防一码多用）
3. AI 接口需先激活 License 才能调用
4. 营销邮件建议配置 `mail.unsubscribe_url`（退订链接），符合合规要求并降低退信率
