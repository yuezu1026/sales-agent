# 导航栏邮件管理下拉菜单任务

## 状态

- 状态：✅ 已完成（2026-08-12 启动，2026-08-13 完成）
- 需求：导航栏把 收件箱/草稿箱/发件箱/邮件模板 收拢到「邮件管理」父菜单下，做成下拉菜单

## 需求原文（用户 2026-08-12）

> 能否把导航栏上的收件箱，草稿箱，发件箱，邮件模板，全部到邮件管理的父目录下？也就是下拉菜单?

## 现状分析

- `Nav.tsx` 顶部导航把 收件箱（/inbox）/草稿箱（/drafts）/发件箱（/sent）/邮件模板（/templates）四个链接平铺渲染
- 路由不变（/inbox、/drafts、/sent、/templates 各自独立页面，App.tsx BizGuard 包裹）
- `nav-links` 是横向滚动容器（overflow-x:auto + overflow-y:hidden）——**下拉菜单不能用 absolute 定位**（会被 overflow-y:hidden 裁剪）→ 菜单用 fixed 定位 + JS 计算坐标
- 系统管理员分支无邮件菜单，不受影响

## 设计决策

| 决策点    | 结论                                                                                                 |
| :-------- | :--------------------------------------------------------------------------------------------------- |
| 父菜单名  | 「邮件管理」，带 ▾ 箭头                                                                              |
| 展开方式  | hover（PC，150ms 延迟收起防闪烁）+ 点击切换 + 点击外部关闭（移动端）                                 |
| 高亮      | 处于 inbox/drafts/sent/templates 任一页时，父菜单高亮（active）                                      |
| 菜单定位  | `position:fixed` + 展开时按按钮 rect 计算 left/top（避开 nav-links overflow 裁剪）；右溢出视口时左移 |
| 子项      | 收件箱 / 草稿箱 / 发件箱 / 邮件模板（NavLink，点击后收起）                                           |
| 路由/后端 | 不改，仅前端导航收拢                                                                                 |

## 改动清单

- [x] 任务文档
- [x] 前端 `Nav.tsx`：四个平铺链接 → 「邮件管理」下拉（state + hover/click + fixed 定位菜单 + 外部点击关闭）
- [x] 前端 `styles.css`：`.nav-dropdown` 系列样式（标题/caret/菜单/子项 hover 与 active）
- [x] 验证：前端 build + 部署 + E2E（三角色导航检查 + 下拉展开/收起/跳转 + 布局 DOM 检查）

## 验证记录（2026-08-13）

### 交互链路（真实鼠标操作，member1 普通用户登录态）

| 步骤 | 操作                            | 结果                                                                      |
| :--- | :------------------------------ | :------------------------------------------------------------------------ |
| 1    | hover「邮件管理▾」              | ✅ 菜单展开，4 子项齐全（收件箱/草稿箱/发件箱/邮件模板）                  |
| 2    | hover 菜单项                    | ✅ 菜单保持展开（150ms 延迟收起，防闪烁）                                 |
| 3    | 点击「收件箱」                  | ✅ 跳转 /app/inbox，菜单收起，父菜单高亮 `nav-dropdown active`            |
| 4    | 依次点击 草稿箱/发件箱/邮件模板 | ✅ 分别跳转 /app/drafts、/app/sent、/app/templates，菜单收起 + 父菜单高亮 |
| 5    | 菜单展开后点击页面空白处        | ✅ 菜单关闭（外部点击收起）                                               |
| 6    | 鼠标移出菜单区域                | ✅ 100ms 仍展开（150ms 缓冲内）、300ms 已收起                             |

### 布局 DOM 检查（getBoundingClientRect，禁截图）

- 菜单 rect：left:389 top:51 right:539 bottom:218，**四边均未溢出视口**（vw=887, vh=650）
- 菜单与相邻「个人设置」导航链接**无重叠**（menuHitSettings=false）
- 4 个子项等高排列（各 39px 高 × 150px 宽，收件箱 t57→96 … 邮件模板 t173→212）
- nav-links 无横向溢出（scrollWidth=clientWidth=819）
- 窄屏 1024px：菜单仍无溢出，标题字号 14px（@media 样式生效）

### 排查记录（部署后初测"菜单打不开"）

- 现象：早期 E2E 用 `locator.click()`/全套 dispatch 事件序列（mouseover+mousedown+mouseup+click）时菜单不展开，且 URL 偶发跳到相邻页面
- 定位：容器内 JS（index-BCrmkh5t.js）grep 确认含 nav-dropdown 最新逻辑（`onClick: x=>{x.stopPropagation(), m()}` 只打开不 toggle）；`document.elementFromPoint` 确认点击落点正确命中 `.nav-dropdown-title`（无元素覆盖）
- 结论：代码本身无 bug。初测失败源于**旧 build/页面状态与测试事件序列干扰**（mouseenter 先打开、后续事件再关闭等时序问题）；改用「真实鼠标 + 单步事件」后全链路稳定通过
- 设计上已规避 toggle 双触发：onClick 只做 openMail（打开），收起只由 hover 移出（150ms）/外部点击/子项点击驱动

## 交付

- 前端 `Nav.tsx`：邮件管理下拉（hover 展开 + 点击打开 + 外部点击/移出关闭 + fixed 定位防裁剪 + 当前页父菜单高亮）
- 前端 `styles.css`：`.nav-dropdown` 系列样式 + 1024px 窄屏适配
- 路由与后端零改动（/inbox、/drafts、/sent、/templates 保持不变）
