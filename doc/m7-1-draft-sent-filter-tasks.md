# M7.1 任务清单：客户详情弹窗「邮件草稿」隐藏已发送邮件

> 状态：✅ 已完成（2026-08-09）
> 需求来源：用户「里面的邮件可能已经发送了，但是还是显示：邮件草稿，这个怎么处理？」

---

## 一、需求原文

1. 客户详情弹窗（「📋 跟进」按钮弹出）中的「邮件草稿」区块，已 SMTP 发送成功的邮件（status=`sent`）仍然显示在该区块中。
2. 用户认为"已经发出去的邮件还躺在草稿里"不符合直觉，需要处理。

---

## 二、排查结论

- **根因：前后端不一致**。M6 拆分草稿箱/发件箱时，全局草稿箱接口 `EmailDraftService.listAll()` 已默认只查 `draft` + `confirmed`（未指定状态时），已发送的 `sent` 由发件箱 `email_send_log` 展示；
- 但客户详情弹窗使用的 `EmailDraftService.listByLead()` 用的是 `findByLeadIdOrderByCreatedAtDesc(leadId)`，**不过滤状态** → `sent` 草稿也返回，前端「邮件草稿」区出现"已发送"行（此时发送/标记按钮都隐藏，只剩已发送 badge + 删除按钮，无操作价值，且与下方「发送记录」重复展示同一封邮件）。
- `listByLead` 全代码库仅 `EmailDraftController.list` 一处调用（客户详情弹窗），改动无副作用。

---

## 三、设计决策

| 项   | 决策                                                                                                                                                            |
| :--- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 方案 | **后端过滤（A 方案）**：`listByLead` 只查 `draft` + `confirmed`，与全局草稿箱完全对齐。语义：**草稿 = 未发送**，已发送的归「发送记录」区展示（含打开/点击反馈） |
| 前端 | 零改动（空态文案「暂无邮件草稿…」依然适用）                                                                                                                     |
| 备选 | B 前端移除（刷新复活，否决）；C 置灰弱化（不符直觉，否决）                                                                                                      |

---

## 四、改动清单

| 文件                                               | 改动                                                                                            |
| :------------------------------------------------- | :---------------------------------------------------------------------------------------------- |
| `backend/.../repository/EmailDraftRepository.java` | 新增 `findByLeadIdAndStatusInOrderByCreatedAtDesc(leadId, List<String> statuses)`（或类似方法） |
| `backend/.../service/EmailDraftService.java`       | `listByLead` 改为只查 `draft` + `confirmed`；注释说明与 `listAll` 对齐                          |
| `backend/.../controller/EmailDraftController.java` | 无需改动（list 已调用 listByLead）                                                              |
| `doc/m7-1-draft-sent-filter-tasks.md`              | 本任务文档                                                                                      |

---

## 五、验证记录

1. ✅ 接口级：`GET /api/leads/5/email-drafts` 修复前返回 6 条 `sent` 草稿 → 修复后返回 **0 条**（测试客户A 的 6 条已发送草稿：推进试点方案确认/重试测试邮件/UI重试测试/B1/B2/B3 全部被过滤）
2. ✅ 不误伤：临时插入 1 条 `draft` 状态草稿（id=27）→ 接口正常返回 1 条；验证后已删除
3. ✅ 发送记录不受影响：`GET /api/leads/5/email-send-logs` 返回 8 条（sent/failed/重试按钮齐全）
4. ✅ 前端页面级（浏览器 E2E）：客户管理 → 测试客户A →「📋 跟进」弹窗：
   - 「邮件草稿」区显示"暂无邮件草稿，可先「✉ 邮件」AI 生成后保存"（修复前此处显示 6 条已发送邮件）
   - 「发送记录」区 8 条完整显示（✅已发送/失败原因/↻重试）
5. ✅ DOM 测量：modal 640×554 居中，页面无横向溢出（docScrollWidth 794≤809），modal 无上下溢出，内部滚动容器正常（跟进 468/发送记录 341 在 maxHeight 内），**10 个按钮零重叠**
6. ✅ 全局草稿箱 `/app/drafts`：正常显示"暂无草稿"，状态筛选仍为「草稿/待发」（无已发送，M6 语义不变）

## 六、交付

- 线上已部署：backend 镜像 `ai-customer-deploy-backend:latest` 重建（M7.1 改动），`aic-backend` 容器双网络（ai-customer-deploy_default + ai-customer_default），健康检查 200
- 前端零改动，无需重建
- Git：待提交推送
