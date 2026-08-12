# M7.9 任务清单：系统登录次数统计（登录页 + 工作台显示）

> 状态：✅ 已完成并部署上线（2026-08-10）
> 需求来源：用户「能否帮我加个用户登录次数的统计吗？直接显示在登录界面？」

---

## 一、需求原文

1. 添加"用户登录次数"统计。
2. 显示在登录界面。
3. 澄清确认（用户选择）：统计类型 = **系统累计登录次数**（全局统计，无需登录可见）；显示位置 = **登录页 + 工作台**。

---

## 二、设计决策

| 项         | 决策                                                                                                      |
| :--------- | :-------------------------------------------------------------------------------------------------------- |
| 方案       | 新建 `login_logs` 表记录每次成功登录（username + login_at）；统计接口聚合：累计次数 / 今日次数 / 今日人数 |
| 接口       | `GET /api/auth/login-stats`（免登录，登录页可匿名访问）→ `{ totalLogins, todayLogins, todayUsers }`       |
| 记录时机   | `AuthController.login` 校验成功后插入一条 login_log；同时顺带更新 `users.last_login_at`                   |
| 今日口径   | `LocalDate.now().atStartOfDay()`，与 AiService usage 统计一致                                             |
| 放行       | `WebConfig` authInterceptor excludePathPatterns 增加 `/api/auth/login-stats`                              |
| 前端登录页 | 挂载时请求统计，登录表单下方（测试账号提示条下）显示「📊 系统累计登录 N 次 · 今日 N 次 · N 人登录」       |
| 前端工作台 | Dashboard 加「系统登录统计」卡片（stat-grid-3：累计 / 今日 / 今日人数）                                   |
| 迁移       | V19\_\_login_log.sql（最新迁移 V18）                                                                      |

---

## 三、改动清单

- [x] 后端 `V19__login_log.sql`：login_logs 表（id, username, login_at + 索引）
- [x] 后端 `LoginLog.java` 实体 + `LoginLogRepository.java`（countByLoginAtGreaterThanEqual / countDistinctUsernameAfter / findTopByOrderByLoginAtDesc）
- [x] 后端 `AuthController.java`：login 成功后记录登录日志 + 更新 lastLoginAt（UserService.recordLogin）；新增 `/login-stats` 接口返回 `{ totalLogins, todayLogins, todayUsers }`
- [x] 后端 `WebConfig.java`：放行 `/api/auth/login-stats`
- [x] 前端 `Login.tsx`：挂载时请求统计（skipAuthRedirect），登录表单下方显示「📊 系统累计登录 N 次 · 今日 N 次 · N 人登录」
- [x] 前端 `Dashboard.tsx`：工作台加「系统登录统计」卡片（stat-grid-3：累计登录 / 今日登录次数 / 今日登录人数）
- [x] 前端 `styles.css`：登录页统计条样式 `.login-stats-tip`
- [x] 构建 + 部署（后端重建镜像 + 前端重建）
- [x] E2E：登录页显示统计；工作台显示统计；DOM 测量无溢出/无重叠

---

## 四、验证记录

### 后端接口（服务器 2026-08-10 22:58）

- `GET /api/auth/login-stats` 免登录返回 `{"code":0,"data":{"totalLogins":1,"todayLogins":1,"todayUsers":1}}` ✅
- POST `/api/auth/login` 成功后再次查询 → `totalLogins:2, todayLogins:2, todayUsers:1` ✅（登录即 +1）
- Flyway：`Successfully applied 1 migration ... now at version v19` ✅

### 前端 E2E（https://sales-agent.top/app/login，浏览器 2026-08-10 23:0x）

- 登录页显示统计条：**📊 系统累计登录 2 次 · 今日 2 次 · 1 人登录** ✅（位于测试账号提示条与登录按钮之间）
- 登录成功后工作台显示「系统登录统计」卡片：**3 累计登录 / 3 今日登录次数 / 1 今日登录人数** ✅（curl 2 次 + 浏览器 1 次）
- DOM 测量（必查项）：
  - 登录页：统计条(459-493) / 测试提示条(398-443) / 按钮行(509-552) 三块无重叠；全部元素无水平/垂直溢出 ✅
  - 工作台：客户概览(145-321) / 邮件效果(341-532) / 登录统计(552-719) 卡片无重叠、无水平溢出；垂直超出视口为正常滚动 ✅
- 旧 bundle 缓存问题：部署后浏览器可能加载旧 JS，需强制刷新（Ctrl+F5）才能看到统计条

---

## 五、交付

- [x] 后端：V19 迁移 + LoginLog 实体/仓库 + AuthController 登录记录与统计接口 + WebConfig 放行
- [x] 前端：Login 页统计条 + Dashboard 统计卡片 + 样式
- [x] 服务器部署：后端镜像重建（ai-customer-deploy-backend:latest 18088b356776）+ 前端镜像重建（ai-customer-deploy-frontend:latest，bundle index-BZYxMVTh.js）
- [x] 新 bundle 验证：grep 到 `login-stats` / `系统累计登录` / `系统登录统计` 均已打包
- 数据库 `login_logs` 现有数据：3 条（均为 admin，2026-08-10）
