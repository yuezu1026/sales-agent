/**
 * HTML 邮件正文工具（M3-2 邮件模板 HTML 支持）：
 * 模板/AI 生成的正文可能为 HTML（内联样式美化），发送时按 text/html，
 * 前端渲染时用 dangerouslySetInnerHTML，纯文本则按 pre-wrap 显示。
 */

/** 判断正文是否为 HTML：包含常见 HTML 标签即按 HTML 处理 */
export function isHtmlText(text: string | null | undefined): boolean {
  if (!text) return false;
  const t = text.toLowerCase();
  return (
    t.includes("<!doctype html") ||
    t.includes("<html") ||
    t.includes("<p") ||
    t.includes("<br") ||
    t.includes("<div") ||
    t.includes("<span") ||
    t.includes("<table") ||
    t.includes("<ul") ||
    t.includes("<li") ||
    t.includes("<strong") ||
    t.includes("<b>") ||
    t.includes("<h1") ||
    t.includes("<h2") ||
    t.includes("<h3") ||
    t.includes("<a ")
  );
}
