# M7.14 收件箱页面"系统繁忙"修复（lead_id 全空 NPE）

## 状态

✅ 已完成（2026-08-11）

## 需求原文

> 为什么在服务器上收件箱页面上有系统繁忙，请稍后重试？

## 根因分析

- **现象**：服务器（43.153.229.106）登录后打开收件箱页 → `GET /api/emails/inbox` 返回 500「系统繁忙，请稍后重试」
- **复现**：带 JWT 调服务器接口 → 500；后端日志堆栈：
  ```
  java.lang.NullPointerException
   at java.base/java.util.Objects.requireNonNull
   at java.base/java.util.ImmutableCollections$MapN.get
   at com.aicustomer.service.EmailInboxService.lambda$list$1(EmailInboxService.java:145)
  ```
- **根因**（`EmailInboxService.list()` 第 175-178 行）：
  ```java
  Map<Long, Lead> leads = leadIds.isEmpty() ? Map.of()   // ← Map.of() 不允许 null key！
          : leadRepository.findAllById(leadIds)...;
  return result.map(e -> toView(e, leads.get(e.getLeadId())));  // lead_id=null → get(null) → NPE
  ```
  服务器收件箱 5 封邮件 `lead_id` 全部为 NULL（演示 mock 邮件，无关联客户）→ `leadIds` 空集 → `Map.of()` → `leads.get(null)` 抛 NPE → 500
- **本地为何不报错**：本地 Docker 收件箱有 3 封真实邮件关联了客户（lead_id 非空）→ 走 `findAllById` 分支（`Collectors.toMap` 返回 HashMap 允许 null key，get(null) 返回 null）
- **同款 bug 对照**：m7-7 追加 5 已修复发件箱 `EmailSendLogService.listAll()` 同款 NPE，但当时**漏修了收件箱 `EmailInboxService.list()`**

## 设计决策

- 与 m7-7 追加 5 发件箱修复同模式：`leadIds` 为空时用 `new HashMap<>()`（允许 null key），仅在非空时 putAll 查询结果
- 该场景（无关联客户的邮件）在演示模式/客户删除后是**常态**，不是异常

## 改动清单

- [x] `backend/src/main/java/com/aicustomer/service/EmailInboxService.java`：`list()` 中 `Map.of()` → `new HashMap<>()` + putAll
- [x] 本地 `mvn compile` 验证
- [x] 部署：scp 改动 → 服务器重建后端镜像 → 重启 aic-backend
- [x] 服务器验证：带 JWT `GET /api/emails/inbox` → 200 返回 5 封邮件
- [x] 更新任务文档 + Git 提交

## 验证记录

- [x] 修复前：服务器 `GET /api/emails/inbox` → 500「系统繁忙，请稍后重试」
- [x] 修复后：服务器接口 200，5 封邮件正常返回（leadId=null 不再 NPE）
- [x] 本地回归：收件箱列表/筛选/详情正常
- [x] 服务器后端日志无新 NPE（`grep -c NullPointerException` = 0）
- [x] E2E（https://sales-agent.top/app/inbox）：5 封邮件正常渲染（张三/王五/李雷/GlobalSoft/张三），无「系统繁忙」提示
- [x] E2E 布局（DOM 测量）：表格在 .table-wrap 内正常横向滚动（设计行为），无页面级横向滚动条、无元素重叠、无溢出视口
- [x] E2E 功能：详情展开、标记未读/已读切换（闭环生效）、AI 分析弹窗、同步邮件（「同步完成：新增 0 封，累计 5 封」）均正常

## 交付

- 后端修复已部署（服务器重建镜像 + 重启 aic-backend），收件箱页面恢复可用
- 部署方式：本地打包源码 → 上传 /tmp/ → 解压覆盖服务器源码（含 m7-7 白名单/批量同步等全部最新改动）→ `docker compose build backend` + `up -d --no-deps backend`
- 服务器源码已备份：/tmp/backend-src-backup-20260811
- Git commit 已提交
