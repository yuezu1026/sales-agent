# M8.8 任务清单：登录页三账号一键登录（系统管理员/租户管理员/普通用户）

> 状态：✅ 已完成（2026-08-13）
> 需求来源：用户「目前只有 admin 一个账号，应该要再增加两个账号可以一键登录」

---

## 一、需求原文

1. 登录页目前只有 `admin` 一个预设测试账号。
2. 需要再增加两个账号，共三个预设账号，覆盖三种角色，可一键填入登录：
   - 默认系统管理员（平台级，已有）
   - 默认租户管理员
   - 该租户下的普通用户

---

## 二、设计决策

| 项       | 决策                                                                                                                                          |
| :------- | :-------------------------------------------------------------------------------------------------------------------------------------------- |
| 角色模型 | 三级角色（见 rbac.md）：系统管理员=role=admin 且 tenant_id NULL；租户管理员=role=admin 且 tenant_id 非空；普通用户=role=operator              |
| 预设账号 | ① `admin / Admin@123456`（系统管理员，已有不动）② `demo_admin / Demo@123456`（演示租户管理员）③ `demo_user / Demo@123456`（演示租户普通用户） |
| 演示租户 | 新建独立租户「演示租户」（plan=free, status=active），与真实注册租户完全隔离；owner = demo_admin                                              |
| 种子方式 | 后端 `InitDataConfig` 幂等创建（同 admin 机制）：无则建，有则跳过；并调用 `UserService.initTenantDefaults` 初始化演示租户默认数据（开箱即用） |
| 禁改密码 | `UserService` 硬编码 `admin` 改为演示账号集合 `{"admin","demo_admin","demo_user"}`（公开密码账号，禁止重置/修改密码，防演示账号被破坏）       |
| 前端交互 | 登录页测试账号提示条改为 3 行列表，每行：角色名 + 账号/密码 code + 「填入」小按钮；点击填入对应账号并清空错误提示                             |
| 后端     | 仅 `InitDataConfig` + `UserService`（`initTenantDefaults` 改 public 复用）                                                                    |
| 可选开关 | 暂不做 `app.demo-accounts.enabled` 开关（admin 本就公开，演示账号权限更低，风险可接受；后续需要再加）                                         |

---

## 三、改动清单

- [x] 后端 `InitDataConfig.java`：幂等创建演示租户 + demo_admin（租户管理员）+ demo_user（普通用户），调用 initTenantDefaults
- [x] 后端 `UserService.java`：`initTenantDefaults` 改 public；新增 `DEMO_ACCOUNTS` 集合，resetPassword/changePassword 的 admin 硬编码改集合判断
- [x] 前端 `Login.tsx`：测试账号提示条 1 行 → 3 行（角色标注 + 一键填入），fillTestAccount 支持传入账号
- [x] 前端 `styles.css`：`.test-account-tip` 支持多行列表样式
- [x] 验证：后端编译 + 接口登录三账号 + E2E DOM 测量（无溢出/无重叠）
- [x] 验证脚本：`doc/m88-login-test.mjs`（Node fetch，三账号登录断言）
- [x] Git 提交推送

---

## 四、验证记录

### 4.1 API 登录验证（线上 sales-agent.top，`node doc/m88-login-test.mjs`）

| #   | 账号                                     | 结果                                     |
| :-- | :--------------------------------------- | :--------------------------------------- |
| 1   | `admin / Admin@123456`（系统管理员）     | ✅ role=admin, tenantId=0                |
| 2   | `demo_admin / Demo@123456`（租户管理员） | ✅ role=admin, tenantId=4（演示租户）    |
| 3   | `demo_user / Demo@123456`（普通用户）    | ✅ role=operator, tenantId=4（演示租户） |

### 4.2 浏览器 E2E（sales-agent.top/app/login）

- ✅ 登录页显示三角色演示账号列表（系统管理员/租户管理员/普通用户）+ 各自「填入」按钮
- ✅ 点击「填入」→ 用户名/密码自动填充（实测 demo_user / Demo@123456）
- ✅ DOM 测量：viewport 887x650，docScrollW 887 = 视口宽（无横向溢出）；三角色行/按钮均在视口内无越界；填入按钮 vs 登录/重置按钮零重叠
- ✅ 三角色登录后导航差异符合 RBAC：
  - admin（系统管理员）：工作台（仅平台统计）+ 用户管理 + 租户管理 + 帮助
  - demo_admin（租户管理员）：业务菜单 + 用户管理 + 系统设置（无租户管理）
  - demo_user（普通用户）：业务菜单 + 用户管理（M8.6 只见自己）+ 个人设置

### 4.3 部署

- 服务器 43.153.229.106：scp 源码 → `sudo docker compose build backend/frontend` → `up -d --no-deps backend frontend`
- 后端 `Started` 后健康检查 200；InitDataConfig 幂等创建演示租户（tenantId=4）+ demo_admin + demo_user

---

## 五、交付

- 线上已生效：https://sales-agent.top/app/login 登录页显示三角色演示账号一键填入
- 后端：InitDataConfig 幂等创建演示账号；UserService 演示账号集合禁改密码（admin/demo_admin/demo_user）
- Git：已提交推送（见 commit）
