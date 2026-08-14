# 修复任务：/app/ 子路径静态资源全部 404（logo/图标消失）

> 状态：✅ 已完成（2026-08-14）
> 类型：线上 Bug 修复（nginx location 优先级）

---

## 一、问题现象（用户反馈）

服务器上登录后页面的图标（logo）不见了，疑似被删除。

## 二、排查过程与根因（已登录服务器 43.153.229.106 实测）

### 1. 文件没被删除

容器内静态资源完好（`sudo docker exec aic-frontend ls /usr/share/nginx/html/`）：

```
logo.svg      1171B   Aug 13 12:01
favicon.svg    503B   Aug 13 12:01
alipay.jpg / wechat-pay.jpg / assets/  均在
```

### 2. 容器直连正常，经外层 nginx 即 404

| 访问方式                                        | 结果                         |
| :---------------------------------------------- | :--------------------------- |
| 容器直连 `:8081/logo.svg`                       | 200 image/svg+xml 1171B ✅   |
| HTTPS `https://sales-agent.top/app/logo.svg`    | **404** 162B text/html ❌    |
| HTTPS `https://sales-agent.top/app/favicon.svg` | **404** ❌                   |
| HTTPS `https://sales-agent.top/app/assets/*.js` | **404** ❌                   |
| HTTPS `https://sales-agent.top/app/`（index）   | 200 ✅（无扩展名，未被拦截） |

### 3. 根因：外层 nginx 正则 location 抢走 /app/ 请求

外层站点配置 `/etc/nginx/sites-enabled/sales-agent` 于 **2026-08-14 的 "P2 优化"**
新增了宣传站静态资源缓存的正则 location：

```nginx
location ~* \.(?:css|js|svg|png|jpg|jpeg|gif|webp|ico|woff2?)$ {
    expires 1h;
    try_files $uri =404;
}
```

nginx 匹配规则：**正则 location 优先于普通前缀 location**（除非前缀带 `^~`）。
因此 `/app/logo.svg`、`/app/assets/*.js` 等请求先命中 `\.svg$`/`\.js$` 正则，
被该 location 以 `root /var/www/sales-agent`（宣传站目录）处理 →
`/var/www/sales-agent/app/logo.svg` 不存在 → `try_files $uri =404` → **404**。

原本应命中的 `location /app/ { proxy_pass http://127.0.0.1:8081/; }` 被完全绕过。

### 4. 为什么页面还能打开、只是图标没了

- `/app/`（index.html 入口）无扩展名 → 不匹配正则 → 仍走 `/app/` 前缀代理 ✅
- 应用 JS/CSS bundle 此前已被浏览器按 `max-age=31536000, immutable` 长缓存，
  不再重新请求 → 应用主体仍可运行（**硬刷新会整页白屏，隐患极大**）
- `logo.svg`/`favicon.svg` 无长缓存 → 每次真实请求 → 404 → 图标裂掉

## 三、修复方案

给 `/app/` 与 `/api/` 前缀 location 加 `^~` 修饰符，阻止正则 location 抢占：

```nginx
location ^~ /app/ { ... }   # 产品前端
location ^~ /api/ { ... }   # 产品后端 API（防御性一并处理）
```

`^~` 语义：若该前缀匹配成功，则不再检查正则 location —— 宣传站的正则缓存规则
只作用于宣传站自身资源，互不干扰。

## 四、改动清单

- [x] 服务器 `/etc/nginx/sites-enabled/sales-agent`：
      `location /app/` → `location ^~ /app/`；`location /api/` → `location ^~ /api/`
      （脚本 `scripts/m810-fix-nginx.sh`；修改前备份至 `/etc/nginx/backups/sales-agent.bak-20260814`，`nginx -t` 通过后 reload）
      ⚠️ 踩坑：备份文件起初放在 `sites-enabled/` 内被 nginx 一并加载，报
      `duplicate default server` → 备份必须放 `sites-enabled/` 之外（已移至 `/etc/nginx/backups/`）
- [x] 仓库 `gateway/nginx.server-ssl.conf` 同步为服务器最新配置（含 P2 优化 + `^~` 修复），保持仓库与服务器一致

## 五、验证记录

- [x] `https://sales-agent.top/app/logo.svg` → 200 image/svg+xml 1171B
- [x] `https://sales-agent.top/app/favicon.svg` → 200 image/svg+xml 503B
- [x] `https://sales-agent.top/app/assets/Dashboard-BCG98riK.js` → 200 application/javascript 323KB
- [x] `https://sales-agent.top/app/` → 200（应用入口正常）
- [x] `https://sales-agent.top/api/health` → 200；宣传站 `/` → 200 36KB（未受影响）
- [x] 浏览器 E2E（DOM 测量，非截图）：
  - `/app/login`：logo `complete=true`、`naturalWidth=150`（真实加载），显示 48×48，无横向溢出
  - 登录后工作台 `/app`：导航 logo `naturalWidth=150`、32×32 位于导航栏内，`scrollWidth==clientWidth` 无横向溢出，全部菜单/卡片渲染正常

## 六、经验教训

- nginx 子路径反代站点新增**正则 location** 时，必须确认不会抢走子路径请求；
  反代前缀 location 一律用 `^~` 加固
- 服务器 nginx 配置变更必须同步回仓库 `gateway/`，避免配置漂移
