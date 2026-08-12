# M5 任务清单：原生弹出框改造为 Toast 风格弹层

> 状态：✅ 已完成（2026-08-09）
> 需求来源：用户「怎么确认框的标题有 Code 字样。请排查所有弹出框，看能否改成 toast 风格的提示框？」

---

## 一、需求原文

1. 确认框标题出现 "Code" 字样 → 排查原因。
2. 排查所有弹出框，看能否改成 toast 风格的提示框。

---

## 二、排查结论

### 2.1 "Code" 字样来源

- 应用内自定义 Modal 标题全部正常（"新增客户"、"AI 生成邮件—XX"、"AI 意图分析"、"编辑模板—XX" 等），无 "Code"。
- **根因**：原生 `window.confirm()` / `window.prompt()` 对话框在 VS Code 内置浏览器（Simple Browser）中，标题栏固定显示宿主产品名 **"Code"**，应用代码无法控制。只有改用应用内自定义弹层才能消除。

### 2.2 弹出框全量清单

| 类型                                      | 数量 | 位置                                                                                                                                                                                                    |
| :---------------------------------------- | :--- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `window.confirm`（原生确认框，标题=Code） | 13   | Customers×6（删客户/重试发送/删微信消息/删跟进/删草稿/SMTP 发送）、Drafts×2（发送/删除）、Inbox×1（删邮件）、Profile×1（删画像）、Prospect×1（删数据源）、Settings×1（恢复邮箱）、Templates×1（删模板） |
| `window.prompt`（原生输入框，标题=Code）  | 1    | Users×1（重置密码）                                                                                                                                                                                     |
| 自定义业务 Modal（标题正常）              | 4    | Customers（新增/编辑、AI 生成邮件、客户详情、微信沟通）、Inbox（AI 意图分析）、Templates（新建/编辑）、Prospect（数据源）——保留不动                                                                     |

### 2.3 改造可行性

- **确认类不能纯 toast**（toast 无法收集确认/取消决策），但可改为 **toast 风格的应用内确认卡片**（居中小卡片、圆角、轻阴影、弹出动画、标题可控），彻底摆脱 "Code" 宿主标题。
- **prompt 类**改为 toast 风格输入卡片（带输入框）。
- 通知类（操作成功/失败）各页面已有本地 toast/msg，本次不动。

---

## 三、设计决策

| 项       | 决策                                                                                                                                                                                                            |
| :------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 组件位置 | 新建 `frontend/src/utils/dialog.tsx`（模块级函数式 API，无需 Provider/挂载点）                                                                                                                                  |
| API      | `confirmDialog(text, {title?, danger?, confirmText?, cancelText?}) → Promise<boolean>`；`promptDialog(text, {title?, placeholder?, defaultValue?}) → Promise<string\|null>`                                     |
| 实现     | `createRoot` 动态挂载临时容器；点遮罩/Esc/取消 → resolve(false/null)，确认 → resolve(true/输入值)；自动清理 DOM                                                                                                 |
| 样式     | `styles.css` 新增 `.dialog-mask/.dialog-card/.dialog-title/.dialog-body/.dialog-actions`（toast 风格：380px 小卡片、圆角 10px、`dialog-pop` 弹出动画），危险操作标题红色 `.dialog-title-danger` + `.btn-danger` |
| 改造范围 | 13 处 confirm + 1 处 prompt；业务 Modal 不动                                                                                                                                                                    |

---

## 四、改动清单

| 文件                               | 改动                                          |
| :--------------------------------- | :-------------------------------------------- |
| `frontend/src/utils/dialog.tsx`    | 新建：confirmDialog / promptDialog            |
| `frontend/src/styles.css`          | 新增 dialog 弹层样式 + btn-danger             |
| `frontend/src/pages/Customers.tsx` | 6 处 confirm → confirmDialog（删除类 danger） |
| `frontend/src/pages/Drafts.tsx`    | 2 处 confirm → confirmDialog                  |
| `frontend/src/pages/Inbox.tsx`     | 1 处 confirm → confirmDialog                  |
| `frontend/src/pages/Profile.tsx`   | 1 处 confirm → confirmDialog                  |
| `frontend/src/pages/Prospect.tsx`  | 1 处 confirm → confirmDialog                  |
| `frontend/src/pages/Settings.tsx`  | 1 处 confirm → confirmDialog                  |
| `frontend/src/pages/Templates.tsx` | 1 处 confirm → confirmDialog                  |
| `frontend/src/pages/Users.tsx`     | 1 处 prompt → promptDialog                    |

---

## 五、验证记录

- [x] 本地 `npm run build` 通过（bundle index-PVbhztBP.js）
- [x] 服务器部署后：删除客户 → 出现应用内确认卡片（标题"操作确认"红色）非浏览器原生框（浏览器 DOM 断言：`.dialog-card` 存在，`window.confirm` 不再触发）
- [x] 重置密码 → 应用内输入卡片（标题"重置密码"、输入框 autoFocus、placeholder 正确），长度校验（<8 位提示"新密码至少 8 位"）、取消/Esc/遮罩分支、确认分支（op_e2e 密码重置成功 + 新密码登录验证通过）
- [x] 布局检查：弹层 380×143px，卡片右缘 587/595 < 视口 809，无溢出、按钮无重叠（getBoundingClientRect 测量）
- [x] 删除确定/取消双分支：删除"弹层测试客户"（临时造数 id=22）→ 取消不删、确认后删除成功、列表刷新恢复 4 条原始客户
- [x] 确认后无原生对话框残留：14 处原生调用已全部替换（grep 服务器 bundle：`window.confirm` 出现 0 次）

---

## 六、交付

- 已部署到服务器 43.153.229.106（bundle `index-NbZ-K-Mz.js`，273025 B），外网访问正常（200）。
- 14 处原生弹出框（13 confirm + 1 prompt）全部替换为 toast 风格应用内弹层，**浏览器标题栏不再显示 "Code"**。
- 4 个业务 Modal（新增/编辑、AI 生成邮件、客户详情、微信沟通、AI 意图分析、模板编辑、数据源）保留原样。
- 改动文件：`frontend/src/utils/dialog.tsx`（新建）、`frontend/src/styles.css`、8 个页面文件（Customers/Drafts/Inbox/Profile/Prospect/Settings/Templates/Users）。
