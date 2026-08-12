# 捐助拾客 Shike 页面任务

## 状态

- 状态：✅ 已完成（2026-08-13 完成并提交）
- 需求：导航栏加「捐助」入口，页面标题「捐助拾客 Shike」，金额 5/10/20/50/100/200 或自定义，捐赠人（账号）选填，支付宝/微信支付按钮，捐助记录展示

## 需求原文（用户 2026-08-13，附参考截图：某开源站捐助页）

> 能否在在导航栏中搞个# 捐助，收到的捐助资金用于开源项目的开发开销，5，10，20，50，100，200,或者自己自己填写金额，捐赠人(账号：选填，不是必填)，然后放入微信支付，或者支付宝支付，页面标题是：捐助拾客Shike

## 设计决策

| 决策点   | 结论                                                                                                                                                                        |
| :------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 入口     | 导航栏加「❤ 捐助」链接，所有角色可见（含平台管理员分支），路由 `/donate` 公开（无需登录，仿照退订落地页）                                                                   |
| 页面     | `Donate.tsx`：标题「捐助拾客 Shike」；说明「收到的捐助资金用于开源项目的开发开销」；金额网格 5/10/20/50/100/200 + 自定义金额；捐赠人（账号）选填；支付宝/微信支付按钮       |
| 支付     | MVP 模拟支付：点击支付按钮 → 确认弹窗 → 记录入库 → 成功提示（明确演示环境不真实扣款，预留真实支付接入点）                                                                   |
| 后端     | `donations` 表（id/donor/amount_cents/channel/created_at）；公开接口 POST /api/donations（创建）+ GET /api/donations?page=&size=（分页列表+总额），WebConfig exclude 免登录 |
| 校验     | 金额 1~100000 元（分存储，支持两位小数）；donor 选填 ≤64，空→「匿名用户」；channel ∈ alipay/wechat                                                                          |
| 记录展示 | 捐助记录：已收到捐助总额 ¥xx,xxx.xx + 分页（每页 10 条，上一页/下一页）+ 列表（❤ 捐赠人 捐助了 ¥xx 时间）                                                                   |

## 改动清单

- [x] 任务文档
- [x] `V24__donation.sql` + `Donation.java` + `DonationRepository.java` + `DonationController.java` + `WebConfig` exclude
- [x] `frontend/src/pages/Donate.tsx`：捐助页
- [x] `frontend/src/pages/Nav.tsx`：加「❤ 捐助」链接（全角色，sysAdmin 与租户两分支均加）
- [x] `frontend/src/App.tsx`：`/donate` 路由（不包 BizGuard，公开）
- [x] `frontend/src/styles.css`：捐助页样式（.donate-\* 系列，紫 #5b5ce6 / 微信绿 #07c160 / 金额粉 #e0447c）
- [x] 验证：后端 build/部署 + API 测试；前端 build/部署 + E2E（三视角导航可见、金额选择/自定义、支付、记录分页）+ 布局 DOM 检查

## 验证记录（2026-08-13）

### 后端 API（curl 直测，免登录）

- V24 迁移成功（flyway_schema_history 显示 24 donation t）
- POST 正常：`{"amountCents":5000,"channel":"alipay","donor":""}` → 入库成功，donor 空→「匿名用户」✅
- POST 金额下限：amountCents=50 → 400「捐助金额需在 1 元 ~ 100000 元之间」✅
- POST 非法渠道：channel=paypal → 400「支付渠道不正确」✅
- GET 分页：totalCents/totalPages/page/items 正确 ✅

### 前端 E2E（Playwright，DOM 测量禁截图）

- 三角色导航均见「❤ 捐助」：admin（平台）/ rbac_a（租户管理员）/ member1（普通用户）✅
- 免登录公开访问：清 storage 后直达 /app/donate 正常渲染 ✅
- 支付宝支付 ¥50（默认选中）：确认弹窗→确认→成功提示+记录出现 ✅
- 自定义金额 88.5 + 捐赠人「热心网友小王」+ 微信支付：预设取消选中、总额累加、列表倒序 ✅
- 校验分支：空金额/0/100001 均前端拦截「捐助金额需在 1 ~ 100000 元之间」，弹窗不出现 ✅
- 取消支付：记录数不变、弹窗关闭 ✅
- 分页：12 条数据 2 页，翻页/上一页/禁用态正确，总额 ¥638.50 ✅
- 布局 DOM 检查（1280px）：金额按钮 6 个无重叠无溢出、支付按钮不重叠、导航链接不重叠 ✅
- 移动端布局（375px）：网格/记录项/时间无溢出 ✅
- 测试数据已清理（DELETE FROM donations）

## 交付

- 导航栏「❤ 捐助」入口（全角色），/app/donate 公开免登录
- 捐助拾客 Shike 页：金额预设 + 自定义 + 捐赠人选填 + 支付宝/微信模拟支付 + 捐助记录（总额+分页+❤列表）
- 后端 donations 表 + 公开 REST 接口（WebConfig exclude）
- 真实支付接入点预留：channel 字段 + DonationController 注释说明

---

## 需求 2（用户 2026-08-13）：支付按钮弹出真实收款码

> 能否把 alipay.jpg 置换到 /app/donate 页面中，点击支付宝按钮，弹出这个支付宝收款码，而 wechat-pay.jpg，则是点击微信支付，弹出的收款码图片

### 设计决策

| 决策点   | 结论                                                                                                                                                             |
| :------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 图片存放 | 工作区根目录 `alipay.jpg` / `wechat-pay.jpg` 复制到 `frontend/public/`，经 `${import.meta.env.BASE_URL}` 引用（/app/alipay.jpg）                                 |
| 交互流程 | 校验金额通过 → 弹出收款码弹窗（支付宝=alipay.jpg / 微信=wechat-pay.jpg，显示当前金额）→ 用户扫码真实付款 → 点「我已完成支付」记录入库；「取消」/点遮罩关闭不记录 |
| 弹窗     | 复用 `.modal-mask`/`.modal`，新增 `.donate-qr-modal`（居中、图片 max-height 55vh 防溢出）                                                                        |
| 移除     | 原 confirmDialog「模拟支付」确认弹窗（改为真实收款码流程）                                                                                                       |

### 改动清单

- [x] 复制图片到 `frontend/public/`（alipay.jpg / wechat-pay.jpg，容器内 /usr/share/nginx/html 验证存在，HTTP 200）
- [x] `Donate.tsx`：qrChannel/qrCents 状态 + 收款码弹窗 + confirmDonate（移除原 confirmDialog 模拟支付）
- [x] `styles.css`：`.donate-qr-modal` 样式（400px 宽、图片 max-height 55vh、按钮居中）
- [x] 构建部署 + E2E（弹窗图片正确、完成支付入库、取消不入库、布局无溢出）
- [x] 任务 md 更新 + Git 提交

### 验证记录（需求 2）

- 支付宝按钮 → 弹窗显示 `/app/alipay.jpg`（naturalWidth>0 加载成功），标题「支付宝扫码捐助」+ 金额 ￥50.00 ✅
- 微信支付按钮 → 弹窗显示 `/app/wechat-pay.jpg`，标题「微信支付扫码捐助」✅
- 「我已完成支付」→ 记录入库（扫码测试 ￥66.00 出现在列表首位）+ 成功提示 + 弹窗关闭 ✅
- 「取消」/点遮罩 → 弹窗关闭且不入库 ✅
- 金额 0 → 不弹窗，提示「捐助金额需在 1 ~ 100000 元之间」✅
- 弹窗打开期间金额锁定（qrCents），改输入框不影响弹窗展示与入库金额 ✅
- 布局 DOM：桌面 1280px 弹窗/图片/按钮无溢出无重叠；移动端 375px modal 336px 宽、图片 448px 高无溢出 ✅
- 测试记录已清理（仅删 donor='扫码测试'，用户自测数据保留）
