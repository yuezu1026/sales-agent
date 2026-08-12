# 个人设置可编辑个人信息任务

## 状态

- 状态：✅ 已完成（2026-08-13 启动，2026-08-13 完成）
- 需求：个人设置中，可以修改个人信息，也应该显示个人账号，修改密码，修改自己邮箱地址，微信，电话号码，公司名称的修改
- 追加需求 1：公司名称只能由租户的管理员才能修改
- 追加需求 2：普通用户：公司名称，只读，不能修改

## 需求原文（用户 2026-08-13）

> 个人设置中，可以修改个人信息，也应该显示个人账号，修改密码，修改自己邮箱地址，微信，电话号码，公司名称的修改
>
> 追加 1：公司名称只能由租户的管理员才能修改
>
> 追加 2：普通用户：公司名称，只读，不能修改

## 现状分析

- `User` 实体字段：id/username/passwordHash(@JsonIgnore)/displayName/tenantId/role/status/createdAt/lastLoginAt —— **无 email/wechat/phone/companyName 字段**
- 公司名称存在 **Tenant.name**（register 时用 companyName 命名租户，或默认 username）
- `GET /auth/me` 返回 User 全对象（passwordHash 忽略）；`POST /auth/change-password` 已存在
- `frontend/src/pages/Settings.tsx`：isAdmin 显示「系统设置」（AI 配置/邮箱配置/修改密码/退订管理），operator 显示「个人设置」+ 只有修改密码卡
- `client.ts` 无 username 存取函数；/auth/me 即可拿完整 User
- 平台管理员（tenantId=null）无租户 → 无公司名称概念

## 设计决策

| 决策点       | 结论                                                                                                                                                                                                                     |
| :----------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 个人信息字段 | users 表新增 email/wechat/phone 三列（可空）；displayName 沿用已有字段                                                                                                                                                   |
| 公司名称     | 沿用 Tenant.name（租户级），个人设置改公司名 → 更新本租户 Tenant.name；平台管理员（tenantId=null）隐藏                                                                                                                   |
| 新接口       | `PUT /auth/profile`：更新本人 displayName/email/wechat/phone/companyName（基于 token 身份，无需管理员）                                                                                                                  |
| me 扩展      | `GET /auth/me` 响应附加 companyName（租户名，@Transient），前端回填用                                                                                                                                                    |
| 校验         | 业务校验失败用 400（不用 401）；displayName 空/超长校验；email/phone 宽松格式校验                                                                                                                                        |
| 前端位置     | Settings.tsx 顶部新增「个人信息」卡片（所有角色可见），下方保留修改密码/系统设置等原内容                                                                                                                                 |
| 权限         | 仅能改自己的资料（ATTR_USERNAME 定位用户）；不能改 username/role/tenantId/status                                                                                                                                         |
| 公司名权限   | 公司名称仅租户管理员（role=admin 且 tenantId 非空）可修改：前端仅租户管理员可编辑（普通用户 disabled 只读、平台管理员无租户不显示）；后端 updateProfile 非租户管理员提交 companyName → 400「仅租户管理员可修改公司名称」 |

## 改动清单

- [x] 任务文档
- [x] 后端 V23 迁移：users 表加 email/wechat/phone 列
- [x] 后端 User 实体：新增字段 + getter/setter + @Transient companyName
- [x] 后端 UserService：updateProfile + me 填充 companyName；公司名仅租户管理员可改（非租户管理员提交 → 400）
- [x] 后端 AuthController：PUT /auth/profile + me 返回 companyName
- [x] 前端 Settings.tsx：个人信息卡片（账号只读 + 可编辑资料 + 保存）；公司名称租户管理员可编辑/普通用户 disabled 只读（title 提示）/平台管理员不显示
- [x] 验证：编译 + build + 部署 + E2E（普通用户改资料成功/空值校验/布局 DOM 检查/公司名权限三视角）

## 验证记录

### API 验证（后端重启后）

| 场景                                 | 结果                                            |
| :----------------------------------- | :---------------------------------------------- |
| member1 普通用户带 companyName 修改  | 400「仅租户管理员可修改公司名称」✅             |
| member1 不带 companyName 修改资料    | 200，displayName/email/wechat/phone 保存成功 ✅ |
| rbac_a 租户管理员带 companyName 修改 | 200，公司名 → 张三科技公司V2 ✅                 |
| admin 平台管理员带 companyName 修改  | 400「仅租户管理员可修改公司名称」✅             |
| displayName 空 / email 格式错        | 400（需求 B 已验证）✅                          |

### 浏览器 E2E（三角色视角，localhost/app）

| 角色                 | 公司名称字段表现                                           | 验证                                                          |
| :------------------- | :--------------------------------------------------------- | :------------------------------------------------------------ |
| member1（普通用户）  | 可见 + disabled 只读，title「公司名称仅租户管理员可修改」  | 保存成功且 DB 租户名未变；playwright fill 被 disabled 阻止 ✅ |
| rbac_a（租户管理员） | 可见 + 可编辑（disabled=false）                            | 改为「张三科技公司」保存成功，DB tenants id=4 name 已更新 ✅  |
| admin（平台管理员）  | 无设置页入口（BizGuard 拦截 /settings → /users，既有设计） | API 传 companyName → 400 ✅                                   |

### 布局 DOM 检查（getBoundingClientRect，禁截图）

- 个人信息卡 8 个输入框 left=40/right=1225/width=1185 完全一致，无重叠（保存按钮与修改密码按钮 btnOverlap=false）✅
- 无水平溢出（overflowX=false）；「原密码/新密码」超出视口底部属正常页面滚动（scrollHeight=1052 > 800）✅

### 数据现状（测试已改）

- member1：displayName=张三丰 / email=zhangsan@test.com / wechat=zs_wechat / phone=13800138000；租户 4 名=张三科技公司（rbac_a 改回）

## 交付

- 后端：`V23__user_profile.sql`（email/wechat/phone 列）、User 实体新字段、UserService.updateProfile（公司名权限校验）、AuthController PUT /auth/profile
- 前端：Settings.tsx 个人信息卡片 + 公司名称权限（租户管理员可编辑/普通用户只读/平台管理员不显示）
- 已部署：docker compose build + up -d --force-recreate（容器前端 JS index-CPaES0D7.js）
