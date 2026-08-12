import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken } from "../api/client";
import { isHtmlText } from "../utils/html";
import { confirmDialog } from "../utils/dialog";
import { Nav } from "./Nav";

/** 发件箱视图行（表 email_send_log 全局视图，M6） */
interface SendLogView {
  id: number;
  leadId: number | null;
  leadCompanyName: string | null;
  leadContactName: string | null;
  fromEmail: string;
  toEmail: string;
  subject: string;
  body: string;
  status: string;
  errorMsg: string | null;
  sentAt: string | null;
  openedAt: string | null;
  clickedAt: string | null;
  createdAt: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

const SEND_STATUS_LABEL: Record<string, string> = {
  queued: "排队中",
  sent: "已发送",
  failed: "失败",
  bounced: "退信",
};

const SEND_STATUS_BADGE: Record<string, string> = {
  queued: "badge-new",
  sent: "badge-converted",
  failed: "badge-danger",
  bounced: "badge-danger",
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

/** 发件箱：全局 SMTP 发送记录（含打开/点击追踪） */
export default function Sent() {
  const navigate = useNavigate();
  const [logs, setLogs] = useState<SendLogView[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [msg, setMsg] = useState<{
    kind: "success" | "error";
    text: string;
  } | null>(null);

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams({ page: String(page), size: "10" });
      if (keyword.trim()) params.set("keyword", keyword.trim());
      if (statusFilter) params.set("status", statusFilter);
      const data = await api<Page<SendLogView>>(
        `/email-send-logs?${params.toString()}`,
      );
      // 页码越界保护：在最后一页删除数据后总页数减少，回退到最后一页重新加载
      if (data.totalPages > 0 && data.number >= data.totalPages) {
        setPage(data.totalPages - 1);
        return;
      }
      setLogs(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    }
  }, [page, keyword, statusFilter]);

  useEffect(() => {
    load();
  }, [load]);

  /** 重试失败的发送记录（仅 failed；复用客户维度重试接口，草稿须仍为待发） */
  const retry = async (log: SendLogView) => {
    if (
      !(await confirmDialog(
        `确认重试发送「${log.subject}」给 ${log.toEmail}？`,
      ))
    )
      return;
    setMsg(null);
    setRetryingId(log.id);
    try {
      const res = await api<{
        status: string;
        toEmail: string;
        errorMsg: string | null;
      }>(`/leads/${log.leadId}/email-send-logs/${log.id}/retry`, {
        method: "POST",
      });
      if (res.status === "sent") {
        setMsg({ kind: "success", text: `重试成功，已发送至 ${res.toEmail}` });
      } else {
        setMsg({
          kind: "error",
          text: `重试失败：${res.errorMsg ?? "未知错误"}`,
        });
      }
      await load();
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    } finally {
      setRetryingId(null);
    }
  };

  /** 删除发送记录 */
  const remove = async (log: SendLogView) => {
    if (
      !(await confirmDialog(`确认删除这条发送记录「${log.subject}」？`, {
        danger: true,
      }))
    )
      return;
    setMsg(null);
    try {
      await api(`/email-send-logs/${log.id}`, { method: "DELETE" });
      setMsg({ kind: "success", text: "发送记录已删除" });
      if (expandedId === log.id) setExpandedId(null);
      await load();
    } catch (e) {
      setMsg({ kind: "error", text: (e as Error).message });
    }
  };

  return (
    <div>
      <Nav
        current="sent"
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
          <h2 style={{ margin: 0 }}>发件箱</h2>
          <span style={{ fontSize: 13, color: "#999" }}>
            全局 SMTP 发送记录，含打开/点击追踪
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
              placeholder="搜索主题 / 收件人 / 正文"
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
              <option value="queued">排队中</option>
              <option value="sent">已发送</option>
              <option value="failed">失败</option>
              <option value="bounced">退信</option>
            </select>
          </div>

          {logs.length === 0 ? (
            <div className="msg" style={{ color: "#999", padding: "24px 0" }}>
              暂无发送记录，可在草稿箱中将待发邮件 SMTP 发送后在此查看
            </div>
          ) : (
            <div className="table-wrap">
              <table className="table sent-table">
                <thead>
                  <tr>
                    <th>客户</th>
                    <th>收件人</th>
                    <th>主题</th>
                    <th>状态</th>
                    <th>发送时间</th>
                    <th>追踪</th>
                    <th style={{ width: 200 }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {logs.map((log) => (
                    <SentRow
                      key={log.id}
                      log={log}
                      expanded={expandedId === log.id}
                      onToggleExpand={() =>
                        setExpandedId(expandedId === log.id ? null : log.id)
                      }
                      retrying={retryingId === log.id}
                      onRetry={() => retry(log)}
                      onRemove={() => remove(log)}
                      onOpenCustomer={() => navigate("/customers")}
                    />
                  ))}
                </tbody>
              </table>
            </div>
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
                {page + 1} / {totalPages}（共 {totalElements} 条）
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

/** 单行发送记录（点击行展开正文与详情） */
function SentRow(props: {
  log: SendLogView;
  expanded: boolean;
  retrying: boolean;
  onToggleExpand: () => void;
  onRetry: () => void;
  onRemove: () => void;
  onOpenCustomer: () => void;
}) {
  const { log: d, expanded } = props;
  const tracked = d.openedAt || d.clickedAt;
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
            <span style={{ color: "#999" }}>（已删除客户）</span>
          )}
        </td>
        <td style={{ fontSize: 13, color: "#666", whiteSpace: "nowrap" }}>
          {d.toEmail}
        </td>
        <td style={{ maxWidth: 240, wordBreak: "break-word" }}>{d.subject}</td>
        <td>
          <span
            className={`badge ${SEND_STATUS_BADGE[d.status] ?? "badge-new"}`}
          >
            {SEND_STATUS_LABEL[d.status] ?? d.status}
          </span>
        </td>
        <td style={{ fontSize: 13, color: "#666", whiteSpace: "nowrap" }}>
          {d.sentAt ? fmtTime(d.sentAt) : fmtTime(d.createdAt)}
        </td>
        <td style={{ fontSize: 13, whiteSpace: "nowrap" }}>
          {tracked ? (
            <div style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              {d.openedAt && <span>👁 已打开 {fmtTime(d.openedAt)}</span>}
              {d.clickedAt && <span>🖱 已点击 {fmtTime(d.clickedAt)}</span>}
            </div>
          ) : (
            <span style={{ color: "#bbb" }}>-</span>
          )}
        </td>
        <td>
          <div
            style={{ display: "flex", gap: 6 }}
            onClick={(e) => e.stopPropagation()}
          >
            {d.status === "failed" && (
              <button
                className="btn btn-xs btn-primary"
                onClick={props.onRetry}
                disabled={props.retrying}
                title="重新走 SMTP 投递（草稿须仍为待发）"
              >
                {props.retrying ? "重试中…" : "✉ 重试"}
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
          <td colSpan={7} style={{ background: "#fafafa" }}>
            <div style={{ padding: "4px 8px 12px" }}>
              <div style={{ fontSize: 12, color: "#999", marginBottom: 6 }}>
                发件人：{d.fromEmail} ｜ 收件人：{d.toEmail}
                {d.sentAt ? ` ｜ 发送时间：${fmtTime(d.sentAt)}` : ""}
                {d.openedAt ? ` ｜ 首次打开：${fmtTime(d.openedAt)}` : ""}
                {d.clickedAt ? ` ｜ 首次点击：${fmtTime(d.clickedAt)}` : ""}
                {d.errorMsg && (
                  <span style={{ color: "#ff4d4f" }}>
                    {" "}
                    ｜ 失败原因：{d.errorMsg}
                  </span>
                )}
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
