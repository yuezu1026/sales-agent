# SaaS 改造任务（ai-customer → sales-agent）

## 状态

- 状态：✅ 已完成（2026-08-12 启动，当日完成）
- 源项目：`D:\project-ai\ai-customer`（AI 智能获客助手 MVP，Spring Boot 4.1 + React 18 + PostgreSQL，单机 License 模式）
- 目标：迁移到本目录，改造为**开放注册 + 多租户数据隔离**的 SaaS 系统

## 需求原文（用户 2026-08-12）

> 请把 这个项目的代码：D:\project-ai\ai-customer，迁移过来到这个目录下，并改造为可以注册的saas 模式的系统？

## 设计决策（用户已确认）

| 决策点        | 结论                                                                                                            |
| :------------ | :-------------------------------------------------------------------------------------------------------------- |
| SaaS 改造深度 | 开放注册 + 租户数据隔离（不做套餐计费/团队邀请，本期）                                                          |
| License 机制  | **去掉**，改为注册即用（删除激活码/设备指纹相关代码与表）                                                       |
| 数据隔离方案  | 单库 + 业务表加 `tenant_id` 列                                                                                  |
| 迁移范围      | 只迁源码+配置，排除构建产物（target/node_modules/dist/.venv/tar 包等）                                          |
| 平台账号      | 保留 InitDataConfig 初始 admin（tenant_id=NULL，平台级）；注册用户各自创建租户+租户管理员；本期不做平台管理后台 |

## 改造设计

### 1. 租户模型

- 新表 `tenants`：id / name（公司名）/ owner_user_id / plan（默认 free）/ status / created_at / expire_at
- `users` 加 `tenant_id` 列（NULL = 平台级账号，如初始 admin）
- 注册流程：`POST /api/auth/register`（username/password/displayName/companyName）→ 事务内创建租户 + 租户管理员（role=admin）+ 初始化租户 system_config 默认值（含 AI key 从全局 env 兜底）

### 2. 数据隔离

- 业务表全部加 `tenant_id BIGINT NOT NULL`：ai_usage_log / system_config / lead / follow_up / email_draft / email_inbox / email_send_log / email_template / email_unsubscribe / data_source / customer_profile / prompt_template / wechat_message / ai_cache
- `system_config` 唯一约束改为 `(tenant_id, config_key)`
- JWT claims 携带 `tenantId`；AuthInterceptor 解析后放 request attribute；Service 层按租户过滤

### 3. License 移除

- 删除：LicenseInterceptor / LicenseController / LicenseService / License entity / LicenseRepository / license 表（V21 中 DROP）/ license-public.key 引用 / app.license 配置 / 前端 Activate 页
- `/api/ai/**` 不再做 License 拦截

### 4. AI 配置租户化

- AiService 已支持从 system_config 动态构建 ChatModel（ai.api_key/ai.base_url/ai.model_name）→ 改为**按当前租户**读取；租户未配置时回退到全局环境变量 AI_API_KEY

### 5. 前端

- 新增注册页（用户名/密码/显示名/公司名）
- 删除 Activate 激活页与入口
- client.ts 适配注册接口与 tenantId 字段

## 改动清单

- [x] 任务文档
- [x] 源码迁移（robocopy 排除构建产物）
- [x] V21\_\_tenant_saas.sql（tenants 表 / users.tenant_id / 业务表 tenant_id / system_config 唯一约束 / DROP license）
- [x] 实体：Tenant 新建；User 加 tenantId；14 个业务实体加 tenantId
- [x] JwtUtil：generate/parse 携带 tenantId
- [x] AuthInterceptor：解析 tenantId 放 request attribute
- [x] AuthController：/register 注册接口（事务：租户+管理员+默认配置）
- [x] 删除 License 相关：interceptor/controller/service/entity/repository/WebConfig 注册/application.yml
- [x] Service 层按租户隔离（Lead/Email\*/Prospect/DataSource/CustomerProfile/FollowUp/WechatMessage/PromptTemplate/AiUsageLog/SystemConfig/EmailUnsubscribe/AiCache）
  - [x] 14 个业务仓库：findById/findAll/count 等默认方法改为 `TenantContext.require()` + 按租户派生方法
  - [x] 11 处实体创建 setTenantId（防 V21 NOT NULL 报错）
  - [x] 4 个 spec 查询（LeadService x2 / EmailInboxService / EmailDraftService / EmailSendLogService）加租户 predicate
  - [x] 公开端点租户定位：/api/unsubscribe + /api/track/\*\* 带 tenantId 参数（缺失回退 1L）
  - [x] 定时任务租户上下文：scheduledSync 遍历所有租户逐个 set/clear；EmailInboxService 改本地调用 EmailMailboxService（不再经 MCP HTTP，继承调用线程上下文）
  - [x] MCP 工具绑定默认租户 1（外部 Agent 无登录态）
- [x] AiService 按租户读 AI key（回退 env）
- [x] 前端：Register 页 / App 路由 / client.ts / Nav 去激活入口
  - [x] 新增 `Register.tsx`（用户名 3-32 位字母数字下划线 / 密码≥8 / 两次一致 / 显示名·公司名选填，注册即登录直达工作台）
  - [x] 删除 `Activate.tsx` + `/activate` 路由；Login 页去 License 提示改加「免费注册」链接
  - [x] Nav/Dashboard/Help 去 License 逻辑；styles.css 清理 license-\* 样式加 auth-switch 样式
  - [x] 前端 `npm run build` 通过（tsc + vite）
- [x] 部署：docker-compose.yml 去 license env / .env.example / README
  - [x] docker-compose.yml / deploy-compose.yml / .env.example 删 HW_SALT + machine-id 挂载
  - [x] 删除 license-tool/（厂商离线签发工具）+ .gitignore 清理
  - [x] README 重写（SaaS 说明 + 注册指引 + 2026-08-12 改动记录）
- [x] 验证：mvn 编译 + 前端 build + E2E（注册→登录→数据隔离→License 已移除）

## 验证记录（2026-08-12 全部通过）

### 构建

- 后端 `mvn -q compile` 通过（含全部租户隔离改造 + GlobalExceptionHandler 404 处理器）
- 前端 `npm run build` 通过（tsc + vite，Help.tsx 引号修复）
- Docker 全容器运行：aic-db Healthy / aic-backend / aic-frontend / aic-gateway 全部 Up；`GET /api/health` → UP

### E2E 主流程（浏览器实测）

- **注册校验分支**：空表单 →「请输入用户名」；用户名 `ab` →「用户名需 3-32 位，仅支持字母、数字、下划线」；两次密码不一致 →「两次输入的密码不一致」；重复用户名 → 400「用户名已存在」；短密码 → 400「密码至少 8 位」✅ 全部分支触发
- **注册即登录**：注册 `tenant_a` 成功 → 自动跳转工作台，导航显示「用户管理/系统设置」（租户管理员角色）✅
- **租户默认配置初始化**：tenant_a 系统设置页出现 21 项 AI/邮箱默认配置（ai.model_name 等）✅
- **数据隔离**：tenant_a 创建客户「租户A专属客户公司」成功（共 1 条）；注册 tenant_b 后客户列表「共 0 条」，看不到 tenant_a 数据 ✅
- **License 移除**：带 token `GET /api/license` → 404「接口不存在」；`/app/activate` 前端无路由（SPA 回退）；工作台/登录/注册/全部页面无 License 横幅与激活码文案 ✅（新增 NoResourceFoundException → 404 处理器，未知 API 不再 500）
- **平台 admin**：admin/Admin@123456 登录成功（tenantId=0 平台级）；客户/AI 用量等租户级接口 → 400「当前账号无租户上下文」属预期，前端优雅降级只显示全局统计；登录统计接口正常 ✅
- **用户管理按租户隔离**：tenant_a 用户列表只见本租户用户（listAll 按 TenantContext 分流）✅
- **页面遍历**：帮助/潜客挖掘/客户画像/收件箱/草稿箱/发件箱/邮件模板/用户管理 8 页全部正常打开、无 License 残留文案 ✅

### DOM 布局测量（getBoundingClientRect，禁截图）

- 注册页：0 问题（无溢出/无重叠）✅
- 登录页：0 问题 ✅
- 工作台：无横向溢出（scrollWidth==clientWidth），地图卡片纵向超出视口但页面可正常纵向滚动 ✅
- 客户管理表格：在 `.table-wrap` 横向滚动容器内（容器 right≤视口），页面无横向溢出 ✅
- 新增客户 modal：无超出视口/无横向溢出，表单内容长时 modal 内部可滚动（正常）✅

## 交付

- 2026-08-12 Git 提交并推送 https://github.com/yuezu1026/sales-agent（main 分支），提交信息「SaaS 多租户改造：开放注册+数据隔离+移除License」（含本任务文档）
