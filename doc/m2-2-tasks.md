# M2-2 任务清单：数据源对接（Function Calling 潜客挖掘）

> 对齐《MVP 核心功能规划》第二章 2.1 与第四章 M2 第二步：数据源对接
> 周期：2 周　状态：✅ 已完成（2026-08-09）
> 验收标准：配置数据源 → 输入挖掘条件 → Function Calling 拉取 → 人工筛选入库（source_type=api）→ 客户列表可见

---

## 一、任务总览

| 项       | 内容                                                                                  |
| :------- | :------------------------------------------------------------------------------------ |
| 目标     | data_source 表 + Function Calling 调 1 家合规 API 挖掘 → 写入 lead（source_type=api） |
| 数据源   | 内置 mock 演示源（15 家虚构企业）+ 企查查 qichacha（真实 API 对接骨架，可配置启用）   |
| 去重规则 | source_type+source_id 唯一；已入库条目在挖掘结果中置灰标记、checkbox 禁用             |
| 敏感项   | 数据源 API Key AES 加密落库（AesUtil），列表回显脱敏 ••••••                           |
| 权限     | 沿用单角色 admin，全部接口走 AuthInterceptor（/api/\*\* 需 Bearer token）             |

---

## 二、后端实现

### 2.1 数据层（Flyway V8）

- `V8__data_source.sql`：data_source 表（id/name/type/api_base_url/api_key_encrypted/enabled/created_at）
  - `uk_ds_type`：type 唯一（一个类型一个数据源）
  - 种子数据：内置演示数据源（mock，启用）、企查查（预留）（qichacha，停用）
- `entity/DataSource.java` + `repository/DataSourceRepository.java`
  - `findByEnabledTrueOrderByIdAsc()` / `findByType(String)` / `existsByType(String)`

### 2.2 服务层：Provider 抽象

- `service/prospect/CompanyDataProvider`：接口 `type()` + `search(ProspectQuery, int limit)`
- `MockCompanyDataProvider`（@Component，type=mock）：15 家虚构企业，条件过滤（containsIgnoreCase）
- `QichachaDataProvider`（@Component，type=qichacha）：真实 API 骨架
  - 读取 DataSource 配置（findByType + enabled + apiKeyEncrypted 判空）
  - AesUtil 解密 API Key → RestClient GET `{baseUrl}/api/company/search`
  - JSON 解析（递归找 data.result/data.items 数组）+ 字段别名映射
  - 失败抛 `BizException.badRequest`
- `ProspectService.search`：pickProvider 选择策略
  - 优先已启用且注册了 Provider 的数据源 → 无启用数据源时回退 mock → 均不可用时 badRequest
  - 结果标记 inLibrary（`leadRepository.existsBySourceTypeAndSourceId("api", sourceId)` 或 company_name 忽略大小写）

### 2.3 Function Calling 工具

- `ProspectTools`（@Component）：`@Tool(name="search_company")` 方法 searchCompany(industry/region/scale/keyword/limit)
  - 内部调 ProspectService.search → objectMapper 序列化为 JSON 字符串；BizException → `{"error":"..."}`
- `config/ProspectToolConfig`：`@Bean ToolCallbackProvider prospectToolCallbackProvider`
  - `MethodToolCallbackProvider.builder().toolObjects(prospectTools).build()`
  - 已注册为 Spring AI ToolCallbackProvider，ChatClient 注入后即可支持自然语言挖掘（当前表单直接调用 /api/prospect/search）

### 2.4 控制层

- `DataSourceController`（/api/data-sources）：列表（apiKeyMasked 脱敏）/ 新增（name/type 必填、type 判重）/ 编辑（type 不可改、API Key 传 MASK 或空则保持原值）/ 删除 / 启用切换
- `ProspectController`（/api/prospect）：
  - POST /search（industry/region/scale/keyword/limit，默认 limit 20）
  - POST /import（List\<ProspectCompany\>，空则 badRequest）→ @Transactional 批量入库
    - 返回 {success, duplicate, errors}；sourceId 缺省 `api:<company_name 小写>`
    - lead 设 sourceType="api"、status="new"、profileScore=0、notes="由「xx」数据源挖掘入库"

---

## 三、前端实现（pages/Prospect.tsx）

| 区块       | 内容                                                                                      |
| :--------- | :---------------------------------------------------------------------------------------- |
| 挖掘条件   | 行业 / 地区 / 规模 / 关键词 + 「开始挖掘」                                                |
| 结果表格   | checkbox 全选/单选、「导入所选（n）」；已入库行 opacity 0.55 + checkbox disabled + 徽标   |
| 数据源管理 | 表格（名称/类型/接口地址/API Key/状态/操作）+ 新增/编辑/删除/启用切换                     |
| 编辑 modal | 名称可改、类型编辑时 disabled、API Key password 输入（MASK 表示保持不变）、启用 checkbox  |
| 路由/导航  | /prospect + Nav 链接「潜客挖掘」                                                          |
| 样式       | .table-wrap .table.prospect-table（min-width 900px，内部横向滚动）、.data-source-table 等 |

---

## 四、E2E 验证（2026-08-09，DOM 测量全部通过）

- ✅ 挖掘页表单（行业=SaaS、地区=深圳）→ 开始挖掘 → 结果表格 2 家（云启软件/数澜科技）
- ✅ 布局：`.table-wrap` 内部横向滚动（overflow-x:auto），页面无横向溢出（docScrollWidth=视口）；按钮零重叠
- ✅ 全选未入库 → 导入所选 → toast「入库完成：成功 2 条，跳过重复 0 条」
- ✅ 再次挖掘同条件 → 对应行「已入库」置灰 + checkbox 禁用（去重分支）
- ✅ 客户管理页 → 新入库 2 条来源显示「API 导入」；测试数据 lead id=4（CSV 导入）/ id=5（手动录入）完好
- ✅ 数据源管理：编辑改名 → 保存；停用 mock → 挖掘回退内置演示（15 家）；恢复启用
- ✅ 新增数据源：空值校验（名称和类型不能为空）、类型判重（「数据源类型「mock」已存在」）、正常新增
- ✅ 删除：confirm 弹窗（取消/确认两分支）→ 「数据源已删除」
- ✅ API Key：AES 加密落库（DB 为密文 Rv6j...=），列表回显 ••••••，编辑时 MASK 保持原值
- ✅ qichacha 启用 → 真实 API 请求执行（无真实 key 返回空列表，不抛错）；恢复停用
- ✅ modal 布局：无溢出（rect 均在视口内）

---

## 五、遗留/后续

- ❌ 真实企查查 API Key 与配额（需商务采购后填入数据源配置启用）
- ❌ ChatClient 注入 ToolCallbackProvider 支持自然语言挖掘（工具已注册，可无缝接入）
- ❌ 天眼查/启信宝等其他数据源 Provider（复用 CompanyDataProvider 抽象，type 唯一即可）
