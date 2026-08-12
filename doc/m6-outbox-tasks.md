# M6 任务清单：草稿箱拆分为「草稿箱」+「发件箱」

> 状态：✅ 已完成（2026-08-09）
> 需求来源：用户「草稿箱，是否要改成发件箱，因为里面的数据不仅仅只有草稿，还包括已经发送的邮件记录？」→ 确认采用方案 B（拆分两个视图）

---

## 一、需求原文

1. 草稿箱页面的数据目前混有草稿（draft/confirmed）与已发送（sent）的邮件记录，名不副实。
2. 确认是否改为「发件箱」→ 最终决策：**拆成「草稿箱」+「发件箱」两个视图**（方案 B）。

---

## 二、排查结论

### 2.1 数据现状

| 表                                  | 内容                         | 状态枚举                                                                                                      |
| :---------------------------------- | :--------------------------- | :------------------------------------------------------------------------------------------------------------ |
| `email_draft`（现"草稿箱"页面展示） | AI 生成的邮件内容            | `draft` 草稿 / `confirmed` 待发 / `sent` 已发送                                                               |
| `email_send_log`（V10，发送闭环）   | 每次 SMTP 实际发送的完整日志 | `queued` 排队 / `sent` 成功 / `failed` 失败 / `bounced` 退信；含 `opened_at`/`clicked_at` 打开点击追踪（V18） |
| `email_inbox`（收件箱）             | 客户回复的邮件               | —                                                                                                             |

### 2.2 问题

- "草稿箱"页面混入已发送记录（筛选下拉已有"已发送"），与页面名不符。
- 已发送邮件的**打开/点击追踪**（M4-6）目前只在客户详情可见，无全局视角。

### 2.3 方案对比

- 方案 A 纯改名"发件箱"：草稿/待发混在"发件箱"里依然名不副实，否决。
- **方案 B 拆分两个视图（采用）**：草稿箱只含 draft/confirmed；发件箱直接展示 `email_send_log`（更完整：发送时间/失败原因/打开点击追踪）。
- 方案 C 保持现状：否决。

---

## 三、设计决策

| 项         | 决策                                                                                                                                                                                                   |
| :--------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 草稿箱     | `email-drafts` 接口默认过滤 `sent`（status 为空时查 `draft`+`confirmed`），前端筛选项去掉"已发送"                                                                                                      |
| 发件箱     | 新页面 `/sent`，数据源 `email_send_log` 全局视图（跨客户分页/关键词/状态筛选），直接展示发送时间、失败原因、打开/点击追踪                                                                              |
| 后端接口   | 新增 `EmailSendLogService` + `EmailSendLogGlobalController`：`GET /api/email-send-logs`（分页视图）、`DELETE /api/email-send-logs/{id}`；重试复用现有 `/api/leads/{leadId}/email-send-logs/{id}/retry` |
| 发件箱视图 | 表格列：客户/收件人/主题/状态/发送时间/追踪/操作；展开行显示正文+错误原因；操作含重试（仅 failed）、删除（danger 确认弹层）                                                                            |
| 前端路由   | `App.tsx` 加 `/sent`；`Nav.tsx` 草稿箱后加"发件箱"链接                                                                                                                                                 |
| 数据库     | 无需迁移                                                                                                                                                                                               |

---

## 四、改动清单

| 文件                                                       | 改动                                    |
| :--------------------------------------------------------- | :-------------------------------------- |
| `backend/.../service/EmailSendLogService.java`             | 新建：全局发送记录分页视图 + 删除       |
| `backend/.../controller/EmailSendLogGlobalController.java` | 新建：GET/DELETE `/api/email-send-logs` |
| `backend/.../service/EmailDraftService.java`               | `listAll` 默认过滤 `sent`               |
| `frontend/src/pages/Sent.tsx`                              | 新建：发件箱页面                        |
| `frontend/src/pages/Drafts.tsx`                            | 筛选项去掉"已发送"                      |
| `frontend/src/App.tsx`                                     | 加 `/sent` 路由                         |
| `frontend/src/pages/Nav.tsx`                               | 加"发件箱"导航链接                      |
| `doc/m6-outbox-tasks.md`                                   | 本任务文档                              |

---

## 五、验证记录

- [x] 本地后端编译通过（`mvn package -DskipTests` 静默通过）
- [x] 本地 `npm run build` 通过（49 modules，本地 bundle `index-CHeHqevu.js` 279.56 kB）
- [x] 服务器部署后：草稿箱不再出现 sent 记录（DB 中 6 条 sent 全部被过滤，`/api/email-drafts` total=0；下拉仅 全部/草稿/待发）
- [x] 发件箱：列表展示（11 条 2 页→删 1 条后 10 条）、关键词筛选（"合作邀约"→1 条）、状态筛选（"失败"→3 条）、展开正文（发件人/收件人/时间/打开/点击 + HTML 正文渲染）、删除（danger 确认弹层"操作确认"：确定→删除成功+toast"发送记录已删除"；取消→记录保留）、重试按钮仅 failed 显示（sent 行无）
- [x] 布局检查：发件箱表格宽 726px < 视口 809px、无水平溢出；删除确认弹层 380px 居中无越界（getBoundingClientRect 测量）

---

## 六、交付

- 后端新增 `GET /api/email-send-logs`（分页/关键词/状态筛选视图）+ `DELETE /api/email-send-logs/{id}`；重试复用现有 `/api/leads/{leadId}/email-send-logs/{id}/retry`
- 前端新增「发件箱」页面（`/sent`），展示 `email_send_log` 全局记录：客户/收件人/主题/状态/发送时间/追踪（👁 已打开 🖱 已点击）/操作（重试仅 failed、删除 danger 确认）；支持展开正文、关键词/状态筛选、分页
- 「草稿箱」只保留 draft/confirmed，副标题提示已发送记录见「发件箱」
- 已部署至服务器 43.153.229.106：后端镜像 `c5aba7415dbd`（健康 200）、前端镜像 `234fa3bd1ca3`（bundle `index-DIamSlKi.js` 含"发件箱"/"email-send-logs"标记）；外层 nginx root/app 200、api 401（未认证属预期）
- E2E 测试数据（3 条）已清理；历史遗留测试记录（id 3-10：B1/B2/B3 placeholder、UI重试测试、重试测试邮件等）仍在发件箱中，如需清理可删除
