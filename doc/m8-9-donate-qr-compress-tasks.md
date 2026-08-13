# M8.9 任务清单：捐助收款码图片压缩（< 20KB）

> 状态：✅ 已完成（2026-08-13）
> 需求来源：用户「支付的图片是否可以再压缩一下大小？目前好像占的字节太多了」「能否压缩到 20k 以下？」

---

## 一、需求原文

1. 捐助页收款码图片（alipay.jpg / wechat-pay.jpg）字节数偏大。
2. 希望压缩到 20KB 以下。

---

## 二、现状分析

| 文件                             | 原尺寸    | 原大小   | 页面显示                                  |
| :------------------------------- | :-------- | :------- | :---------------------------------------- |
| `frontend/public/alipay.jpg`     | 1080×1620 | 155.7 KB | 弹窗 max-width 400px，实际显示约 370px 宽 |
| `frontend/public/wechat-pay.jpg` | 1217×1658 | 136.9 KB | 同上                                      |

- 原图比显示尺寸大约 3 倍，像素浪费明显
- 服务器出网带宽仅 ~8KB/s（deploy.md），两张图合计 ~293KB → 加载约 36s，压缩收益巨大

## 三、设计决策

| 项   | 决策                                                                                                                                              |
| :--- | :------------------------------------------------------------------------------------------------------------------------------------------------ |
| 方案 | **先裁剪白卡区域**（cv2 定位二维码，裁出二维码+名称，去掉品牌页头/页脚）→ 等比缩放 + JPEG 质量迭代，选「可解码且 <20KB」中宽度最大/质量最高的组合 |
| 红线 | **压缩后二维码必须仍可扫描**：opencv `QRCodeDetector` 程序化解码验证，解码内容与原图一致才算通过                                                  |
| 工具 | Python 脚本 `scripts/compress-donate-qr.py`（opencv-python + Pillow）；原图备份在 `scripts/orig-backup/`（.gitignore 排除，含真实收款码禁止上传） |
| 部署 | scp 两张图到服务器 frontend/public/ → docker compose build frontend → up -d --no-deps frontend                                                    |

**为什么先裁剪**：原图是整页海报（品牌 logo + 标语 + 白卡 + 页脚），二维码只占 ~61% 宽。整页缩到 20KB 时二维码像素太少无法解码；裁掉品牌区后二维码占满画面，同样 20KB 下二维码分辨率最大化。

---

## 四、改动清单

- [x] 压缩 alipay.jpg / wechat-pay.jpg 至 < 20KB
- [x] 二维码解码验证（压缩前后内容一致）
- [ ] 部署服务器前端
- [ ] E2E：捐助页弹窗图片加载字节数实测 + DOM 检查
- [ ] Git 提交推送

---

## 五、验证记录

### 压缩结果（2026-08-13）

| 文件           | 原大小   | 压缩后      | 压缩后宽度   | 解码验证                                                              |
| :------------- | :------- | :---------- | :----------- | :-------------------------------------------------------------------- |
| alipay.jpg     | 155.7 KB | **19.5 KB** | 360px (q=32) | ✅ `https://qr.alipay.com/fkx13063omzoxitifwngj5d` 与原图一致         |
| wechat-pay.jpg | 136.9 KB | **19.3 KB** | 440px (q=26) | ✅ `wxp://f2f0z9He22YRbftkhkfj97uf0l3sagYNemu7ENsd6Nzl2Lc` 与原图一致 |

- 合计 292.6 KB → 38.8 KB（-87%），按服务器 8KB/s 带宽加载从 ~36s 降到 ~5s
- 视觉效果人工确认：二维码清晰、收款人名称（季节(**祖) / Rick(**祖)）可读
- 脚本可重复执行：始终从 `scripts/orig-backup/` 原图压缩，不会二次压缩劣化

### 部署与 E2E（2026-08-13）

- 部署：scp 两图 → `sudo docker compose build frontend` → `up -d --no-deps frontend`，容器内文件 19956/19751 字节 ✅
- 线上 HTTP 实测（`curl -sI https://sales-agent.top/app/alipay.jpg`）：content-length 19956 / 19751，content-type image/jpeg ✅
- 浏览器 performance API 实测：alipay.jpg transferSize 20256（含 300B 头）、wechat-pay.jpg 20051，均 ~20KB ✅（原 155.7/136.9 KB）
- 弹窗 DOM 测量：modal 400×554，无溢出视口（viewport 887×650）；「取消/我已完成支付」按钮无重叠；图片 complete=true，显示 311×359 ✅
- 功能完整性：支付宝/微信支付两个弹窗分支均打开正常、图片加载、取消关闭正常 ✅

---

## 六、交付

- ✅ 两张收款码压缩至 <20KB（19.5/19.3 KB，-87%），二维码解码验证通过
- ✅ 已部署线上并 E2E 验证
- ✅ 压缩脚本 `scripts/compress-donate-qr.py` 可复跑（原图备份在 `scripts/orig-backup/`，已 gitignore）
