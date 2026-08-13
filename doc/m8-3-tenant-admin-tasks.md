# M8.3 任务清单：租户管理（列表展示，仅系统管理员）

> 状态：✅ 已完成（2026-08-13）
> 需求来源：用户「超级管理员登录进去，应该有个租户管理功能？你觉得呢？ 目前好像没有。」→ 确认方案「暂时不停用租户的功能，目前功能免费试用，仅仅显示租户的列表信息」

---

## 〇、需求原文

1. 超级管理员登录后应该有「租户管理」功能（当前缺失）
2. **不做**租户停用/启用（免费试用阶段，仅展示列表）

## 一、设计决策

| 项       | 决策                                                                                                                          |
| :------- | :---------------------------------------------------------------------------------------------------------------------------- |
| 范围     | 仅只读列表：租户名 / 租户管理员 / 套餐 / 状态 / 用户数 / 创建时间 / 到期时间；无任何操作按钮                                  |
| 后端     | 新建 `TenantController`（`GET /api/tenants`），`requireSystemAdmin` 鉴权（平台级），返回 `TenantVO` 列表                      |
| 用户数   | `UserRepository` 加一次性聚合查询（`group by tenant_id`），避免 N+1                                                           |
| 管理员名 | `ownerUserId` 批量查用户名（`findAllById`），容忍 owner 为 null / 用户已删除                                                  |
| 前端     | 新建 `Tenants.tsx` 只读表格页；Nav 系统管理员分支加「租户管理」；App.tsx 注册 `/tenants` 路由（放 BizGuard 外，页面内自校验） |
| 菜单位置 | 系统管理员导航：工作台 → 用户管理 → **租户管理** → 捐助 → 帮助                                                                |

## 二、改动清单

- [x] `backend/src/main/java/com/aicustomer/repository/UserRepository.java`：加 `countGroupByTenantId()` 聚合查询
- [x] `backend/src/main/java/com/aicustomer/controller/TenantController.java`：新建，`GET /api/tenants` 列表（仅系统管理员）
- [x] `frontend/src/pages/Tenants.tsx`：新建，只读租户列表页
- [x] `frontend/src/pages/Nav.tsx`：系统管理员分支加「租户管理」菜单
- [x] `frontend/src/App.tsx`：lazy 引入 + `/tenants` 路由

## 三、验证记录

- [x] 后端编译：`mvn compile -o` BUILD SUCCESS
- [x] 前端编译：`tsc --noEmit` exit 0；`vite build` exit 0（Tenants chunk 1.7KB 已生成）
- [x] 部署：服务器 `43.153.229.106`，scp 源码 → build backend/frontend → up -d --no-deps（2026-08-13）
- [x] **线上 NPE 修复**：首版 `TenantController` 用 `Map.of()` 存 ownerNames，`get(null)`（ownerUserId 为 NULL 的历史租户「默认租户」）抛 NPE → 500。改为 `HashMap` 后正常
- [x] E2E（线上 sales-agent.top 实测）：
  - admin 登录 → 导航有「租户管理」菜单，列表展示「默认租户」（ID/名称/管理员/套餐/状态/用户数/创建时间/到期时间）
  - 权限矩阵：无 token → 401；普通用户（op_e2e）token → 403「无权限，仅系统管理员可操作」；admin → 200
  - 普通用户导航无「租户管理」菜单；手动访问 `/app/tenants` → 重定向到工作台
  - 桌面（887px）与手机（375px）DOM 测量：表格在 `.table-wrap` 内横向滚动，页面无横向溢出、无元素重叠
- [x] 备注：E2E 用 op_e2e 测试时将其密码重置为 `op_e2e@12345`（原密码未知，属测试账号）

## 四、交付

- Git 提交：`4f1bc9c`（M8.3 新增租户管理）+ `a64a8e0`（任务文档交付记录），已推送 origin/main
- 部署：已上线 sales-agent.top（前后端均已重启生效）
