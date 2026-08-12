# 退订管理移到邮件管理下拉任务

## 状态

- 状态：✅ 已完成（2026-08-12 完成）
- 需求：退订管理放到邮件管理下面（用户 2026-08-12 提出并征询意见，采纳）

## 需求原文（用户 2026-08-12）

> 退订管理，应该放到邮件管理下面，你认为呢？

## 设计决策

| 决策点   | 结论                                                                                                                                                 |
| :------- | :--------------------------------------------------------------------------------------------------------------------------------------------------- |
| 定位     | 采纳。退订管理本质是「邮件营销抑制列表」，与收件箱/草稿箱/发件箱/模板同属邮件域；设置页已堆 5 块内容，邮件功能应收拢到「邮件管理」一个入口           |
| 页面     | 新增独立页 `UnsubscribeManage.tsx`（从 Settings.tsx 迁移退订卡片：loadUnsub/restore/表格），带 tenantAdmin 守卫（非租户管理员重定向工作台）          |
| 路由     | `/unsubs`（避开公开落地页 `/app/unsubscribe`），BizGuard + tenantAdmin 守卫                                                                          |
| 导航     | Nav.tsx 邮件管理下拉（MAIL_ITEMS）末尾加「退订管理」子项，仅租户管理员（role=admin 且非系统管理员）显示；收件箱/草稿箱/发件箱/模板对租户用户全员可见 |
| Settings | 移除退订管理卡片及相关 state/函数（unsubList/unsubMsg/unsubLoading/loadUnsub/restore），设置页聚焦配置                                               |
| 权限     | 与上一需求一致：仅租户管理员可见；平台管理员本就被 BizGuard 挡在邮件页外，且下拉无邮件管理                                                           |

## 改动清单

- [x] 任务文档
- [x] `frontend/src/pages/UnsubscribeManage.tsx`：新建页面（迁移退订卡片逻辑 + tenantAdmin 守卫）
- [x] `frontend/src/pages/Nav.tsx`：MAIL_ITEMS 拆分（全员项 + 租户管理员项），下拉加「退订管理」子项
- [x] `frontend/src/App.tsx`：加 `/unsubs` 路由（BizGuard + tenantAdmin 守卫）
- [x] `frontend/src/pages/Settings.tsx`：删除退订管理卡片与相关 state/函数
- [x] 验证：build + 部署 + E2E（rbac_a 下拉可见退订管理并可打开；member1 普通用户下拉无此子项；admin 无邮件管理入口）

## 验证记录

**E2E（2026-08-12，浏览器 DOM 测量，禁截图）**

1. 租户管理员 rbac_a/rbac654321：
   - 系统设置页已无「退订管理」卡片（设置页只剩 个人信息/AI 配置/邮箱配置/修改密码）✅
   - 邮件管理下拉出现「退订管理」子项（收件箱/草稿箱/发件箱/邮件模板之后，/app/unsubs）✅
   - 点击进入 `/app/unsubs`：显示「退订管理」标题 + 说明文案 +「暂无退订邮箱」✅
2. 普通用户 member1/member4321：
   - 邮件管理下拉仅 4 项（收件箱/草稿箱/发件箱/邮件模板），无「退订管理」✅
   - URL 直达 `/app/unsubs` → 重定向工作台 `/app`（tenantAdmin 守卫）✅
3. 平台管理员 admin/Admin@123456：
   - 导航无邮件管理入口（仅 工作台/用户管理/帮助）✅
   - URL 直达 `/app/unsubs` → BizGuard 重定向 `/app/users` ✅
4. 布局 DOM 检查（getBoundingClientRect）：退订管理卡片 rect(top:80, bottom:212, left:16, right:1264)，无横向/纵向溢出、无重叠；导航栏无溢出 ✅

**部署**：前端新 bundle `index-BUfgLXEP.js` 已部署（docker compose build + up -d --force-recreate frontend）

## 交付

- 新增 `frontend/src/pages/UnsubscribeManage.tsx`：退订管理独立页（迁移自 Settings 的 loadUnsub/restore/表格），tenantAdmin 守卫（非租户管理员重定向工作台）
- `frontend/src/pages/Nav.tsx`：邮件管理下拉拆分为全员项（收件箱/草稿箱/发件箱/邮件模板）+ 租户管理员项（退订管理）
- `frontend/src/App.tsx`：新增 `/unsubs` 路由（BizGuard 包裹 + 页面内 tenantAdmin 守卫）
- `frontend/src/pages/Settings.tsx`：移除退订管理卡片与相关 state/函数，设置页聚焦配置
- 路由说明：`/app/unsubs`（管理页，租户管理员）；`/app/unsubscribe`（公开落地页，任何人可退订，保持不变）
