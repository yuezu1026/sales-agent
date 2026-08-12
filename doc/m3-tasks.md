# M3 任务清单

> 对齐《AI 获客系统产品规划方案》M3 阶段：邮件触达闭环
> 本文件为 M3-2 阶段记录（M3-1 收件箱同步见 m2-2-tasks.md 收件箱部分）

---

# M3-2 SMTP 邮件发送

> 状态：✅ 已完成（2026-08-09）
> 目标：confirmed（待发）草稿 → SMTP 投递 → email_send_log 发送记录 + 每日限频 → 草稿流转 sent
> 依赖：angus-mail（pom.xml 已有，Jakarta Mail 参考实现，同时支持 IMAP 收件 + SMTP 发件），M3-2 零新增依赖

---

## 一、任务总览

| 项   | 内容                                                                                         |
| :--- | :------------------------------------------------------------------------------------------- |
| 目标 | 待发草稿一键 SMTP 发送，发送记录全量落库，每日限频防滥用，失败可重试                         |
| 架构 | **EmailSendService 核心服务 + MCP email_send_email 薄封装 + 前端 REST 直连**（三层职责分离） |
| 数据 | 新增 email_send_log 表（V10 迁移），草稿 status 扩展 sent                                    |
| 适配 | 任意邮箱服务商：465 SSL / 587 STARTTLS / 25 明文，UTF-8 subject/body                         |
| 权限 | 沿用单角色 admin + JWT；smtp.password 与 ai.api_key 同走 AES-256 加密存储（回显 ••••••）     |

## 二、架构决策（用户提问澄清后定稿）

> 用户先后问：①「自建 MCP Server 不能用来发邮件吗？」②「如果 MCP 能发邮件，M3-2 SMTP 是否不用做了？」

- **MCP 能发邮件**：MCP 工具内部最终仍走 SMTP（jakarta.mail.Transport），只是协议封装层。
- **SMTP 必须做**：SMTP 是实际投递机制（协议级），MCP 是工具暴露方式（应用级），两层东西，缺一不可。
- **最终架构**（避免前端自连 MCP 的每次 HTTP 短连接开销）：
  - `EmailSendService`：核心（SMTP 连接 + 发送记录落库 + 每日限频），前后端共用的唯一实现
  - `EmailMcpTools.email_send_email`：薄封装，供外部 AI Agent（Spring AI Client 等）调用
  - 前端按钮 → REST → EmailSendService（不经 MCP）

## 三、后端实现

### 3.1 数据层（Flyway V10）

- `V10__email_send_log.sql`：email_send_log 表
  - id BIGSERIAL PK / lead_id BIGINT REFERENCES lead(id) ON DELETE SET NULL / draft_id BIGINT REFERENCES email_draft(id) ON DELETE SET NULL
  - from_email VARCHAR(128) NOT NULL / to_email VARCHAR(128) NOT NULL / subject VARCHAR(255) NOT NULL / body TEXT NOT NULL
  - status VARCHAR(20) NOT NULL DEFAULT 'queued'（queued / sent / failed）/ error_msg VARCHAR(255) / sent_at TIMESTAMPTZ / created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
  - 索引 idx_esl_lead / idx_esl_status / idx_esl_sent_at
- `entity/EmailSendLog.java`：JPA 实体，字段对齐 V10
- `repository/EmailSendLogRepository.java`：
  - `findByLeadIdOrderByCreatedAtDesc` / `findByDraftIdOrderByCreatedAtDesc`（客户/草稿维度查发送记录）
  - `countByStatusAndSentAtGreaterThanEqual`（限频统计当日 sent 数）
  - `findAllByOrderByCreatedAtDesc`

### 3.2 服务层（service/EmailSendService.java，M3-2 核心）

`sendDraft(leadId, draftId)` 流程：

1. **校验**：草稿存在且归属该 lead → 草稿须 status=confirmed（否则 400「草稿需先标记待发（confirmed）才能发送」）
2. **客户邮箱**：lead.contactEmail 非空（空则 400「客户邮箱未填写，无法发送邮件」）
3. **SMTP 配置**：loadSmtpConfig 读 smtp.host/port/username/password；缺失分别 400；password AES 解密失败按明文兼容；port 默认 465、dailyLimit 默认 50
4. **每日限频**：`countByStatusAndSentAtGreaterThanEqual("sent", 当日0点)` ≥ 上限则 400「今日发送已达上限（N 封）…」
5. **落 queued 记录** → sendSmtp 投递
6. **成功**：entry status=sent + sentAt；草稿 status → sent（不可再编辑）
7. **失败**：entry status=failed + errorMsg 截断 255；**草稿保持 confirmed 可重试**

关键设计：

- **不标 @Transactional**：发送失败时 failed 记录独立提交，草稿状态不污染
- SMTP 三种模式：465 → ssl.enable + trust=\*；587 → starttls.enable + required；其余端口明文
- 超时：connectiontimeout 10000 / timeout 15000 / writetimeout 15000
- `SendResult record(sendLogId, status, toEmail, errorMsg)` 统一返回

### 3.3 接口层

- `EmailDraftController`（修改）：注入 EmailSendService，新增 `POST /api/leads/{leadId}/email-drafts/{id}/send` → ApiResponse.ok(sendResult)
- `EmailMcpTools`（修改）：注入 EmailSendService，新增 `@McpTool(name = "email_send_email") sendEmail(lead_id, draft_id)` 薄封装，异常转 toError（供外部 AI Agent 调用）
- `ConfigController`（已有）：DEFAULT_CONFIGS 已含 smtp.host/port/username/password + mail.daily_limit + mail.unsubscribe_url（共 12 项）；SENSITIVE_KEYS 含 smtp.password

## 四、前端实现

| 区块     | 文件 / 内容                                                                                                                                                                                                                                     |
| :------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 草稿箱   | `pages/Drafts.tsx`：DRAFT_STATUS_LABEL 加 sent「已发送」；confirmed 草稿显示「✉ 发送」按钮，sent 隐藏「✓ 标记待发」只留删除；发送中按钮 disabled「发送中…」；状态筛选加「已发送」；操作列 width 170→220；头部文案改「标记待发后即可 SMTP 发送」 |
| 客户详情 | `pages/Customers.tsx`：客户详情 Modal 邮件草稿区 confirmed 显示「✉ 发送」+「↩ 改回草稿」，sent 隐藏「✓ 标记待发」；sendDraft 复用同一发送逻辑                                                                                                   |
| 交互     | 发送前 confirm 弹窗（主题+客户）；成功 toast「已发送至 xxx」；失败 toast「发送失败：原因」；发送后刷新列表                                                                                                                                      |

## 五、API 清单

| 方法 | 路径                                       | 说明                       |
| :--- | :----------------------------------------- | :------------------------- |
| POST | /api/leads/{leadId}/email-drafts/{id}/send | SMTP 发送待发草稿          |
| MCP  | email_send_email(lead_id, draft_id)        | MCP 工具薄封装（外部调用） |

发送记录查询（复用既有）：lead 维度 `GET /api/leads/{id}/email-drafts` 与草稿箱全局列表。

## 六、E2E 验证（2026-08-09，DOM 测量全部通过）

**后端 API（5 分支全过）**：

- ✅ 分支1 未配置 SMTP：POST send → HTTP 400「未配置 SMTP 服务器，请先在系统设置中配置 smtp.host」
- ✅ 分支2 SMTP 连接失败：假配置（smtp.example.com:465）→ HTTP 200 {sendLogId:1, status:"failed", errorMsg:"Couldn't connect to host, port: smtp.example.com, 465; timeout 10000"} → email_send_log 落 failed 记录，草稿保持 confirmed 可重试
- ✅ 分支3 非 confirmed 草稿：HTTP 400「草稿需先标记待发（confirmed）才能发送」
- ✅ 分支4 每日限频：mail.daily_limit=0 → HTTP 400「今日发送已达上限（0 封），请明天再试或调高 mail.daily_limit」
- ✅ MCP 工具注册：启动日志「Registered tools: 5」（原 4 个 + email_send_email）
- ✅ 前后端编译：mvn compile BUILD SUCCESS / npx tsc -b EXIT=0；V10 迁移已应用（flyway now at v10）

**前端 E2E（DOM 测量，无截图）**：

- ✅ 草稿箱 draft 状态：「✓ 标记待发 + 删除」，无溢出无重叠（table right 769 < 视口 809）
- ✅ 草稿箱 confirmed 状态：「✉ 发送 + ↩ 改回 + 删除」三按钮零重叠，操作列（实际 245px）放得下
- ✅ 状态筛选：全部/草稿/待发/已发送 四选项筛选结果正确（已发送筛选 → 空态提示）
- ✅ 发送交互：confirm 弹窗 →「发送中…」disabled → 400 → 错误 toast「未配置 SMTP 服务器…」→ 按钮恢复
- ✅ 客户详情 Modal：confirmed 草稿显示「✉ 发送 + ↩ 改回草稿 + 删除」；Modal 640×589 无溢出，按钮零重叠
- ✅ 测试数据保持：lead id=4 profile_score=8 / id=5 profile_score=24；草稿 id=5 恢复 draft 状态；假 SMTP 配置与测试 send_log 已清理

## 六.5、M3-2 补充：发送记录查询页 + 失败重试（2026-08-09，Docker E2E 全过）

**需求来源**：M3-2 遗留项「发送记录查询页」——email_send_log 已落库但无专门 UI，用户从客户详情无法看到发送历史。

**后端（email_send_log 查询/重试）**：

- `EmailSendLogController`（新建）：`GET /api/leads/{leadId}/email-send-logs`（createdAt 倒序）+ `POST /api/leads/{leadId}/email-send-logs/{id}/retry`
- `EmailSendService.retry(leadId, logId)`（新增）：校验记录存在 + 归属 lead + status=failed + 关联草稿存在 → 复用 sendDraft 重新投递 → 返回 SendResult{sendLogId, status, toEmail, errorMsg}
- 错误分支：非 failed → 400「仅发送失败的记录可重试」；无关联草稿 → 400「该发送记录无关联草稿，无法重试」；记录不存在 / lead 不匹配 → 404「发送记录不存在」

**前端（客户详情「发送记录」区）**：

- `Customers.tsx`：详情 Modal 三接口并行加载（跟进 + 草稿 + 发送记录）；发送记录区 maxHeight 180px 滚动；状态 badge（sent→紫 / failed→红 / 其他→蓝）+ 收件人 + 时间；failed 记录显示红色失败原因 + 「↻ 重试」按钮（重试中 disabled）
- 空态：「暂无发送记录（草稿标记待发后点击「✉ 发送」即产生记录）」
- 重试交互：confirm 弹窗 → POST retry → 成功 toast「重试成功，已发送至 xxx」/ 失败 toast「重试失败：原因」→ 刷新发送记录 + 草稿（成功时草稿流转 sent）

**Docker 部署验证（docker compose，三容器 aic-db/aic-backend/aic-frontend）**：

- backend Dockerfile 新增 Maven 国内镜像加速（backend/maven-settings.xml → 腾讯云 maven-public，mvn dependency:go-offline 156.9s 完成）
- 接口验证 5 分支全过：列表倒序 ✅ / failed→sent 重试成功 ✅ / 非 failed 400 ✅ / 记录不存在 404 ✅ / lead 不匹配 404 ✅
- 前端 E2E：发送记录列表渲染 ✅ / 空态 ✅ / 重试成功（confirm → toast「重试成功，已发送至 zhang@test.com」→ 新 sent 记录 + 草稿流转 sent）✅ / 重试失败路径（草稿已 sent → 400 → toast 错误原因）✅
- DOM 测量：客户详情 Modal 640×789 无溢出（right 717<809 / bottom 837<885）、7 按钮零重叠、发送记录区滚动正常 ✅
- 测试数据：lead 5 保留 4 条发送记录（sent/failed 各 2）+ 2 条草稿；SMTP 配置与 daily_limit 已恢复原状

## 六.6、邮件正文追加退订链接（2026-08-09，Docker 接口验证 5 分支全过）

**需求来源**：M3-2 遗留项「取消订阅链接渲染」——营销邮件合规硬要求，正文须含退订入口，否则易被判垃圾邮件/违规。

**后端（EmailSendService.appendUnsubscribe）**：

- 发送流程改造：`sendDraft` 中先 `String finalBody = appendUnsubscribe(draft.getBody(), lead.getContactEmail())`，落 queued 记录与 sendSmtp 均用 finalBody（落库 body 保存真实投递内容，非原草稿）
- 新方法 `appendUnsubscribe(body, toEmail)`：
  - `mail.unsubscribe_url` 未配置 / 空白 → 原样返回 body（不追加）
  - 配置含 `{email}` 占位符 → 替换为 `URLEncoder.encode(toEmail, UTF_8)` 编码后邮箱
  - 无占位符 → 自动拼接 `?email=xxx`（URL 已含 `?` 查询参数则用 `&`）
  - 退订块格式：`\n————————————\n如不希望收到此类邮件，请点击退订：<url>\n`；body 为空/空白时退订块独立成文
- `ConfigController`：`mail.unsubscribe_url` 描述更新为「退订链接前缀，支持 {email} 占位符；留空则不追加」
- 前端无需改动：Settings.tsx 动态渲染 description

**Docker 接口验证 5 分支全过**：

1. 未配置 unsubscribe_url → 发送落库 body 原样（不含退订块）✅
2. 前缀无占位符 `https://example.com/unsub` → body 追加 `?email=zhang%40test.com` ✅
3. 前缀含 `{email}` `https://example.com/unsub?e={email}` → 替换为 `e=zhang%40test.com` ✅
4. 验证后恢复配置：unsubscribe_url 空 / daily_limit=1 / SMTP smtp.qq.com 不变 ✅
5. 系统设置页动态渲染新描述（浏览器快照确认）✅

## 六.7、帮助中心（2026-08-09，前端 E2E + Docker 复验全过）

**需求来源**：用户规划「帮助使用说明功能」，选定方案 A（纯前端静态页，无需后端接口）。

**前端（Help.tsx 新建）**：

- 4 区块：
  - 🚀 快速开始：QUICK_STEPS 4 步（登录系统 → 完成系统配置 → 导入历史客户 → 开始获客闭环）
  - 📚 模块使用指南：MODULES 7 个（潜客挖掘🎯 / 客户画像🧬 / 客户管理👥 / 邮件触达✉️ / 微信工作台💬 / 收件箱📥 / 草稿箱🗂️），各含 tips 标签（人机协同红线 / 每日限频 / 退订链接等）
  - 💡 常见问题：FAQS 8 条 accordion（发送失败 / AI 报错 / 每日上限 / License 激活 / 退订链接配置 / 数据安全 / 收件箱 / 人机协同），`useState` 单开互斥 + 箭头旋转
  - ℹ️ 关于：部署方式 / 数据存储 / AI 模型 / 外发原则
- `App.tsx` 新增路由 `/help`；`Nav.tsx` 系统设置后加「帮助」入口；`styles.css` 新增 help-\* 系列（2 列网格 / 蓝底 tip 标签 / FAQ 折叠卡片）

**E2E 验证**：

- 前端 dev：TS 编译通过（TSC_EXIT=0）；DOM 测量视口 809×885 无横向溢出（bodyScrollWidth 794<809）、8 个 FAQ 按钮零重叠；FAQ 展开 ✅ / 切换（1 开 1 收）✅ / 收起（0 展开）✅
- Docker 环境（http://localhost/help）：导航入口 + 4 区块完整渲染复验通过 ✅

## 六.8、退订 endpoint（2026-08-09，Docker 接口 + 前端三态 6 分支全过）

**需求来源**：用户提出「退订链接要有 endpoint，用户点击后不再收到邮件」——合规闭环：点击即生效。

**后端**：

- `V12__email_unsubscribe.sql`（Flyway 迁移）：`email_unsubscribe` 表 —— `email VARCHAR(128)` 主键 + `source VARCHAR(20) DEFAULT 'link'` + `created_at TIMESTAMPTZ DEFAULT NOW()`；按**邮箱维度**拉黑（一个邮箱可对应多个 lead）
- 实体 `EmailUnsubscribe`（email 主键）+ 仓库 `EmailUnsubscribeRepository.existsByEmail()`
- `UnsubscribeController`：`GET /api/unsubscribe?email=xxx`（公开，JWT 拦截器 exclude）—— 邮箱非法 → `status=invalid`；已存在 → `status=already`（幂等，不重复插入）；首次 → `status=unsubscribed`
- `EmailSendService.sendDraft()` 前置拦截：投递前检查收件邮箱是否已退订，命中 → `BizException.badRequest("该邮箱已退订，不再发送营销邮件")`（不发、不落 queued 记录）
- `ConfigController` 描述更新：`mail.unsubscribe_url` 只需填**网站域名前缀**（如 `https://www.example.com`），系统自动拼 `/unsubscribe?email=xxx`（endpoint 固定，2026-08-09 简化——原需手填完整 `/unsubscribe` 路径）；已含 `{email}` 或 `/unsubscribe` 的旧写法仍兼容（自定义落地页）
  - `EmailSendService.appendUnsubscribe()` 自动补全：`!base.contains("{email}") && !base.contains("/unsubscribe")` → 去掉尾部 `/` 后拼 `/unsubscribe`
  - Docker 验证：域名前缀 → `http://localhost:8080/unsubscribe?email=zhang%40test.com`（自动补全）✅；完整路径 → 不重复补 ✅；前端描述渲染 ✅

**前端**：

- `Unsubscribe.tsx`（免登录落地页，路由 `/unsubscribe`）：读 `?email=` 参数调公开接口，三态渲染 —— ✅ 退订成功（将不再收到营销邮件）/ 已退订过 / 链接无效；附「误操作可联系发件方恢复」提示
- 独立于登录态（不依赖 `/auth/me` 守卫，无 token 可访问）

**E2E 验证（Docker 生产环境）**：

1. 无 token 公开访问 `GET /api/unsubscribe?email=xxx` → `unsubscribed` ✅
2. 重复点击 → `already`（幂等）✅
3. 非法邮箱（无 @）→ `invalid` ✅
4. `flyway_schema_history` v12=success ✅
5. 黑名单邮箱发送草稿 → `400 该邮箱已退订，不再发送营销邮件` ✅（不落 queued）
6. 前端落地页三态：首次成功 / 已退订过 / 链接无效（DOM 测量视口 809×885 无溢出、无重叠）✅

**验证后恢复**：lead 5 邮箱 zhang@test.com、草稿 13 状态 sent、daily_limit=1、黑名单测试记录已清理

## 六.10、邮件模板占位符（2026-08-09，Docker 接口 + 前端 E2E 全过）

**需求来源**：M3-2 遗留项「邮件模板」——subject/body 目前直接取草稿内容，同一封草稿无法适配不同客户（用户「继续」）。

**后端**（`EmailSendService`）：

- 新增 `renderTemplate(text, lead)`：发送时把 subject/body 中的 `{xxx}` 占位符替换为 Lead 实际字段值
  - 支持变量：`{companyName}` `{contactName}` `{contactEmail}` `{phone}` `{contactPhone}`（phone 别名）`{gender}` `{industry}` `{region}` `{scale}` `{website}` `{address}` `{date}`（当天日期）`{year}`（年份）
  - **空字段 → 空串**；**未识别占位符原样保留**（如 `{email}` 留给 appendUnsubscribe 处理退订 URL）
- `sendDraft` 改造：校验通过后、落库前执行 `finalSubject = renderTemplate(...)` / `finalBody = renderTemplate(...)`，再 `appendUnsubscribe` 追加退订块，落库 subject/body 保存替换后的真实投递内容

**前端**：

- `Customers.tsx`：「AI 生成邮件」弹窗「生成结果（可编辑）」下方新增变量提示文案（发送时自动替换 + 变量列表）
- `Help.tsx`：FAQ 新增「邮件正文支持哪些变量？」

**E2E 验证（Docker 生产环境）**：

1. 含 `{companyName}` `{contactName}` `{contactPhone}` `{industry}` `{region}` `{scale}` `{website}` `{address}` `{date}` 草稿 → 发送 → 落库全部替换 ✅（`【{companyName}】{contactName}，{date}合作沟通` → `【云启软件】陈启明，2026-08-09合作沟通`）
2. 空字段 `{gender}`（该客户 gender=null）→ 替换为空串 ✅
3. `{email}` 占位符 → 原样保留（不抢退订链接逻辑）✅；`{year}` → 2026 ✅；未知变量 `{unknownVar}` → 原样保留 ✅
4. 前端：生成 modal 打开 → 变量提示显示 → AI 生成 → 保存为草稿成功 toast ✅
5. DOM 测量（视口 809×885）：modal 无溢出 / overlapCount=0 / hint、textarea、subject、按钮均在视口内 ✅

**验证后恢复**：测试草稿/发送记录已清理，配置恢复（unsubscribe_url 空、daily_limit=1）

## 六.11、邮件模板管理（2026-08-09，Docker 接口 + 前端 E2E + DOM 测量全过）

**需求来源**（用户）：①邮件模板要独立管理，可随时编辑美化；②草稿箱看到的应是已替换完占位符的真实内容；③客户邮件生成弹窗可一键套用模板。

**后端**：

- `V13__email_template.sql`：表 `email_template`（id BIGSERIAL PK、name VARCHAR(64) UNIQUE、subject、body、description、created_at/updated_at）
- `EmailTemplate` 实体 + `EmailTemplateRepository`（`findAllByOrderByUpdatedAtDesc` / `existsByName` / `existsByNameAndIdNot`）+ `EmailTemplateService`（create 空字段 400、重名 400「模板名称已存在」；update 部分更新 + 重名校验；delete 不存在 404「邮件模板不存在」）+ `EmailTemplateController`（`GET/POST/PUT/DELETE /api/email-templates`）
- **`util/TemplateRenderer` 公共类**：替换逻辑从 `EmailSendService` 抽出（`EmailDraftService` 与 `EmailSendService` 共用）；支持 13 变量（companyName/contactName/contactEmail/phone/contactPhone 别名/gender/industry/region/scale/website/address/date/year）；空字段→空串、未识别占位符（如 `{email}`）原样保留
- **草稿保存即替换**（`EmailDraftService.create/update`）：拿 Lead 后对 subject/body 先 render 再落库 → 草稿箱见真实内容；`EmailSendService.sendDraft` 发送时再次 render 为**幂等兜底**（覆盖用户手改草稿新增的占位符），随后 appendUnsubscribe

**前端**：

- `Templates.tsx`（新页 + 导航「邮件模板」）：列表（名称/主题占位符/说明/更新时间/操作）+ 新建/编辑 modal（名称/说明/主题/正文 + 13 变量 code 提示 + 👁 实时预览示例客户 示例科技/张三）+ 删除 window.confirm 二次确认
- `Customers.tsx`：「AI 生成邮件」弹窗「触达目标」上方新增「快捷模板」下拉（openGenerate 时拉取模板列表，选择即 `applyTemplate` 填主题/正文，可再编辑）
- `App.tsx` 路由 `/templates`；`styles.css` `.template-table`（min-width 720，5 列）

**E2E 验证（Docker 生产环境）**：

1. 模板 CRUD：创建（含中文 UTF-8 字节 + hex 验证 e9a696e6aca1e8a7a6e8bebe「首次触达」正确）✅ 列表倒序 ✅ 重名 POST→400「模板名称已存在」✅ 空 body→400 ✅ PUT 更新 ✅ DELETE ✅ 不存在 404 ✅
2. **草稿保存替换（核心）**：lead 18（数澜科技/林晓岚）POST 草稿 `SUBJECT-{companyName}-{contactName}-{date}` → `SUBJECT-数澜科技-林晓岚-2026-08-09` ✅；phone/contactPhone 双别名→13910001002 ✅；`{email}`/未知变量原样 ✅；编辑草稿 PUT 同样替换（`{year}`→2026、industry/region/scale/website/address 替换、gender null→空串）✅
3. 前端 E2E：模板页列表/新建（中文+占位符+实时预览「主题：【示例科技】张三，方案评审邀约」）/编辑保存「模板已更新」/删除 confirm 确认「模板已删除」✅；客户邮件弹窗「快捷模板」选「方案跟进」→ 主题/正文自动填入 ✅；保存为草稿 → 草稿箱见「【数澜科技】林晓岚，方案评审邀约」+ 正文「尊敬的 林晓岚…数澜科技…2026-08-09」✅
4. DOM 测量（视口 809×885）：模板编辑 modal（640px 居中，bottom 837<885）与邮件生成弹窗（640px，bottom 377<885）均无溢出、overlapCount=0、无可滚动溢出内容 ✅；模板表格横向滚动为 `.table-wrap` 设计行为（与客户管理表一致）

**验证后恢复**：删除测试草稿（lead18 草稿 0 条）、测试模板「首次触达」已删，保留前端创建示例「方案跟进」（id=1）

## 六.12、邮件模板 HTML 美化 + AI 个性化生成（2026-08-09，Docker 接口 + 前端 E2E + DOM 测量全过）

**需求来源**（用户）：①邮件模板应支持 HTML，起到美化作用；②标题和正文由 AI 根据沟通历史记录个性化编写；③每次生成应基本不同（多样性）。

**后端**：

- `EmailSendService.isHtml(body)`：启发式检测（`<!doctype html`/`<html`/`<p`/`<br`/`<div`/`<span`/`<table`/`<ul`/`<li`/`<strong`/`<b>`/`<h1`-`<h3`/`<a ` 任一命中即 HTML，小写比较、null/blank→false）；`sendSmtp` 中 HTML 用 `message.setContent(body,"text/html; charset=UTF-8")`，纯文本 `setText(body,"UTF-8")`
- `appendUnsubscribe`：HTML body 追加内联样式退订块 `<br><div style="margin-top:16px;padding-top:8px;border-top:1px solid #eee;color:#999;font-size:12px;">如不希望收到此类邮件，请点击<a href="URL" style="color:#1677ff;">退订</a></div>`；纯文本维持原 `\n————————————` 块
- `EmailDraftService.generateWithContext`：新增可选 `templateId`（`GenerateRequest` record 加字段，`EmailDraftController` 透传）；templateId 非空时读模板注入 `templateHint`「【参考模板（仅参考其结构/视觉风格，正文必须重新编写）】+ 名称/主题示例/正文示例（含 {占位符} 替换说明）」
- **systemPrompt 重写**：①主题 ≤20 字且不得与历史邮件主题重复；②正文用简洁美观 HTML（`<p>` 分段、`<b>` 强调、`<ul><li>` 列表、`<span style="color:#1677ff">` 高亮），**全部内联样式，禁止外部 CSS/JS/图片**；③正文 3-6 句话；④**每次生成必须与历史邮件明显不同：换一种措辞、句子结构与引用事实的组合，即使反复使用同一参考模板也要避免雷同**；⑤客户信息已提供，写真实值不输出 {占位符}；⑥严格两行「主题：<主题>\n正文：<HTML>」，不要输出代码块标记
- `parseResult` 增强：先剥离 `` 代码块标记（`replaceFirst("^``[a-zA-Z]_\\s_","").replaceAll("```\\s*$","")`），再按 `split("\n",2)` 取主题/正文（正文可多行）

**前端**：

- `utils/html.ts`：`isHtmlText(text)` 与后端 isHtml 同规则启发式（前端渲染用）
- `Templates.tsx`：页面说明加「HTML 美化」提示；预览区 `isHtmlText(form.body)` 时 `dangerouslySetInnerHTML` 渲染富文本（白底边框 + maxHeight 260 滚动）
- `Customers.tsx`：生成弹窗「AI 生成」按钮 title 提示个性化 + 模板参考；请求体带 `templateId`；生成结果区「✏️ 源码 / 👁 预览」toggle（`genPreview` state，预览渲染 `dangerouslySetInnerHTML`，maxHeight 300 滚动）；客户详情弹窗草稿列表 `isHtmlText(d.body)` 渲染富文本
- `Drafts.tsx`：展开行 `.inbox-body` 内 `isHtmlText(d.body)` 渲染富文本（lineHeight 1.7）

**E2E 验证（Docker 生产环境）**：

1. 接口：带模板（id=5「方案跟进」）生成两次 → 均返回 HTML（`<p>`/`<b>`/`<span style="color:#1677ff">`/`<ul>`）且两次内容措辞/结构明显不同（个性化+多样性 ✅）；不带模板也返回 HTML；`{"goal":"x"}` 校验 400 ✅
2. HTML 草稿保存（id=22）→ 草稿箱/详情富文本渲染；标记待发 + 发送 → status=sent，send_log 落库 body hex 解码 = HTML 原文 + HTML 退订块 ✅
3. 前端 E2E：模板页新建 HTML 模板（预览富文本渲染：`<p>尊敬的<b>张三</b>：</p>` + ul 列表 + 占位符替换 示例科技/2026-08-09）→ 保存 → 编辑加载 → 删除 confirm ✅；客户生成弹窗「快捷模板」下拉含新模板 → 选模板 + AI 生成 → 返回个性化 HTML → 「👁 预览」富文本渲染 → 保存为草稿 → 客户详情草稿列表富文本 ✅；草稿箱展开行富文本（p/ul/span/bold，textLen=207）✅
4. DOM 测量（视口 809×885）：模板编辑 modal（bottom 837<885）、生成弹窗（bottom 837，预览区 321 高）、客户详情弹窗（bottom 671）均 0 溢出；fixed/modal 元素 0 溢出、overlapCount=0（草稿箱页面底部按钮溢出为页面正常滚动 scrollH=1213>885，非布局问题）✅

**验证后恢复**：测试模板「HTML测试模板」已删、测试草稿（draft 22/23）与 send_log 16 已清、daily_limit=1、unsubscribe_url 空

## 七、遗留/后续

- ✅ 真实 SMTP 投递验证（2026-08-08 微信 E2E 完成：smtp.qq.com 授权码发送成功，收件箱同步闭环）
- ✅ 发送记录查询页（2026-08-09 完成，见六.5：客户详情发送记录区 + 失败重试）
- ✅ 取消订阅链接渲染（2026-08-09 完成，见六.6：EmailSendService.appendUnsubscribe + 5 分支验证）
- ✅ 退订 endpoint（2026-08-09 完成，见六.8：公开接口 + email_unsubscribe 黑名单 + 发送前拦截 + 免登录落地页，6 分支验证）
- ✅ 退订管理 UI（2026-08-09 完成，见六.9：后台查看/恢复黑名单 —— list/restore 接口 + Settings 退订管理区块，Docker E2E 全过）
- ✅ 邮件模板占位符（2026-08-09 完成，见六.10：renderTemplate 变量替换 + 前端变量提示 + Help FAQ，Docker E2E 全过）
- ✅ 邮件模板管理（2026-08-09 完成，见六.11：模板 CRUD 独立管理 + 实时预览 + 草稿保存即替换（草稿箱真实内容）+ 生成弹窗快捷模板套用，Docker E2E 全过）
- ✅ 邮件模板 HTML 美化 + AI 个性化生成（2026-08-09 完成，见六.12：HTML 邮件（isHtml 检测 + setContent + HTML 退订块）+ AI 按沟通历史个性化生成（每次不同，参考模板仅取风格）+ 前端源码/预览 toggle 与三处富文本渲染，Docker E2E 全过）
- 🔄 失败重试：人工重试已上线（↻ 重试按钮）；自动重发 / 发送队列未做
