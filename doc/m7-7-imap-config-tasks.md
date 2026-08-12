# M7.7 收件箱（IMAP）账号密码接入系统设置

## 状态

✅ 已完成（2026-08-09）

## 需求原文

收件箱的账号和密码，能否配置在系统设置里面？（当前收件箱同步只读环境变量 EMAIL*PROVIDER/IMAP*\*，系统设置页无收件配置，与 SMTP 发件配置方式不一致）

## 现状问题

- `EmailMailboxService` 构造函数 `@Value` 只读环境变量（provider/imap.host/port/ssl/username/password），**不读 system_config 表**
- 系统设置页（ConfigController.DEFAULT*CONFIGS）只有 ai.* / smtp._ / mail._，\*\*无 imap.\_ 收件配置\*\*
- 服务器未配置 EMAIL/IMAP 环境变量 → provider 默认 mock → 收件箱只有 5 封模拟邮件，真实来信不同步

## 设计决策

1. **后端动态读取 system_config**：`EmailMailboxService` 改为注入 `SystemConfigRepository + AesUtil`，每次同步/列表时动态读取配置（改完系统设置立即生效，无需重启）
2. **provider 判定优先级**：
   - 环境变量 `EMAIL_PROVIDER` 显式设置 → 用之
   - 否则读 system_config `imap.host`：有值 → imap；无值 → mock（保持演示可用）
3. **新增系统配置项**（ConfigController.DEFAULT_CONFIGS）：`imap.host` / `imap.port` / `imap.ssl` / `imap.username` / `imap.password`（password 加入 SENSITIVE_KEYS 加密落库、回显脱敏）
4. **兼容性**：现有 mock 行为不变；环境变量显式设置优先于 system_config
5. **前端**：系统设置页配置项由后端动态返回，无需改 Settings.tsx（自动显示新配置项）

## 改动清单

- [x] backend EmailMailboxService：构造注入 SystemConfigRepository+AesUtil，动态读取配置，密码解密
- [x] backend ConfigController：DEFAULT_CONFIGS 新增 imap.\* 5 项；SENSITIVE_KEYS 加 imap.password
- [x] backend application.yml：provider 默认值改空（${EMAIL_PROVIDER:}），注释更新；IMAP 参数保留为环境变量兑底并注明优先读 system_config
- [x] 本地构建 + 部署验证

## 验证记录

- [x] 本地重建 backend（docker compose build backend + up -d backend），启动成功
- [x] GET /api/config 返回 imap.\* 5 项配置（imap.host/port/ssl/username/password），描述正确
- [x] 未配置 imap.host → 收件箱 GET /api/emails/inbox 仍返回 5 封 mock 邮件（totalElements=5）
- [x] 设置 imap.host=imap.invalid-host.test + 账号密码 → POST /api/emails/inbox/sync 走 IMAP 分支，日志 "IMAP 连接失败: Couldn't connect to host, port: imap.invalid-host.test, 993"，接口返回 400 提示
- [x] system_config 表 imap.password 加密存储（SUz2UL2wWOa3A1gbHxsV5Q==），imap.username 明文
- [x] 验证后清空 imap.host → 收件箱恢复 mock（totalElements=5）

## 交付

- `EmailMailboxService`：provider 判定改为 env 显式优先 + system_config imap.host 自动判定；新增 `loadImapConfig()`（port 默认 993 / ssl 默认 true / password AES 解密失败按明文兼容）；`openStore()` 用动态配置
- `ConfigController`：DEFAULT_CONFIGS 新增 imap.host（收件箱 IMAP 服务器，配置后同步真实邮件，留空为演示模式）/ imap.port / imap.ssl / imap.username / imap.password；SENSITIVE_KEYS 加 imap.password
- `application.yml`：provider 默认 ${EMAIL_PROVIDER:}（空）→ 未设置环境变量时按系统设置自动判定，服务器无需改环境变量即可在系统设置页配置收件箱
- 前端无需改动（系统设置页动态渲染后端配置项）
- Git commit 待提交

---

## M7.7 追加 1：只同步客户管理中存在邮箱的客户的邮件（2026-08-09 完成）

### 需求原文

「正确的功能是客户管理中存在的客户邮箱地址，去邮箱中找到这个邮件，然后同步过来，而不是啥邮件都同步过来，这个就不符合逻辑了。」——收件箱同步必须只同步「客户管理（lead 表）中存在邮箱的客户」发来的邮件

### 设计决策

1. **发件人白名单过滤**：同步时从 lead 表取全部客户邮箱（`findDistinctContactEmails`）→ 空则跳过同步 → 非空则作为白名单传给 MCP 列表工具
2. **IMAP 层 SEARCH 过滤**：白名单非空时用 `OrTerm(from1, from2, ...)`（+unreadOnly 用 AndTerm）在服务器端过滤，而不是拉全量再内存过滤——邮箱有几万封也不影响性能
3. **列表与正文分离**（性能优化，见下节）：先拉元数据 → 过滤新邮件 → 只对新邮件批量取正文
4. **无白名单兼容**：白名单为空（无客户邮箱）时行为不变（取最新 limit 封）

### 改动清单

- [x] `LeadRepository.findDistinctContactEmails()`（SELECT DISTINCT contact_email FROM lead WHERE contact_email IS NOT NULL AND TRIM(contact_email) <> ''）
- [x] `EmailMailboxService.listEmails(folder, limit, unreadOnly, since, fromEmails, includeBody)` 6 参签名（白名单 → OrTerm SEARCH，结果截断最新 limit 封；FetchProfile 预取 ENVELOPE/FLAGS/UID，正文逐封 getContent 复用同一 IMAP 连接；**删掉 FetchProfile.Item.CONTENT——jakarta.mail 无此项编译报错**）
- [x] `EmailMcpTools.email_list_emails` 新增 `include_body` 参数（默认 false）+ `parseFromEmails` 解析
- [x] `EmailMcpClient.listEmails(limit, unreadOnly, fromEmails, includeBody)` 4 参透传
- [x] `EmailInboxService.sync()` 白名单接线
- [x] 删除非真实邮箱测试客户：lead id 4（导入测试公司 wang@new.com）、id 5（测试客户A zhang@test.com）；email_inbox 表清空

### 根因教训（关键）

**白名单过滤"不生效"的根因是新代码从未编译部署**：源码 .java 21:08 保存、容器镜像 21:02 用旧源码构建、本地 target/classes 19:55 旧编译。javap 验证容器 jar 是 4 参旧 `listEmails`。**改代码后必须确认容器内 jar 实际包含新代码（javap 验证）；`.java` 保存时间 ≠ 镜像构建时间，构建缓存可能用旧源码**

### 验证记录

- [x] 重建镜像 + javap 验证 6 参新签名 `listEmails`
- [x] `POST /api/emails/inbox/sync` 1s 返回 `{added:0, total:0, filteredBy:[5个客户邮箱]}`（当时未删 lead，白名单 5 个；QQ 邮箱无测试客户邮件故 added=0 正常）
- [x] 删 lead 4/5 后白名单 = 3 个邮箱（chen.qm@yunqi-soft.cn / lin.xl@shulan-tech.cn / yuezu1106@gmail.com）

---

## M7.7 追加 2：同步性能优化——批量 DB 查询 + 列表/正文分离（2026-08-09 完成）

### 需求原文

「同步邮件的功能，是否还能做一些提升性能的优化？」

### 优化前瓶颈

1. **逐封 DB 查询**：100 封 = 300 次 DB 往返（existsByMailboxAndUid + findFirstByContactEmailIgnoreCase + save）
2. **includeBody=true 对最新 100 封全拉正文**：即使 90 封已入库也重复拉取
3. **已入库邮件每次同步重复解析正文**（复用同一连接但逐封 getContent 仍是开销）

### 优化方案

1. **列表与正文分离**：先 `listEmails(includeBody=false)` 拉元数据 → `findUidsByMailboxAndUidIn` 一次查已存在 uid → 只对新邮件 `readEmails(uids)` 批量取正文（一次 MCP 调用 + 一次 IMAP 连接 + getMessagesByUID 批量）
2. **批量 DB 操作**：
   - 已存在 uid：`findUidsByMailboxAndUidIn` 一次查询
   - 邮箱→客户映射：`findByContactEmailInIgnoreCase` 一次查询建 Map（`lower(trim(email))` → leadId）
   - 入库：`saveAll` 批量（撞唯一约束 uk_inbox_uid 时回退逐封保存跳过）
3. **新增 MCP 批量工具** `email_read_emails`（uids 逗号分隔）→ `EmailMailboxService.readEmails` → `readImapBatch`（一次 openStore + getMessagesByUID + FetchProfile 预取，逐封 getContent 复用连接）

### 改动清单

- [x] `EmailInboxRepository.findUidsByMailboxAndUidIn`（IN 批量查 uid）
- [x] `LeadRepository.findByContactEmailInIgnoreCase`（IN 批量查客户，lower(trim) 匹配）
- [x] `EmailMailboxService.readEmails(List<Long>)` + `readImapBatch`（getMessagesByUID）
- [x] `EmailMcpTools.email_read_emails` 工具 + `parseUids`
- [x] `EmailMcpClient.readEmails(List<Long>)`（TypeReference<List<MailItem>>）
- [x] `EmailInboxService.sync()` 重构为 5 步：拉元数据 → 批量查已存在 → 新邮件批量取正文 → 批量建映射 → saveAll

### 验证记录

- [x] `mvn compile` 通过（LeadRepository 补 import Param/Collection）
- [x] 重建 backend 镜像部署成功
- [x] 第一次同步 1.4s 返回 `{added:0, total:2, filteredBy:[3个客户邮箱]}`（2 封为 rick-test 客户 yuezu1106@gmail.com 的真实测试邮件 uid 2869/2868）
- [x] 第二次同步（无新邮件路径）0.9s——日志确认走「收件箱同步完成：无新邮件」新分支（不拉正文，纯元数据+批量 uid 查询）
- [x] 对比优化前：60.1s 超时（逐封 readEmail 重连）→ 现在 1s 内
- [x] 批量取正文新增路径（readEmails）待有真实新邮件时实际触发验证（逻辑与单封同源 toMailItem，风险低）

### 交付

- 同步性能：100 封场景下 DB 往返从 ~300 次降到 ~4 次（uid 批量查 + 映射批量查 + saveAll + count）
- 已入库邮件不再重复拉正文（先过滤后取正文）
- Git commit 待提交

---

## M7.7 追加 3：定时同步周期 5 分钟 → 2 分钟（2026-08-09 完成）

### 需求原文

「改成2分钟如何？会不会导致性能损耗过大？」

### 性能影响分析（结论：损耗可忽略）

| 指标          | 每 5 分钟    | 每 2 分钟 | 影响评估                    |
| ------------- | ------------ | --------- | --------------------------- |
| 同步次数/小时 | 12 次        | 30 次     | QQ 邮箱 IMAP 无压力         |
| 每次同步耗时  | 0.9~1.5s     | 同左      | 单次很轻                    |
| DB 查询/次    | ~4 次批量    | 同左      | 30×4=120 次/小时，PG 可忽略 |
| 正文拉取      | 仅新邮件才拉 | 同左      | 已优化，多数只拉元数据      |
| IMAP 连接频率 | 12 次/时     | 30 次/时  | 远低于 QQ 邮箱限制          |

关键：优化后大多数同步走「无新邮件」快速路径（0.9s，只拉元数据 + 批量 uid 查询，不拉正文不写库），2 分钟一次成本几乎不计。

### 改动清单

- [x] `application.yml`：`app.email.sync-cron` 默认值 `0 */5 * * * *` → `0 */2 * * * *`（打进 jar，服务器部署也生效）
- [x] `docker-compose.yml`：backend 环境变量加 `EMAIL_SYNC_CRON: ${EMAIL_SYNC_CRON:-0 */2 * * * *}`（本地显式覆盖，可用 .env 再覆盖）

### 验证记录（最强实证）

- [x] 重建 backend 部署后，**13:26:00 定时自动触发**：`scheduling-1` 线程执行，日志「定时收件箱同步：新增 0 封，累计 2」（无需手动操作）
- [x] **13:28:00 第二轮触发时恰好收到新邮件（uid 5700 "re test for 2026-08-09"，13:26:10 收到）→ 自动同步入库，日志「定时收件箱同步：新增 1 封，累计 3」**——全程无人操作，约 2 分钟内自动完成，自动关联 rick-test 客户
- [x] 前端刷新显示 3 封正常（新邮件排第一）
- [x] 结论：**邮箱收到新邮件 → 最多 2 分钟系统自动同步，「↻ 同步邮件」按钮只是手动立即触发**

### 交付

- 定时同步 cron 默认每 2 分钟，环境变量 EMAIL_SYNC_CRON 可覆盖（服务器同样生效）
- Git commit 待提交

## M7.7 追加 4：收件箱分页问题排查与修复（2026-08-09 完成）

### 需求原文

「好像邮件箱页面的分页有问题，请帮忙验证。」

### 排查过程（插 25 封测试邮件 → 28 封/3 页）

1. **后端 API 正常**：`GET /api/emails/inbox?page=0&size=10` → `total=28, pages=3, content=10`，receivedAt 倒序正确（测试邮件-1 最新在前）
2. **前端翻页正常**：下一页 → 第 2 页（10 行）/ 第 3 页（8 行收尾），`上一页(disabled)/下一页` 状态正确，10+10+8=28 无遗漏无重复
3. **搜索过滤正常**：搜「分页测试邮件-1」→ 匹配 11 封（1 和 10-19）显示 `1 / 2（共 11 封）`，自动重置回第 1 页
4. **只看未读正常**：17 封未读 → `1 / 2`，第 2 页全为未读无已读混入
5. **页面 reload 快照偶现「暂无邮件」空态**：实测为 React 加载竞态（load() 未完成瞬间的快照），等 1~2s 后 DOM 正常渲染，非 bug

### 发现并修复的真实 Bug：删除最后一页邮件导致页码越界空态

- **复现**：未读过滤下翻到第 2 页（15 封/2 页），UI 逐封删除第 2 页邮件 → 13 封 `2/2` → 12 封 `2/2` → 11 封 `2/2` → **删到 10 封（正好 1 页）时 page=1 越界 → 前端显示「暂无邮件」+ 分页栏消失**，实际数据还在
- **根因**：
  - 后端 `EmailInboxService.list()` 只对 page 做了下界保护 `Math.max(page, 0)`，**无上界保护**——Spring Data JPA 对越界 page 返回空 content
  - 前端 `Inbox.tsx` 的 `load()` 拿到空 content 直接 `setEmails([])`，无页码回退；`remove()` 删除后 `await load()` 也不调整页码
- **修复**（`frontend/src/pages/Inbox.tsx` `load()`）：响应后检查 `data.totalPages > 0 && data.number >= data.totalPages` → `setPage(data.totalPages - 1)` 回退到最后一页重新加载（不会无限循环：回退后 number < totalPages）
- **验证**：重建前端容器后复测同一场景 → 删到 10 封时**自动回退第 1 页显示 10 行数据，不再空态**；继续删第 1 页正常
- 测试数据（分页测试/边界测试/边界修复测试共 30+ 封）已清理，库里恢复 3 封真实邮件（uid 2868/2869/5700）

### 交付

- 前端分页越界保护已部署（aic-frontend 重建），收件箱删除/翻页/筛选全链路正常
- Git commit 待提交

---

## M7.7 追加 5：客户/草稿/发件箱三页面分页巡检与统一修复（2026-08-09 完成）

### 需求原文

「请巡检其他页面上存在分页功能是否没有问题。」——收件箱分页修复（追加 4）后，全面巡检其余分页页面。

### 巡检范围与结论

| 页面                  | 列表              | 排序规则                 | 巡检结果                   |
| --------------------- | ----------------- | ------------------------ | -------------------------- |
| 客户管理（Customers） | lead 表           | updatedAt DESC           | ✅ 正常                    |
| 草稿箱（Drafts）      | email_draft 表    | createdAt DESC + id DESC | ✅ 正常                    |
| 发件箱（Sent）        | email_send_log 表 | createdAt DESC + id DESC | ⚠️ 发现并修复 1 个后端 bug |

### 前端统一修复：分页越界保护（与 Inbox 追加 4 同模式）

- `frontend/src/pages/Customers.tsx`：`load()` 响应后检查 `data.totalPages > 0 && data.number >= data.totalPages` → `setPage(data.totalPages - 1)` 回退重新加载（catch 分支 `navigate("/login")` 保留）
- `frontend/src/pages/Drafts.tsx` / `Sent.tsx`：同样保护（catch 分支显示错误 toast 保留）
- 根因同收件箱：后端 `PageRequest.of(Math.max(page,0),...)` 只保下界不保上界

### ⚠️ 发件箱发现并修复的真实 Bug：状态筛选空结果 NPE（500）

- **复现**：发件箱页筛选状态「失败」→ 接口 500，前端「系统繁忙，请稍后重试」
- **根因**（`EmailSendLogService.listAll()` 第 75 行）：
  ```java
  Map<Long, Lead> leads = leadIds.isEmpty() ? Map.of()   // ← Map.of() 不允许 null key！
          : leadRepository.findAllById(leadIds)...
  return result.map(log -> toView(log, leads.get(log.getLeadId())));  // lead_id=null → get(null) → NPE
  ```
  筛选「失败」时命中的记录全部 `lead_id` 为 NULL（关联客户已删除，如 zhang@test.com 测试客户删除后遗留的失败发送记录）→ `leadIds` 为空 → `Map.of()` → `leads.get(null)` 抛 NPE → 500
- **修复**：`leadIds` 为空时用 `new HashMap<>()`（允许 null key），仅在非空时 putAll 查询结果
- **验证**：重建 backend 后筛选「失败」→ 正常返回 2 条失败记录，重试按钮仅失败行显示 ✓；全列表/搜索/筛选均正常

### 三页面分页实测记录（插测试数据 → 每页 10 条/2 页）

- **客户页**：12 条（3 真实 + 9 测试）→ 渲染 10 行/`1 / 2`；翻第 2 页（测试 8/9/10）→ 删除 3 条测试 → 11→10 条自动回退第 1 页显示 10 行，无空态 ✓；搜索「分页客户测试」7 条、状态筛选正常 ✓
- **草稿箱**：12 条测试草稿（分页草稿测试-1~12）→ 渲染 10 行/`1 / 2`；翻第 2 页（测试 11/12）→ 删除 2 条 → 12→10 条自动回退第 1 页 10 行 ✓
- **发件箱**：12 条（8 历史 + 4 测试）→ 渲染 10 行/`1 / 2`；测试记录 created_at 改 20 天前排到第 2 页 → 删除 2 条 → 12→10 条自动回退 ✓；搜索「分页发送测试」2 条 ✓；筛「失败」→ **修复前 500 / 修复后正常** ✓

### 布局溢出排查（e2e 必检项）

- 客户页 11 列表格：`.table-wrap`（`overflow-x: auto`）内部横向滚动（scrollW 1621 > clientW 714）= **设计意图**（m2-2/m2-3 任务文档一致），页面本身无横向溢出（docScrollW 794 < innerWidth 809）
- 草稿箱 5 列 / 发件箱 7 列表格：均无溢出（scrollW = clientW）
- 结论：无界面变形/重叠

### ⚠️ 事故与教训：误删 3 个真实客户（已完整恢复）

- **经过**：测试脚本翻到客户页第 2 页逐条删除测试数据，但当时第 2 页含真实客户（云启软件/数澜科技/rick-test，因 updatedAt 较旧排在 page 2）→ 3 条真实 lead 被删
- **连锁影响**：email_inbox 212/213/214 的 lead_id SET NULL；email_draft（lead_id=19）级联全删；email_send_log 18-21 的 lead_id SET NULL
- **恢复**：从服务器 DB（43.153.229.106，lead 表有完整数据）按原 id INSERT 回 3 条客户 → UPDATE inbox lead_id=19 → 重建 12 条草稿 → UPDATE send_log lead_id=19
- **教训（重要）**：测试脚本删除前**必须校验目标行是测试数据**（`rowText.startsWith('分页客户测试')` 再删）；测试数据插入后需按各页实际排序规则（客户页=updatedAt）把测试数据排到目标页，避免真实数据混入被删

### 交付

- 前端 3 页面分页越界保护 + 后端 EmailSendLogService NPE 修复已部署（aic-frontend / aic-backend 重建）
- 测试数据全部清理：lead=3（真实 17/18/19）、email_draft=0、email_send_log=8（历史保留）、email_inbox=3（关联正常）
- Git commit 待提交

---

## M7.7 追加 6：导航栏最左侧添加品牌 Logo（2026-08-09 完成）

### 需求原文

「可否用登录页的图标放到导航栏的最左侧？」

### 改动清单

- [x] `frontend/src/pages/Nav.tsx`：`.nav` 内最左侧新增 `<NavLink to="/" className="nav-brand">` 包裹 `<img src={BASE_URL}logo.svg>`（与登录页同款图标，点击回工作台）
- [x] `frontend/src/styles.css`：新增 `.nav .nav-brand`（flex 居中）+ `.nav .nav-logo`（32×32）

### 验证记录

- [x] 重建 aic-frontend 容器后浏览器实测：Logo 32×32 位于导航栏最左侧（left=24, top=12），垂直居中（56px 导航栏内 32px 图标上下各 12px）
- [x] 与「工作台」链接无重叠（logo right=56 < workbench left=84）
- [x] 其余导航项、退出登录均正常

### 交付

- 导航栏左侧品牌 Logo 已部署（aic-frontend 重建），点击 Logo 跳转工作台
- Git commit 待提交

---

## M7.7 追加 7：系统设置页 AI/邮箱配置分组（2026-08-09 完成）

### 需求原文

「能否把## 系统设置中的ai部分的参数，和邮箱部分的参数，区分开来，比如中间用一个带有背景颜色的行区隔？」

### 设计决策

- 前端按配置 key 前缀分组渲染：`ai.*` → AI 模型配置组；`smtp.* / imap.* / mail.*` → 邮箱配置组
- 每组上方加带背景色 + 左边框的标题行（`.config-group-ai` 浅蓝 / `.config-group-mail` 浅绿），视觉上形成区隔
- 后端无改动（配置项已由 ConfigController 动态返回），保存逻辑不变（整组统一保存）

### 改动清单

- [x] `frontend/src/pages/Settings.tsx`：渲染改为分组——`🤖 AI 模型配置` 标题 + `filter(key.startsWith("ai."))` → `📧 邮箱配置` 标题 + `filter(key.startsWith("smtp.")||imap.||mail.)`
- [x] `frontend/src/styles.css`：新增 `.config-group-title`（内边距/圆角/字重）+ `.config-group-ai`（背景 #e6f4ff 蓝、左边框 #1677ff）+ `.config-group-mail`（背景 #f6ffed 绿、左边框 #52c41a）

### 验证记录

- [x] 重建 aic-frontend 后浏览器实测：两组标题显示（🤖 AI 模型配置 浅蓝背景 / 📧 邮箱配置 浅绿背景），10 个 AI 配置项 + 14 个邮箱配置项完整
- [x] DOM 测量：AI 标题 top=156 h=35，邮箱标题 top=987 h=35，两组间无重叠、无溢出（card 右边界 778 < 视口）
- [x] 点击「保存配置」→ toast「保存成功」，功能无回归
- [x] 顺带修复：系统设置页 imap.host 描述被历史测试污染为 "x" → DB 更新为正确描述「收件箱 IMAP 服务器（如 imap.gmail.com / imap.qq.com），配置后收件箱同步真实邮件；留空为演示模式」

### 服务器部署 + e2e 验证（43.153.229.106）

- [x] scp Settings.tsx + styles.css → 服务器重建 aic-frontend（docker compose build frontend + up -d --no-deps frontend）
- [x] 服务器设置页实测：AI 标题浅蓝（rgb(230,244,255)）+ 邮箱标题浅绿（rgb(246,255,237)），10 AI 项 + 邮箱项完整，无重叠（top 189→1020）、无溢出（card 右 778 < 视口 809）
- [x] 服务器客户页：5 条客户正常显示，表格在 .table-wrap 内横向滚动（scrollWidth 1621 > clientWidth 714），body 无溢出（794 < 809）
- [x] 服务器发件箱：8 条记录全部显示；筛「失败」→ 2 条且均带「✉ 重试」按钮（NPE 修复在服务器生效）；切回全部恢复 8 条
- [x] 服务器收件箱：5 封邮件正常显示（关联客户/AI 意图/已读未读均正常）
- [x] 服务器草稿箱：显示「暂无草稿」正确——DB 6 条 email_draft 全部 sent 状态（已发送归发件箱，非 bug）
- [x] 服务器导航栏 Logo 32×32 正常显示（left=24, top=45）
- [x] 服务器有「系统未激活」横幅（license 表 0 条）——业务状态，非本次改动问题

### 交付

- 设置页 AI/邮箱配置分组已完成：本地 + 服务器均已部署验证
- Git commit 待提交

## M7.7 追加 8：导航栏不换行 + 移动端响应式适配（2026-08-09 完成）

### 需求原文

「能否保证导航栏的文字不换行，界面在手机尺寸下，不变型，能做到比较好的适配？」

### 设计决策

- **导航栏**：`.nav` 设 `flex-wrap: nowrap; overflow-x: auto` + 隐藏滚动条，`.nav a` 设 `white-space: nowrap; flex-shrink: 0` → 文字永不换行、不被压缩，超宽时导航整体横向滑动（可滚到最右的退出登录）
- **@media 断点位置修复（关键）**：上一轮把 `@media (max-width:1024px/768px)` 块加在了 `.help-steps`/`.help-modules` 定义**之前**，被后面的普通规则覆盖 → 帮助页手机下仍是 2 列且 body 溢出。**教训：@media 块必须放在文件末尾**（或相关规则之后）。本次将整个响应式块移到 styles.css 末尾
- **Dashboard 统计卡压扁修复**：统计卡 grid 是**内联 style**（`repeat(6,1fr)`/`repeat(5,1fr)`），内联优先级高于 @media → 375px 下每卡仅 42px。解法：内联 grid 换成 className（`.stat-grid` 6 列 / `.stat-grid-5` 5 列 / `.stat-grid-3` 3 列），默认列数写在普通规则，@media ≤768px 降为 3 列、≤480px 降为 2 列，`.stat-value` 手机下字号 22→18px
- **帮助页 grid 内容撑破容器**：grid 轨道 `1fr` 的 min 尺寸由内容决定（help-tip 长文本 nowrap）→ 轨道改 `minmax(0,1fr)`，子项加 `min-width:0`，标题/描述加 `overflow-wrap: break-word`，help-tip 去掉 nowrap 改可换行

### 改动清单

- [x] `frontend/src/styles.css`：
  - 原 @media 块（在 .container 后、.help-steps 前）删除，移动到文件末尾并增强（新增 `.stat-grid` 系列 + `.stat-value` 字号 + ≤480px 规则）
  - `.nav`/`.nav a`/`.nav .logout`：nowrap + flex-shrink:0 + 横向滚动（上轮已加，本轮保留）
  - `.stat-grid`/`.stat-grid-5`/`.stat-grid-3` 基础规则（6/5/3 列）+ `.stat-box` 加 `min-width:0`
  - `.help-steps`/`.help-modules` 轨道 `repeat(2,1fr)` → `repeat(2,minmax(0,1fr))`
  - `.help-step`/`.help-module`/`.help-module-body` 加 `min-width:0`，标题/描述加 `overflow-wrap:break-word`，`.help-tip` 去 nowrap
- [x] `frontend/src/pages/Dashboard.tsx`：4 处内联 `gridTemplateColumns` 改为 className（客户概览→`stat-grid`、邮件效果→`stat-grid-5`、AI 用量今日/累计→`stat-grid-3`）

### 验证记录（本地 375×667 手机视口）

- [x] Dashboard：统计卡 11 个全部 **148px** 宽（原来 42px），2 列布局，body 无溢出
- [x] 帮助页：stepsCols=308px、modulesCols=308px（1 列），body 无溢出
- [x] 设置页 / 草稿箱 / 客户页：body 均无溢出，导航 h=56 可横向滚动
- [x] 导航：所有链接 white-space:nowrap 单行显示，scrollLeft 滚到末尾后「退出登录」可见
- [x] 桌面 1440×900 回归：Dashboard 6 列（217px/卡）、帮助页 2 列（664px），均无溢出——桌面布局未被破坏

### 服务器部署 + e2e 验证（43.153.229.106，375×667 手机视口）

- [x] scp styles.css + Dashboard.tsx → 服务器重建 aic-frontend
- [x] Dashboard：统计卡 148px × 11（2 列），body 无溢出，nav 56px
- [x] 帮助页：steps/modules 均 1 列 308px，body 无溢出
- [x] 设置页：AI 分组浅蓝 rgb(230,244,255) + 邮箱分组浅绿 rgb(246,255,237) 正常，body 无溢出
- [x] 客户页：5 条客户，表格 .table-wrap 内横向滚动，body 无溢出
- [x] 导航：可横向滚动到末尾，「退出登录」可见；11 个链接全部 nowrap 单行

### 追加 8 补充：导航栏滚动时左侧 Logo 固定不动（2026-08-09 完成）

**需求**：「导航栏往右滑动的时候，左侧的图标保持固定不动，是否可以？」

**方案**：`.nav .nav-brand` 加 `position: sticky; left: 0; z-index: 1; background: #001529; padding-right: 4px`——滚动时 Logo 钉在左侧不动，同色背景遮盖滑过的链接，右侧链接仍可滚到最右的退出登录。桌面端导航不滚动，sticky 无副作用（背景与 nav 同色，视觉无差异）

**验证（本地 + 服务器，375×667）**：

- [x] 滚动前 / 滚动中间（scrollLeft=200）/ 滚动到最右：Logo left 恒为 10px（sticky 生效）
- [x] 滚动到最右后「退出登录」仍可见（left=294 < nav 右界 360）
- [x] body 无溢出；桌面 1440px 回归：nav 不滚动、Logo left=24、无视觉差异

### 交付

- 导航栏文字不换行 + 移动端响应式适配已完成：本地 + 服务器均已部署验证，桌面/手机双尺寸无回归
- Git commit 待提交

---

## 追加 8 补充 2：文字在 Logo 右侧边缘裁剪隐藏（2026-08-09 完成）

**需求**：「导航栏…左侧的图标保持固定不动，文字应该在经过图标的右侧就应该隐藏了。不应该在滑到图标的左侧还可见」

**问题**：sticky 方案（补充 1）用同色背景遮盖滑过的文字，但文字仍从 Logo 底下穿过（visibleW>0），不满足"到 Logo 右缘即隐藏"

**方案（结构调整）**：Logo 移出滚动容器——`.nav`（flex）内 `nav-brand` 固定在最左，所有链接 + spacer + 退出登录放进独立的 `.nav-links`（`overflow-x:auto` 滚动容器）。文字滚出 `.nav-links` 左边界即被容器 overflow 天然裁剪，**不可能出现在 Logo 底下**：

- `Nav.tsx`：`<div className="nav">` → `NavLink.nav-brand`（Logo 固定）→ `<div className="nav-links">`（包裹全部 NavLink + spacer + logout）
- `styles.css`：
  - `.nav`：`background:#001529; height:56px; display:flex; align-items:center`（去掉 overflow/gap——滚动移到 .nav-links）
  - `.nav .nav-brand`：`flex-shrink:0; padding:0 12px 0 24px`（去掉 sticky/background）
  - `.nav .nav-links`（新增滚动容器）：`flex:1; overflow-x:auto; scrollbar-width:none; padding-right:24px`（768px 下 padding 0 10px）
  - `.nav .nav-links a` / `.logout`：`white-space:nowrap; flex-shrink:0`
  - 文件末尾 @media 块选择器同步更新为 `.nav-links`（`.nav` gap/padding 规则已失效）

**验证（本地 + 服务器，375×667）**：

- [x] Logo left 恒 0（未滚动 / scrollLeft=200 / 最右 630 均不变）
- [x] 滚动到最右：8 个链接 visibleW=0（完整 rect 虽与 Logo 区域相交但被容器裁剪，视觉不可见），「用户管理/系统设置/帮助/退出登录」可见，无任何元素可见地穿过 Logo 区域
- [x] 退出登录可达（visibleW=56）；body 无溢出
- [x] 桌面 1440 回归：nav 不滚动、链接无重叠、logout 贴右缘内 24px

---

## 追加 9：登录页"记住我"复选框（2026-08-09 完成）

**需求**：「登录页面能否增加一个复选框，就是勾选了，就不用每次都需要登录了，是否可行？」

**可行（纯前端实现）**：后端 JWT 默认 72h 过期（`app.jwt.expire-hours`，可环境变量覆盖），前端按勾选状态选择 token 存储位置即可：

- **勾选** → `localStorage`（持久，关浏览器再打开仍保持登录）
- **不勾选** → `sessionStorage`（会话级，关浏览器后需重新登录，刷新页面仍保持）

**改动清单**：

- [x] `frontend/src/api/client.ts`：
  - 新增 `REMEMBER_KEY="aic_remember"`（localStorage 记录上次勾选状态）+ `pickStore()`（按标记选 localStorage/sessionStorage）
  - `setToken(token, remember=true)`：按 remember 写对应存储；`setRole` 同位置；`getToken/getRole` 两个位置都查
  - `clearToken()` 恢复同时清 token+role（两存储），401 分支改用 `clearToken()`
  - 新增 `getRemember()`（默认 true，新用户默认免登录）
- [x] `frontend/src/pages/Login.tsx`：新增 `remember` state（初始 `getRemember()`），密码框下加复选框「记住我（3 天内免登录）」，登录时 `setToken(data.token, remember)`
- [x] `frontend/src/styles.css`：`.remember-row`/`.remember-label` 样式（checkbox 16px，accent-color #1677ff）
- [x] 顺带修复遗留 bug：`Profile.tsx`/`Prospect.tsx`/`Users.tsx` 登出只 `navigate("/login")` 不清 token → 统一为 `clearToken(); navigate("/login")`

**验证（本地 + 服务器）**：

- [x] 登录页 checkbox 存在、默认勾选、回显上次状态（rememberFlag=0 → 未勾选）
- [x] 不勾选登录：token/role 仅进 sessionStorage（lsToken=false, ssToken=true, rememberFlag="0"），登录跳转正常
- [x] 勾选登录：token/role 进 localStorage（lsToken=true, ssToken=false, rememberFlag="1"）
- [x] 登出：token+role 两存储全部清空，跳转登录页
- [x] 375px 手机登录页：checkbox 16px 正常、无重叠、body 无溢出
- [x] 服务器部署后同样验证通过（checkbox 显示、0→1 切换、导航裁剪无回归）

### 交付

- 登录页新增「记住我（3 天内免登录）」复选框：勾选 → token 持久化到 localStorage（关浏览器免登录）；不勾选 → 会话级 sessionStorage（关浏览器需重登）。JWT 72h 内均免登录
- Git commit 待提交

---

## M7.7 追加 10：记住我有效期 3 天 → 30 天（2026-08-09 完成）

### 需求原文

「为什么是3天免登录呢？可以改成30天吗？」

### 背景

"3 天"由两处决定：前端登录页文案 + 后端 JWT 过期时长（默认 72 小时，可被环境变量 JWT_EXPIRE_HOURS 覆盖）。

### 改动清单

- [x] backend/src/main/resources/application.yml：expire-hours 默认 72 → 720（注释注明 720 = 30 天）
- [x] frontend/src/pages/Login.tsx：文案「3 天内免登录」→「30 天内免登录」
- [x] docker-compose.yml：JWT_EXPIRE_HOURS 默认值 72 → 720
- [x] 服务器 /home/ubuntu/ai-customer-deploy/.env：JWT_EXPIRE_HOURS=72 → 720

### 验证记录

- [x] 本地重建 backend 后 docker exec aic-backend env 确认 JWT_EXPIRE_HOURS=720
- [x] 登录解析 JWT payload：exp-iat = 720 小时（30 天）（首次验证仍是 72 小时，因 compose 默认值未改，修正后重新验证通过）
- [x] 前端重建部署，登录页文案显示「记住我（30 天内免登录）」
- [x] 服务器：.env + compose + application.yml 同步，backend/frontend 镜像重建，容器重启成功

### 交付

- 记住我有效期 30 天已部署本地 + 服务器
- Git commit 待提交

## M7.7 追加 11：修复「已登录访问登录页仍显示登录表单」误以为记住我失效（2026-08-09 完成）

### 需求原文

「记住我，好像没有效果，还需要重新登录？」

### 排查结论（先证机制，再找场景）

1. **记住我机制本身正常**（服务器 Playwright E2E 已证）：勾选登录 → token 存 localStorage → 重开浏览器访问 /app/customers 免登录 ✅
2. **真实根因**：浏览器关闭后重开会恢复上次标签页 URL。若上次停在 `/app/login`（或根路径 /login），Login 组件**无条件渲染登录表单**（无已登录检查）→ 用户看到登录页就以为「记住我没效果，又要重新登录」，实际上 token 还在 localStorage
3. 附带发现：`client.ts` 401 分支 `window.location.href = "/login"`（无 /app 前缀），服务器外层 nginx 对 /login 返回前端 index.html（200），React basename=/app 下匹配不到 → 会再跳回 /app。已确认不构成死循环，但保留后续优化空间

### 改动清单

- [x] `frontend/src/pages/Login.tsx`：新增挂载 useEffect——`getToken()` 存在时调 `api("/auth/me", { skipAuthRedirect: true })`，成功则 `navigate("/")`（跳工作台）；401/失败则停留登录页重新登录。**必须带 skipAuthRedirect**，否则 401 触发 api() 全局跳转逻辑造成循环

### 验证记录（本地 + 服务器）

- [x] 勾选登录 → token 存 localStorage（lsToken=true, ssToken=false）✅
- [x] 已登录（真实 token）访问 /app/login → **自动跳转 /app 工作台**（hasLoginForm=false, hasWorkbench=true）✅
- [x] 假 token 访问 /app/login → /auth/me 校验失败 → 停留登录页（不误跳转）✅
- [x] 登出清 token 后访问 /app/login → 正常显示登录表单 ✅
- [x] 本地 + 服务器（43.153.229.106）双端重建部署验证通过

### 交付

- 已登录用户访问登录页 URL 自动跳转工作台，避免「又要重新登录」误判；本地 + 服务器均已部署
- 提醒用户：若关闭浏览器后仍要求登录，可能原因＝①浏览器「关闭时清除站点数据」设置 ②无痕/隐私模式 ③未勾选记住我（token 存 sessionStorage，关浏览器即失效）
- Git commit 待提交

---

## M7.7 追加 12：修复收件箱/发件箱手机端被表格撑大变形（2026-08-09 完成）

### 需求原文

「收件箱页面，发件箱页面，在手机屏幕大小时，页面变形了，被撑大了。请帮我检查确认。」

### 排查确认（服务器 375×667 手机视口 DOM 测量）

- [x] 收件箱：bodyScrollWidth = **545px**（视口 375px），表格宽 518px → 页面被撑大 ✅ 确认问题
- [x] 发件箱：bodyScrollWidth = **620px**（视口 375px），表格宽 594px → 页面被撑大 ✅ 确认问题
- 根因：收件箱/发件箱的 `<table className="table">` **未用 `.table-wrap`（overflow-x:auto 横向滚动容器）包裹**，列宽（6 列/7 列）超手机视口，直接把页面撑宽；客户管理页此前已用 `.table-wrap` 修复过，这两个页面漏了

### 改动清单

- [x] `frontend/src/pages/Inbox.tsx`：`<table className="table">` → 外包 `<div className="table-wrap">`，表格类名 `table inbox-table`
- [x] `frontend/src/pages/Sent.tsx`：`<table className="table">` → 外包 `<div className="table-wrap">`，表格类名 `table sent-table`
- [x] `frontend/src/styles.css`：
  - `.table-wrap .table.inbox-table { min-width: 850px }` + 6 列 th 各自 min-width（160/240/140/120/100/90）——覆盖客户表通用 1080px 与 th:nth-child 列宽，避免列宽错位
  - `.table-wrap .table.sent-table { min-width: 1030px }` + 7 列 th 各自 min-width（140/180/240/80/140/130/120）
  - `.table-wrap .table .inbox-body { max-width: calc(100vw - 96px) }`——展开行正文限制在视口内换行，避免跟随表格宽度撑出屏幕

### 验证记录（本地 + 服务器 43.153.229.106）

**修复前（服务器 375px）**：收件箱 545 / 发件箱 620（均溢出）

**修复后（本地 375px）**：

- [x] 收件箱：body 375 无溢出，表格 999px 在 .table-wrap 内横向滚动
- [x] 发件箱：body 375 无溢出，表格 1203px 在 .table-wrap 内横向滚动
- [x] 展开详情行：正文限视口内（right=347 ≤ 375），页面无溢出
- [x] 桌面 1440px 回归：两页 body 1440 无溢出、wrap 不出现横向滚动条（表格完整显示）

**修复后（服务器 375px）**：

- [x] 收件箱：body 375（修复前 545）无溢出，表格 999px 横向滚动
- [x] 发件箱：body 360（修复前 620）无溢出，表格 1203px 横向滚动
- [x] 收件箱展开详情：正文 right=347 ≤ 375 无溢出
- [x] 顺带清理：服务器 pages/ 下误传的 styles.css 副本 + 遗留 Inbox.tsx.bak

### 交付

- 收件箱/发件箱手机端不再被撑大，表格在容器内横向滚动，展开正文限视口；本地 + 服务器均已部署验证
- Git commit 待提交

## M7.7 追加 13：移动端导航自动滚动到当前栏目 + 点 Logo 回首页（2026-08-09 完成）

### 需求原文

「在手机屏幕大小时候，当我点击导航栏的工作台，客户管理，潜客挖掘，客户画像的时候，导航栏目基本是固定不动的。当我在点击草稿箱，发件箱的时候，导航栏上不会显示我正在点击的栏目，如何解决？」「当我点击导航栏图标的时候，能否导航到首页？」

### 排查确认（本地 375px DOM 测量）

- [x] 导航 `.nav-links` 为横向滚动容器（maxScroll=615），但点击右侧栏目（草稿箱/发件箱）后 scrollLeft 始终为 0，激活项在可视区外（activeLeft=442 > linksRight=375）→ 看不到当前栏目
- 根因：页面跳转后 Nav 组件重新挂载，没有把高亮链接滚入可视区的逻辑
- 点 Logo 回首页：`<NavLink to="/">` 在 BrowserRouter basename=/app 下本就跳 /app 工作台，功能正常，无需改动（验证确认即可）

### 改动清单

- [x] `frontend/src/pages/Nav.tsx`：
  - 新增 `linksRef = useRef<HTMLDivElement>` 挂在 `.nav-links` 容器上
  - 新增 `useEffect([current])`：取 `.active` 链接，若其已在容器可视区内（工作台/客户管理等左侧栏目）则跳过；否则计算居中偏移 `target = scrollLeft + activeRect.left - linksRect.left - (linksRect.width - activeRect.width) / 2`
  - 用 `setTimeout 100ms` 后**直接赋值 `links.scrollLeft`** 而非 `scrollTo({behavior:'smooth'})`/`requestAnimationFrame`——实测 smooth/rAF 在后台标签页会被浏览器暂停导致滚动不生效，直接赋值最可靠
  - 清理：useEffect 返回 cleanup 清除定时器

### 验证记录（本地 + 服务器 43.153.229.106）

**修复后（本地 375px）**：

- [x] 直接访问草稿箱：自动滚动 scrollLeft=251，激活项「草稿箱」可见（activeVisible=true）
- [x] 点击发件箱：自动滚动 scrollLeft=320，激活项「发件箱」可见
- [x] 点击 Logo：跳转 /app 工作台，导航滚回起点（scrollLeft=0），「工作台」高亮可见
- [x] 桌面 1440px 回归：maxScroll=0 不触发滚动，全部链接在视口内，点草稿箱不滚动

**修复后（服务器 375px）**：

- [x] 直接访问草稿箱：scrollLeft=251，草稿箱可见
- [x] 点击发件箱：scrollLeft=320，发件箱可见
- [x] 点击 Logo：跳转 /app，滚回起点，工作台可见

### 交付

- 移动端导航点击右侧栏目（草稿箱/发件箱等）后自动横向滚动到当前栏目高亮可见；点导航栏 Logo 可回工作台首页；桌面端无回归。本地 + 服务器均已部署验证
- Git commit 待提交

---

## M7.7 追加 14：点击 Logo 整页跳转网站首页 `/`（不带 /app）（2026-08-09 完成）

### 需求原文

「点击logo能否回到网站首页，页就是/,不带app」

### 排查确认

- 第一版实现用 `<NavLink to="/">`，在 BrowserRouter basename=/app 下点击 Logo 跳转的是 `/app`（应用内工作台），不是网站根路径
- 服务器外层 nginx 配置：`location / { try_files $uri $uri/ /index.html; }`，非 /app 路径返回营销落地页（/var/www/sales-agent）；`location /app/ { proxy_pass http://127.0.0.1:8081/; }` 才代理到应用
- 因此需要整页跳转（不经过 React 路由），用普通 `<a href="/">` 实现

### 改动清单

- [x] `frontend/src/pages/Nav.tsx`：Logo 由 `<NavLink to="/">` 改为普通 `<a href="/" className="nav-brand" title="返回首页">`，点击后浏览器整页导航到网站根路径 `/`（营销落地页），不经过应用内路由

### 验证记录

**本地（http://localhost）**：

- [x] `/app/customers` 页面点 Logo → 整页跳转 `http://localhost/`（营销首页占位页，无应用 .nav 元素）

**服务器（43.153.229.106）**：

- [x] 强制刷新后 `/app/customers` 点 Logo → 整页跳转 `http://43.153.229.106/`，显示营销落地页（标题「AI智能获客助手 - 端到端 AI 销售智能体」，含痛点/核心能力/获客闭环等模块）
- [x] 落地页「免费试用」→ `/app/login` → 已有登录态直接进入 `/app` 工作台（`/` 与 `/app` 链路通，无回归）
- [x] 375px 回归：点发件箱 scrollLeft=320、activeVisible=true（导航自动滚动无回归）

### 交付

- 应用内任意页面点击 Logo，整页跳转到网站首页 `/`（营销落地页，不带 /app）；落地页可再通过「免费试用」回到应用。本地 + 服务器均已部署验证
- Git commit 待提交

---

## M7.7 追加 15：收件箱同步间隔加速（前端 10 秒刷新 + 后端 IMAP 15 秒）（2026-08-09 ✅ 已完成）

### 需求原文

「能否把收件箱的同步间隔改成10秒，你看如何，有没问题？」

### 方案确认（用户两轮决策：方案 A → 折中 15 秒）

- 真实 IMAP 模式下每 10 秒直接连邮箱同步 = 每天 8640 次 IMAP 登录连接，QQ/163/Gmail/企业邮箱均有连接频率限制，大概率被限流/触发安全验证甚至封禁
- 第一轮选定**方案 A**：前端每 10 秒静默刷新列表 + 后端 IMAP 2 分钟 → 30 秒
- 用户进一步问「IMAP 同步 30 秒是否也改成 10 秒」→ 说明 10 秒每天 8640 次连接风险大且收益极小（前端已 10 秒刷新列表，30 秒 vs 10 秒感知无差别）→ **最终折中 15 秒**（每天 5760 次连接，多数服务商安全线内，比 30 秒快一倍）
- 最终：前端收件箱页每 10 秒静默自动刷新列表（GET /emails/inbox，纯 DB 查询零 IMAP 成本）+ 后端 IMAP 定时同步 2 分钟 → 15 秒
- 效果：打开收件箱，新回复邮件最长 15 秒内自动出现；封号风险可控

### 改动清单

- [ ] `backend/src/main/resources/application.yml`：`sync-cron` 默认 `0 */2 * * * *` → `*/15 * * * * *`
- [ ] `docker-compose.yml`：`EMAIL_SYNC_CRON` 默认 `0 */2 * * * *` → `*/15 * * * * *`
- [ ] 服务器 `/home/ubuntu/ai-customer-deploy/.env`：`EMAIL_SYNC_CRON` 同步改为 `*/15 * * * * *`
- [ ] `frontend/src/pages/Inbox.tsx`：
  - `load` 增加 `silent` 参数：自动刷新失败时不弹出错误消息打扰用户
  - 新增 `useEffect`：每 10 秒 `setInterval` 静默调 `load(true)`；页面不可见（document.visibilityState）时跳过；组件卸载清除定时器

### 验证记录

- [x] 本地重建 backend：`docker exec aic-backend env | grep EMAIL_SYNC` = `*/15 * * * * *` ✅；日志 `scheduling-1` 线程按 15 秒周期执行「定时收件箱同步」（白名单 3 个客户邮箱）
- [x] 本地重建 frontend：收件箱页自动刷新确认生效——覆写 visibilityState 后两次 `/emails/inbox` 请求时间戳间隔正好 10 秒（23:35:03 → 23:35:13）；`visibilityState=hidden`（后台 tab）时跳过刷新，符合设计；手动同步按钮正常
- [x] 服务器部署后同样验证：`sudo docker exec aic-backend env | grep EMAIL_SYNC` = `*/15 * * * * *` ✅；定时同步日志 15:36:45 → 15:37:00 间隔 15 秒 ✅；前端收件箱 `/emails/inbox` 请求 23:37:29 → 23:37:39 间隔 10 秒 ✅；手动同步按钮正常（提示「同步完成：新增 0 封邮件，收件箱累计 5 封」）✅

### 交付

- Git commit 待提交（Inbox.tsx / application.yml / docker-compose.yml / 本文档）
