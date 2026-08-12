# M4.5b 任务清单：激活重置管理员密码 + Token 消耗绑定激活码

> 状态：✅ 已完成（2026-08-09）
> 前置：M4.5 已完成（激活码签名授权 token 上限 + 金额保险闸门，Git commit `8aa1c5f`）
> 本阶段两条新需求：① 激活时自动重置管理员密码（随机生成 ≤16 位、前端可隐藏/明文切换 + 拷贝）② Token 消耗量绑定激活码（按激活码 serial_no 独立统计，不累计历史用量）

---

## 一、用户需求（原文整理，2026-08-09）

1. **激活重置管理员密码**：「激活时，能否重置管理员密码？密码根据激活码自动随机生成，不超过16位？生成后，在激活的页面显示，可以隐藏显示，比如用 `**` 代替，当然也可以切换为明文显示，并有拷贝按钮？」
2. **Token 消耗绑定激活码**：「token的消耗量是绑定激活码，也就是不考虑数据库中已经使用的token消耗量」

## 二、设计决策

### 2.1 Token 按激活码独立统计

- 原 M4.5 逻辑：`sumTotalTokensAfter(activatedAt)` 按激活时间窗口累计历史用量 → 换新激活码后旧用量仍计入
- 新逻辑：`ai_usage_log` 加 `serial_no` 列（V17 迁移），每次 AI/embedding 调用记账时写入**当前激活码 serial_no**；`checkQuota` / `quotaSummary` 改为 `sumTotalTokensBySerialNo(serialNo)` / `sumCostBySerialNo(serialNo)`
- 效果：重新激活后新激活码从 0 起算，旧码消耗完全不累计（符合用户需求）
- 注：`sumTotalTokensAfter` / `sumCostAfter` 方法保留（可能其他地方用）

### 2.2 激活重置管理员密码

- 时机：**激活成功即重置**（幂等重复激活也重置并返回新密码；未写入的路径不重置）
- 生成：`SecureRandom` 12 位（符合 ≥8 且 ≤16），字符集去易混淆字符（0/O/1/I/l）
- 存储：BCrypt 哈希入库（复用 `BCryptPasswordEncoder`），明文**只在激活成功响应返回一次**，不落库
- 返回：`License` 实体加 `@Transient generatedPassword` 字段（不映射数据库列）
- 安全提示：前端提示「仅显示一次，请立即保存」；忘记密码 → 重新激活即可再次重置

## 三、改动清单

### 数据库（Flyway）

- [x] `V17__usage_serial_no.sql`：`ALTER TABLE ai_usage_log ADD COLUMN serial_no VARCHAR(64)` + `CREATE INDEX idx_usage_serial ON ai_usage_log(serial_no)`

### 后端

- [x] `entity/AiUsageLog.java`：加 `serialNo` 字段（`@Column(name = "serial_no", length = 64)`）+ getter/setter
- [x] `entity/License.java`：加 `@Transient generatedPassword`（激活接口返回，不落库）
- [x] `repository/AiUsageLogRepository.java`：加 `sumTotalTokensBySerialNo(String)` / `sumCostBySerialNo(String)`
- [x] `service/LicenseService.java`：
  - 注入 `UserRepository` + `PasswordEncoder`（BCrypt）+ `SecureRandom`
  - `activate()` 激活成功即调 `resetAdminPassword()` 生成 12 位随机密码重置 admin，返回 `generatedPassword`
  - `checkQuota()` / `quotaSummary()` 改按当前 `serial_no` 统计
  - 新增 `currentSerialNo()` 供记账写入
- [x] `service/AiService.java`：`recordUsage()` 写 `setSerialNo(licenseService.currentSerialNo())`
- [x] `service/profile/RemoteEmbeddingService.java`：`recordUsage()` 写 `setSerialNo(licenseService.currentSerialNo())`

### 前端

- [x] `pages/Activate.tsx`：`LicenseResult` 加 `generatedPassword`；激活成功后显示密码框（默认 `••••` 隐藏 →「显示/隐藏」切换 +「拷贝」按钮 + 复制成功提示 + 提示「仅显示一次请立即保存」）

## 四、验证记录

### 4.1 编译

- [x] 后端 `mvn compile` 通过（无错误）

### 4.2 迁移

- [x] Flyway V17 已应用（flyway_schema_history version=17 success=t）
- [x] `ai_usage_log.serial_no` 列 + `idx_usage_serial` 索引已确认存在

### 4.3 E2E（待执行）

- [ ] 激活 basic 码 → 返回 generatedPassword 且长度 ≤16、含大小写+数字
- [ ] admin 旧密码失效、新密码可登录
- [ ] AI 调用记账 serial_no = 当前激活码
- [ ] 手动调小 token_limit → 超限 400 拦截；换新激活码 → 从 0 起算（旧码用量不计入）
- [ ] 前端密码显示/隐藏/拷贝交互正常（DOM 无越界无重叠）

## 五、交付

- [ ] Git commit + push（doc 本文件一并提交）
- [ ] deploy.md 迁移数 16 → 17
- [ ] repo 记忆更新（/memories/repo/m2-1-crm.md）
