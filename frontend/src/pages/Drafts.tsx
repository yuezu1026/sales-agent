import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken } from "../api/client";
import { isHtmlText } from "../utils/html";
import { confirmDialog } from "../utils/dialog";
import { Nav } from "./Nav";

/** 邮件草稿全局视图（M2-1.7 补充，表 email_draft 跨客户管理） */
interface EmailDraftView {
  id: number;
  leadId: number;
  leadCompanyName: string | null;
  leadContactName: string | null;
  subject: string;
  body: string;
  tone: string;
  status: string;
  createdAt: string;
  confirmedAt: string | null;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const DRAFT_STATUS_LABEL: Record<string, string> = {
  draft: "草稿",
  confirmed: "待发",
  sent: "已发送",
};

const TONE_LABEL: Record<string, string> = {
  formal: "正式",
  friendly: "亲切",
  neutral: "中性",
};

function fmtTime(s: string | null) {
  if (!s) return "";
  const d = new Date(s);
  if (Number.isNaN(d.getTime())) return s;
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(
    d.getHours(),
  )}:${p(d.getMinutes())}`;
}

/** 邮件草稿管理（全局）：跨客户搜索 / 状态筛选 / 标记待发 / 改回 / 删除 */
export default function Drafts() {
  const navigate = useNavigate();
  const [drafts, setDrafts] = useState<EmailDraftView[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [sendingId, setSendingId] = useState<number | null>(null);
  const [msg, setMsg] = useState<{
    kind: "success" | "error";
    text: string;
  } | null>(null);

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams({ page: String(page), size: "10" });
      if (keyword.trim()) params.set("keyword", keyword.trim());
      if (statusFilter) params.set("status", statusFilter);
      const data = await api<Page<EmailDraftView>>(
        `/email-drafts?${params.toString()}`,
      );
      // 页码越界保护：在最后一页删除数据后总页数减少，回退到最后一页重新加载
      if (data.totalPages > 0 && data.number >= data.totalPages) {
        setPage(data.totalPages - 1);
        return;
      }
      setDrafts(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    }
  }, [page, keyword, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  /** 草稿状态流转 draft ↔ confirmed */
  const toggleStatus = async (d: EmailDraftView) => {
    const next = d.status === "confirmed" ? "draft" : "confirmed";
    setMsg(null);
    try {
      await api(`/leads/${d.leadId}/email-drafts/${d.id}/status`, {
        method: "PUT",
        body: JSON.stringify({ status: next }),
      });
      setMsg({
        kind: "success",
        text:
          next === "confirmed" ? "已标记待发，可进入发送流程" : "已改回草稿",
      });
      await load();
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    }
  };

  /** SMTP 发送（M3-2）：仅 confirmed 草稿可发，成功/失败均落发送记录 */
  const send = async (d: EmailDraftView) => {
    if (
      !(await confirmDialog(
        `确认通过 SMTP 发送邮件「${d.subject}」给 ${d.leadCompanyName ?? "该客户"}？`,
      ))
    )
      return;
    setMsg(null);
    setSendingId(d.id);
    try {
      const res = await api<{
        sendLogId: number;
        status: string;
        toEmail: string;
        errorMsg: string | null;
      }>(`/leads/${d.leadId}/email-drafts/${d.id}/send`, {
        method: "POST",
      });
      if (res.status === "sent") {
        setMsg({ kind: "success", text: `已发送至 ${res.toEmail}` });
      } else {
        setMsg({
          kind: "error",
          text: `发送失败：${res.errorMsg ?? "未知错误"}`,
        });
      }
      await load();
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    } finally {
      setSendingId(null);
    }
  };

  /** 删除草稿 */
  const remove = async (d: EmailDraftView) => {
    if (!(await confirmDialog("确认删除这封邮件草稿？", { danger: true })))
      return;
    setMsg(null);
    try {
      await api(`/leads/${d.leadId}/email-drafts/${d.id}`, {
        method: "DELETE",
      });
      setMsg({ kind: "success", text: "草稿已删除" });
      if (expandedId === d.id) setExpandedId(null);
      await load();
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    }
  };

  return (
    <div>
      <Nav
        current="drafts"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="container">
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: 12,
          }}
        >
          <h2 style={{ margin: 0 }}>草稿箱</h2>
          <span style={{ fontSize: 13, color: "#999" }}>
            跨客户统一管理，标记待发后即可 SMTP 发送；已发送记录见「发件箱」
          </span>
        </div>

        {msg && <div className={`msg ${msg.kind}`}>{msg.text}</div>}

        <div className="card" style={{ marginTop: 16 }}>
          <div
            style={{
              display: "flex",
              gap: 8,
              flexWrap: "wrap",
              marginBottom: 16,
              alignItems: "center",
            }}
          >
            <input
              className="filter-input"
              placeholder="搜索主题 / 正文"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPage(0);
              }}
            />
            <select
              className="filter-input"
              style={{ width: "auto" }}
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="">全部状态</option>
              <option value="draft">草稿</option>
              <option value="confirmed">待发</option>
            </select>
          </div>

          {drafts.length === 0 ? (
            <div className="msg" style={{ color: "#999", padding: "24px 0" }}>
              暂无草稿，可在客户管理中「✉ 邮件」AI 生成后保存
            </div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>客户</th>
                  <th>主题</th>
                  <th>状态</th>
                  <th>保存时间</th>
                  <th style={{ width: 220 }}>操作</th>
                </tr>
              </thead>
              <tbody>
                {drafts.map((d) => (
                  <DraftRow
                    key={d.id}
                    draft={d}
                    expanded={expandedId === d.id}
                    onToggleExpand={() =>
                      setExpandedId(expandedId === d.id ? null : d.id)
                    }
                    onToggleStatus={() => toggleStatus(d)}
                    onSend={() => send(d)}
                    sending={sendingId === d.id}
                    onRemove={() => remove(d)}
                    onOpenCustomer={() => navigate("/customers")}
                  />
                ))}
              </tbody>
            </table>
          )}

          {totalPages > 1 && (
            <div
              style={{
                display: "flex",
                justifyContent: "center",
                gap: 8,
                marginTop: 16,
              }}
            >
              <button
                className="btn btn-xs btn-default"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                上一页
              </button>
              <span style={{ fontSize: 13, alignSelf: "center" }}>
                {page + 1} / {totalPages}（共 {totalElements} 封）
              </span>
              <button
                className="btn btn-xs btn-default"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                下一页
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/** 单行草稿（点击行展开正文） */
function DraftRow(props: {
  draft: EmailDraftView;
  expanded: boolean;
  sending: boolean;
  onToggleExpand: () => void;
  onToggleStatus: () => void;
  onSend: () => void;
  onRemove: () => void;
  onOpenCustomer: () => void;
}) {
  const { draft: d, expanded } = props;
  return (
    <>
      <tr
        onClick={props.onToggleExpand}
        style={{ cursor: "pointer" }}
        className={expanded ? "inbox-row-unread" : ""}
      >
        <td>
          {d.leadCompanyName ? (
            <>
              <a
                href={`${import.meta.env.BASE_URL}customers`}
                onClick={(e) => {
                  e.preventDefault();
                  e.stopPropagation();
                  props.onOpenCustomer();
                }}
                style={{ fontWeight: 600 }}
              >
                {d.leadCompanyName}
              </a>
              {d.leadContactName && (
                <div style={{ fontSize: 12, color: "#999" }}>
                  {d.leadContactName}
                </div>
              )}
            </>
          ) : (
            <span style={{ color: "#999" }}>（未关联）</span>
          )}
        </td>
        <td style={{ maxWidth: 320, wordBreak: "break-word" }}>{d.subject}</td>
        <td>
          <span
            className={`badge badge-${
              d.status === "confirmed" ? "converted" : "new"
            }`}
          >
            {DRAFT_STATUS_LABEL[d.status] ?? d.status}
          </span>
        </td>
        <td style={{ fontSize: 13, color: "#666", whiteSpace: "nowrap" }}>
          {fmtTime(d.createdAt)}
        </td>
        <td>
          <div
            style={{ display: "flex", gap: 6 }}
            onClick={(e) => e.stopPropagation()}
          >
            {d.status === "confirmed" && (
              <button
                className="btn btn-xs btn-primary"
                onClick={props.onSend}
                disabled={props.sending}
                title="通过 SMTP 发送这封邮件"
              >
                {props.sending ? "发送中…" : "✉ 发送"}
              </button>
            )}
            {d.status !== "sent" && (
              <button
                className="btn btn-xs btn-default"
                onClick={props.onToggleStatus}
                title={d.status === "confirmed" ? "改回草稿" : "标记邮件为待发"}
              >
                {d.status === "confirmed" ? "↩ 改回" : "✓ 标记待发"}
              </button>
            )}
            <button
              className="btn btn-xs btn-default"
              onClick={props.onRemove}
              style={{ color: "#ff4d4f" }}
            >
              删除
            </button>
          </div>
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={5} style={{ background: "#fafafa" }}>
            <div style={{ padding: "4px 8px 12px" }}>
              <div style={{ fontSize: 12, color: "#999", marginBottom: 6 }}>
                语气：{TONE_LABEL[d.tone] ?? d.tone}
                {d.confirmedAt
                  ? ` ｜ 标记待发时间：${fmtTime(d.confirmedAt)}`
                  : ""}
              </div>
              <div className="inbox-body">
                {isHtmlText(d.body) ? (
                  <span
                    dangerouslySetInnerHTML={{ __html: d.body }}
                    style={{ display: "block", lineHeight: 1.7 }}
                  />
                ) : (
                  d.body
                )}
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}
