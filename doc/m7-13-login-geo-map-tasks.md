# M7.13 任务清单：工作台访问者全国地图地理点状分布图

> 状态：✅ 已完成（2026-08-11）
> 需求来源：用户「能否在工作台页面，显示访问者在全国地图的地理点状分布图？」

---

## 一、需求原文

在工作台（Dashboard）页面展示访问者（登录者）在全国地图上的**地理点状分布图**。

---

## 二、设计决策（用户已确认）

| 项       | 决策                                                                                                                                             |
| :------- | :----------------------------------------------------------------------------------------------------------------------------------------------- |
| 数据源   | 登录时记录客户端 IP（nginx 已透传 X-Forwarded-For/X-Real-IP），用 **ip2region.xdb 离线库**解析归属地（内置后端镜像，零外部依赖，国内城市级精度） |
| 地图渲染 | **自托管中国地图 SVG**：阿里 DataV GeoJSON 构建期转 SVG 路径打进前端 bundle，零运行时外部请求，与项目现有纯 SVG 图表风格一致                     |
| 历史数据 | **只统计新登录**（改造上线后的登录才有 IP/地理点），历史记录 ip/geo 为 NULL，地图空态提示                                                        |
| 表结构   | V20 迁移：login_logs 增加 `ip VARCHAR(64)` + `geo VARCHAR(128)`（存 ip2region 原始串，登录时一次解析固化）                                       |
| 接口     | `GET /api/auth/login-geo` → `{ points: [{province, city, count}] }`，JWT 鉴权（仅工作台用）                                                      |
| 前端     | 新建 `ChinaMap.tsx`（SVG 地图 + 散点，点半径随次数缩放，hover 提示），Dashboard 新增「访问者地理分布」卡片                                       |
| 坐标表   | 构建期脚本从 DataV 各省 GeoJSON 计算地级市质心（ Mercator 投影），生成 `chinaMapData.ts`（地图路径 + 市/省质心坐标表）；市名缺失时回退省会       |

---

## 三、改动清单

- [x] 资源：`backend/src/main/resources/ip2region.xdb`（~11MB 离线 IP 库）
- [x] 后端 `pom.xml`：新增 `org.lionsoul:ip2region` 依赖
- [x] 后端 `V20__login_log_ip.sql`：login_logs 加 ip / geo 列
- [x] 后端 `LoginLog.java`：实体加 ip / geo 字段
- [x] 后端 `IpGeoService.java`：新建，xdb 内存加载 + IP→省/市解析（内网 IP 跳过）
- [x] 后端 `AuthController.java`：登录时提取真实 IP 并解析入库；新增 `/login-geo` 聚合接口
- [x] 后端 `LoginLogRepository.java`：新增 `countByGeo()` 聚合查询
- [x] 脚本 `scripts/gen_china_map.py`：GeoJSON → SVG 路径 + 城市质心坐标表 → `chinaMapData.ts`
- [x] 前端 `chinaMapData.ts`：生成物（地图 path + 坐标表）
- [x] 前端 `ChinaMap.tsx`：SVG 地图散点组件
- [x] 前端 `Dashboard.tsx`：新增地理分布卡片
- [x] 前端 `styles.css`：地图卡片样式
- [x] 前端 `ChinaMap.tsx`：散点半径缩小（2.5~8 viewBox 单位，原 5~18）
- [x] 前端 `nginx.conf`：index.html 禁缓存 + assets 长缓存（修复手机浏览器缓存旧 HTML 导致地图不显示）
- [x] 构建 + 部署（后端/前端镜像重建）
- [x] E2E：地图渲染、散点位置、空态、DOM 测量无溢出/重叠（桌面 + 手机视口）

---

## 四、验证记录

### 4.1 本地构建

- 后端 `mvn compile`：EXIT=0，无 IDE 报错
- 前端 `npm run build`：EXIT=0，产物 `index-pDXOYAfY.js`（含地图数据，gzip ~313KB）
- ip2region 3.3.7 API 实测（IpTest.java）：5 个真实 IP 解析格式正确，如 `中国|江苏省|南京市|0|CN`

### 4.2 服务器部署（43.153.229.106）

- 后端 7 文件 + 前端 4 文件 scp 上传成功；`ip2region.xdb` 校验 11,122,152 字节
- `docker compose build backend` 成功（镜像 483MB）→ `up -d --no-deps backend`
- Flyway 日志：`Migrating schema "public" to version "20 - login log ip"` → `now at version v20` ✅
- 启动日志：`ip2region.xdb 已载入内存（离线 IP 定位就绪）` ✅
- `docker compose build frontend` + 重启成功；前后端容器同在 `ai-customer-deploy_default` 网络（无网络分裂）

### 4.3 E2E（https://sales-agent.top/app）

1. ✅ 新 bundle 加载后，工作台出现「访问者地理分布」卡片 + 中国地图 SVG
2. ✅ 空态：无地理记录时显示「暂无带地理位置的登录记录」提示
3. ✅ 接口 `GET /api/auth/login-geo`：未登录 401；登录后 200
4. ✅ 重新登录（admin/Admin@123456）产生新记录 → 接口返回 `{"province":"台湾省","city":null,"count":1}`（服务器云 IP 定位结果）
5. ✅ 地图渲染散点 1 个，hover tooltip 显示「台湾省 登录 1 次」
6. ✅ DOM 测量：SVG(760×600) 与圆点完全在卡片内、无横向溢出（bodyScrollWidth 981 < 视口 996）、圆点未超出 SVG 边界

### 4.4 手机不显示问题排查（浏览器缓存，非网络/代码）

**现象**：手机屏幕看不到地图卡片。

**排查过程**（逐项排除）：

1. ❌ 非网络问题：新 bundle `index-Cwza4_H2.js` 可访问、gzip 正常
2. ❌ 非 Service Worker：注册数为 0
3. ❌ 非 gateway proxy_cache：未配置
4. ✅ **根因 = 浏览器 HTTP 缓存**：手机缓存了旧 `index.html`，其引用的旧 bundle `index--quFZ8R6.js` 在服务器上已删除 → 404 → 地图卡片不渲染、`login-geo` 请求不发出

**修复**（已部署并验证）：

- `frontend/nginx.conf` 新增：`location = /index.html { add_header Cache-Control "no-cache"; }` + `location /assets/ { add_header Cache-Control "public, max-age=31536000, immutable"; }`
- 线上验证：`index.html` → `Cache-Control: no-cache`；assets → `max-age=31536000, immutable` ✅

**手机视口 E2E**（390×844，加载新 bundle 后）：

- 卡片 351×353、SVG 297×235、2 个散点（5×5、6×6）
- dotsOutsideSvg=0、svgOverflowCard=false、horizontalScroll=false ✅ 全部通过

**用户需知**：手机浏览器仍持有旧缓存的 `index.html`，需**手动清除一次浏览器缓存（或强制刷新）**才能看到地图；之后每次发版都会自动拉取最新 HTML，不再复发。

### 4.5 手机"刷新可见、重开不可见"二次排查（no-cache 力度不足）

**现象**：用户反馈手机端"刷新（reload）可见地图，重新打开链接（普通导航）又看不到"。

**根因**：`no-cache` 语义是"使用前重新验证"，但国内部分手机浏览器（微信/夸克/UC/华为等内置 WebView）对无 `Cache-Control` 的**旧缓存条目**采用启发式缓存（时长可达数天），普通导航直接命中旧条目**不发请求**；而"刷新"是 reload 语义会强制 revalidate → 拿到新版。故出现"刷新可见、重开不可见"。

**修复**（已部署并验证，双保险）：

1. `frontend/nginx.conf`：HTML 入口缓存头升级为最强组合
   ```nginx
   location = /index.html {
       add_header Cache-Control "no-store, no-cache, must-revalidate, max-age=0";
       add_header Pragma "no-cache";
       add_header Expires "0";
   }
   ```
2. `frontend/index.html`：构建产物内嵌 3 个 meta 缓存标签（`Cache-Control: no-cache, no-store, must-revalidate` / `Pragma: no-cache` / `Expires: 0`），对不信任响应头的 WebView 兜底
3. 宣传站入口链接 `/app/login` → `/app/index.html`（**全新 URL 绕过手机里缓存的旧条目**，4 处按钮全部替换）
4. assets 长缓存 `public, max-age=31536000, immutable` 保持不变

**线上验证**（部署后）：

- `/app/`、`/app/index.html`：`cache-control: no-store, no-cache, must-revalidate, max-age=0` + `pragma: no-cache` + `expires: 0` ✅
- `/app/assets/index-Cwza4_H2.js`：`public, max-age=31536000, immutable` ✅
- 构建产物 index.html 含 3 个 meta 缓存标签 ✅；宣传站首页入口为 `/app/index.html` ✅
- 手机视口（390×844）：地图卡片 + SVG(297×235) + 2 散点，无溢出/重叠 ✅

**用户操作**：手机里旧的 `/app`、`/app/login` 缓存条目服务器端无法主动清除，需**清一次手机浏览器缓存**；此后从宣传站点"免费试用"走 `/app/index.html` 新入口，或任意入口访问都会强制拉取最新版，不再复发。

---

## 五、交付

| 项   | 说明                                                                                                                      |
| :--- | :------------------------------------------------------------------------------------------------------------------------ |
| 功能 | 工作台新增「访问者地理分布」卡片：中国地图 SVG + 按登录 IP 离线定位的散点分布，点半径随登录次数缩放，hover 显示省市与次数 |
| 数据 | 仅统计新版本上线后的登录（历史记录 ip/geo 为 NULL，不计入）                                                               |
| 接口 | `GET /api/auth/login-geo`（JWT 鉴权）→ `{ points: [{province, city, count}] }`                                            |
| 依赖 | ip2region 3.3.7 离线库（内置镜像，零外部请求）；前端纯 SVG，零运行时外部依赖                                              |
| 迁移 | Flyway V20（login_logs 加 ip/geo 列），已随部署自动应用                                                                   |
| 状态 | ✅ 已部署上线，E2E 通过                                                                                                   |
