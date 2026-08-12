# M7.8 任务清单：登录页给出默认测试账号（免手动输入）

> 状态：✅ 已完成（2026-08-10）
> 需求来源：用户「在登录页面，给出默认的测试账号，不用用户自己输入」

---

## 一、需求原文

1. 登录页面目前需要用户自己输入用户名/密码。
2. 希望在登录页直接给出默认测试账号，用户不用自己输入（可一键填入）。

---

## 二、设计决策

| 项       | 决策                                                                                                  |
| :------- | :---------------------------------------------------------------------------------------------------- |
| 方案     | 登录框「记住我」下方新增测试账号提示条：显示 `admin / Admin@123456` + 「一键填入」小按钮              |
| 交互     | 点击「一键填入」→ 自动填充用户名 `admin`、密码 `Admin@123456`，并清空错误提示；用户再点「登 录」即可  |
| 默认账号 | `admin / Admin@123456`（后端 `InitDataConfig` 首次启动自动创建，密码激活后会被 License 激活流程重置） |
| 样式     | 复用 .btn-xs 小按钮；提示条用浅灰虚线边框 + 等宽 code 高亮，与登录框整体风格一致                      |
| 后端     | 零改动（纯前端）                                                                                      |

---

## 三、改动清单

- [x] `frontend/src/pages/Login.tsx`：新增 `fillTestAccount()`（setUsername("admin") / setPassword("Admin@123456") / setMsg(null)）+ 测试账号提示条（含「一键填入」按钮）
- [x] `frontend/src/styles.css`：新增 `.test-account-tip` 样式（flex 布局、虚线边框、code 高亮）
- [x] 构建 + 部署前端（2026-08-10 已上线：scp 源码 → docker compose build frontend → up -d --no-deps frontend）
- [x] E2E：一键填入后输入框内容正确；DOM 测量无溢出/无重叠；登录功能回归（登录接口回归受后端未启动限制，本地 8080 未运行）

---

## 四、验证记录

1. ✅ TS 编译：`npx tsc --noEmit` exit=0
2. ✅ 页面级（浏览器 E2E）：登录页显示测试账号提示条（admin / Admin@123456）+ 「一键填入」按钮
3. ✅ 功能：点「一键填入」→ 用户名/密码自动填入 admin / Admin@123456，无需手动输入
4. ✅ DOM 测量：viewport 809x650，docScrollW 809 = 视口宽（无横向溢出）；提示条/按钮均在视口内无越界；tip按钮 vs 登录/重置按钮、登录 vs 重置 均零重叠
5. ⚠️ 登录回归：本地后端 8080 未启动（/api/health 超时），登录请求 500 为后端未运行所致，与本次改动无关；前端登录逻辑未改动

---

## 五、交付

- 线上已部署：2026-08-10 22:26 镜像重建（ai-customer-deploy-frontend:latest），aic-frontend 容器重启
  - 新 bundle：`index-fXsH5yYq.js`（677650B），css：`index-BplsdBeD.css`
  - 线上验证：https://sales-agent.top/app/login 200；/app/assets/index-fXsH5yYq.js 200 677650B；bundle 含 `Admin@123456` + `test-account-tip` 特征 ✅；/api/health 200
  - 坑：docker exec 容器内 grep 中文会误报 NOT FOUND（locale 问题），改用宿主机 curl 拉 bundle 后 grep 验证
- 后端零改动
- Git：已提交推送（e923e98）
