# M7.5 任务清单：删除工作台标题旁的激活提示（与顶部横幅重复）

> 状态：✅ 已完成（2026-08-09）
> 需求来源：用户「工作台页面上的：需要激活才能使用，请联系管理员激活，邮箱地址：39002818@qq.com，是否可以删除，因为多余？页面顶部有。」

---

## 一、需求原文

工作台页面标题旁显示「需要激活才能使用，请联系管理员激活，邮箱地址：39002818@qq.com」，但页面顶部（导航栏）已经有全站通用的未激活提示横幅「⚠️ 系统未激活，AI 功能暂不可用，请联系管理员激活（39002818@qq.com）」，因此工作台这处是**多余的**，需要删除。

---

## 二、排查结论

- 全站未激活提示已有两处：
  1. `Nav.tsx` 导航栏顶部横幅 `.license-nav-banner`（**全站所有页面通用**）——保留
  2. `Dashboard.tsx` 工作台标题右侧内联提示 `.license-inline-warning`（第 89-97 行）——**冗余，删除**
- `.license-inline-warning` 样式（styles.css 第 434-445 行）仅被 Dashboard 这一处使用，一并清理
- `Dashboard.tsx` 的 `CONTACT_EMAIL` 常量仅服务于该提示，删除后一并移除

---

## 三、设计决策

| 项   | 决策                                                                                                  |
| :--- | :---------------------------------------------------------------------------------------------------- |
| 方案 | 删除 Dashboard 标题右侧的未激活内联提示（保留 Nav 顶部横幅作为唯一未激活提示）                        |
| 细节 | 同时删除 styles.css 中仅此一处使用的 `.license-inline-warning` 样式和 Dashboard 的 CONTACT_EMAIL 常量 |
| 后端 | 零改动                                                                                                |

---

## 四、改动清单

- [x] `frontend/src/pages/Dashboard.tsx`：删除标题旁 `license-inline-warning` 内联提示 + `CONTACT_EMAIL` 常量
- [x] `frontend/src/styles.css`：删除 `.license-inline-warning` 样式（仅此一处使用）
- [x] 构建 + 部署前端（服务器 node:22-alpine 构建镜像，rm -f aic-frontend → up -d，bundle `index-N5H_Y9jK.js`）
- [x] E2E：未激活状态下工作台标题旁无内联提示但 Nav 顶部横幅仍在；DOM 零溢出零重叠

---

## 五、验证记录（E2E 全过）

**1. 工作台页面**（当前系统未激活态）

- 标题「工作台」旁**无** `license-inline-warning` 内联提示 ✅
- Nav 顶部横幅「⚠️ 系统未激活，AI 功能暂不可用，请联系管理员激活（39002818@qq.com）」正常显示（全站唯一提示）✅

**2. bundle 内容核验**（index-N5H_Y9jK.js）

- `需要激活才能使用，请联系管理员激活`（Dashboard 独有文案）→ 不存在 ✅
- `系统未激活，AI 功能暂不可用`（Nav 横幅文案）→ 存在 ✅
- `系统尚未激活`（登录页横幅文案）→ 存在 ✅
- `license-inline-warning` class → 不存在（样式已清理）✅

**3. DOM 布局测量（getBoundingClientRect）**

- bodyScrollWidth 809 = 视口 809，**页面零溢出** ✅
- 工作台标题行在视口内（16~793），按钮零重叠 ✅

---

## 六、交付

- 前端改动 2 文件：`frontend/src/pages/Dashboard.tsx`（删内联提示 + CONTACT_EMAIL 常量）、`frontend/src/styles.css`（删 .license-inline-warning 样式）
- 后端零改动；DB 零改动
- 已部署线上：aic-frontend 镜像重建，bundle `index-N5H_Y9jK.js`，`/app/` 200
- 未激活提示现在只保留 Nav 顶部横幅（全站通用）一处，登录页横幅保留（登录前无导航栏，必须有）
- Git 提交推送待做（本次任务 md 一并提交）
