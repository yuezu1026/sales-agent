import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken } from "../api/client";
import { isHtmlText } from "../utils/html";
import { confirmDialog } from "../utils/dialog";
import RichTextEditor from "../components/RichTextEditor";
import { Nav } from "./Nav";

/** 邮件模板（V13）：可复用主题/正文，支持占位符变量，保存草稿/发送时按客户字段替换 */
interface EmailTemplate {
  id: number;
  name: string;
  subject: string;
  body: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
}

/** 支持变量（与后端 TemplateRenderer 保持一致） */
const VAR_HINTS: { name: string; desc: string }[] = [
  { name: "{companyName}", desc: "公司名称" },
  { name: "{contactName}", desc: "联系人" },
  { name: "{contactEmail}", desc: "联系人邮箱" },
  { name: "{phone}", desc: "联系电话" },
  { name: "{contactPhone}", desc: "联系电话（别名）" },
  { name: "{gender}", desc: "性别" },
  { name: "{industry}", desc: "行业" },
  { name: "{region}", desc: "地区" },
  { name: "{scale}", desc: "规模" },
  { name: "{website}", desc: "网站" },
  { name: "{address}", desc: "地址" },
  { name: "{date}", desc: "当天日期" },
  { name: "{year}", desc: "当前年份" },
];

/** 预览用示例客户数据（未识别的占位符原样保留） */
const PREVIEW_SAMPLE: Record<string, string> = {
  companyName: "示例科技",
  contactName: "张三",
  contactEmail: "zhangsan@example.com",
  phone: "13800000000",
  contactPhone: "13800000000",
  gender: "先生",
  industry: "软件服务",
  region: "上海",
  scale: "50-200人",
  website: "https://www.example.com",
  address: "上海市浦东新区xx路xx号",
};

function previewText(text: string) {
  const now = new Date();
  const date = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(
    2,
    "0",
  )}-${String(now.getDate()).padStart(2, "0")}`;
  return text.replace(/\{(\w+)\}/g, (m, key: string) => {
    if (key === "date") return date;
    if (key === "year") return String(now.getFullYear());
    return PREVIEW_SAMPLE[key] ?? m;
  });
}

function fmtTime(s: string | null) {
  if (!s) return "";
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(
    d.getHours(),
  )}:${p(d.getMinutes())}`;
}

const EMPTY_FORM = { name: "", subject: "", body: "", description: "" };

/** 邮件模板管理：随时编辑美化（实时预览），在客户邮件生成时一键套用 */
export default function Templates() {
  const navigate = useNavigate();
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [modal, setModal] = useState<"new" | EmailTemplate | null>(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [saving, setSaving] = useState(false);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [msg, setMsg] = useState<{
    kind: "success" | "error";
    text: string;
  } | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await api<EmailTemplate[]>("/email-templates");
      setTemplates(data);
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openNew = () => {
    setForm(EMPTY_FORM);
    setModal("new");
    setMsg(null);
  };

  const openEdit = (t: EmailTemplate) => {
    setForm({
      name: t.name,
      subject: t.subject,
      body: t.body,
      description: t.description ?? "",
    });
    setModal(t);
    setMsg(null);
  };

  const submit = async () => {
    setSaving(true);
    setMsg(null);
    try {
      if (modal === "new") {
        await api("/email-templates", {
          method: "POST",
          body: JSON.stringify(form),
        });
        setMsg({ kind: "success", text: "模板已创建" });
      } else if (modal) {
        await api(`/email-templates/${modal.id}`, {
          method: "PUT",
          body: JSON.stringify(form),
        });
        setMsg({ kind: "success", text: "模板已更新" });
      }
      setModal(null);
      await load();
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    } finally {
      setSaving(false);
    }
  };

  const remove = async (t: EmailTemplate) => {
    if (!(await confirmDialog(`确认删除模板「${t.name}」？`, { danger: true })))
      return;
    setDeletingId(t.id);
    setMsg(null);
    try {
      await api(`/email-templates/${t.id}`, { method: "DELETE" });
      setMsg({ kind: "success", text: "模板已删除" });
      await load();
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div>
      <Nav
        current="templates"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="page">
        <h2>邮件模板</h2>
        <div
          style={{
            fontSize: 13,
            color: "#888",
            marginBottom: 12,
            lineHeight: 1.6,
          }}
        >
          可复用的邮件主题/正文模板：支持占位符变量（如 {"{companyName}"}），
          正文支持<b> 富文本编辑</b>（加粗 / 列表 / 链接 / 标题等，保存为
          HTML），保存草稿与发送时按客户实际字段自动替换；
          在客户邮件生成弹窗中可一键套用，AI 将结合沟通历史个性化编写。
        </div>
        <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
          <button className="btn btn-sm" onClick={openNew}>
            ＋ 新建模板
          </button>
        </div>
        {msg && (
          <div
            className={`msg ${msg.kind === "success" ? "success" : "error"}`}
            style={{ textAlign: "left" }}
          >
            {msg.text}
          </div>
        )}
        <div className="table-wrap">
          <table className="table template-table">
            <thead>
              <tr>
                <th>模板名称</th>
                <th>主题（占位符）</th>
                <th>说明</th>
                <th>更新时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {templates.map((t) => (
                <tr key={t.id}>
                  <td>{t.name}</td>
                  <td
                    style={{
                      maxWidth: 300,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {t.subject}
                  </td>
                  <td
                    style={{
                      maxWidth: 180,
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                      whiteSpace: "nowrap",
                    }}
                  >
                    {t.description || "-"}
                  </td>
                  <td>{fmtTime(t.updatedAt)}</td>
                  <td>
                    <div style={{ display: "flex", gap: 6 }}>
                      <button
                        className="btn btn-sm"
                        onClick={() => openEdit(t)}
                      >
                        编辑
                      </button>
                      <button
                        className="btn btn-sm btn-default"
                        disabled={deletingId === t.id}
                        onClick={() => remove(t)}
                      >
                        {deletingId === t.id ? "删除中..." : "删除"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {templates.length === 0 && (
                <tr>
                  <td
                    colSpan={5}
                    style={{ textAlign: "center", color: "#999" }}
                  >
                    暂无模板，点击「＋ 新建模板」创建第一个
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* 新建 / 编辑模板 Modal */}
      {modal && (
        <div className="modal-mask" onClick={() => setModal(null)}>
          <div
            className="modal modal-wide"
            onClick={(e) => e.stopPropagation()}
          >
            <h3>
              {modal === "new" ? "新建邮件模板" : `编辑模板 — ${modal.name}`}
            </h3>
            <div className="form-item">
              <label>
                模板名称 <span style={{ color: "#e74c3c" }}>*</span>
              </label>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="如：首次触达 / 方案跟进 / 老客户关怀"
              />
            </div>
            <div className="form-item">
              <label>适用说明</label>
              <input
                value={form.description}
                onChange={(e) =>
                  setForm({ ...form, description: e.target.value })
                }
                placeholder="可选，说明该模板的适用场景"
              />
            </div>
            <div className="form-item">
              <label>
                邮件主题 <span style={{ color: "#e74c3c" }}>*</span>
              </label>
              <input
                value={form.subject}
                onChange={(e) => setForm({ ...form, subject: e.target.value })}
                placeholder={`如：【{companyName}】{contactName}，{date}合作沟通`}
              />
            </div>
            <div className="form-item">
              <label>
                邮件正文 <span style={{ color: "#e74c3c" }}>*</span>
              </label>
              <RichTextEditor
                key={modal === "new" ? "new" : modal.id}
                value={form.body}
                onChange={(html) => setForm({ ...form, body: html })}
                placeholder="输入邮件正文，支持 {companyName} 等占位符变量，发送时自动替换为客户字段…"
              />
            </div>
            <div
              style={{
                fontSize: 12,
                color: "#888",
                margin: "4px 0 8px",
                lineHeight: 1.8,
              }}
            >
              <b>支持变量：</b>
              {VAR_HINTS.map((v) => (
                <span
                  key={v.name}
                  style={{ marginRight: 10, whiteSpace: "nowrap" }}
                >
                  <code
                    style={{
                      background: "#f4f4f4",
                      padding: "1px 4px",
                      borderRadius: 3,
                    }}
                  >
                    {v.name}
                  </code>
                  {v.desc}
                </span>
              ))}
            </div>
            {(form.subject || form.body) && (
              <div
                style={{
                  border: "1px dashed #ddd",
                  borderRadius: 6,
                  padding: "8px 10px",
                  marginBottom: 8,
                  background: "#fafafa",
                }}
              >
                <div style={{ fontSize: 12, color: "#666", marginBottom: 4 }}>
                  👁 预览效果（示例客户：示例科技 / 张三）：
                </div>
                <div style={{ fontSize: 13, color: "#333" }}>
                  <b>主题：</b>
                  {previewText(form.subject) || "（空）"}
                </div>
                <div style={{ fontSize: 13, color: "#333" }}>
                  <b>正文：</b>
                  {isHtmlText(form.body) ? (
                    <span
                      dangerouslySetInnerHTML={{
                        __html: previewText(form.body),
                      }}
                      style={{
                        display: "block",
                        background: "#fff",
                        border: "1px solid #eee",
                        borderRadius: 6,
                        padding: "10px 12px",
                        marginTop: 4,
                        lineHeight: 1.7,
                        wordBreak: "break-word",
                        maxHeight: 260,
                        overflowY: "auto",
                      }}
                    />
                  ) : (
                    <span
                      style={{
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-word",
                      }}
                    >
                      {previewText(form.body) || "（空）"}
                    </span>
                  )}
                </div>
              </div>
            )}
            {msg && (
              <div
                className={`msg ${msg.kind === "success" ? "success" : "error"}`}
                style={{ textAlign: "left" }}
              >
                {msg.text}
              </div>
            )}
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <button className="btn btn-sm" disabled={saving} onClick={submit}>
                {saving ? "保存中..." : "保存模板"}
              </button>
              <button
                className="btn btn-sm btn-default"
                onClick={() => setModal(null)}
              >
                取消
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
