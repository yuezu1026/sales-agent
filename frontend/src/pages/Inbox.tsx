import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken } from "../api/client";
import { confirmDialog } from "../utils/dialog";
import { Nav } from "./Nav";

/** 收件箱邮件（M2-1.6，表 email_inbox） */
interface EmailInbox {
  id: number;
  leadId: number | null;
  leadCompanyName: string | null;
  leadContactName: string | null;
  fromAddress: string;
  fromName: string | null;
  subject: string | null;
  body: string | null;
  receivedAt: string;
  isRead: boolean;
  aiIntent: string | null;
  aiSummary: string | null;
  aiAnalysisStatus: string;
  createdAt: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** AI 分析结果（邮件意图 + 回复建议） */
interface AnalyzeResult {
  intent: string;
  summary: string;
  replySubject: string;
  replyBody: string;
}

const INTENT_LABEL: Record<string, string> = {
  inquiry: "询价",
  quote: "报价",
  objection: "异议",
  followup: "跟进",
  positive: "积极",
  other: "其他",
};

const INTENT_BADGE: Record<string, string> = {
  inquiry: "badge-new",
  quote: "badge-converted",
  objection: "badge-contacted",
  followup: "badge-followup",
  positive: "badge-interested",
  other: "badge-invalid",
};

const ANALYSIS_STATUS_LABEL: Record<string, string> = {
  pending: "未分析",
  analyzed: "已分析",
  failed: "分析失败",
};

const FOLLOW_UP_METHODS: Record<string, string> = {
  phone: "电话",
  email: "邮件",
  wechat: "微信",
  visit: "拜访",
  other: "其他",
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

/** 收件箱（M2-1.6）：MCP 抓取客户回复邮件 + 管理（已读/转跟进/AI 分析/删除） */
export default function Inbox() {
  const navigate = useNavigate();
  const [emails, setEmails] = useState<EmailInbox[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);
  const [expandedId, setExpandedId] = useState<number | null>(null);

  // 一键转跟进
  const [convertMethod, setConvertMethod] = useState("phone");
  const [convertingId, setConvertingId] = useState<number | null>(null);

  // AI 意图分析弹窗
  const [aiMail, setAiMail] = useState<EmailInbox | null>(null);
  const [aiResult, setAiResult] = useState<AnalyzeResult | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiMsg, setAiMsg] = useState<string | null>(null);
  const [savingDraft, setSavingDraft] = useState(false);

  const load = useCallback(
    async (silent = false) => {
      try {
        const params = new URLSearchParams({ page: String(page), size: "10" });
        if (keyword.trim()) params.set("keyword", keyword.trim());
        if (unreadOnly) params.set("unreadOnly", "true");
        const data = await api<Page<EmailInbox>>(
          `/emails/inbox?${params.toString()}`,
        );
        // 页码越界保护：在最后一页删除邮件后，总页数减少，当前页码可能超出范围
        // （后端对超出范围的分页返回空列表），此时回退到最后一页重新加载
        if (data.totalPages > 0 && data.number >= data.totalPages) {
          setPage(data.totalPages - 1);
          return;
        }
        setEmails(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);
      } catch (e) {
        // 自动刷新（silent=true）失败不打扰用户，仅手动操作时提示
        if (!silent) setMsg((e as Error).message);
      }
    },
    [page, keyword, unreadOnly],
  );

  useEffect(() => {
    load();
  }, [load]);

  // 自动刷新：每 10 秒静默刷新已入库邮件列表（IMAP 抓取由后端 15 秒定时同步完成，
  // 这里只拉列表，零 IMAP 成本）；页面不可见时跳过，组件卸载后清除定时器
  useEffect(() => {
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        load(true);
      }
    }, 10000);
    return () => window.clearInterval(timer);
  }, [load]);

  /** 手动触发 MCP 同步 */
  const sync = async () => {
    setSyncing(true);
    setMsg(null);
    try {
      const r = await api<{ added: number; total: number }>(
        "/emails/inbox/sync",
        { method: "POST" },
      );
      setMsg(`同步完成：新增 ${r.added} 封邮件，收件箱累计 ${r.total} 封`);
      setPage(0);
      await load();
    } catch (e) {
      setMsg((e as Error).message);
    } finally {
      setSyncing(false);
    }
  };

  /** 标记已读 / 未读 */
  const toggleRead = async (mail: EmailInbox) => {
    try {
      await api(`/emails/inbox/${mail.id}/read`, {
        method: "PUT",
        body: JSON.stringify({ read: !mail.isRead }),
      });
      await load();
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  /** 一键转跟进记录 */
  const convert = async (mail: EmailInbox) => {
    if (!mail.leadId) {
      setMsg(
        "该邮件未关联客户，无法转为跟进记录（请先在客户管理中为对应客户填写相同邮箱）",
      );
      return;
    }
    setConvertingId(mail.id);
    setMsg(null);
    try {
      await api(`/emails/inbox/${mail.id}/convert-follow-up`, {
        method: "POST",
        body: JSON.stringify({ method: convertMethod }),
      });
      setMsg("已转为跟进记录");
      setExpandedId(null);
    } catch (e) {
      setMsg((e as Error).message);
    } finally {
      setConvertingId(null);
    }
  };

  /** 打开 AI 分析弹窗 */
  const openAnalyze = (mail: EmailInbox) => {
    setAiMail(mail);
    setAiResult(null);
    setAiMsg(null);
  };

  const analyze = async () => {
    if (!aiMail) return;
    setAiLoading(true);
    setAiMsg(null);
    try {
      const r = await api<AnalyzeResult>(`/emails/inbox/${aiMail.id}/analyze`, {
        method: "POST",
      });
      setAiResult(r);
      await load();
    } catch (e) {
      setAiMsg((e as Error).message);
    } finally {
      setAiLoading(false);
    }
  };

  /** AI 回复建议保存为客户邮件草稿 */
  const saveReplyDraft = async () => {
    if (!aiMail || !aiResult) return;
    if (!aiMail.leadId) {
      setAiMsg("该邮件未关联客户，无法保存回复草稿");
      return;
    }
    setSavingDraft(true);
    setAiMsg(null);
    try {
      await api(`/leads/${aiMail.leadId}/email-drafts`, {
        method: "POST",
        body: JSON.stringify({
          subject: aiResult.replySubject,
          body: aiResult.replyBody,
          tone: "neutral",
        }),
      });
      setAiMsg("回复草稿已保存到客户邮件草稿");
    } catch (e) {
      setAiMsg((e as Error).message);
    } finally {
      setSavingDraft(false);
    }
  };

  /** 删除邮件 */
  const remove = async (mail: EmailInbox) => {
    if (
      !(await confirmDialog(
        `确认删除邮件「${mail.subject || mail.fromAddress}」？`,
        { danger: true },
      ))
    )
      return;
    try {
      await api(`/emails/inbox/${mail.id}`, { method: "DELETE" });
      if (expandedId === mail.id) setExpandedId(null);
      await load();
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  return (
    <div>
      <Nav
        current="inbox"
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
          <h2 style={{ margin: 0 }}>收件箱</h2>
          <button className="btn btn-sm" onClick={sync} disabled={syncing}>
            {syncing ? "同步中…" : "↻ 同步邮件"}
          </button>
        </div>

        {msg && (
          <div
            className={`msg ${
              msg.includes("成功") || msg.includes("完成") ? "success" : "error"
            }`}
          >
            {msg}
          </div>
        )}

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
              placeholder="搜索发件人 / 主题 / 内容"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPage(0);
              }}
            />
            <label
              style={{
                fontSize: 13,
                display: "flex",
                alignItems: "center",
                gap: 4,
              }}
            >
              <input
                type="checkbox"
                checked={unreadOnly}
                onChange={(e) => {
                  setUnreadOnly(e.target.checked);
                  setPage(0);
                }}
              />
              只看未读
            </label>
          </div>

          {emails.length === 0 ? (
            <div className="msg" style={{ color: "#999", padding: "24px 0" }}>
              暂无邮件，点击「同步邮件」从邮箱抓取客户回复
            </div>
          ) : (
            <div className="table-wrap">
              <table className="table inbox-table">
                <thead>
                  <tr>
                    <th>发件人</th>
                    <th>主题</th>
                    <th>时间</th>
                    <th>关联客户</th>
                    <th>AI 意图</th>
                    <th style={{ width: 90 }}>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {emails.map((mail) => (
                    <EmailRow
                      key={mail.id}
                      mail={mail}
                      expanded={expandedId === mail.id}
                      converting={convertingId === mail.id}
                      convertMethod={convertMethod}
                      onConvertMethod={setConvertMethod}
                      onToggleExpand={() =>
                        setExpandedId(expandedId === mail.id ? null : mail.id)
                      }
                      onToggleRead={() => toggleRead(mail)}
                      onConvert={() => convert(mail)}
                      onAnalyze={() => openAnalyze(mail)}
                      onRemove={() => remove(mail)}
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

      {aiMail && (
        <div className="modal-mask" onClick={() => setAiMail(null)}>
          <div
            className="modal modal-wide"
            onClick={(e) => e.stopPropagation()}
          >
            <h3>AI 意图分析</h3>
            <div style={{ fontSize: 13, color: "#666", marginBottom: 12 }}>
              《{aiMail.subject || "（无主题）"}》 —{" "}
              {aiMail.fromName || aiMail.fromAddress}
            </div>
            {aiResult ? (
              <>
                <div className="form-item">
                  <label>意图</label>
                  <div style={{ marginTop: 4 }}>
                    <span
                      className={`badge ${
                        INTENT_BADGE[aiResult.intent] || "badge-invalid"
                      }`}
                    >
                      {INTENT_LABEL[aiResult.intent] || aiResult.intent}
                    </span>
                  </div>
                </div>
                <div className="form-item">
                  <label>摘要</label>
                  <div className="inbox-body">{aiResult.summary}</div>
                </div>
                <div className="form-item">
                  <label>回复主题</label>
                  <input
                    className="filter-input"
                    style={{ width: "100%", marginTop: 4 }}
                    value={aiResult.replySubject}
                    readOnly
                  />
                </div>
                <div className="form-item">
                  <label>回复建议</label>
                  <div className="inbox-body">{aiResult.replyBody}</div>
                </div>
                <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
                  <button
                    className="btn btn-sm btn-default"
                    onClick={() => setAiMail(null)}
                  >
                    关闭
                  </button>
                  <button
                    className="btn btn-sm"
                    onClick={saveReplyDraft}
                    disabled={savingDraft || !aiMail.leadId}
                    title={
                      !aiMail.leadId ? "该邮件未关联客户" : "保存到客户邮件草稿"
                    }
                  >
                    {savingDraft ? "保存中…" : "保存为回复草稿"}
                  </button>
                </div>
              </>
            ) : (
              <>
                <p style={{ fontSize: 13, color: "#666" }}>
                  AI 将分析邮件意图（询价 / 报价 / 异议 / 跟进 /
                  积极），生成摘要与回复建议。
                </p>
                {aiLoading && (
                  <div className="msg" style={{ color: "#1677ff" }}>
                    AI 分析中，请稍候…
                  </div>
                )}
                {aiMsg && (
                  <div
                    className={`msg ${
                      aiMsg.includes("成功") ? "success" : "error"
                    }`}
                  >
                    {aiMsg}
                  </div>
                )}
                <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
                  <button
                    className="btn btn-sm btn-default"
                    onClick={() => setAiMail(null)}
                  >
                    取消
                  </button>
                  <button
                    className="btn btn-sm"
                    onClick={analyze}
                    disabled={aiLoading}
                  >
                    {aiLoading ? "分析中…" : "开始分析"}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

/** 单行邮件（含展开详情） */
function EmailRow(props: {
  mail: EmailInbox;
  expanded: boolean;
  converting: boolean;
  convertMethod: string;
  onConvertMethod: (m: string) => void;
  onToggleExpand: () => void;
  onToggleRead: () => void;
  onConvert: () => void;
  onAnalyze: () => void;
  onRemove: () => void;
}) {
  const { mail, expanded, converting, convertMethod, onConvertMethod } = props;
  const analysisLabel =
    ANALYSIS_STATUS_LABEL[mail.aiAnalysisStatus] || mail.aiAnalysisStatus;
  return (
    <>
      <tr
        className={mail.isRead ? "" : "inbox-row-unread"}
        style={{ cursor: "pointer" }}
        onClick={props.onToggleExpand}
      >
        <td>
          {!mail.isRead && <span className="inbox-unread-dot" />}
          <div>{mail.fromName || mail.fromAddress}</div>
          <div style={{ fontSize: 12, color: "#999" }}>{mail.fromAddress}</div>
        </td>
        <td>
          <div>{mail.subject || "（无主题）"}</div>
          {mail.aiIntent && (
            <span
              className={`badge ${INTENT_BADGE[mail.aiIntent] || "badge-invalid"}`}
              style={{ marginTop: 4 }}
            >
              {INTENT_LABEL[mail.aiIntent] || mail.aiIntent}
            </span>
          )}
        </td>
        <td style={{ whiteSpace: "nowrap", fontSize: 13 }}>
          {fmtTime(mail.receivedAt)}
        </td>
        <td style={{ fontSize: 13 }}>
          {mail.leadId ? (
            <a
              href={`${import.meta.env.BASE_URL}customers`}
              style={{ color: "#1677ff" }}
            >
              {mail.leadCompanyName || `客户#${mail.leadId}`}
            </a>
          ) : (
            <span style={{ color: "#999" }}>未关联</span>
          )}
        </td>
        <td>
          <span
            className={`badge ${mail.isRead ? "badge-invalid" : "badge-new"}`}
          >
            {mail.isRead ? "已读" : "未读"}
          </span>
          <div style={{ fontSize: 12, color: "#999", marginTop: 4 }}>
            {analysisLabel}
          </div>
        </td>
        <td>
          <button
            className="btn btn-xs btn-default"
            onClick={(e) => {
              e.stopPropagation();
              props.onToggleExpand();
            }}
          >
            {expanded ? "收起" : "详情"}
          </button>
        </td>
      </tr>
      {expanded && (
        <tr>
          <td
            colSpan={6}
            style={{ background: "#fafafa", padding: "12px 16px" }}
          >
            <div style={{ fontSize: 13, marginBottom: 8 }}>
              <strong>发件人：</strong>
              {mail.fromName
                ? `${mail.fromName} <${mail.fromAddress}>`
                : mail.fromAddress}
              <br />
              <strong>收件时间：</strong>
              {fmtTime(mail.receivedAt)}
              {mail.aiSummary && (
                <>
                  <br />
                  <strong>AI 摘要：</strong>
                  {mail.aiSummary}
                </>
              )}
            </div>
            <div className="inbox-body">{mail.body || "（无正文）"}</div>
            <div
              style={{
                display: "flex",
                gap: 8,
                marginTop: 12,
                flexWrap: "wrap",
                alignItems: "center",
              }}
            >
              <button
                className="btn btn-xs btn-default"
                onClick={props.onToggleRead}
              >
                {mail.isRead ? "标记未读" : "标记已读"}
              </button>
              <select
                className="filter-input"
                style={{ width: "auto" }}
                value={convertMethod}
                onChange={(e) => onConvertMethod(e.target.value)}
              >
                {Object.entries(FOLLOW_UP_METHODS).map(([v, label]) => (
                  <option key={v} value={v}>
                    转{label}跟进
                  </option>
                ))}
              </select>
              <button
                className="btn btn-xs"
                onClick={props.onConvert}
                disabled={converting || !mail.leadId}
                title={!mail.leadId ? "该邮件未关联客户" : ""}
              >
                {converting ? "转换中…" : "一键转跟进"}
              </button>
              <button
                className="btn btn-xs btn-primary"
                onClick={props.onAnalyze}
              >
                AI 分析
              </button>
              <button
                className="btn btn-xs btn-danger"
                onClick={props.onRemove}
              >
                删除
              </button>
            </div>
          </td>
        </tr>
      )}
    </>
  );
}
