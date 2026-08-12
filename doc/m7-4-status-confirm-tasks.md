# M7.4 任务清单：客户管理状态变更操作增加确认提示

> 状态：✅ 已完成（2026-08-09）
> 需求来源：用户「客户管理里页面中，状态变化的操作，是否可以都要弹出提示框，让用户再次确认。否则改了状态，改不回来了。」

---

## 一、需求原文

1. 客户管理页面中，所有"状态变化"的操作都要弹出提示框让用户再次确认。
2. 原因：状态流转是单向的（`new → contacted → interested → converted/invalid`），`converted`（已转化）和 `invalid`（无效）是**终态**，一旦误改**无法回退**。

---

## 二、排查结论

- 后端 `LeadService.STATUS_TRANSITIONS`：`converted`/`invalid` 均为 `List.of()`（无任何可转目标）→ **不可回退**，与前端 `STATUS_NEXT` 一致。
- 客户管理页面的状态变化操作共 2 处，目前均**无确认弹窗**：
  1. 客户列表「状态」下拉框 → `changeStatus(lead, status)`（改成终态不可回退，风险最高）
  2. 客户详情弹窗「邮件草稿」区 → `toggleDraftStatus(draft)`（draft ↔ confirmed 可逆，但也是状态变化）
- 其他操作（删除客户/草稿/跟进、重试发送、SMTP 发送）已有 `confirmDialog` 确认。
- 已有可复用的应用内确认弹层 `confirmDialog`（utils/dialog.tsx，非原生 window.confirm）。

---

## 三、设计决策

| 项   | 决策                                                                                                                           |
| :--- | :----------------------------------------------------------------------------------------------------------------------------- |
| 方案 | 两处状态变化均加 `confirmDialog`：                                                                                             |
|      | ① `changeStatus`：改为**终态**（converted/invalid）时弹「终态变更确认」（danger 红字 + ⚠️ 不可回退提示）；改为中间态时普通确认 |
|      | ② `toggleDraftStatus`：改为「待发/草稿」普通确认                                                                               |
| 细节 | 取消确认后需把下拉框还原为原状态（`setLeads` 重渲染强制 select 回到原值，避免"显示新值但实际没改"的假象）                      |
| 样式 | `.dialog-body` 加 `white-space: pre-wrap`，支持确认文案中 `\n` 换行                                                            |
| 后端 | 零改动                                                                                                                         |

---

## 四、改动清单

- [x] `frontend/src/pages/Customers.tsx`：`changeStatus` 加确认（终态 danger 提示 + 取消还原 select）；`toggleDraftStatus` 加确认
- [x] `frontend/src/styles.css`：`.dialog-body` 加 `white-space: pre-wrap`
- [x] 构建 + 部署前端（服务器 node:22-alpine 构建镜像，rm -f aic-frontend → up -d，bundle `index-_GGErh43.js`）
- [x] E2E：全部关键分支验证通过（见下）

---

## 五、验证记录（E2E 全过）

**1. 终态变更确认（导入测试公司 new → invalid）**

- 弹窗：标题「终态变更确认」（danger 红字）+ 文案「确认将客户「导入测试公司」状态从「新线索」改为「无效」？\n\n⚠️ 该状态为终态，变更后不可回退。」+ 取消/确认变更按钮 ✅
- 取消分支：点取消 → 下拉还原为「新线索」、badge 新线索、弹窗关闭 ✅
- 确认分支：点确认变更 → 状态变为「无效」、下拉禁用（终态）、badge 无效 ✅

**2. 中间态确认（云启软件 new → contacted，数澜科技 new → contacted）**

- 弹窗：标题「状态变更确认」（普通样式，无 danger）+ 文案「确认将客户「云启软件」状态从「新线索」改为「已触达」？」✅
- 取消分支（数澜科技）：点取消 → 保持 new ✅
- 确认分支（云启软件）：点确认变更 → 变为「已触达」，下拉可继续流转（有意向/无效）✅

**3. 邮件草稿流转确认（测试客户A 详情弹窗，临时插入草稿测试）**

- 「✓ 标记待发」→ 弹「操作确认」：文案「确认将邮件「…」标记为「待发」？」→ 取消保持草稿 / 确定变待发（按钮变 ✉ 发送/↩ 改回草稿）✅
- 「↩ 改回草稿」→ 弹「操作确认」：文案「标记为「草稿」？」→ 确定后恢复草稿 ✅

**4. 终态客户 select 禁用（测试客户A converted）**

- 下拉 disabled，无法触发状态变更（天然无确认弹窗，符合预期）✅

**5. DOM 布局测量（禁截图，getBoundingClientRect）**

- 确认弹窗 `dialog-card` 380px 居中（中点=容器中点）、完全在视口内零溢出；标题/正文/按钮零重叠；body 44px 高（pre-wrap 换行生效）✅
- 客户列表在 `.table-wrap`（overflow-x:auto）内横向滚动属正常设计；`body.scrollWidth 794 < 809` 页面零溢出；按钮零重叠 ✅

**6. 测试数据**：验证后已还原（导入测试公司/云启软件/数澜科技 = new；测试客户A = converted；测试草稿已删除）✅

---

## 六、交付

- 前端改动 2 文件：`frontend/src/pages/Customers.tsx`（changeStatus/toggleDraftStatus 加确认弹窗）、`frontend/src/styles.css`（.dialog-body pre-wrap）
- 后端零改动；DB 零 schema 改动
- 已部署线上：aic-frontend 镜像重建，bundle `index-_GGErh43.js`，`/app/customers` 200
- E2E 全部关键分支验证通过（终态确认/取消、中间态确认/取消、草稿流转双向、终态禁用、DOM 零溢出零重叠）
- Git 提交推送待做（本次任务 md 一并提交）
