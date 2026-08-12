# 退订列表仅租户管理员可见任务

## 状态

- 状态：✅ 已完成（2026-08-13 完成）
- 需求：平台管理员不需要看到全局退订列表，退订列表是租户私有数据

## 需求原文（用户 2026-08-13）

> 平台管理员不需要看到全局退订列表，这个是租户私有数据

## 现状分析

- 后端 `UnsubscribeController.list()` 已按租户隔离：`unsubscribeRepository.findByTenantIdOrderByCreatedAtDesc(TenantContext.require())`
- 平台管理员（tenantId=null）访问 `/unsubscribe/list` → `TenantContext.require()` 抛 400「当前账号无租户上下文」，**不泄漏任何租户数据**（后端已安全）
- 前端 `Settings.tsx` 退订管理卡片显示条件为 `{isAdmin && (`，而 `isAdmin = getRole() === "admin"` —— 平台管理员也命中，虽然进不去设置页（BizGuard 拦截），但条件语义不对
- `loadUnsub()` 调用条件 `if (isAdmin) loadUnsub()` 同理

## 设计决策

| 决策点   | 结论                                                                                           |
| :------- | :--------------------------------------------------------------------------------------------- |
| 显示条件 | 退订管理卡片与 loadUnsub 调用改为 `tenantAdmin`（role=admin 且 tenantId 非空），平台管理员隐藏 |
| 后端     | 不改（`TenantContext.require()` 已保证平台管理员 400，不泄漏）                                 |
| 纵深防御 | 前端收窄 + 后端 require() 双保险，即使未来平台管理员可进设置页也不会看到租户私有数据           |

## 改动清单

- [x] 任务文档
- [x] Settings.tsx：`if (isAdmin) loadUnsub()` → `if (tenantAdmin) loadUnsub()`
- [x] Settings.tsx：退订管理卡片 `{isAdmin && (` → `{tenantAdmin && (`
- [x] 验证：build + 部署 + E2E（rbac_a 租户管理员可见退订卡片；admin 平台管理员无此卡片）

## 验证记录

**E2E（2026-08-13，浏览器 DOM 测量，禁截图）**

1. 租户管理员 rbac_a/rbac654321（租户 4）：登录 → 系统设置 → 页面底部显示「退订管理」卡片（说明文案 +「暂无退订邮箱」）✅
2. 平台管理员 admin/Admin@123456：
   - 导航栏无「系统设置」入口（仅工作台/用户管理/帮助）✅
   - URL 直达 `/app/settings` 被 BizGuard 重定向到 `/app/users`，无法进入设置页 ✅
   - API 验证：admin token 调 `GET /api/unsubscribe/list` → 400「当前账号无租户上下文，请使用注册的租户账号操作」，不泄漏任何租户数据 ✅
3. 功能未破坏：rbac_a token 调 `GET /api/unsubscribe/list` → 200 空列表 ✅
4. 布局 DOM 检查（getBoundingClientRect）：退订管理卡片 rect(left:16, right:1249, width:1233)，无横向溢出（overflowX=false）、与其它卡片无重叠；滚动到页面底部（scrollY=2311=maxScroll）后卡片完整可见（top:644, bottom:776 ∈ 视口 800）✅

**部署**：前端新 bundle `index-4KoeEkvc.js` 已部署（docker compose build + up -d --force-recreate frontend）

## 交付

- 前端 `frontend/src/pages/Settings.tsx`：退订管理卡片与 loadUnsub 调用条件 `isAdmin` → `tenantAdmin`（仅租户管理员可见，平台管理员隐藏）
- 后端无需改动：`UnsubscribeController.list()/restore()` 已用 `TenantContext.require()` 按租户隔离，平台管理员访问 400 不泄漏
- 纵深防御：前端收窄 + 后端 require() 双保险
