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
- [ ] E2E：admin 登录 → 导航有「租户管理」→ 列表展示各租户（含用户数）；普通管理员/普通用户无此菜单且手动访问 `/tenants` 被拦截
- [ ] 界面检查：表格无重叠/溢出（DOM 测量）

## 四、交付

- Git 提交：`4f1bc9c`（M8.3 新增租户管理），已推送 origin/main
- 部署：待下次部署时随前端一起（scp → build → up -d --no-deps frontend），后端需重启容器生效
