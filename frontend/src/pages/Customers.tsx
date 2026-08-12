import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken, getToken } from "../api/client";
import { isHtmlText } from "../utils/html";
import { confirmDialog } from "../utils/dialog";
import RichTextEditor from "../components/RichTextEditor";
import { Nav } from "./Nav";

interface Lead {
  id: number;
  companyName: string;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  wechatId: string | null;
  wechatName: string | null;
  gender: string | null;
  industry: string | null;
  region: string | null;
  scale: string | null;
  website: string | null;
  address: string | null;
  stockCode: string | null;
  sourceType: string;
  sourceId: string | null;
  profileScore: number;
  profileSummary: string | null;
  status: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/** 跟进记录（表 follow_up） */
interface FollowUp {
  id: number;
  leadId: number;
  method: string;
  content: string;
  happenedAt: string;
  createdAt: string;
}

/** 邮件草稿（表 email_draft） */
interface EmailDraft {
  id: number;
  leadId: number;
  subject: string;
  body: string;
  tone: string;
  status: string;
  createdAt: string;
  confirmedAt: string | null;
}

/** 邮件模板（表 email_template，V13） */
interface EmailTemplate {
  id: number;
  name: string;
  subject: string;
  body: string;
  description: string | null;
  updatedAt: string;
}

/** 微信沟通消息（表 wechat_message） */
interface WechatMessage {
  id: number;
  leadId: number;
  direction: string; // in=客户发来 / out=我方发出
  content: string;
  aiReply: string | null;
  status: string; // recorded / ai_confirmed
  sentAt: string;
  createdAt: string;
}

/** 邮件发送记录（表 email_send_log，M3-2） */
interface EmailSendLog {
  id: number;
  leadId: number;
  draftId: number | null;
  fromEmail: string;
  toEmail: string;
  subject: string;
  body: string;
  status: string; // queued / sent / failed / bounced
  errorMsg: string | null;
  sentAt: string | null;
  openedAt: string | null; // M4-6 首次打开时间
  clickedAt: string | null; // M4-6 首次点击时间
  createdAt: string;
}

const FOLLOW_UP_METHODS: Record<string, string> = {
  phone: "电话",
  email: "邮件",
  wechat: "微信",
  visit: "拜访",
  other: "其他",
};

const DRAFT_STATUS_LABEL: Record<string, string> = {
  draft: "草稿",
  confirmed: "待发",
  sent: "已发送",
};

const SEND_LOG_STATUS_LABEL: Record<string, string> = {
  queued: "排队中",
  sent: "已发送",
  failed: "失败",
  bounced: "退信",
};

const STATUS_LABEL: Record<string, string> = {
  new: "新线索",
  contacted: "已触达",
  interested: "有意向",
  converted: "已转化",
  invalid: "无效",
};

/** 行业下拉选项（空值=未填写；含"其他"兜底） */
const INDUSTRY_OPTIONS = [
  "SaaS/软件",
  "互联网",
  "金融",
  "制造",
  "零售/电商",
  "医疗健康",
  "教育",
  "物流",
  "房地产",
  "能源",
  "其他",
];

/** 客户渠道来源选项（manual=手动录入，与后端 source_type 兼容） */
const SOURCE_OPTIONS = [
  "朋友介绍",
  "展会",
  "广告投放",
  "官网",
  "社交媒体",
  "电话拜访",
  "其他",
];

/** 来源显示映射（兼容旧数据 manual/csv/api） */
const SOURCE_LABEL: Record<string, string> = {
  manual: "手动录入",
  csv: "CSV 导入",
  api: "API 导入",
};

/** 状态流转合法目标（与后端 LeadService.STATUS_TRANSITIONS 一致） */
const STATUS_NEXT: Record<string, string[]> = {
  new: ["contacted", "invalid"],
  contacted: ["interested", "invalid"],
  interested: ["converted", "invalid"],
  converted: [],
  invalid: [],
};

const EMPTY_FORM = {
  companyName: "",
  contactName: "",
  contactEmail: "",
  contactPhone: "",
  wechatId: "",
  wechatName: "",
  gender: "",
  industry: "",
  region: "",
  scale: "",
  website: "",
  address: "",
  stockCode: "",
  sourceType: "manual",
  notes: "",
};

/** 客户管理（M2-1）：列表 / 搜索筛选 / 新增编辑 / 打标 / 一键生成邮件 / CSV 导入导出 */
export default function Customers() {
  const navigate = useNavigate();
  const [leads, setLeads] = useState<Lead[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [keyword, setKeyword] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [msg, setMsg] = useState<string | null>(null);

  const [modal, setModal] = useState<null | "create" | "edit">(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);

  // 一键生成邮件
  const [genLead, setGenLead] = useState<Lead | null>(null);
  const [goal, setGoal] = useState("");
  const [genLoading, setGenLoading] = useState(false);
  const [genResult, setGenResult] = useState("");
  const [genSubject, setGenSubject] = useState("");
  const [genMsg, setGenMsg] = useState<string | null>(null);
  const [savingDraft, setSavingDraft] = useState(false);
  // 邮件模板（M3-2 补充：一键套用，占位符保存草稿时自动替换；AI 生成时可作为风格参考）
  const [templates, setTemplates] = useState<EmailTemplate[]>([]);
  const [templateId, setTemplateId] = useState("");
  // 生成结果视图（M7 富文本：edit 所见即所得 / source 源码 / preview 预览）
  const [genMode, setGenMode] = useState<"edit" | "source" | "preview">("edit");
  // 富文本编辑器重建 key（AI 生成/套模板后强制加载新内容，TipTap content 仅创建时生效）
  const [genResultKey, setGenResultKey] = useState(0);

  // 跟进记录 + 邮件草稿（客户详情弹窗）
  const [detailLead, setDetailLead] = useState<Lead | null>(null);
  const [followUps, setFollowUps] = useState<FollowUp[]>([]);
  const [fuMethod, setFuMethod] = useState("phone");
  const [fuContent, setFuContent] = useState("");
  const [fuMsg, setFuMsg] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<EmailDraft[]>([]);
  const [draftMsg, setDraftMsg] = useState<string | null>(null);
  const [draftSendingId, setDraftSendingId] = useState<number | null>(null);

  // 邮件发送记录（M3-2 补充：发送历史 + 失败重试）
  const [sendLogs, setSendLogs] = useState<EmailSendLog[]>([]);
  const [sendLogMsg, setSendLogMsg] = useState<string | null>(null);
  const [retryingId, setRetryingId] = useState<number | null>(null);

  // 微信沟通（M2-1.8 记录式工作台）
  const [wechatLead, setWechatLead] = useState<Lead | null>(null);
  const [wmMessages, setWmMessages] = useState<WechatMessage[]>([]);
  const [wmMsg, setWmMsg] = useState<string | null>(null);
  const [wmDirection, setWmDirection] = useState<"in" | "out">("in");
  const [wmContent, setWmContent] = useState("");
  const [wmGoal, setWmGoal] = useState("");
  const [wmAiReply, setWmAiReply] = useState<string | null>(null);
  const [wmSugLoading, setWmSugLoading] = useState(false);
  const [wmSaving, setWmSaving] = useState(false);

  const load = useCallback(async () => {
    try {
      const params = new URLSearchParams({ page: String(page), size: "10" });
      if (keyword.trim()) params.set("keyword", keyword.trim());
      if (statusFilter) params.set("status", statusFilter);
      const data = await api<Page<Lead>>(`/leads?${params.toString()}`);
      // 页码越界保护：在最后一页删除数据后总页数减少，当前页码可能超出范围
      // （后端对越界分页返回空列表），此时回退到最后一页重新加载
      if (data.totalPages > 0 && data.number >= data.totalPages) {
        setPage(data.totalPages - 1);
        return;
      }
      setLeads(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch {
      navigate("/login");
    }
  }, [page, keyword, statusFilter, navigate]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setMsg(null);
    setModal("create");
  };

  const openEdit = (lead: Lead) => {
    setForm({
      companyName: lead.companyName,
      contactName: lead.contactName ?? "",
      contactEmail: lead.contactEmail ?? "",
      contactPhone: lead.contactPhone ?? "",
      wechatId: lead.wechatId ?? "",
      wechatName: lead.wechatName ?? "",
      gender: lead.gender ?? "",
      industry: lead.industry ?? "",
      region: lead.region ?? "",
      scale: lead.scale ?? "",
      website: lead.website ?? "",
      address: lead.address ?? "",
      stockCode: lead.stockCode ?? "",
      sourceType: lead.sourceType ?? "manual",
      notes: lead.notes ?? "",
    });
    setEditingId(lead.id);
    setMsg(null);
    setModal("edit");
  };

  const submit = async () => {
    if (!form.companyName.trim()) {
      setMsg("公司名称不能为空");
      return;
    }
    if (!form.contactName.trim()) {
      setMsg("联系人不能为空");
      return;
    }
    if (!form.contactEmail.trim()) {
      setMsg("邮箱不能为空");
      return;
    }
    setSaving(true);
    setMsg(null);
    try {
      if (modal === "create") {
        await api("/leads", { method: "POST", body: JSON.stringify(form) });
      } else {
        await api(`/leads/${editingId}`, {
          method: "PUT",
          body: JSON.stringify(form),
        });
      }
      setModal(null);
      setPage(0);
      await load();
    } catch (e) {
      setMsg((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const remove = async (lead: Lead) => {
    if (
      !(await confirmDialog(`确认删除客户「${lead.companyName}」？`, {
        danger: true,
      }))
    )
      return;
    try {
      await api(`/leads/${lead.id}`, { method: "DELETE" });
      await load();
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  const changeStatus = async (lead: Lead, status: string) => {
    if (status === lead.status) return;
    // M7.4：状态变更必须确认；终态（converted/invalid）不可回退，用 danger 提示
    const isTerminal = status === "converted" || status === "invalid";
    const ok = await confirmDialog(
      `确认将客户「${lead.companyName}」状态从「${STATUS_LABEL[lead.status] ?? lead.status}」改为「${STATUS_LABEL[status] ?? status}」？` +
        (isTerminal ? "\n\n⚠️ 该状态为终态，变更后不可回退。" : ""),
      {
        title: isTerminal ? "终态变更确认" : "状态变更确认",
        danger: isTerminal,
        confirmText: "确认变更",
      },
    );
    if (!ok) {
      // 取消：强制重渲染，让下拉框还原为原状态（避免显示新值但实际未改的假象）
      setLeads((prev) => prev.map((l) => (l.id === lead.id ? { ...l } : l)));
      return;
    }
    try {
      await api(`/leads/${lead.id}/status`, {
        method: "PUT",
        body: JSON.stringify({ status }),
      });
      await load();
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  const openGenerate = (lead: Lead) => {
    setGenLead(lead);
    setGoal("");
    setGenResult("");
    setGenMsg(null);
    setTemplateId("");
    setGenMode("edit");
    setGenResultKey((k) => k + 1);
    // 拉取邮件模板供一键套用
    api<EmailTemplate[]>("/email-templates")
      .then(setTemplates)
      .catch(() => setTemplates([]));
  };

  /** 套用模板：主题/正文填入可编辑区（保存草稿时占位符按该客户字段自动替换） */
  const applyTemplate = (id: string) => {
    const t = templates.find((x) => String(x.id) === id);
    if (!t) return;
    setGenSubject(t.subject);
    setGenResult(t.body);
    setGenResultKey((k) => k + 1);
    setGenMsg(null);
  };

  const generate = async () => {
    if (!genLead) return;
    if (!goal.trim()) {
      setGenMsg("请填写触达目标");
      return;
    }
    setGenLoading(true);
    setGenMsg(null);
    try {
      // M2-1.7：带沟通记录上下文生成（跟进/已发邮件/客户回复 → 延续性邮件）
      // M3-2：可选模板作为风格参考，AI 结合沟通历史个性化编写，每次生成不同
      const data = await api<{ subject: string; body: string }>(
        `/leads/${genLead.id}/email-drafts/generate`,
        {
          method: "POST",
          body: JSON.stringify({
            goal: goal.trim(),
            tone: "neutral",
            templateId: templateId ? Number(templateId) : null,
          }),
        },
      );
      setGenSubject(data.subject ?? "");
      setGenResult(data.body ?? "");
      setGenMode("edit");
      setGenResultKey((k) => k + 1);
    } catch (e) {
      setGenMsg((e as Error).message);
    } finally {
      setGenLoading(false);
    }
  };

  /** CSV 导出：fetch 带 token 取 blob 下载（UTF-8 BOM 兼容 Excel） */
  const exportCsv = async () => {
    try {
      const params = new URLSearchParams();
      if (keyword.trim()) params.set("keyword", keyword.trim());
      if (statusFilter) params.set("status", statusFilter);
      const token = getToken();
      const resp = await fetch(`/api/leads/export.csv?${params.toString()}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!resp.ok) {
        const body = await resp.json().catch(() => null);
        throw new Error(body?.message || "导出失败");
      }
      const blob = await resp.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `leads_${new Date().toISOString().slice(0, 10)}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  /** CSV 导入：FormData 上传，展示统计结果 */
  const importCsv = async (file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    try {
      const token = getToken();
      const resp = await fetch("/api/leads/import", {
        method: "POST",
        body: fd,
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const body = await resp.json();
      if (!resp.ok || body.code !== 0) {
        throw new Error(body.message || "导入失败");
      }
      const r = body.data;
      const errText =
        r.errors && r.errors.length > 0
          ? `，错误 ${r.errors.length} 条：${r.errors.slice(0, 3).join("；")}`
          : "";
      setMsg(
        `导入完成：成功 ${r.success} 条，重复 ${r.duplicate} 条${errText}`,
      );
      setPage(0);
      await load();
    } catch (e) {
      setMsg((e as Error).message);
    }
  };

  /** 打开客户详情（跟进记录 + 邮件草稿 + 发送记录） */
  const openDetail = async (lead: Lead) => {
    setDetailLead(lead);
    setFuMsg(null);
    setDraftMsg(null);
    setSendLogMsg(null);
    setFuContent("");
    setFuMethod("phone");
    try {
      const [fuData, draftData, logData] = await Promise.all([
        api<FollowUp[]>(`/leads/${lead.id}/follow-ups`),
        api<EmailDraft[]>(`/leads/${lead.id}/email-drafts`),
        api<EmailSendLog[]>(`/leads/${lead.id}/email-send-logs`),
      ]);
      setFollowUps(fuData);
      setDrafts(draftData);
      setSendLogs(logData);
    } catch (e) {
      setFuMsg((e as Error).message);
    }
  };

  const closeDetail = () => setDetailLead(null);

  /** 刷新客户详情内发送记录 */
  const reloadSendLogs = async (leadId: number) => {
    const logData = await api<EmailSendLog[]>(
      `/leads/${leadId}/email-send-logs`,
    );
    setSendLogs(logData);
  };

  /** 重试失败的发送记录（重新 SMTP 投递，成功后草稿流转 sent） */
  const retrySendLog = async (log: EmailSendLog) => {
    if (!detailLead) return;
    if (
      !(await confirmDialog(
        `确认重试发送「${log.subject}」给 ${log.toEmail}？`,
      ))
    )
      return;
    setSendLogMsg(null);
    setRetryingId(log.id);
    try {
      const res = await api<{
        sendLogId: number;
        status: string;
        toEmail: string;
        errorMsg: string | null;
      }>(`/leads/${detailLead.id}/email-send-logs/${log.id}/retry`, {
        method: "POST",
      });
      if (res.status === "sent") {
        setSendLogMsg(`重试成功，已发送至 ${res.toEmail}`);
      } else {
        setSendLogMsg(`重试失败：${res.errorMsg ?? "未知错误"}`);
      }
      // 重试会产生新记录；若成功草稿已流转 sent，一并刷新草稿
      const [logData, draftData] = await Promise.all([
        api<EmailSendLog[]>(`/leads/${detailLead.id}/email-send-logs`),
        api<EmailDraft[]>(`/leads/${detailLead.id}/email-drafts`),
      ]);
      setSendLogs(logData);
      setDrafts(draftData);
    } catch (e) {
      setSendLogMsg((e as Error).message);
    } finally {
      setRetryingId(null);
    }
  };

  /** 打开微信会话 */
  const openWechat = async (lead: Lead) => {
    setWechatLead(lead);
    setWmMsg(null);
    setWmContent("");
    setWmGoal("");
    setWmDirection("in");
    setWmAiReply(null);
    try {
      const data = await api<WechatMessage[]>(
        `/leads/${lead.id}/wechat-messages`,
      );
      setWmMessages(data);
    } catch (e) {
      setWmMsg((e as Error).message);
    }
  };

  const closeWechat = () => setWechatLead(null);

  /** 记录一条微信消息（in/out） */
  const addWechatMessage = async () => {
    if (!wechatLead) return;
    if (!wmContent.trim()) {
      setWmMsg("消息内容不能为空");
      return;
    }
    setWmSaving(true);
    setWmMsg(null);
    try {
      await api(`/leads/${wechatLead.id}/wechat-messages`, {
        method: "POST",
        body: JSON.stringify({
          direction: wmDirection,
          content: wmContent.trim(),
          aiReply: wmAiReply,
        }),
      });
      setWmContent("");
      setWmAiReply(null);
      const data = await api<WechatMessage[]>(
        `/leads/${wechatLead.id}/wechat-messages`,
      );
      setWmMessages(data);
    } catch (e) {
      setWmMsg((e as Error).message);
    } finally {
      setWmSaving(false);
    }
  };

  /** AI 生成微信回复建议（填入输入框，可编辑后确认发出） */
  const suggestWechat = async () => {
    if (!wechatLead) return;
    setWmSugLoading(true);
    setWmMsg(null);
    try {
      const data = await api<{ reply: string }>(
        `/leads/${wechatLead.id}/wechat-messages/suggest`,
        {
          method: "POST",
          body: JSON.stringify({
            goal: wmGoal.trim() || undefined,
            tone: "friendly",
          }),
        },
      );
      setWmContent(data.reply ?? "");
      setWmDirection("out");
      setWmAiReply(data.reply ?? null);
      setWmMsg("已生成回复建议（可编辑），确认后点「记录为已发」");
    } catch (e) {
      setWmMsg((e as Error).message);
    } finally {
      setWmSugLoading(false);
    }
  };

  /** 删除微信消息 */
  const removeWechatMessage = async (id: number) => {
    if (!wechatLead) return;
    if (!(await confirmDialog("确认删除这条微信消息？", { danger: true })))
      return;
    try {
      await api(`/leads/${wechatLead.id}/wechat-messages/${id}`, {
        method: "DELETE",
      });
      const data = await api<WechatMessage[]>(
        `/leads/${wechatLead.id}/wechat-messages`,
      );
      setWmMessages(data);
    } catch (e) {
      setWmMsg((e as Error).message);
    }
  };

  /** 新增跟进记录 */
  const addFollowUp = async () => {
    if (!detailLead) return;
    if (!fuContent.trim()) {
      setFuMsg("跟进内容不能为空");
      return;
    }
    try {
      await api(`/leads/${detailLead.id}/follow-ups`, {
        method: "POST",
        body: JSON.stringify({ method: fuMethod, content: fuContent.trim() }),
      });
      setFuContent("");
      setFuMsg(null);
      const data = await api<FollowUp[]>(`/leads/${detailLead.id}/follow-ups`);
      setFollowUps(data);
    } catch (e) {
      setFuMsg((e as Error).message);
    }
  };

  /** 删除跟进记录 */
  const removeFollowUp = async (id: number) => {
    if (!detailLead) return;
    if (!(await confirmDialog("确认删除这条跟进记录？", { danger: true })))
      return;
    try {
      await api(`/leads/${detailLead.id}/follow-ups/${id}`, {
        method: "DELETE",
      });
      const data = await api<FollowUp[]>(`/leads/${detailLead.id}/follow-ups`);
      setFollowUps(data);
    } catch (e) {
      setFuMsg((e as Error).message);
    }
  };

  /** 删除邮件草稿 */
  const removeDraft = async (id: number) => {
    if (!detailLead) return;
    if (!(await confirmDialog("确认删除这封邮件草稿？", { danger: true })))
      return;
    try {
      await api(`/leads/${detailLead.id}/email-drafts/${id}`, {
        method: "DELETE",
      });
      const data = await api<EmailDraft[]>(
        `/leads/${detailLead.id}/email-drafts`,
      );
      setDrafts(data);
    } catch (e) {
      setDraftMsg((e as Error).message);
    }
  };

  /** 草稿状态流转 draft ↔ confirmed（M7.4：加确认） */
  const toggleDraftStatus = async (draft: EmailDraft) => {
    if (!detailLead) return;
    const next = draft.status === "confirmed" ? "draft" : "confirmed";
    const ok = await confirmDialog(
      `确认将邮件「${draft.subject}」标记为「${DRAFT_STATUS_LABEL[next] ?? next}」？`,
    );
    if (!ok) return;
    try {
      await api(`/leads/${detailLead.id}/email-drafts/${draft.id}/status`, {
        method: "PUT",
        body: JSON.stringify({ status: next }),
      });
      const data = await api<EmailDraft[]>(
        `/leads/${detailLead.id}/email-drafts`,
      );
      setDrafts(data);
    } catch (e) {
      setDraftMsg((e as Error).message);
    }
  };

  /** SMTP 发送草稿（M3-2）：仅 confirmed 可发，结果以 toast 提示 */
  const sendDraft = async (draft: EmailDraft) => {
    if (!detailLead) return;
    if (
      !(await confirmDialog(
        `确认通过 SMTP 发送邮件「${draft.subject}」给 ${detailLead.contactName ?? "该客户"}？`,
      ))
    )
      return;
    setDraftMsg(null);
    setDraftSendingId(draft.id);
    try {
      const res = await api<{
        sendLogId: number;
        status: string;
        toEmail: string;
        errorMsg: string | null;
      }>(`/leads/${detailLead.id}/email-drafts/${draft.id}/send`, {
        method: "POST",
      });
      if (res.status === "sent") {
        setDraftMsg(`已发送至 ${res.toEmail}`);
      } else {
        setDraftMsg(`发送失败：${res.errorMsg ?? "未知错误"}`);
      }
      const data = await api<EmailDraft[]>(
        `/leads/${detailLead.id}/email-drafts`,
      );
      setDrafts(data);
      // 发送会新增一条发送记录，同步刷新
      await reloadSendLogs(detailLead.id);
    } catch (e) {
      setDraftMsg((e as Error).message);
    } finally {
      setDraftSendingId(null);
    }
  };

  /** 保存 AI 生成邮件为草稿 */
  const saveDraft = async () => {
    if (!genLead) return;
    if (!genSubject.trim()) {
      setGenMsg("请填写邮件主题");
      return;
    }
    if (!genResult.trim()) {
      setGenMsg("请先生成邮件内容");
      return;
    }
    setSavingDraft(true);
    setGenMsg(null);
    try {
      await api(`/leads/${genLead.id}/email-drafts`, {
        method: "POST",
        body: JSON.stringify({
          subject: genSubject.trim(),
          body: genResult,
          tone: "neutral",
        }),
      });
      setGenMsg("已保存为草稿（未发送），可在客户详情的邮件草稿中查看");
      setGenResult("");
      setGenSubject("");
    } catch (e) {
      setGenMsg((e as Error).message);
    } finally {
      setSavingDraft(false);
    }
  };

  return (
    <div>
      <Nav
        current="customers"
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
          <h2 style={{ margin: 0 }}>客户管理</h2>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button className="btn btn-sm btn-default" onClick={exportCsv}>
              导出 CSV
            </button>
            <label className="btn btn-sm btn-default" style={{ width: "auto" }}>
              导入 CSV
              <input
                type="file"
                accept=".csv,text/csv"
                style={{ display: "none" }}
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) importCsv(f);
                  e.target.value = "";
                }}
              />
            </label>
            <button className="btn btn-sm" onClick={openCreate}>
              + 新增客户
            </button>
          </div>
        </div>

        <div className="card" style={{ marginTop: 16 }}>
          <div
            style={{
              display: "flex",
              gap: 8,
              flexWrap: "wrap",
              marginBottom: 16,
            }}
          >
            <input
              className="filter-input"
              placeholder="搜索公司/联系人/邮箱/电话/行业"
              value={keyword}
              onChange={(e) => {
                setKeyword(e.target.value);
                setPage(0);
              }}
            />
            <select
              className="filter-input"
              style={{ width: 120 }}
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                setPage(0);
              }}
            >
              <option value="">全部状态</option>
              {Object.entries(STATUS_LABEL).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
            <span style={{ alignSelf: "center", color: "#888", fontSize: 13 }}>
              共 {totalElements} 条
            </span>
          </div>

          {msg && (
            <div
              className={`msg ${msg.includes("成功") || msg.includes("完成") ? "success" : "error"}`}
            >
              {msg}
            </div>
          )}

          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>公司</th>
                  <th>联系人</th>
                  <th>性别</th>
                  <th>电话</th>
                  <th>微信</th>
                  <th>邮箱</th>
                  <th>行业</th>
                  <th>画像分</th>
                  <th>来源</th>
                  <th>状态</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {leads.length === 0 && (
                  <tr>
                    <td
                      colSpan={11}
                      style={{ textAlign: "center", color: "#999" }}
                    >
                      暂无客户，点击右上角「+ 新增客户」或「导入 CSV」
                    </td>
                  </tr>
                )}
                {leads.map((lead) => (
                  <tr key={lead.id}>
                    <td>
                      {lead.companyName}
                      {lead.notes ? (
                        <span
                          title={lead.notes}
                          style={{
                            color: "#bbb",
                            marginLeft: 4,
                            cursor: "help",
                          }}
                        >
                          ℹ
                        </span>
                      ) : null}
                    </td>
                    <td>{lead.contactName || "-"}</td>
                    <td>{lead.gender || "-"}</td>
                    <td>{lead.contactPhone || "-"}</td>
                    <td>
                      {lead.wechatName || lead.wechatId ? (
                        <span title={lead.wechatId || ""}>
                          {lead.wechatName || lead.wechatId}
                          {lead.wechatName && lead.wechatId
                            ? ` (${lead.wechatId})`
                            : ""}
                        </span>
                      ) : (
                        "-"
                      )}
                    </td>
                    <td>{lead.contactEmail || "-"}</td>
                    <td>{lead.industry || "-"}</td>
                    <td>
                      {lead.profileScore && lead.profileScore > 0 ? (
                        <span
                          title={lead.profileSummary || "画像相似度"}
                          style={{
                            cursor: "help",
                            display: "inline-block",
                            minWidth: 36,
                            textAlign: "center",
                            padding: "2px 8px",
                            borderRadius: 10,
                            fontSize: 12,
                            background:
                              lead.profileScore >= 50
                                ? "#d4edda"
                                : lead.profileScore >= 25
                                  ? "#fff3cd"
                                  : "#f8f9fa",
                            color: "#333",
                          }}
                        >
                          {lead.profileScore}
                        </span>
                      ) : (
                        <span style={{ color: "#ccc" }}>-</span>
                      )}
                    </td>
                    <td>{SOURCE_LABEL[lead.sourceType] ?? lead.sourceType}</td>
                    <td>
                      <span className={`badge badge-${lead.status}`}>
                        {STATUS_LABEL[lead.status] ?? lead.status}
                      </span>
                    </td>
                    <td>
                      <div
                        style={{ display: "flex", gap: 6, flexWrap: "wrap" }}
                      >
                        <select
                          className="status-select"
                          value={lead.status}
                          disabled={
                            (STATUS_NEXT[lead.status] ?? []).length === 0
                          }
                          onChange={(e) => changeStatus(lead, e.target.value)}
                          title="变更状态"
                        >
                          <option value={lead.status}>
                            {STATUS_LABEL[lead.status] ?? lead.status}
                          </option>
                          {(STATUS_NEXT[lead.status] ?? []).map((s) => (
                            <option key={s} value={s}>
                              {STATUS_LABEL[s]}
                            </option>
                          ))}
                        </select>
                        <button
                          className="btn btn-xs btn-primary"
                          onClick={() => openGenerate(lead)}
                          title="AI 生成邮件"
                        >
                          ✉ 邮件
                        </button>
                        <button
                          className="btn btn-xs btn-primary"
                          onClick={() => openWechat(lead)}
                          title="微信沟通（记录消息 / AI 生成回复）"
                        >
                          💬 微信
                        </button>
                        <button
                          className="btn btn-xs btn-default"
                          onClick={() => openDetail(lead)}
                          title="跟进记录与邮件草稿"
                        >
                          📋 跟进
                        </button>
                        <button
                          className="btn btn-xs btn-default"
                          onClick={() => openEdit(lead)}
                        >
                          编辑
                        </button>
                        <button
                          className="btn btn-xs btn-danger"
                          onClick={() => remove(lead)}
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div
              style={{
                display: "flex",
                gap: 8,
                marginTop: 16,
                justifyContent: "flex-end",
              }}
            >
              <button
                className="btn btn-sm btn-default"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                上一页
              </button>
              <span
                style={{ alignSelf: "center", color: "#555", fontSize: 13 }}
              >
                {page + 1} / {totalPages}
              </span>
              <button
                className="btn btn-sm btn-default"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                下一页
              </button>
            </div>
          )}
        </div>
      </div>

      {/* 新增 / 编辑 Modal */}
      {modal && (
        <div className="modal-mask" onClick={() => setModal(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{modal === "create" ? "新增客户" : "编辑客户"}</h3>
            <div className="form-item">
              <label>公司名称 *</label>
              <input
                value={form.companyName}
                onChange={(e) =>
                  setForm({ ...form, companyName: e.target.value })
                }
              />
            </div>
            <div className="form-row">
              <div className="form-item">
                <label>联系人 *</label>
                <input
                  value={form.contactName}
                  onChange={(e) =>
                    setForm({ ...form, contactName: e.target.value })
                  }
                />
              </div>
              <div className="form-item">
                <label>性别</label>
                <select
                  value={form.gender}
                  onChange={(e) => setForm({ ...form, gender: e.target.value })}
                >
                  <option value="">未知</option>
                  <option value="男">男</option>
                  <option value="女">女</option>
                </select>
              </div>
            </div>
            <div className="form-row">
              <div className="form-item">
                <label>电话</label>
                <input
                  value={form.contactPhone}
                  onChange={(e) =>
                    setForm({ ...form, contactPhone: e.target.value })
                  }
                />
              </div>
              <div className="form-item">
                <label>邮箱 *</label>
                <input
                  value={form.contactEmail}
                  onChange={(e) =>
                    setForm({ ...form, contactEmail: e.target.value })
                  }
                />
              </div>
            </div>
            <div className="form-row">
              <div className="form-item">
                <label>微信号</label>
                <input
                  value={form.wechatId}
                  onChange={(e) =>
                    setForm({ ...form, wechatId: e.target.value })
                  }
                  placeholder="如 wxid_xxx 或手机号"
                />
              </div>
              <div className="form-item">
                <label>微信昵称</label>
                <input
                  value={form.wechatName}
                  onChange={(e) =>
                    setForm({ ...form, wechatName: e.target.value })
                  }
                  placeholder="如 王经理"
                />
              </div>
            </div>
            <div className="form-row">
              <div className="form-item">
                <label>行业</label>
                <select
                  value={form.industry}
                  onChange={(e) =>
                    setForm({ ...form, industry: e.target.value })
                  }
                >
                  <option value="">未填写</option>
                  {INDUSTRY_OPTIONS.map((opt) => (
                    <option key={opt} value={opt}>
                      {opt}
                    </option>
                  ))}
                  {form.industry &&
                    !INDUSTRY_OPTIONS.includes(form.industry) && (
                      <option value={form.industry}>
                        {form.industry}（自定义）
                      </option>
                    )}
                </select>
              </div>
              <div className="form-item">
                <label>地区</label>
                <input
                  value={form.region}
                  onChange={(e) => setForm({ ...form, region: e.target.value })}
                />
              </div>
            </div>
            <div className="form-row">
              <div className="form-item">
                <label>规模</label>
                <input
                  value={form.scale}
                  onChange={(e) => setForm({ ...form, scale: e.target.value })}
                  placeholder="如 1-50 / 51-200"
                />
              </div>
              <div className="form-item">
                <label>官网</label>
                <input
                  value={form.website}
                  onChange={(e) =>
                    setForm({ ...form, website: e.target.value })
                  }
                />
              </div>
            </div>
            <div className="form-item">
              <label>渠道来源</label>
              <select
                value={form.sourceType}
                onChange={(e) =>
                  setForm({ ...form, sourceType: e.target.value })
                }
              >
                <option value="manual">手动录入</option>
                {SOURCE_OPTIONS.map((opt) => (
                  <option key={opt} value={opt}>
                    {opt}
                  </option>
                ))}
                {form.sourceType &&
                  form.sourceType !== "manual" &&
                  !SOURCE_OPTIONS.includes(form.sourceType) && (
                    <option value={form.sourceType}>
                      {SOURCE_LABEL[form.sourceType] ?? form.sourceType}
                      （自定义）
                    </option>
                  )}
              </select>
            </div>
            <div className="form-item">
              <label>公司地址</label>
              <input
                value={form.address}
                onChange={(e) => setForm({ ...form, address: e.target.value })}
                placeholder="如 上海市浦东新区xxx路xx号"
              />
            </div>
            <div className="form-item">
              <label>股票代码（如已上市）</label>
              <input
                value={form.stockCode}
                onChange={(e) =>
                  setForm({ ...form, stockCode: e.target.value })
                }
                placeholder="如 600519 / 0700.HK / AAPL"
              />
            </div>
            <div className="form-item">
              <label>备注</label>
              <textarea
                rows={3}
                value={form.notes}
                onChange={(e) => setForm({ ...form, notes: e.target.value })}
              />
            </div>
            {msg && (
              <div className="msg error" style={{ textAlign: "left" }}>
                {msg}
              </div>
            )}
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              <button className="btn btn-sm" disabled={saving} onClick={submit}>
                {saving ? "保存中..." : "保存"}
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

      {/* AI 生成邮件 Modal */}
      {genLead && (
        <div className="modal-mask" onClick={() => setGenLead(null)}>
          <div
            className="modal modal-wide"
            onClick={(e) => e.stopPropagation()}
          >
            <h3>AI 生成邮件 — {genLead.companyName}</h3>
            <div
              style={{
                fontSize: 12,
                color: "#888",
                marginBottom: 10,
                lineHeight: 1.5,
              }}
            >
              💬 已结合跟进记录、已发邮件与客户回复自动生成延续性内容
            </div>
            <div className="form-item">
              <label>快捷模板</label>
              <select
                value={templateId}
                onChange={(e) => {
                  setTemplateId(e.target.value);
                  applyTemplate(e.target.value);
                }}
              >
                <option value="">选择模板自动填入（可再编辑）</option>
                {templates.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </div>{" "}
            <div className="form-item">
              <label>触达目标</label>
              <input
                value={goal}
                onChange={(e) => setGoal(e.target.value)}
                placeholder="如：推进方案评审，约下周线上沟通"
              />
            </div>
            <button
              className="btn btn-sm"
              disabled={genLoading}
              onClick={generate}
              title={
                templateId
                  ? "结合沟通历史个性化编写，并参考所选模板风格（HTML 美化，每次生成不同）"
                  : "结合沟通历史个性化编写（HTML 美化，每次生成不同）"
              }
            >
              {genLoading ? "生成中..." : "AI 生成"}
            </button>
            {genMsg && (
              <div
                className={`msg ${genMsg.includes("已保存") ? "success" : "error"}`}
                style={{ textAlign: "left" }}
              >
                {genMsg}
              </div>
            )}
            {genResult && (
              <div style={{ marginTop: 12 }}>
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                  }}
                >
                  <label style={{ fontSize: 14, color: "#555" }}>
                    生成结果（可编辑）
                  </label>
                  <div style={{ display: "flex", gap: 6 }}>
                    <button
                      className={`btn btn-xs ${
                        genMode === "edit" ? "" : "btn-default"
                      }`}
                      onClick={() => setGenMode("edit")}
                      title="富文本所见即所得编辑"
                    >
                      🖊 编辑
                    </button>
                    <button
                      className={`btn btn-xs ${
                        genMode === "source" ? "" : "btn-default"
                      }`}
                      onClick={() => setGenMode("source")}
                      title="查看/编辑 HTML 源码"
                    >
                      ✏️ 源码
                    </button>
                    <button
                      className={`btn btn-xs ${
                        genMode === "preview" ? "" : "btn-default"
                      }`}
                      onClick={() => setGenMode("preview")}
                      title="预览 HTML 美化效果"
                    >
                      👁 预览
                    </button>
                  </div>
                </div>
                <div
                  style={{
                    fontSize: 12,
                    color: "#888",
                    margin: "4px 0 8px",
                    lineHeight: 1.6,
                  }}
                >
                  支持变量，发送时自动替换： {"{companyName}"} {"{contactName}"}{" "}
                  {"{contactEmail}"} {"{phone}"} {"{contactPhone}"}{" "}
                  {"{industry}"} {"{region}"} {"{date}"}；正文支持富文本编辑
                  （加粗 / 列表 / 链接 / 标题等）
                </div>
                {genMode === "preview" ? (
                  <div
                    style={{
                      border: "1px solid #eee",
                      borderRadius: 6,
                      background: "#fafafa",
                      padding: "10px 12px",
                      minHeight: 120,
                      maxHeight: 300,
                      overflowY: "auto",
                      fontSize: 14,
                      lineHeight: 1.7,
                      wordBreak: "break-word",
                    }}
                    dangerouslySetInnerHTML={{ __html: genResult }}
                  />
                ) : genMode === "source" ? (
                  <textarea
                    rows={10}
                    value={genResult}
                    onChange={(e) => setGenResult(e.target.value)}
                  />
                ) : (
                  <RichTextEditor
                    key={genResultKey}
                    value={genResult}
                    onChange={setGenResult}
                    placeholder="AI 生成结果，可直接富文本编辑…"
                    minHeight={180}
                  />
                )}
                <div className="form-item" style={{ marginTop: 8 }}>
                  <label>邮件主题</label>
                  <input
                    value={genSubject}
                    onChange={(e) => setGenSubject(e.target.value)}
                    placeholder={`如：与${genLead.companyName}的合作沟通`}
                  />
                </div>
              </div>
            )}
            <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
              {genResult && (
                <button
                  className="btn btn-sm"
                  disabled={savingDraft}
                  onClick={saveDraft}
                >
                  {savingDraft ? "保存中..." : "💾 保存为草稿"}
                </button>
              )}
              <button
                className="btn btn-sm btn-default"
                onClick={() => setGenLead(null)}
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 客户详情 Modal：跟进记录 + 邮件草稿 */}
      {detailLead && (
        <div className="modal-mask" onClick={closeDetail}>
          <div
            className="modal modal-wide"
            onClick={(e) => e.stopPropagation()}
          >
            <h3>📋 {detailLead.companyName} — 跟进记录与邮件</h3>

            <h4 style={{ margin: "12px 0 8px" }}>跟进记录</h4>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <select
                className="filter-input"
                style={{ width: 100 }}
                value={fuMethod}
                onChange={(e) => setFuMethod(e.target.value)}
              >
                {Object.entries(FOLLOW_UP_METHODS).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
              <input
                className="filter-input"
                style={{ flex: 1, minWidth: 200 }}
                placeholder="跟进内容，如：电话沟通了试用需求，对方有意向"
                value={fuContent}
                onChange={(e) => setFuContent(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") addFollowUp();
                }}
              />
              <button className="btn btn-sm" onClick={addFollowUp}>
                + 添加
              </button>
            </div>
            {fuMsg && (
              <div
                className={`msg ${fuMsg.includes("成功") || fuMsg.includes("完成") ? "success" : "error"}`}
                style={{ textAlign: "left" }}
              >
                {fuMsg}
              </div>
            )}
            <div
              style={{
                maxHeight: 180,
                overflowY: "auto",
                marginTop: 8,
                border: "1px solid #eee",
                borderRadius: 8,
                padding: 8,
              }}
            >
              {followUps.length === 0 ? (
                <div style={{ color: "#999", fontSize: 13, padding: 4 }}>
                  暂无跟进记录
                </div>
              ) : (
                followUps.map((fu) => (
                  <div
                    key={fu.id}
                    style={{
                      display: "flex",
                      gap: 8,
                      alignItems: "flex-start",
                      padding: "6px 0",
                      borderBottom: "1px solid #f2f2f2",
                    }}
                  >
                    <span className={`badge badge-${fu.method}`}>
                      {FOLLOW_UP_METHODS[fu.method] ?? fu.method}
                    </span>
                    <div style={{ flex: 1, fontSize: 14 }}>
                      <div>{fu.content}</div>
                      <div style={{ fontSize: 12, color: "#999" }}>
                        {new Date(fu.happenedAt).toLocaleString("zh-CN")}
                      </div>
                    </div>
                    <button
                      className="btn btn-xs btn-danger"
                      onClick={() => removeFollowUp(fu.id)}
                    >
                      删除
                    </button>
                  </div>
                ))
              )}
            </div>

            <h4 style={{ margin: "16px 0 8px" }}>邮件草稿</h4>
            {draftMsg && (
              <div
                className={`msg ${draftMsg.includes("成功") || draftMsg.includes("完成") ? "success" : "error"}`}
                style={{ textAlign: "left" }}
              >
                {draftMsg}
              </div>
            )}
            <div
              style={{
                maxHeight: 220,
                overflowY: "auto",
                border: "1px solid #eee",
                borderRadius: 8,
                padding: 8,
              }}
            >
              {drafts.length === 0 ? (
                <div style={{ color: "#999", fontSize: 13, padding: 4 }}>
                  暂无邮件草稿，可先「✉ 邮件」AI 生成后保存
                </div>
              ) : (
                drafts.map((d) => (
                  <div
                    key={d.id}
                    style={{
                      padding: "6px 0",
                      borderBottom: "1px solid #f2f2f2",
                    }}
                  >
                    <div
                      style={{
                        display: "flex",
                        gap: 8,
                        alignItems: "center",
                        flexWrap: "wrap",
                      }}
                    >
                      <strong style={{ fontSize: 14 }}>{d.subject}</strong>
                      <span
                        className={`badge badge-${d.status === "confirmed" ? "converted" : "new"}`}
                      >
                        {DRAFT_STATUS_LABEL[d.status] ?? d.status}
                      </span>
                      <span style={{ fontSize: 12, color: "#999" }}>
                        {new Date(d.createdAt).toLocaleString("zh-CN")}
                      </span>
                      <span style={{ flex: 1 }} />
                      {d.status === "confirmed" && (
                        <button
                          className="btn btn-xs btn-primary"
                          onClick={() => sendDraft(d)}
                          disabled={draftSendingId === d.id}
                          title="通过 SMTP 发送这封邮件"
                        >
                          {draftSendingId === d.id ? "发送中…" : "✉ 发送"}
                        </button>
                      )}
                      {d.status !== "sent" && (
                        <button
                          className="btn btn-xs btn-default"
                          onClick={() => toggleDraftStatus(d)}
                          title={
                            d.status === "confirmed"
                              ? "改回草稿"
                              : "标记邮件为待发"
                          }
                        >
                          {d.status === "confirmed"
                            ? "↩ 改回草稿"
                            : "✓ 标记待发"}
                        </button>
                      )}
                      <button
                        className="btn btn-xs btn-danger"
                        onClick={() => removeDraft(d.id)}
                      >
                        删除
                      </button>
                    </div>
                    <div
                      style={{
                        fontSize: 13,
                        color: "#666",
                        whiteSpace: "pre-wrap",
                        marginTop: 4,
                        background: "#fafafa",
                        borderRadius: 6,
                        padding: 8,
                        wordBreak: "break-word",
                      }}
                    >
                      {isHtmlText(d.body) ? (
                        <span
                          dangerouslySetInnerHTML={{ __html: d.body }}
                          style={{
                            display: "block",
                            fontSize: 13,
                            lineHeight: 1.7,
                            color: "#333",
                          }}
                        />
                      ) : (
                        d.body
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>

            <h4 style={{ margin: "16px 0 8px" }}>发送记录</h4>
            {sendLogMsg && (
              <div
                className={`msg ${sendLogMsg.includes("成功") || sendLogMsg.includes("完成") ? "success" : "error"}`}
                style={{ textAlign: "left" }}
              >
                {sendLogMsg}
              </div>
            )}
            <div
              style={{
                maxHeight: 180,
                overflowY: "auto",
                border: "1px solid #eee",
                borderRadius: 8,
                padding: 8,
              }}
            >
              {sendLogs.length === 0 ? (
                <div style={{ color: "#999", fontSize: 13, padding: 4 }}>
                  暂无发送记录（草稿标记待发后点击「✉ 发送」即产生记录）
                </div>
              ) : (
                sendLogs.map((log) => (
                  <div
                    key={log.id}
                    style={{
                      padding: "6px 0",
                      borderBottom: "1px solid #f2f2f2",
                    }}
                  >
                    <div
                      style={{
                        display: "flex",
                        gap: 8,
                        alignItems: "center",
                        flexWrap: "wrap",
                      }}
                    >
                      <strong style={{ fontSize: 14 }}>{log.subject}</strong>
                      <span
                        className={`badge badge-${
                          log.status === "sent"
                            ? "converted"
                            : log.status === "failed"
                              ? "danger"
                              : "new"
                        }`}
                      >
                        {SEND_LOG_STATUS_LABEL[log.status] ?? log.status}
                      </span>
                      <span style={{ fontSize: 12, color: "#999" }}>
                        → {log.toEmail}
                      </span>
                      <span style={{ fontSize: 12, color: "#999" }}>
                        {new Date(log.createdAt).toLocaleString("zh-CN")}
                      </span>
                      {log.status === "sent" &&
                        (log.openedAt || log.clickedAt) && (
                          <span style={{ fontSize: 12, color: "#999" }}>
                            {log.openedAt && (
                              <span
                                className="badge badge-converted"
                                title={`打开时间：${new Date(log.openedAt).toLocaleString("zh-CN")}`}
                              >
                                ✅ 已打开
                              </span>
                            )}
                            {!log.openedAt && log.clickedAt && (
                              <span
                                className="badge badge-new"
                                title={`点击时间：${new Date(log.clickedAt).toLocaleString("zh-CN")}`}
                              >
                                👆 已点击
                              </span>
                            )}
                            {log.openedAt && log.clickedAt && (
                              <span
                                className="badge badge-interested"
                                title={`点击时间：${new Date(log.clickedAt).toLocaleString("zh-CN")}`}
                              >
                                👆 已点击
                              </span>
                            )}
                          </span>
                        )}
                      <span style={{ flex: 1 }} />
                      {log.status === "failed" && (
                        <button
                          className="btn btn-xs btn-primary"
                          onClick={() => retrySendLog(log)}
                          disabled={retryingId === log.id}
                          title="重新执行 SMTP 发送"
                        >
                          {retryingId === log.id ? "重试中…" : "↻ 重试"}
                        </button>
                      )}
                    </div>
                    {log.status === "failed" && log.errorMsg && (
                      <div
                        style={{
                          fontSize: 12,
                          color: "#d33",
                          marginTop: 4,
                          background: "#fdf2f2",
                          borderRadius: 6,
                          padding: "4px 8px",
                          wordBreak: "break-all",
                        }}
                      >
                        失败原因：{log.errorMsg}
                      </div>
                    )}
                  </div>
                ))
              )}
            </div>

            <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
              <button className="btn btn-sm btn-default" onClick={closeDetail}>
                关闭
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 微信沟通 Modal（M2-1.8 记录式工作台）：会话记录 + AI 生成回复 */}
      {wechatLead && (
        <div className="modal-mask" onClick={closeWechat}>
          <div
            className="modal modal-wide"
            onClick={(e) => e.stopPropagation()}
          >
            <h3>💬 {wechatLead.companyName} — 微信沟通</h3>
            <div style={{ fontSize: 12, color: "#888", marginBottom: 8 }}>
              微信号：
              {wechatLead.wechatId || "-"}　昵称：
              {wechatLead.wechatName || "-"}
              {!wechatLead.wechatId && !wechatLead.wechatName
                ? "（未填写，可在「编辑客户」中补充）"
                : ""}
            </div>
            <div
              style={{
                maxHeight: 340,
                overflowY: "auto",
                border: "1px solid #eee",
                borderRadius: 8,
                padding: 12,
                background: "#f7f8fa",
                marginBottom: 10,
              }}
            >
              {wmMessages.length === 0 ? (
                <div
                  style={{
                    color: "#999",
                    fontSize: 13,
                    textAlign: "center",
                    padding: "20px 0",
                    lineHeight: 1.8,
                  }}
                >
                  暂无微信沟通记录
                  <br />
                  可先记录客户发来的消息，再用「AI 生成回复」起草回复
                </div>
              ) : (
                wmMessages.map((m) => (
                  <div
                    key={m.id}
                    style={{
                      display: "flex",
                      flexDirection:
                        m.direction === "out" ? "row-reverse" : "row",
                      marginBottom: 10,
                    }}
                  >
                    <div style={{ position: "relative", maxWidth: "72%" }}>
                      <button
                        className="btn btn-xs btn-danger"
                        onClick={() => removeWechatMessage(m.id)}
                        title="删除该消息"
                        style={{
                          position: "absolute",
                          top: -14,
                          right: -6,
                          padding: "0 6px",
                          lineHeight: "18px",
                          borderRadius: 10,
                          fontSize: 11,
                          zIndex: 1,
                        }}
                      >
                        ✕
                      </button>
                      <div
                        style={{
                          background:
                            m.direction === "out" ? "#95ec69" : "#fff",
                          border: "1px solid #eee",
                          borderRadius: 10,
                          padding: "8px 10px",
                          fontSize: 14,
                          whiteSpace: "pre-wrap",
                          wordBreak: "break-word",
                          color: "#333",
                        }}
                      >
                        {m.content}
                        <div
                          style={{
                            fontSize: 11,
                            color: "#999",
                            marginTop: 4,
                            display: "flex",
                            gap: 6,
                            alignItems: "center",
                            justifyContent:
                              m.direction === "out" ? "flex-end" : "flex-start",
                          }}
                        >
                          {m.status === "ai_confirmed" && (
                            <span title="AI 建议确认后发出">🤖</span>
                          )}
                          <span>
                            {new Date(m.sentAt).toLocaleString("zh-CN")}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>

            <div
              style={{
                display: "flex",
                gap: 8,
                flexWrap: "wrap",
                marginBottom: 8,
              }}
            >
              <input
                className="filter-input"
                style={{ flex: 1, minWidth: 200 }}
                placeholder="触达目标（可选），如：约下周线上演示"
                value={wmGoal}
                onChange={(e) => setWmGoal(e.target.value)}
              />
              <button
                className="btn btn-sm"
                disabled={wmSugLoading}
                onClick={suggestWechat}
                title="基于客户画像与沟通时间线生成回复建议"
              >
                {wmSugLoading ? "生成中…" : "🤖 AI 生成回复"}
              </button>
            </div>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <select
                className="filter-input"
                style={{ width: 110 }}
                value={wmDirection}
                onChange={(e) => setWmDirection(e.target.value as "in" | "out")}
              >
                <option value="in">客户发来</option>
                <option value="out">我方发出</option>
              </select>
              <input
                className="filter-input"
                style={{ flex: 1, minWidth: 200 }}
                placeholder="消息内容，如：王总您好，方案已发您邮箱，方便约个时间聊聊？"
                value={wmContent}
                onChange={(e) => setWmContent(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") addWechatMessage();
                }}
              />
              <button
                className="btn btn-sm"
                disabled={wmSaving}
                onClick={addWechatMessage}
              >
                {wmSaving
                  ? "保存中…"
                  : wmDirection === "out"
                    ? "📤 记录为已发"
                    : "📥 记录客户消息"}
              </button>
            </div>
            {wmMsg && (
              <div
                className={`msg ${wmMsg.includes("已生成") || wmMsg.includes("成功") ? "success" : "error"}`}
                style={{ textAlign: "left" }}
              >
                {wmMsg}
              </div>
            )}
            <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
              <button className="btn btn-sm btn-default" onClick={closeWechat}>
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
