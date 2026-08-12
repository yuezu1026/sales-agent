# M7 任务清单：邮件模板正文支持富文本编辑器

> 状态：✅ 已完成（2026-08-09）
> 需求来源：用户「邮件模板：新建邮件模板的邮件正文应该支持富文本，能否用一个富文本编辑器？」
> 补充需求（同日）：「AI 生成邮件 — 测试客户A，页面中的生成结果（邮件正文）也应该支持富文本框」

---

## 一、需求原文

1. 邮件模板的新建/编辑弹窗中，正文目前是 `<textarea>`，需要用户手写 HTML（如 `<p>`、`<b>`、`<span style=...>`）才能美化。
2. 希望正文支持富文本编辑（所见即所得）：加粗/斜体/下划线/标题/列表/链接/撤销重做等，无需手写 HTML。
3. 【补充】客户管理「✉ 邮件」AI 生成邮件弹窗中的生成结果（正文）同样改为富文本编辑，与模板编辑体验一致。

---

## 二、排查结论

- 后端 `EmailTemplate.body` 为 `TEXT`，本就支持任意 HTML，Controller/Service 无 HTML 校验限制 → **后端零改动**。
- 前端已有 `isHtmlText()` 判断 + 预览 `dangerouslySetInnerHTML` + 发送按 `text/html`（M3-2 链路），富文本编辑器输出的标准 HTML（`<p>/<strong>/<em>/<ul>/<h2>/<a>`）全部兼容。
- 占位符变量 `{companyName}` 等以普通文本输入，保存/发送时的 `TemplateRenderer` 替换逻辑不受影响。
- `main.tsx` 启用了 `StrictMode` → 选型需避开 StrictMode 双挂载有坑的编辑器（如 wangEditor 的 React 包装）。

### 富文本编辑器选型

| 方案                                      | 结论                                                                                                                                       |
| :---------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------- |
| **TipTap**（@tiptap/react + starter-kit） | ✅ 采用：React 18 + StrictMode 兼容；活跃维护；StarterKit 自带 加粗/斜体/下划线/删除线/标题/有序无序列表/引用/链接/撤销重做；输出标准 HTML |
| wangEditor                                | ❌ React 包装在 StrictMode 下有已知双挂载问题，且 2023 年后维护停滞                                                                        |
| react-quill                               | ❌ Quill 较老，React 18 兼容需谨慎                                                                                                         |
| 手写 contenteditable                      | ❌ 功能少、撤销/链接/列表等都要自实现，性价比低                                                                                            |

---

## 三、设计决策

| 项        | 决策                                                                                                                                                                                                    |
| :-------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 依赖      | `@tiptap/react` `@tiptap/pm` `@tiptap/starter-kit` `@tiptap/extension-placeholder`（占位提示）`@tiptap/extension-text-align`（左/中/右对齐）                                                            |
| 组件      | 新建 `frontend/src/components/RichTextEditor.tsx`：受控组件 `value/onChange/placeholder/minHeight`；工具栏：B / I / U / S / H2 / H3 / 引用 / 无序列表 / 有序列表 / 链接 / 清除格式 / 撤销 / 重做 / 对齐 |
| 接入      | `Templates.tsx` 弹窗正文 `textarea` 替换为 `RichTextEditor`；弹窗用 `key`（新建=`"new"`，编辑=模板 id）强制重建编辑器以加载初始 HTML；`onChange` 写回 `form.body`（`editor.getHTML()`）                 |
| 保存/预览 | 保存与预览逻辑不变（`form.body` 即 HTML）；旧模板纯文本正文自动被 `<p>` 包裹，向后兼容                                                                                                                  |
| 后端      | 零改动                                                                                                                                                                                                  |
| 样式      | `styles.css` 增加工具栏/编辑区样式，与现有 `.form-item`/`.modal-wide` 风格统一                                                                                                                          |

---

## 四、改动清单

| 文件                                         | 改动                                                                                             |
| :------------------------------------------- | :----------------------------------------------------------------------------------------------- |
| `frontend/package.json`                      | 新增 TipTap 依赖                                                                                 |
| `frontend/src/components/RichTextEditor.tsx` | 新建：TipTap 富文本组件                                                                          |
| `frontend/src/pages/Templates.tsx`           | 正文 `textarea` → `RichTextEditor`（key 重建 + onChange 写回）                                   |
| `frontend/src/pages/Customers.tsx`           | AI 生成邮件弹窗：生成结果三态视图（富文本编辑/源码/预览），AI 生成与套模板后 key 重建加载新内容  |
| `frontend/src/styles.css`                    | 编辑器工具栏/内容区样式                                                                          |
| `frontend/vite.config.ts` + `src/main.tsx`   | **base=`/app/` + BrowserRouter basename（路由修复：/app/\* 深链不再被 catch-all 跳到 landing）** |
| `doc/m7-richtext-editor-tasks.md`            | 本任务文档                                                                                       |

---

## 五、验证记录

- [x] 本地 `npm run build` 通过（base=/app/ 后 dist 资源带 /app/ 前缀）
- [x] 路由修复：`/app/*` 深链不再被 catch-all 跳到 landing（/app/templates、/app/sent、/app/drafts 均正常渲染，nav hrefs 带 /app/ 前缀）
- [x] 新建模板：富文本工具栏可用（加粗→`<strong>`、H2→`<h2>`、预览实时联动），保存后列表与预览渲染正确
- [x] 编辑回显：已有模板的 HTML 正文（h2+strong）正确加载进编辑器
- [x] 占位符变量 `{contactName}`/`{companyName}` 输入/保存/替换正常（草稿 id 26 验证，占位符替换为实际字段）
- [x] AI 生成邮件弹窗：套用模板后富文本编辑器渲染；加粗/链接（🔗→插入链接对话框→`<a href>`）/列表（•列表→`<ul>`）均生效；🖊 编辑/✏️ 源码/👁 预览三态切换正常
- [x] 保存为草稿闭环：草稿 body 完整保存富文本 HTML（`<ul><li><p>…<a><strong>…</strong></a></p></li></ul>`）
- [x] 删除分支：确认框「取消」保留、「确定」删除，toast 提示正常
- [x] 部署后 E2E 布局：getBoundingClientRect 无溢出/无重叠（modal 640px、工具栏两行 flex-wrap、scrollWidth=clientWidth、按钮无相交）
- [x] 测试数据清理：E2E 创建的模板与草稿均已删除

---

## 六、交付

- **TipTap 3.29.2**（@tiptap/react、@tiptap/pm、starter-kit、extension-placeholder、extension-text-align）
- `frontend/src/components/RichTextEditor.tsx`：受控富文本组件（key 重建加载新内容，onChange 回写 HTML）
- `frontend/src/pages/Templates.tsx`：正文 `textarea` → RichTextEditor
- `frontend/src/pages/Customers.tsx`：AI 生成邮件弹窗生成结果三态视图（富文本/源码/预览），AI 生成与套模板后 key 重建
- `frontend/vite.config.ts` + `frontend/src/main.tsx`：**base=`/app/` + BrowserRouter basename，修复 M7 首次部署引入的 /app/\* 深链跳 landing 回归**
- 线上 bundle：`index-DmILj1xa.js`（ai-customer-deploy-frontend 镜像），内网 /app/templates 200
- 备注：草稿/发件箱仍存有历史测试记录（email_send_log id 3-10 等），未清理（用户未决定）
