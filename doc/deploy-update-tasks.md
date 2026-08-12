# 部署任务：本地新版替换服务器前后端

## 状态

- 状态：✅ 已完成（2026-08-13）
- 需求：把本地最新代码（SaaS 版，含捐助页）整体部署到服务器 43.153.229.106，替换服务器的前后端与数据库迁移

## 需求原文（用户 2026-08-13）

> 请部署到服务器中：43.153.229.106，替换服务器里面的前后端。
> 是替换服务器的前后端，db

## 服务器现状（部署前调研）

| 项         | 值                                                                               |
| :--------- | :------------------------------------------------------------------------------- |
| 部署目录   | `/home/ubuntu/ai-customer-deploy/`（backend/ frontend/ docker-compose.yml .env） |
| 容器       | `aic-backend`(8080) `aic-frontend`(8081) `aic-db`(postgres:16-alpine, 5433→5432) |
| DB Flyway  | V1~V20；license 表 0 条（从未激活，移除 License 无风险）                         |
| maven 镜像 | 腾讯云（服务器）；本地阿里云                                                     |
| DB 端口    | 5433:5432（5432 被 docker-pg 占用，**compose 不可覆盖**）                        |
| 外层网关   | /root 的 prod compose（HTTPS 443/80），不动                                      |

## 设计决策

| 决策点     | 结论                                                                            |
| :--------- | :------------------------------------------------------------------------------ |
| 替换范围   | 仅 `backend/` + `frontend/` 源码目录；保留服务器 docker-compose.yml 与 .env     |
| 文件传输   | 本地 tar 打包（排除 target/node_modules/dist/tsbuildinfo）→ scp → 服务器解压    |
| maven 镜像 | 解压后用备份的腾讯云 maven-settings.xml 覆盖（服务器访问腾讯云更稳）            |
| 数据安全   | 部署前 pg_dump 备份（服务器 + 本地双份）；V21 迁移存量数据归默认租户 id=1       |
| 构建       | 服务器 `docker compose build backend frontend` + `up -d`，Flyway 自动跑 V21~V24 |

## 改动清单

- [x] 数据库备份（服务器 /home/ubuntu/backup/ + 本地双份）✅ 已完成
- [x] license-public.key 备份 ✅ 已完成
- [x] 本地打包 backend/frontend（排除构建产物）
- [x] scp 上传 + 服务器解压替换
- [x] 恢复腾讯云 maven-settings.xml（本地 backend/maven-settings.xml 即腾讯云版，解压后天然一致，无需额外恢复）
- [x] 服务器构建 + 重启（Flyway V21~V24）
- [x] 验证：迁移成功、登录、捐助页、接口
- [x] 任务 md 更新 + Git 提交

## 验证记录

### 数据库迁移（Flyway 自动执行，无需手动变更表）

| 检查项 | 结果 |
| :--- | :--- |
| Flyway V21~V24 应用 | ✅ 4 个迁移全部 Applied（日志 `Successfully applied 4 migrations ... now at version v24`，0.319s） |
| tenants 表 + 默认租户 id=1 | ✅ 创建成功，租户数 1 |
| donations 表 | ✅ 创建成功（V24） |
| license 表 | ✅ 已删除（V21，原表 0 条无风险） |
| users.email/wechat/phone 列 | ✅ 已添加（V23） |
| 存量用户租户 | ✅ admin/op_e2e tenant_id=NULL（平台级账号，V21 设计如此），业务数据归默认租户 id=1 |
| 迁移兼容性预检 | ✅ V21/V22 引用的 4 个约束名 + 3 个索引名 + login_logs/license 表名均与服务器 DB 一致 |

### 修复的 bug：用户列表 NPE

- **现象**：`GET /api/users` 返回 500
- **根因**：`UserService.listAll` 中 `names.get(u.getTenantId())` 对平台级账号（tenant_id=NULL）在 `Map.of()` 上取 null key → NPE
- **修复**：空租户映射改用 `new HashMap<>()`（容忍 null key），`UserService.java` 209 行
- **复测**：登录 200 / 用户列表 200（admin tenantId=null 正常展示）/ 捐助列表 200

### 线上 E2E（https://sales-agent.top，DOM 测量禁截图）

| 检查项 | 结果 |
| :--- | :--- |
| 外层网关 `/` `/app/donate` `/api` | ✅ 均 200 |
| 捐助页渲染 | ✅ 导航/金额按钮/输入框/捐助记录完整 |
| 支付宝弹窗 | ✅ 收款码加载、金额正确、取消/遮罩关闭正常 |
| 微信支付弹窗 | ✅ 收款码加载（1217x1658，显示宽高比 0.735 无变形） |
| 桌面弹窗 DOM 测量 | ✅ 400x539 在视口内无溢出，图片宽高比 0.668（原图 0.667）无变形 |
| 手机 375x667 页面 | ✅ 无溢出、无重叠、无横向滚动（bodyScrollW==bodyClientW） |
| 手机 375x667 弹窗 | ✅ 336x549 在视口内，图片 0.668 无变形 |
| 金额 0 校验 | ✅ 提示「捐助金额需在 1 ~ 100000 元之间」不弹窗 |
| 支付闭环入库 | ✅ 1 元 → 我已完成支付 → toast「捐助成功」+ 列表记录 + 总额更新（测试后已清理） |
| 测试数据清理 | ✅ 删除测试记录 1 条，donations 归零 |
| 登录页/注册页 | ✅ 渲染正常；注册校验分支（短密码/非法用户名）均 400 正确提示 |
| 后端接口 | ✅ 登录 200 / 用户列表 200 / 捐助列表 200 |

### 部署操作要点

- `docker compose up -d` 会尝试重建 aic-db（容器名冲突）→ 必须用 `--no-deps` 只重建 backend/frontend
- 服务器 compose 含 gateway 服务但无 gateway 目录（外层 /root prod compose 处理 80/443），全量 up 会报 path not found
- 旧源码目录已备份为 `backend.bak-20260813` / `frontend.bak-20260813`
- DB 端口 5433:5432 保持不变（5432 被 docker-pg 占用）
