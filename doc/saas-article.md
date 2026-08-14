# 单机 License 产品 SaaS 化改造实录：多租户数据隔离、License 移除与那些绕不开的坑

> 一个"AI 智能获客助手"从单机 License 模式改造为开放注册 SaaS 系统的完整技术复盘——租户模型怎么建、14 张业务表怎么加租户隔离、JWT 与拦截器怎么配合、定时任务和 MCP 的租户上下文坑，以及 E2E 全链路验证。

## 一、改造背景：为什么单机产品要 SaaS 化

我的上一个项目是一套**本地部署 + License 激活码**模式的 AI 智能获客系统：企业买断后部署在自己服务器上，激活码绑定机器指纹、走云端 AI API 配额。

产品跑通后遇到一个现实问题：**交付成本高、触达新客户慢**。每个客户都要走"联系 → 报价 → 发激活码 → 指导部署"的流程，而且很多中小团队其实更想要"注册就能用"的轻量体验。

所以决定做一次彻底改造：**从单机 License 模式 → 开放注册 + 多租户数据隔离的 SaaS 模式**。本文记录这次改造的技术决策、实现细节和踩过的坑。

## 二、改造前先想清楚：范围与取舍

SaaS 化最容易踩的坑是一上来就想"全套"：套餐计费、团队邀请、子账号体系、审计……结果半年做不完。

我做的取舍是：

| 决策点        | 结论                                                                          |
| :------------ | :---------------------------------------------------------------------------- |
| SaaS 改造深度 | **开放注册 + 租户数据隔离**（本期不做套餐计费/团队邀请）                      |
| License 机制  | **彻底移除**（删激活码/设备指纹相关代码与表），改为注册即用                   |
| 数据隔离方案  | **单库 + 业务表加 `tenant_id` 列**（不引入 Postgres RLS，保持兼容简单）       |
| 平台账号      | 保留初始 `admin`（tenant_id=NULL，平台级）；注册用户各自创建租户 + 租户管理员 |

这个取舍背后是一个原则：**先让"多租户隔离"这个核心跑稳，商业化的部分后面再加**。数据隔离做错了会出安全事故，而计费做错了只是少赚钱。

## 三、租户模型：一次注册，事务内完成"租户 + 管理员 + 默认配置"

核心新增一张 `tenants` 表，`users` 表加 `tenant_id` 列（NULL 表示平台级账号）：

```sql
CREATE TABLE tenants (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,          -- 公司名
    owner_user_id BIGINT,                         -- 创建人
    plan         VARCHAR(32)  DEFAULT 'free',
    status       VARCHAR(32)  DEFAULT 'active',
    created_at   TIMESTAMPTZ DEFAULT now(),
    expire_at    TIMESTAMPTZ
);
```

注册接口 `POST /api/auth/register` 是整个改造的枢纽，它在**一个事务**里完成三件事：

1. 创建租户（公司名缺省用用户名）
2. 创建租户管理员（role=admin，绑定 tenant_id）
3. 初始化该租户的 `system_config` 默认值、默认数据源、默认 Prompt 模板

```java
@Transactional
public User register(String username, String password, String displayName, String companyName) {
    // 1) 创建租户
    Tenant tenant = new Tenant();
    tenant.setName(companyName);
    tenant.setPlan("free");
    tenant = tenantRepository.save(tenant);

    // 2) 创建租户管理员
    User user = new User();
    user.setRole(User.ROLE_ADMIN);
    user.setTenantId(tenant.getId());
    user = userRepository.save(user);

    // 3) 回填租户 owner
    tenant.setOwnerUserId(user.getId());
    tenantRepository.save(tenant);

    // 4) 初始化租户默认配置（AI key 从全局 env 兜底）...
}
```

这里有个关键设计：**租户的 AI 配置（api_key 等）是隔离的**。新租户注册后，AI 配置先继承全局环境变量的兜底值，租户管理员可在系统设置页改自己的——互不影响。

## 四、数据隔离：14 张业务表 + JWT 携带 tenantId + 拦截器注入

这是本次改造的**技术核心**。方案是经典的"单库 + 行级租户列"：

### 1. 所有业务表加 `tenant_id`

14 张业务表全部加 `tenant_id BIGINT NOT NULL`：`ai_usage_log`、`system_config`、`lead`、`follow_up`、`email_draft`、`email_inbox`、`email_send_log`、`email_template`、`email_unsubscribe`、`data_source`、`customer_profile`、`prompt_template`、`wechat_message`、`ai_cache`。

注意 `system_config` 的唯一约束从 `(config_key)` 改成了 `(tenant_id, config_key)`——**同一配置项，每个租户各有一份**。

### 2. JWT 里带 tenantId

登录签发的 JWT claims 里增加 `tenantId` 字段，前端存 token，每次请求带回来。

### 3. 拦截器解析 + ThreadLocal 上下文

`AuthInterceptor` 在 `preHandle` 时解析 JWT 中的 `tenantId`，写入线程级 `TenantContext`（ThreadLocal），`afterCompletion` 时清理：

```java
public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    public static void set(Long tenantId) { CURRENT.set(tenantId); }
    public static Long get() { return CURRENT.get(); }

    /** 平台级账号访问业务接口时抛 400 */
    public static Long require() {
        Long tenantId = CURRENT.get();
        if (tenantId == null) {
            throw BizException.badRequest("当前账号无租户上下文");
        }
        return tenantId;
    }
    public static void clear() { CURRENT.remove(); }
}
```

### 4. Service/Repository 层按租户过滤

14 个业务仓库的 `findById`/`findAll`/`count` 等默认方法全部改为 `TenantContext.require()` + 按租户派生方法；4 个用 Specification 的复杂查询（Lead/EmailInbox/EmailDraft/EmailSendLog）加租户 predicate；11 处实体创建处 `setTenantId`（防 NOT NULL 报错）。

这样改完的效果：**任何一个租户的用户，永远只能看到自己租户的数据**，不管从哪个接口进。这是 SaaS 的生死线。

## 五、License 移除：不是注释掉，是连根拔

License 机制曾是我的"防盗版三板斧"（激活码离线签名 + 机器指纹 + Token 配额），但 SaaS 化后它的使命结束了。

删除清单：`LicenseInterceptor` / `LicenseController` / `LicenseService` / License 实体 / LicenseRepository / license 表（Flyway V21 中 DROP）/ 公钥引用 / `app.license` 配置 / 前端 Activate 激活页 / 激活码相关样式。

这里有个容易忽略的细节：**删 License 后，未知 API 访问不再有 License 拦截器兜底**，Spring Boot 4 默认对不存在的路径会直接 500。我加了 `NoResourceFoundException → 404` 的全局异常处理器，让未知接口干净地返回 404 而不是 500：

```java
@ExceptionHandler(NoResourceFoundException.class)
public ApiResponse<Void> handleNotFound(...) { return ApiResponse.error(404, "接口不存在"); }
```

改造后实测：带 token 访问 `GET /api/license` → 404「接口不存在」，符合预期。

## 六、AI 配置租户化：动态构建 ChatModel 的按租户读取

项目所有 AI 能力（邮件生成、回复分析、潜客挖掘）统一走 AiService，它支持从 `system_config` 动态构建 ChatModel（`ai.api_key` / `ai.base_url` / `ai.model_name`，敏感项 AES 加密落库）。

租户化改造后，AiService **按当前租户**读配置：

```java
// 按租户读 AI 配置；租户未配置时回退全局环境变量 AI_API_KEY
String apiKey = systemConfigService.get("ai.api_key", TenantContext.require());
if (!hasText(apiKey)) apiKey = System.getenv("AI_API_KEY");
```

效果：**每个租户可以用自己的大模型 Key 和配额**，租户 A 用 DeepSeek、租户 B 用通义千问都行，系统设置页保存立即生效，无需重启。

## 七、那些绕不开的坑

### 坑 1：定时任务没有请求上下文

Scheduled 定时任务（如每 5 分钟 IMAP 拉取邮件）**不在 HTTP 请求线程里**，`TenantContext` 是空的。解决方案是遍历所有租户、逐个 `set/clear`：

```java
for (Tenant t : tenantRepository.findAll()) {
    TenantContext.set(t.getId());
    try { syncMailbox(t); } finally { TenantContext.clear(); }
}
```

同时把 `EmailInboxService` 的邮件拉取从"经 MCP HTTP 调用"改为**本地方法调用**，继承调用线程的上下文——一个隐蔽的坑：如果走 HTTP 自调，子请求又会是新线程，租户上下文照样丢。

### 坑 2：MCP 外部工具没有登录态

后端通过 Spring AI 暴露 MCP Server 端点，`@McpTool` 工具（如邮件发送）会被外部 Agent 调用——**外部调用者没有 JWT，拿不到租户上下文**。处理：MCP 工具绑定默认租户 1，外部 Agent 操作落在约定租户内。

### 坑 3：公开端点需要显式传租户

`/api/unsubscribe`（退订）和 `/api/track/**`（邮件追踪像素）是**免登录公开接口**，没有 JWT。处理：URL 带 `tenantId` 参数定位租户，缺失时回退默认租户 1。

### 坑 4：平台级 admin 的边界

初始 `admin`（tenant_id=NULL）是平台级账号，登录后访问租户级接口会因 `TenantContext.require()` 抛 400「当前账号无租户上下文」——这是**预期行为**，前端优雅降级只显示全局统计（登录统计等平台级接口正常）。

## 八、前端改造：注册页 + 去激活

前端改动相对小但全面：

- 新增 `Register.tsx`：用户名 3-32 位字母数字下划线 / 密码 ≥8 位 / 两次一致 / 显示名与公司名选填，**注册即登录直达工作台**
- 删除 `Activate.tsx` + `/activate` 路由；登录页 License 提示改为「免费注册」链接
- Nav/Dashboard/Help 去掉 License 逻辑，清理 license-\* 样式

## 九、验证：E2E 全链路，一个分支都不能漏

改造这种核心架构，验证必须覆盖到每个分支。我用 Playwright 做 DOM 几何测量（`getBoundingClientRect()` 检查溢出/重叠，而非截图）逐条验证：

**注册校验分支**：空表单 →「请输入用户名」；`ab` →「用户名需 3-32 位…」；两次密码不一致 →「两次输入的密码不一致」；重复用户名 → 400「用户名已存在」；短密码 → 400「密码至少 8 位」✅

**主流程**：注册 `tenant_a` 成功自动跳工作台，导航显示租户管理员菜单；默认配置初始化 21 项；`tenant_a` 创建客户后注册 `tenant_b`，客户列表「共 0 条」——**数据隔离生效** ✅

**License 移除**：带 token 访问 `/api/license` → 404；`/app/activate` 无路由；全页面无 License 横幅 ✅

**平台 admin**：`admin` 登录成功，租户级接口优雅降级，登录统计正常 ✅

**页面遍历 + DOM 测量**：8 个业务页面全部正常打开、无 License 残留文案、无元素溢出/重叠 ✅

## 十、小结

这次改造给我最大的三点体会：

1. **SaaS 化的第一优先级是数据隔离，不是计费**。先把 `tenant_id` 从表、到 JWT、到拦截器、到 Service 层贯穿起来，隔离做扎实了，后面加任何商业化功能都稳。
2. **线程上下文（ThreadLocal）是一把双刃剑**。它让 Service 层代码几乎零侵入地拿到租户，但定时任务、MCP 外部调用、子线程这些"没有请求上下文"的场景必须逐个排查，漏一个就是跨租户数据泄露。
3. **删除比新增更考验工程能力**。移除 License 不只是删代码，还要处理"没有拦截器兜底后未知接口变 500"这种连锁反应，以及全前端去残留文案。

---

**一点广告**（放在最后，不打扰正文）：这套系统现在做成了 **AI 智能获客助手**，支持注册即用——AI 潜客挖掘、个性化邮件/微信触达、合规退订管理、登录与邮件数据复盘全部内置，数据按租户完全隔离。如果你也在做 B2B 获客，或者对「本地部署产品 → SaaS 多租户改造」这个话题感兴趣，欢迎一起交流。

> 项目地址：https://github.com/yuezu1026/sales-agent

---

_本文是一次真实 SaaS 改造的技术复盘，所有实现细节均来自线上项目。如果你喜欢这类「全栈产品落地」的内容，欢迎点赞收藏，后续会继续分享 Spring AI 2.0 实战、MCP 集成、登录统计曲线等细节。_
