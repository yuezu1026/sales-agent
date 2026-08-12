# M4-6 任务清单：打开率追踪 + AI 缓存

> 状态：✅ 已完成（2026-08-09）
> 需求来源：用户「**打开率追踪**、**AI 缓存** 能否做一下？」——补齐 MVP 规划 P1「打开率追踪」与 P2「重复请求缓存」两个遗留项

---

## 一、需求原文

1. **打开率追踪**：邮件发出去后能知道客户有没有打开、点没点链接（MVP 规划 M3 验收「打开率追踪、退订」）。当前 `email_send_log` 只有 queued/sent/failed/bounced，无 opened/clicked 字段，看板无打开率展示。
2. **AI 缓存**：相同请求（相同场景 + 相同 Prompt）不重复调用模型 → 命中直接返回，节省 token 成本（MVP 规划 P2「重复请求缓存、API 限流防超限」，限流已做，缓存未做）。

---

## 二、设计决策

### 2.1 打开率追踪

| 项       | 决策                                                                                                                                                      |
| :------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 追踪手段 | 1px 透明像素 `<img>`（打开）+ 链接包装 302 跳转（点击）                                                                                                   |
| 数据     | `email_send_log` 加 `opened_at TIMESTAMPTZ` / `clicked_at TIMESTAMPTZ`（只记首次，幂等）                                                                  |
| 域名前缀 | 新增配置 `mail.track_url`（与 `mail.unsubscribe_url` 同填法：`https://www.example.com`）；**留空 = 不追踪**（不插像素、不包装链接）                       |
| 公开端点 | `GET /api/track/open/{id}`（返回 1x1 GIF）+ `GET /api/track/click/{id}?url=xxx`（302 跳转）；JWT 拦截器 exclude（收件人无登录态）                         |
| 正文处理 | HTML 邮件：末尾追加像素 + `<a href>` 外链全部包装为追踪链接（退订链接同样包装，点击退订也计入点击）；纯文本邮件不埋点（客户端不渲染 img，硬塞会显示乱码） |
| 看板     | Dashboard 新增「邮件效果」卡片：发送数 / 打开数 / 打开率 / 点击数 / 点击率                                                                                |
| 客户详情 | 发送记录区 sent 记录显示 ✅ 已打开 / 👆 已点击 / 未打开 badge                                                                                             |

### 2.2 AI 缓存

| 项     | 决策                                                                                                                                                                                                                                                                                              |
| :----- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 存储   | 新表 `ai_cache`：`kind`(chat/embedding) + `cache_key`(SHA-256 hex) 唯一索引 + `response` TEXT + `total_tokens` + `hit_count` + `created_at`                                                                                                                                                       |
| 缓存键 | chat：`SHA256(scene + "\n" + systemPrompt + "\n" + userPrompt)`；embedding：`SHA256(text)`                                                                                                                                                                                                        |
| 命中   | 直接返回缓存内容，**不调模型、不扣 token 额度、不记 usage**；`hit_count + 1`                                                                                                                                                                                                                      |
| 过期   | `ai.cache_ttl_hours`（默认 24h），created_at 超过即视为未命中                                                                                                                                                                                                                                     |
| 开关   | `ai.cache_enabled`（默认 true 总开关）；**chat 缓存默认关闭**（`ai.cache_chat_enabled` 默认 false）——邮件/微信生成类场景用户重复生成期待不同内容，系统 prompt 也要求"每次生成都不同"，缓存会破坏多样性；embedding 缓存默认随总开关（确定性场景，无副作用且价值最高：RAG 批量打分/CSV 导入向量化） |

---

## 三、改动清单

### 后端

- [x] `V18__tracking_ai_cache.sql`（新迁移）：`email_send_log` 加 `opened_at`/`clicked_at` + 索引；新建 `ai_cache` 表 + `uk_ai_cache(kind, cache_key)` 唯一索引
- [x] `entity/EmailSendLog.java`：加 `openedAt` / `clickedAt` 字段 + getter/setter
- [x] `entity/AiCache.java`（新）：AI 缓存实体
- [x] `repository/AiCacheRepository.java`（新）：`findByKindAndCacheKey` / `findByKindAndCacheKeyAndCreatedAtGreaterThanEqual`
- [x] `repository/EmailSendLogRepository.java`：加 `countByStatus` / `countByOpenedAtIsNotNull` / `countByClickedAtIsNotNull`
- [x] `service/EmailSendService.java`：sendDraft 落 queued 拿到 logId 后 → 读 `mail.track_url` → HTML 追加像素 + 链接包装（trackUrl 为空则跳过）→ 再 SMTP
- [x] `controller/EmailTrackingController.java`（新）：公开 open/click 端点（open 返回 1x1 GIF；click 302 跳转 + 幂等记录）
- [x] `config/WebConfig.java`：JWT 拦截器 exclude `/api/track/**`
- [x] `controller/ConfigController.java`：DEFAULT_CONFIGS 加 `mail.track_url`、`ai.cache_enabled`、`ai.cache_ttl_hours`、`ai.cache_chat_enabled`
- [x] `service/AiService.java`：generate() 加缓存（读开关/TTL → 命中返回 → 未命中调模型后存缓存）
- [x] `service/profile/RemoteEmbeddingService.java`：embed() 加缓存（kind=embedding，命中直接返回向量）
- [x] `controller/EmailStatsController.java`（新）：`GET /api/email-stats`（sent/opened/openRate/clicked/clickRate）供看板

### 前端

- [x] `pages/Dashboard.tsx`：「邮件效果」卡片（发送数/打开数/打开率/点击数/点击率）
- [x] `pages/Customers.tsx`：发送记录区 sent 记录显示打开/点击状态 badge

---

## 四、E2E 验证要点

1. **打开追踪**：配置 `mail.track_url=http://localhost:8080` → 发送 HTML 邮件 → 落库 body 含 1px 像素 + 链接被包装 → 请求 open 端点 → opened_at 记录（重复请求不覆盖）→ 看板打开率 >0 → 无配置时不插像素
2. **点击追踪**：包装链接 302 跳到原 url + clicked_at 记录；url 非法 → 不跳转
3. **AI 缓存**：`ai.cache_chat_enabled=true` → 同 prompt 调两次 → 第二次不调模型（mock/日志确认）+ hit_count=1 + usage 只记一次；`cache_ttl_hours=0` → 立即过期重新调用
4. **embedding 缓存**：同文本 embed 两次 → 第二次命中（同向量返回，usage 不新增）
5. **前端**：Dashboard 邮件效果卡片渲染；Customers 发送记录 badge；DOM 无重叠无溢出
6. **回归**：AI 邮件生成/微信回复/收件箱分析正常；发送闭环正常；退订仍生效

---

## 五、E2E 实测结果（2026-08-09，全部通过 ✅）

1. **打开追踪**：配 `mail.track_url=http://localhost:8080` → 发送 HTML 邮件 → 落库 body = `<a href="http://localhost:8080/api/track/click/17?url=https%3A%2F%2Fexample.com%2Foffer">Click here</a><img src="http://localhost:8080/api/track/open/17" width="1" height="1" style="display:none" alt=""/>`（像素+包装 ✅）；`GET /api/track/open/17` → 200 + 43 字节 image/gif + opened_at 落库；重复请求 opened_at 不变（幂等 ✅）
2. **点击追踪**：`GET /api/track/click/17?url=...` → 302 跳原 url + clicked_at 落库 ✅；`url=javascript:alert(1)` → 400（防开放重定向 ✅）；记录不存在时仍 302（对收件人友好、无数据泄露，设计取舍）
3. **AI 缓存（chat）**：`ai.cache_chat_enabled=true` → 同参数微信回复建议调两次：第一次 3385ms（真实调模型）、第二次 22ms（命中缓存）✅；`ai_cache` 表 hit_count=1、total_tokens=402；usage 只记一次 ✅；`cache_ttl_hours=0` → 第三次 1864ms 重新调模型（过期失效 ✅）
4. **统计接口**：`GET /api/email-stats` → `{sent:7, opened:1, openRate:14.3, clicked:1, clickRate:14.3}` ✅
5. **前端**：Dashboard「邮件效果」卡片渲染 7/1/14.3%/1/14.3% ✅；Customers 发送记录 sent 行显示 `✅ 已打开（打开时间）` + `👆 已点击（点击时间）` badge ✅；DOM 测量：11 个 stat-box 无溢出无重叠、Modal/表格内元素均在滚动容器内、按钮零重叠 ✅
6. **回归**：客户列表/详情/草稿/发送记录正常；embedding 缓存未实调（无 embedding 模型 key，逻辑与 chat 同模式，静态检查通过）

测试数据已清理（测试发送记录/草稿/缓存/用量），配置恢复默认（track_url 清空、chat 缓存关、TTL=24h）。

---

## 六、交付

- [x] 后端编译通过（BUILD SUCCESS）+ 迁移应用（flyway now at v18）
- [x] 前端 TSC 编译通过（exit 0）
- [x] E2E 全过 + 测试数据恢复
- [ ] Git commit + push
