# 品牌名「拾客 Shike」落地任务

## 状态

- 状态：✅ 已完成（2026-08-13 启动，2026-08-13 完成）
- 需求：为产品定品牌名「拾客 / Shike」，并替换产品内「AI智能获客助手」品牌露出

## 需求原文（用户 2026-08-13）

> 能否为这个AI智能获客助手，取个品牌名？
>
> （用户选定）**拾客 / Shike**，这个好，就用这个，能否把这个品牌名也放进去？

## 设计决策

| 决策点     | 结论                                                                                            |
| :--------- | :---------------------------------------------------------------------------------------------- |
| 品牌名形式 | 中文「拾客」+ 英文「Shike」，统一展示为「拾客 Shike」                                           |
| 定位描述   | 「AI 智能获客助手」保留为产品定位描述（title/关于页），品牌名为主标题，避免丢失产品信息         |
| 替换范围   | 前端：index.html title、Login/Register/Nav/Unsubscribe 的 logo alt 与登录/注册标题、Help 关于页 |
| README     | 首行标题改品牌名，定位描述保留                                                                  |
| 不改       | V1\_\_init.sql 历史迁移注释（已应用不改）；logo.svg/favicon.svg（纯图形无文字）                 |

## 改动清单

- [x] 任务文档
- [x] frontend/index.html：`<title>` 改「拾客 Shike · AI 智能获客助手」
- [x] Login.tsx：logo alt + 登录标题「拾客 Shike · 登录」
- [x] Register.tsx：logo alt + 注册标题「拾客 Shike · 注册」
- [x] Nav.tsx：logo alt「拾客 Shike」
- [x] Unsubscribe.tsx：logo alt「拾客 Shike」
- [x] Help.tsx：关于页品牌名「拾客 Shike」+ 定位描述
- [x] README.md：首行标题改品牌名
- [x] 验证：build + 部署 + 浏览器检查（登录页/导航/注册/退订页 title 与标题、布局 DOM 检查）

## 验证记录

### 浏览器检查（localhost/app，新 bundle index-D8WVE1CL.js）

| 位置          | 结果                                                 |
| :------------ | :--------------------------------------------------- |
| 浏览器标题    | 拾客 Shike · AI 智能获客助手 ✅                      |
| 导航 logo alt | 拾客 Shike ✅                                        |
| 登录页        | img alt「拾客 Shike」+ 标题「拾客 Shike · 登录」✅   |
| 注册页        | img alt「拾客 Shike」+ 标题「拾客 Shike · 注册」✅   |
| 退订页        | img alt「拾客 Shike」✅                              |
| 帮助页关于    | 「拾客 Shike（AI 智能获客助手 · AI Sales Agent）」✅ |

### 布局 DOM 检查（getBoundingClientRect，禁截图）

- 登录页 auth-box 无溢出视口、元素无重叠、无水平溢出 ✅
- 文本框宽度一致（360px；16px 为记住我 checkbox）✅

## 交付

- 品牌名「拾客 Shike」落地：index.html title、Login/Register/Nav/Unsubscribe logo alt 与标题、Help 关于页、README 首行
- 定位描述「AI 智能获客助手」保留在 title 与关于页（品牌名为主标题）
- 已部署：docker compose build + up -d --force-recreate frontend（容器前端 JS index-D8WVE1CL.js）
