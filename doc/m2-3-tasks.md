# M2-3 任务清单：RAG 客户画像（CSV 导入向量化 + 检索打分）

> 对齐《MVP 核心功能规划》第四章 M2 第三步：RAG 客户画像
> 周期：1.5 周　状态：✅ 已完成（2026-08-09）
> 验收标准：CSV 导入历史成交客户 → 自动向量化入库 → 潜客检索打分（profile_score）→ 语义检索画像库

---

## 一、任务总览

| 项       | 内容                                                                                               |
| :------- | :------------------------------------------------------------------------------------------------- |
| 目标     | customer_profile 表 + CSV 导入向量化 + 检索打分（profile_score），为潜客挖掘提供画像相似度依据     |
| 向量方案 | 双重方案：默认本地 TF-IDF（零外部依赖）+ 可选远程 OpenAI 兼容 embedding（配置 ai.embedding_model） |
| 检索打分 | 潜客特征（公司名+行业+区域+规模+备注）→ 向量 → 与画像库 topMatch 余弦相似度 → profile_score(0-100) |
| 存储     | 向量统一存 JSON 文本 `{"dim":768,"data":[...]}`（db-design 约定）；余弦相似度维度不一致视为 0      |
| 权限     | 沿用单角色 admin，全部接口走 AuthInterceptor（/api/\*\* 需 Bearer token）                          |

---

## 二、向量化技术选型（关键决策）

> 背景：DeepSeek **无 embedding API**，Spring AI 的 OpenAiEmbeddingModel 需要 OpenAI 兼容端点。

| 方案 | 说明                                                                | 适用场景                            |
| :--- | :------------------------------------------------------------------ | :---------------------------------- |
| 本地 | `LocalTfidfEmbeddingService`：特征哈希 768 维，零外部依赖，离线可用 | 默认方案（ai.embedding_model 留空） |
| 远程 | `RemoteEmbeddingService`：OpenAI 兼容端点（OpenAiEmbeddingModel）   | 配置 ai.embedding_model 后启用      |

`EmbeddingRouter` 统一路由：`ai.embedding_model` 非空 → 远程；否则 → 本地。

---

## 三、后端实现

### 3.1 数据层（Flyway V9）

- `V9__customer_profile.sql`：customer_profile 表
  - id BIGSERIAL PK / company_name VARCHAR(128) NOT NULL / industry / contact_name / contact_email / deal_value NUMERIC(12,2) / tags VARCHAR(255) / description TEXT / embedding TEXT / created_at TIMESTAMPTZ
  - 唯一索引 `uk_cp_company ON LOWER(company_name)`、`idx_cp_email`
- `entity/CustomerProfile.java`（JPA 实体，字段对齐）
- `repository/CustomerProfileRepository.java`：`findByCompanyNameIgnoreCase` / `existsByCompanyNameIgnoreCase` / `findAllByOrderByIdDesc`

### 3.2 向量服务（service/profile 包）

- `ProfileEmbeddingService`：接口 `name()/embed(text)/toJson/fromJson/cosine(a,b)`（cosine 为 default 方法）
- `LocalTfidfEmbeddingService`（DIM=768）：
  - WORD 正则 `[a-z0-9]+`（过滤单字母）+ CHINESE 正则 `[\u4e00-\u9fa5]+`（单字符 0.5、双字 ngram 1.0）
  - FNV-1a 哈希 `Math.floorMod(h, DIM)` 映射特征；L2 归一化
  - toJson 保留 6 位小数；fromJson 校验 dim==768
- `RemoteEmbeddingService`：读 `ai.api_key`（AES 解密）+ `ai.base_url` + `ai.embedding_model`
  - `OpenAiEmbeddingModel.builder().options(...).build()` → `modelClient.embed(text)` → float[]→List\<Float\>
- `EmbeddingRouter`：`active()` 按 ai.embedding_model 路由；代理 embed/toJson/fromJson/cosine

### 3.3 画像服务

- `dto/ProfileSearchResult.java`：`record ProfileSearchResult(CustomerProfile profile, double score)`
- `CustomerProfileService`：
  - `importCsv(csv)` @Transactional 返回 {success, duplicate, errors:[{companyName,reason}]}
    - 重复公司（LOWER 判重）→ duplicate++ 跳过（不进 errors）
    - 公司名为空 → errors 记 "(空)"；dealValue 解析失败 → errors
    - 特征文本 = 公司名+行业+标签+描述（截断 500）→ embed → embedding JSON 落库
  - `list()` / `delete(id)` / `search(query, topN)`（默认 10 上限 20）/ `topMatch(queryVec, minScore)` / `buildFeatureText(p)`
  - 私有 `parseCsv`：跳过首行表头、支持 BOM/引号转义，列顺序 companyName/industry/contactName/contactEmail/dealValue/tags/description
- `LeadProfileScoringService`：构造注入 LeadRepository + CustomerProfileService + EmbeddingRouter
  - MIN_SCORE=0.05；`score(lead)`：特征文本（公司名+行业+区域+规模+备注）→ embed → topMatch
  - profile_score = round(sim\*100)；profile_summary = "相似画像：X（行业），相似度 N%，标签：..."
  - `scoreById(id)` / `scoreAll()` 返回 {total, scored, updated}

### 3.4 控制层

- `CustomerProfileController`（/api/profiles）：
  - POST /import（MultipartFile "file"）
  - GET 列表（倒序）
  - DELETE /{id}
  - GET /search?q=&top=（语义检索）
- `LeadController`（修改）：注入 LeadProfileScoringService
  - PUT /{id}/score（单条重算）
  - POST /score-all（全量重算）
- `ConfigController`（修改）：DEFAULT_CONFIGS 新增 `ai.embedding_model`（"向量模型（画像打分用），如 text-embedding-v3；留空使用本地向量"），现共 12 项

---

## 四、前端实现

| 区块       | 文件 / 内容                                                                                                         |
| :--------- | :------------------------------------------------------------------------------------------------------------------ |
| 画像管理页 | `pages/Profile.tsx`（路由 /profile + Nav「客户画像」）                                                              |
| CSV 导入   | 下载模板按钮（生成 BOM CSV：公司名称\*,行业,联系人,邮箱,成交金额,标签,描述）+ File input + FormData 上传 + 结果详情 |
| 语义检索   | 输入框（Enter 触发）+ 检索结果表（相似度/公司/行业/标签/联系人/成交金额，降序）                                     |
| 画像库列表 | 9 列（描述省略号截断），删除带 confirm                                                                              |
| 向量模式   | badge 显示「本地向量（TF-IDF）」或「远程向量（模型名）」，读 /api/config 的 ai.embedding_model                      |
| 一键打分   | 「一键重算潜客画像分」按钮 → POST /api/leads/score-all → toast「画像打分完成：共 N 条潜客，命中 M 条，更新 K 条」   |
| 客户画像分 | `pages/Customers.tsx`：列表新增「画像分」列，彩色 badge（≥50 绿 / ≥25 黄 / 其余灰），悬浮 title 显示相似画像摘要    |
| 样式       | `.table-wrap .table.profile-table`（min-width 860px，内部横向滚动）；th min-width auto                              |

---

## 五、E2E 验证（2026-08-09，DOM 测量全部通过）

- ✅ 后端 API：导入 3 条（晨曦医疗/数澜科技/云启软件）成功，vector 落库 JSON 文本
- ✅ 语义检索「做智能客服系统的软件公司」→ 云启软件 42% / 晨曦医疗 7% / 数澜科技 4%（排序正确）
- ✅ score-all：{total:4, scored:4, updated:4}；lead id=4 profile_score=8（相似数澜科技）、id=5 profile_score=24（相似云启软件）保持
- ✅ 一键重算 UI：toast「画像打分完成：共 4 条潜客，命中 4 条，更新 0 条」（无变化不重复更新）
- ✅ 客户管理页「画像分」列：24/8/20/30 分 badge 展示，悬浮 title 显示「相似画像：云启软件（企业服务SaaS），相似度 24%，标签：AI客服 数字化」
- ✅ CSV 导入分支：空文件 → 错误 toast；带重复+空公司名 CSV → success 1 / duplicate 1 / errors 1（真错误）
  - 修复记录：重复公司曾误计入 errors 导致「失败 N 条」虚高 → 改为 duplicate++ 跳过
- ✅ 删除闭环：confirm（取消/确认）→ toast「已删除画像」→ 列表刷新（5→4→3 条）
- ✅ 下载模板：blob 下载「客户画像模板.csv」
- ✅ 系统设置页：ai.embedding_model 配置项出现（12 项）
- ✅ 布局：profile 页 `.table-wrap` 内部横向滚动（scrollWidth 881 > clientWidth 746 为设计意图），页面无横向溢出，按钮零重叠；customers 页/settings 页同测通过
- ✅ 前端 `npx tsc -b` 通过（frontend 目录执行）

---

## 六、遗留/后续

- ❌ 真实 embedding 端点配置（需在系统设置填 ai.api_key + ai.embedding_model，如 text-embedding-v3）
- ❌ 画像自动补充：潜客挖掘入库后自动触发打分（当前为「一键重算」手动触发 + 单条 rescore 接口）
- ❌ 画像库批量删除 / 分页（当前列表全量返回）
- ❌ 向量检索换用 pgvector 原生索引（当前为内存余弦计算，画像量级大后再优化）
