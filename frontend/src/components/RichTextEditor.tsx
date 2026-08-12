import { useEffect, useState } from "react";
import { Editor, useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import Placeholder from "@tiptap/extension-placeholder";
import TextAlign from "@tiptap/extension-text-align";
import { promptDialog } from "../utils/dialog";

/**
 * TipTap 富文本编辑器（M7）：
 * - 受控组件：value（HTML）/ onChange（editor.getHTML()），父组件用 key 重建以加载初始内容
 * - 工具栏：加粗/斜体/下划线/删除线/标题H2/H3/引用/无序有序列表/链接/对齐/清除格式/撤销重做
 * - 输出标准 HTML（<p>/<strong>/<em>/<ul>/<h2>/<a>），与现有 isHtmlText / text/html 发送链路兼容
 * - 占位符变量（{companyName} 等）以普通文本输入，保存/发送替换逻辑不受影响
 */

interface RichTextEditorProps {
  value: string;
  onChange: (html: string) => void;
  placeholder?: string;
  minHeight?: number;
}

export default function RichTextEditor({
  value,
  onChange,
  placeholder = "输入邮件正文…",
  minHeight = 160,
}: RichTextEditorProps) {
  // 强制重渲染以刷新工具栏 active 高亮（订阅编辑器事务）
  const [, setTick] = useState(0);

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3] },
        link: { openOnClick: false, autolink: true },
      }),
      Placeholder.configure({ placeholder }),
      TextAlign.configure({ types: ["heading", "paragraph"] }),
    ],
    content: value || "",
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
  });

  useEffect(() => {
    if (!editor) return;
    const fn = () => setTick((t) => t + 1);
    editor.on("transaction", fn);
    return () => {
      editor.off("transaction", fn);
    };
  }, [editor]);

  if (!editor) return null;

  const run = (fn: (e: Editor) => void) => () => fn(editor);

  const btn = (
    label: string,
    title: string,
    active: boolean,
    action: () => void,
  ) => (
    <button
      key={title}
      type="button"
      title={title}
      className={`rich-btn${active ? " active" : ""}`}
      onMouseDown={(e) => e.preventDefault()}
      onClick={action}
    >
      {label}
    </button>
  );

  const setLink = async () => {
    const prev = (editor.getAttributes("link").href as string) || "";
    const url = await promptDialog("输入链接地址（http/https）", {
      title: "插入链接",
      placeholder: "https://",
      defaultValue: prev,
    });
    if (url === null) return; // 取消
    if (url === "") {
      // 空值 = 移除链接
      editor.chain().focus().extendMarkRange("link").unsetLink().run();
      return;
    }
    const href = /^https?:\/\//i.test(url) ? url : `https://${url}`;
    editor.chain().focus().extendMarkRange("link").setLink({ href }).run();
  };

  return (
    <div className="rich-editor">
      <div className="rich-toolbar">
        {btn(
          "B",
          "加粗",
          editor.isActive("bold"),
          run((e) => e.chain().focus().toggleBold().run()),
        )}
        {btn(
          "I",
          "斜体",
          editor.isActive("italic"),
          run((e) => e.chain().focus().toggleItalic().run()),
        )}
        {btn(
          "U",
          "下划线",
          editor.isActive("underline"),
          run((e) => e.chain().focus().toggleUnderline().run()),
        )}
        {btn(
          "S",
          "删除线",
          editor.isActive("strike"),
          run((e) => e.chain().focus().toggleStrike().run()),
        )}
        <span className="rich-sep" />
        {btn(
          "H2",
          "二级标题",
          editor.isActive("heading", { level: 2 }),
          run((e) => e.chain().focus().toggleHeading({ level: 2 }).run()),
        )}
        {btn(
          "H3",
          "三级标题",
          editor.isActive("heading", { level: 3 }),
          run((e) => e.chain().focus().toggleHeading({ level: 3 }).run()),
        )}
        <span className="rich-sep" />
        {btn(
          "❝",
          "引用",
          editor.isActive("blockquote"),
          run((e) => e.chain().focus().toggleBlockquote().run()),
        )}
        {btn(
          "• 列表",
          "无序列表",
          editor.isActive("bulletList"),
          run((e) => e.chain().focus().toggleBulletList().run()),
        )}
        {btn(
          "1. 列表",
          "有序列表",
          editor.isActive("orderedList"),
          run((e) => e.chain().focus().toggleOrderedList().run()),
        )}
        <span className="rich-sep" />
        {btn("🔗", "插入/编辑链接", editor.isActive("link"), setLink)}
        {btn(
          "清除",
          "清除格式",
          false,
          run((e) => e.chain().focus().unsetAllMarks().clearNodes().run()),
        )}
        <span className="rich-sep" />
        {btn(
          "↶",
          "撤销",
          false,
          run((e) => e.chain().focus().undo().run()),
        )}
        {btn(
          "↷",
          "重做",
          false,
          run((e) => e.chain().focus().redo().run()),
        )}
        <span className="rich-sep" />
        {btn(
          "⯇",
          "左对齐",
          editor.isActive({ textAlign: "left" }),
          run((e) => e.chain().focus().setTextAlign("left").run()),
        )}
        {btn(
          "⯈",
          "居中",
          editor.isActive({ textAlign: "center" }),
          run((e) => e.chain().focus().setTextAlign("center").run()),
        )}
        {btn(
          "⯇",
          "右对齐",
          editor.isActive({ textAlign: "right" }),
          run((e) => e.chain().focus().setTextAlign("right").run()),
        )}
      </div>
      <EditorContent
        editor={editor}
        className="rich-content"
        style={{ minHeight }}
      />
    </div>
  );
}
