# M7.10 任务清单：工作台登录次数统计曲线图（日/周/月/年）

> 状态：✅ 已完成并部署上线（2026-08-10）
> 需求来源：用户「能否在工作台页面展示，系统登录次数统计的曲线图？即每日统计，每周，每月统计，和每年统计的曲线图？」

---

## 一、需求原文

1. 在工作台（Dashboard）页面展示系统登录次数的**曲线图**。
2. 维度：**每日统计 / 每周统计 / 每月统计 / 每年统计**（可切换）。

---

## 二、设计决策

| 项       | 决策                                                                                                                                                                      |
| :------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 数据源   | 复用 M7.9 的 `login_logs` 表（username + login_at），无需新表                                                                                                             |
| 接口     | `GET /api/auth/login-trend?range=daily\|weekly\|monthly\|yearly` → `{ range, points:[{label,count}] }`                                                                    |
| 聚合口径 | 在 Java 侧按桶聚合：daily=最近 14 天（MM-dd）；weekly=最近 12 周按周一（MM-dd）；monthly=最近 12 个月（yyyy-MM）；yearly=最近 5 年（yyyy）；**无数据桶补 0 保证曲线连续** |
| 查询     | `LoginLogRepository.findByLoginAtGreaterThanEqual(start)`（一次查出区间内日志，内存分桶）                                                                                 |
| 鉴权     | 仅工作台使用，走默认 JWT 鉴权（**不放行**，与 /login-stats 的免登录场景区分）                                                                                             |
| 前端     | Dashboard「系统登录统计」卡片增强：保留 stat-grid-3 数字，下方加 **日/周/月/年切换按钮 + SVG 折线图**（项目无图表库，纯 SVG polyline 实现，零新依赖）                     |
| 组件     | 新建 `frontend/src/components/TrendChart.tsx`（通用 SVG 折线图，标签/网格/数据点/数值提示）                                                                               |
| 样式     | styles.css 新增 `.range-switch`（按钮组）+ `.trend-chart`（图表容器）                                                                                                     |

---

## 三、改动清单

- [x] 后端 `LoginLogRepository.java`：新增 `findByLoginAtGreaterThanEqual(LocalDateTime)`
- [x] 后端 `AuthController.java`：新增 `GET /login-trend` 接口（daily/weekly/monthly/yearly 四种聚合 + 非法 range 400 拒绝）
- [x] 前端 `TrendChart.tsx`：新建 SVG 折线图组件
- [x] 前端 `Dashboard.tsx`：登录统计卡片加范围切换 + 曲线图（含空态/加载态）
- [x] 前端 `styles.css`：range-switch / trend-chart 样式
- [x] 构建 + 部署（后端重建镜像 + 前端重建）
- [x] E2E：曲线图渲染；切换日/周/月/年数据变化；DOM 测量无溢出/无重叠

---

## 四、验证记录

### 后端接口（服务器 2026-08-10 23:33）

- `GET /api/auth/login-trend?range=daily` → 14 天桶（07-28~08-10），08-10=36 次，其余补 0 ✅
- `range=weekly` → 12 周桶（05-25 周一~08-10 周一），本周 36 次 ✅
- `range=monthly` → 12 月桶（2025-09~2026-08），2026-08=36 次 ✅
- `range=yearly` → 5 年桶（2022~2026），2026=36 次 ✅
- `range=hourly`（非法）→ HTTP 400 拒绝 ✅
- 无数据桶全部补 0，曲线连续无缺口 ✅

### 前端 E2E（https://sales-agent.top/app，浏览器 2026-08-10 23:3x）

- 登录页统计条：📊 系统累计登录 36 次 · 今日 36 次 · 1 人登录（登录后变 37）✅
- 工作台「系统登录统计」卡片：新增 每日/每周/每月/每年 切换按钮组 + SVG 折线图 ✅
- 每日图：14 个 MM-dd 标签，08-10 数据点 37，y 轴刻度 37/28/19/9/0 ✅
- 每周图：12 个周一起始标签（05-25~08-10），本周 37 ✅
- 每月图：12 个 yyyy-MM 标签（2025-09~2026-08），2026-08=37，active 态正确 ✅
- 每年图：5 个年份标签（2022~2026），2026=37 ✅
- DOM 测量（必查项）：登录统计卡片(16-964.7) / 3 个 stat-box(40/344/648 三列) / 4 个切换按钮(40/111/182/254) / 图表容器(40-940.7) 全部无水平溢出、无互相重叠；卡片底部 626.1 < 视口 650 ✅

---

## 五、交付

- [x] 后端：LoginLogRepository 区间查询 + AuthController login-trend 接口（日/周/月/年聚合）
- [x] 前端：TrendChart SVG 折线图组件 + Dashboard 登录统计卡片增强（切换按钮 + 曲线图）
- [x] 服务器部署：后端镜像重建（ai-customer-deploy-backend:latest 787b6df9efbb）+ 前端镜像重建（ai-customer-deploy-frontend:latest，bundle index--quFZ8R6.js）
- [x] 新 bundle 验证：grep 到 `login-trend` / `range-switch` / `trend-chart` 均已打包
