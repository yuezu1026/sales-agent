# M8.2 任务清单：手机端登录页加载慢优化（代码分割 + 压缩 + 带宽排查）

> 状态：✅ 已完成（2026-08-13）
> 需求来源：用户「服务器上点击立即免费试用按钮，跳到登录页，在手机端非常非常慢。请帮我检测是不是如此？怎么好像有第三方在线字体，可以本地化不？按你的建议来」

---

## 〇、需求原文

1. 服务器上点「立即免费试用」→ 登录页，手机端**非常非常慢**
2. 怀疑有第三方在线字体，问能否本地化
3. 按建议执行优化

## 一、检测结论（2026-08-13 实测）

### 1.1 无第三方字体（排除）

| 页面                  | 字体来源                                                                         | 结论          |
| :-------------------- | :------------------------------------------------------------------------------- | :------------ |
| 宣传站 landing（`/`） | `--font: "PingFang SC","Microsoft YaHei","Segoe UI",Arial,sans-serif` 纯系统字体 | ✅ 无在线字体 |
| 登录页应用（`/app`）  | index.html 无字体 link、styles.css 无 @font-face、bundle 无字体                  | ✅ 无在线字体 |

- 宣传站 `<link rel="preconnect" href="https://fonts.googleapis.com">` 为**无用残留**（只预连接从未拉取），非慢因，但可顺手删除

### 1.2 真正的瓶颈：服务器带宽极低

- JS bundle（gzip 后 362KB）实测下载：
  - 第 1 次：**42.8s**（8.6 KB/s）
  - 第 2 次：60s 只下完 81KB（**1.3 KB/s**）
- 首字节仅 0.63s → 服务器响应快，纯传输带宽被卡死（连 1Mbps 都达不到）
- 手机端 4G/5G 访问同一服务器只会更慢 → 与用户现象吻合

## 二、设计决策

| 项     | 决策                                                                                                                                                                              |
| :----- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 前端   | **路由级代码分割**（`React.lazy` + `Suspense`）：登录页首屏只加载 Login chunk，主 bundle 大幅缩小                                                                                 |
| 压缩   | 容器内 nginx 已带 gzip（外层 nginx gzip on）；检查是否可开 Brotli（需 ngx_brotli 模块，官方 nginx 镜像默认无 → 大概率保持 gzip + 确认已开启）                                     |
| 带宽   | SSH 上服务器查 nginx 访问日志/流量，确认带宽规格（腾讯云控制台）                                                                                                                  |
| 字体   | 删除 landing 无用 `preconnect fonts.googleapis.com`（顺手）—— 未执行，宣传站静态文件在服务器 /var/www/sales-agent（不在仓库内），不影响登录页性能，暂缓                           |
| Brotli | **未开启**：宿主机 nginx `-V` 仅 `--with-http_gzip_static_module`；容器官方 nginx 镜像无 ngx_brotli 模块。开启需换第三方镜像/重编译，收益仅 10-15% 且带宽是硬瓶颈 → **维持 gzip** |

## 三、改动清单

- [x] `frontend/src/App.tsx`：全部 17 个页面组件改 `React.lazy` + `<Suspense>` fallback（`.page-loading`「加载中…」）
- [x] `frontend/nginx.conf`：容器内 nginx 加强 gzip（`gzip on; gzip_comp_level 6; gzip_min_length 1k; gzip_vary on; gzip_types ...`）
- [x] `scripts/sales-agent.nginx`：宿主机 nginx gzip 参数增强（`gzip_comp_level 6; gzip_min_length 1k; gzip_vary on`）
- [ ] 宣传站 index.html：删除 `preconnect fonts.googleapis.com`（服务器 /var/www/sales-agent，仓库外，暂缓）
- [x] 本地构建验证：tsc exit 0；主 chunk 362KB gzip → **56.85KB gzip**（5.8x）；Login chunk 仅 1.28KB
- [x] 服务器带宽排查：access.log 今日 5.5MB 总传输，最大响应为旧 bundle 反复下载，无爬虫占带宽
- [x] Brotli 检测：宿主机与容器均无 brotli 模块（nginx -V 实测）→ 维持 gzip
- [x] 部署服务器：scp 源码 → `sudo docker compose build frontend` → `sudo docker compose up -d --no-deps frontend`（**docker 命令必须加 sudo**，ubuntu 不在 docker 组）
- [x] E2E：手机视口 375×812 完整流程验证（见下）

## 四、验证记录

### 4.1 线上 gzip 效果（服务器 curl 实测）

| 资源                         | gzip 传输大小 | 备注                 |
| :--------------------------- | :------------ | :------------------- |
| 主 chunk `index-CC6-0cK6.js` | 66KB          | 旧版 362KB → 5.5x    |
| Login chunk                  | 1.3KB         | 登录页首屏只需这个   |
| Dashboard chunk              | 119KB         | 懒加载，登录页不加载 |

### 4.2 手机视口 E2E（375×812，2026-08-13）

| 检查项            | 结果                                                                                           |
| :---------------- | :--------------------------------------------------------------------------------------------- |
| 宣传站→登录页全程 | ~11.6s（主 chunk 66KB 下载 8.2s = 8KB/s 带宽瓶颈）；旧版 362KB/42.8s → **快 3.7 倍**           |
| Suspense fallback | 「加载中…」正常显示                                                                            |
| 登录页 DOM        | ✅ 无溢出、无重叠、无横向滚动（页面正好一屏 812px）                                            |
| 空值校验          | ✅ 提示「请输入用户名和密码」，不跳转                                                          |
| 错误密码          | ✅ 提示「用户名或密码错误」（400 业务校验，**不登出不跳转**）                                  |
| 正确密码          | ✅ 登录成功进工作台，登录统计 +1（累计 143 / 今日 8）                                          |
| 退出登录          | ✅ 回到登录页                                                                                  |
| 导航 tab（3 个）  | ✅ 用户管理/捐助/帮助均正常（懒加载 chunk 生效）                                               |
| 返回首页          | ✅ 回到宣传站                                                                                  |
| Dashboard DOM     | ✅ 无全局溢出；`.nav-links` scrollWidth 384>clientWidth 308 为**有意的横向滚动设计**（非 bug） |

## 五、交付

- 路由级代码分割上线：登录页首屏 JS 362KB gzip → 66KB gzip（旧版加载 42.8s → 新版 ~8s，带宽瓶颈下快 3.7-4 倍）
- 容器 nginx gzip 加强 + 宿主机 nginx gzip 参数增强（配置文件已留档 `scripts/sales-agent.nginx`）
- Brotli 评估结论：未开启，官方镜像无模块，收益 <15% 且带宽是硬瓶颈 → 不建议换镜像，待带宽/CDN 问题解决后再议
- **遗留问题**：服务器出网带宽被限（~8KB/s），这是根本瓶颈。建议：① 腾讯云控制台确认带宽规格并升级 ② 静态资源上 CDN（如腾讯云 COS+CDN）
- 顺手项未做：宣传站 `/var/www/sales-agent/index.html` 的 `preconnect fonts.googleapis.com` 残留（仓库外，暂缓）
