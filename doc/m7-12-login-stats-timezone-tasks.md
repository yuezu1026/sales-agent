# M7.12 任务清单：修复"今日登录统计"跨天不重置（容器 UTC 时区问题）

> 状态：✅ 已完成（2026-08-11）
> 需求来源：用户「帮我排查下系统登录统计，都已经过了0点了，为什么今日的统计次数是昨天的？」

---

## 一、问题现象

- 北京时间 2026-08-11 00:08 已过 0 点，但 `GET /api/auth/login-stats` 返回的 `todayLogins` 仍是 37（昨天的数字），"今日"没有归零。

## 二、根因分析

- 宿主机（服务器）时区 = CST（UTC+8，中国腾讯云）；但**后端容器 `eclipse-temurin:21-jre` 默认时区 = UTC**（实测 `Mon Aug 10 04:08 PM UTC`，TZ 为空）。
- `AuthController.loginStats()` 用 `LocalDate.now().atStartOfDay()` 按 **JVM 默认时区（UTC）** 算"今日起点"→ 北京时间 0:00~8:00 之间 UTC 仍是"昨天"，`LocalDate.now()` 返回昨天的日期。
- 于是"今日"= UTC 口径的 8-10 一整天（含北京 8-10 全天的 37 次登录），且**北京 8-11 凌晨的登录也会被计入 UTC 的 8-10**，直到北京 8:00 才"归零"。

## 三、受影响范围（同一根因）

| 位置                                 | 影响                                                 |
| :----------------------------------- | :--------------------------------------------------- |
| `AuthController.loginStats()`        | 登录页/工作台「今日登录次数/人数」（用户报告的问题） |
| `AuthController.loginTrend()`        | 趋势图 daily/weekly/monthly/yearly 桶边界偏移 8 小时 |
| `AiService.usageSummary()`           | 工作台「AI 用量今日」                                |
| `EmailSendService.checkDailyLimit()` | 邮件每日限频（mail.daily_limit）跨天不重置           |

## 四、设计决策（三层，保证自洽）

| 层         | 改动                                                                                        | 作用                                                                                       |
| :--------- | :------------------------------------------------------------------------------------------ | :----------------------------------------------------------------------------------------- |
| 1 容器时区 | `Dockerfile` 加 `ENV TZ=Asia/Shanghai`                                                      | JVM 默认时区 = 中国时区（根本解）                                                          |
| 2 启动兜底 | `AiCustomerApplication` 静态块 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))` | 即使 TZ 漏配也生效；且 Hibernate 解释 LocalDateTime→TIMESTAMPTZ 与统计口径同时区，保证自洽 |
| 3 代码显式 | 4 处 `LocalDate.now()` → `LocalDate.now(ZoneId.of("Asia/Shanghai"))`                        | 语义明确，将来部署到非中国时区也不受影响                                                   |

> 说明：实体字段 `LocalDateTime.now()`（存储绝对时刻）无需改——JVM 时区正确后 TIMESTAMPTZ 存的就是正确的 UTC 绝对时刻；只有"按自然日/月/年聚合"的逻辑必须显式指定北京时区。

## 五、改动清单

- [x] 创建本文档
- [x] `backend/Dockerfile`：`ENV TZ=Asia/Shanghai`
- [x] `AiCustomerApplication.java`：启动时设置默认时区（静态块 `TimeZone.setDefault(...)` 兜底）
- [x] `AuthController.java`：loginStats + loginTrend 用 `ZoneId.of("Asia/Shanghai")`（+ import）
- [x] `AiService.java`：usageSummary 今日起点用北京时区（+ import）
- [x] `EmailSendService.java`：checkDailyLimit 用北京时区（+ import）
- [x] 本地 `mvn compile` 验证（通过）
- [x] 部署：scp 改动 + 重建后端镜像（d3dbb45da70f）+ 重启
- [x] 服务器验证：容器时区 CST；今日统计按北京时间
- [x] E2E：登录页/工作台统计 + 趋势图 4 个 range 正常、DOM 无重叠溢出
- [x] 更新任务文档 + Git 提交

---

## 六、验证记录

**1. 服务器（北京时间 2026-08-11 00:11）**

- 容器时区：`Tue Aug 11 12:11:13 AM CST 2026`（修复前为 UTC）✅
- `GET /api/auth/login-stats` → `{"totalLogins":38,"todayLogins":1,"todayUsers":1}`（修复前 todayLogins 显示昨天的 37）✅
- `GET /api/auth/login-trend`（JWT）：
  - daily 最后 3 桶：`08-09:0, 08-10:37, 08-11:2` ✅（08-11 桶已按北京时间出现）
  - weekly 最后桶：`08-10:39`（本周含今日）✅
  - monthly 最后桶：`2026-08:39` ✅
  - yearly 最后桶：`2026:39` ✅

**2. E2E（浏览器 https://sales-agent.top/app/）**

- 登录页统计：`系统累计登录 39 次 · 今日 2 次 · 1 人登录` ✅
- 工作台「系统登录统计」：`累计登录 40 · 今日登录次数 3 · 今日登录人数 1`（北京 8-11 0 点后新增 3 次）✅
- 趋势图 X 轴到 08-11，最后一桶 = 3（今日），08-10 = 37 ✅
- 周/月/年切换：最后桶分别为 08-10(40) / 2026-08 / 2026，均按北京时间聚合 ✅
- DOM 测量（996x650 视口）：range 按钮无重叠、图表无横向溢出（hScroll=false）、文档总高 1047 > 图表绝对底部 982.8（可滚动到达）、滚动后图表完整在视口内 ✅

**3. 其他同根因修复点（未逐一 E2E，接口逻辑已随时区生效）**

- AI 用量今日统计（AiService.usageSummary）
- 邮件每日限频（EmailSendService.checkDailyLimit）

---

## 七、交付

- 后端镜像重建并部署（ai-customer-deploy-backend:latest, d3dbb45da70f），容器 aic-backend 已重启
- 提交信息：`修复登录统计今日口径 UTC 时区问题（容器设 Asia/Shanghai + 代码显式时区）`
