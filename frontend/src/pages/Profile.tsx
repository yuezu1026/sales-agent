import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken, getToken } from "../api/client";
import { confirmDialog } from "../utils/dialog";
import { Nav } from "./Nav";

/** 客户画像（与后端 CustomerProfile 实体对齐） */
interface CustomerProfile {
  id: number;
  companyName: string;
  industry: string | null;
  contactName: string | null;
  contactEmail: string | null;
  dealValue: number | null;
  tags: string | null;
  description: string | null;
  embedding: string | null;
  createdAt: string;
}

/** 语义检索结果 */
interface ProfileSearchResult {
  profile: CustomerProfile;
  score: number;
}

/** CSV 导入结果 */
interface ImportResult {
  success: number;
  duplicate: number;
  errors: { companyName: string; reason: string }[];
}

/** 全量打分结果 */
interface ScoreAllResult {
  total: number;
  scored: number;
  updated: number;
}

/** 画像管理（M2-3 RAG 客户画像）：CSV 导入向量化 + 语义检索 + 潜客画像打分 */
export default function Profile() {
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);
  const [profiles, setProfiles] = useState<CustomerProfile[]>([]);
  const [importing, setImporting] = useState(false);
  const [scoring, setScoring] = useState(false);
  const [msg, setMsg] = useState<{ type: "ok" | "err"; text: string } | null>(
    null,
  );
  const [importResult, setImportResult] = useState<ImportResult | null>(null);

  // 语义检索
  const [query, setQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<ProfileSearchResult[]>([]);

  // 向量模式（读系统设置 ai.embedding_model）
  const [embeddingModel, setEmbeddingModel] = useState("");

  const toast = (type: "ok" | "err", text: string) => {
    setMsg({ type, text });
    window.setTimeout(() => setMsg(null), 4000);
  };

  const loadProfiles = useCallback(async () => {
    try {
      const data = await api<CustomerProfile[]>("/profiles");
      setProfiles(data);
    } catch (e) {
      if ((e as Error).message.includes("未登录")) {
        navigate("/login");
      } else {
        toast("err", (e as Error).message);
      }
    }
  }, [navigate]);

  const loadConfig = useCallback(async () => {
    try {
      const data =
        await api<{ key: string; value: string; description: string }[]>(
          "/config",
        );
      const item = data.find((c) => c.key === "ai.embedding_model");
      setEmbeddingModel(item?.value?.trim() ?? "");
    } catch {
      // 配置读取失败不阻塞页面
    }
  }, []);

  useEffect(() => {
    loadProfiles();
    loadConfig();
  }, [loadProfiles, loadConfig]);

  /** 上传 CSV 导入画像 */
  const doImport = async () => {
    const file = fileRef.current?.files?.[0];
    if (!file) {
      toast("err", "请先选择 CSV 文件");
      return;
    }
    setImporting(true);
    setImportResult(null);
    const fd = new FormData();
    fd.append("file", file);
    try {
      const token = getToken();
      const resp = await fetch("/api/profiles/import", {
        method: "POST",
        body: fd,
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      const body = await resp.json();
      if (!resp.ok || body.code !== 0) {
        throw new Error(body.message || "导入失败");
      }
      const r = body.data as ImportResult;
      setImportResult(r);
      toast(
        "ok",
        `导入完成：成功 ${r.success} 条，重复 ${r.duplicate} 条` +
          (r.errors.length > 0 ? `，失败 ${r.errors.length} 条` : ""),
      );
      if (fileRef.current) {
        fileRef.current.value = "";
      }
      await loadProfiles();
    } catch (e) {
      toast("err", (e as Error).message);
    } finally {
      setImporting(false);
    }
  };

  /** 下载 CSV 模板 */
  const downloadTemplate = () => {
    const headers = "公司名称*,行业,联系人,邮箱,成交金额,标签,描述";
    const sample =
      "云启科技,企业服务,李明,li@yunqi.com,500000,AI 客服 数字化,深耕客服系统 SaaS，企业客户 300+";
    const blob = new Blob(["\uFEFF" + headers + "\r\n" + sample], {
      type: "text/csv;charset=utf-8",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "客户画像模板.csv";
    a.click();
    URL.revokeObjectURL(url);
  };

  /** 语义检索 */
  const doSearch = async () => {
    if (!query.trim()) {
      toast("err", "请输入检索内容");
      return;
    }
    setSearching(true);
    try {
      const data = await api<ProfileSearchResult[]>(
        `/profiles/search?q=${encodeURIComponent(query.trim())}&top=10`,
      );
      setSearchResults(data);
      if (data.length === 0) {
        toast("err", "未找到相似画像，试试其他关键词");
      }
    } catch (e) {
      toast("err", (e as Error).message);
    } finally {
      setSearching(false);
    }
  };

  /** 删除画像 */
  const doDelete = async (p: CustomerProfile) => {
    if (
      !(await confirmDialog(`确认删除画像「${p.companyName}」？`, {
        danger: true,
      }))
    ) {
      return;
    }
    try {
      await api(`/profiles/${p.id}`, { method: "DELETE" });
      toast("ok", "已删除画像");
      await loadProfiles();
    } catch (e) {
      toast("err", (e as Error).message);
    }
  };

  /** 一键重算潜客画像分 */
  const doScoreAll = async () => {
    setScoring(true);
    try {
      const r = await api<ScoreAllResult>("/leads/score-all", {
        method: "POST",
      });
      toast(
        "ok",
        `画像打分完成：共 ${r.total} 条潜客，命中 ${r.scored} 条，更新 ${r.updated} 条`,
      );
    } catch (e) {
      toast("err", (e as Error).message);
    } finally {
      setScoring(false);
    }
  };

  /** 向量模式显示 */
  const vectorMode = embeddingModel
    ? `远程向量（${embeddingModel}）`
    : "本地向量（TF-IDF）";

  return (
    <div className="page">
      <Nav
        current="profile"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      {msg && (
        <div
          className={`toast ${msg.type === "ok" ? "toast-ok" : "toast-err"}`}
        >
          {msg.text}
        </div>
      )}

      <h2 className="page-title">客户画像</h2>

      {/* CSV 导入 */}
      <div className="card">
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: 8,
            marginBottom: 12,
          }}
        >
          <h3 style={{ margin: 0 }}>
            CSV 导入历史客户画像{" "}
            <span
              className="badge badge-new"
              style={{ fontSize: 12, verticalAlign: "middle" }}
            >
              {vectorMode}
            </span>
          </h3>
          <button className="btn btn-default btn-sm" onClick={downloadTemplate}>
            下载模板
          </button>
        </div>
        <p style={{ color: "#999", marginTop: 0 }}>
          列顺序：公司名称*、行业、联系人、邮箱、成交金额、标签、描述。导入后自动向量化，
          用于潜客画像相似度打分（<code>profile_score</code>）。
        </p>
        <div className="form-row">
          <div className="form-item">
            <label>CSV 文件</label>
            <input ref={fileRef} type="file" accept=".csv" />
          </div>
          <div className="form-item form-item-btn">
            <label>&nbsp;</label>
            <button
              className="btn btn-primary"
              onClick={doImport}
              disabled={importing}
            >
              {importing ? "导入中…" : "导入画像"}
            </button>
          </div>
          <div className="form-item form-item-btn">
            <label>&nbsp;</label>
            <button
              className="btn btn-default"
              onClick={doScoreAll}
              disabled={scoring}
            >
              {scoring ? "打分中…" : "一键重算潜客画像分"}
            </button>
          </div>
        </div>
        {importResult && (
          <div
            className="msg"
            style={{
              marginTop: 12,
              color:
                importResult.success > 0
                  ? "#155724"
                  : importResult.errors.length > 0
                    ? "#856404"
                    : "#333",
              background: importResult.success > 0 ? "#d4edda" : "#fff3cd",
              border:
                importResult.success > 0
                  ? "1px solid #c3e6cb"
                  : "1px solid #ffeeba",
              borderRadius: 4,
              padding: "8px 12px",
            }}
          >
            成功 {importResult.success} 条，重复 {importResult.duplicate} 条，
            失败 {importResult.errors.length} 条
            {importResult.errors.length > 0 && (
              <ul style={{ margin: "6px 0 0 18px" }}>
                {importResult.errors.slice(0, 5).map((e, i) => (
                  <li key={i}>
                    {e.companyName || "(空)"}：{e.reason}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>

      {/* 语义检索 */}
      <div className="card" style={{ marginTop: 16 }}>
        <h3 style={{ margin: "0 0 12px" }}>语义检索</h3>
        <div className="form-row">
          <div className="form-item" style={{ flex: 1 }}>
            <label>检索内容</label>
            <input
              placeholder="如：深圳做 AI 客服系统的 SaaS 公司"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && doSearch()}
            />
          </div>
          <div className="form-item form-item-btn">
            <label>&nbsp;</label>
            <button
              className="btn btn-primary"
              onClick={doSearch}
              disabled={searching}
            >
              {searching ? "检索中…" : "检索"}
            </button>
          </div>
        </div>
        {searchResults.length > 0 && (
          <div className="table-wrap" style={{ marginTop: 12 }}>
            <table className="table profile-table">
              <thead>
                <tr>
                  <th>相似度</th>
                  <th>公司名称</th>
                  <th>行业</th>
                  <th>标签</th>
                  <th>联系人</th>
                  <th>成交金额</th>
                </tr>
              </thead>
              <tbody>
                {searchResults.map((r) => (
                  <tr key={r.profile.id}>
                    <td>
                      <span
                        className="badge"
                        style={{
                          background:
                            r.score >= 0.5
                              ? "#d4edda"
                              : r.score >= 0.25
                                ? "#fff3cd"
                                : "#f8f9fa",
                          color: "#333",
                        }}
                      >
                        {Math.round(r.score * 100)}%
                      </span>
                    </td>
                    <td>{r.profile.companyName}</td>
                    <td>{r.profile.industry || "-"}</td>
                    <td>{r.profile.tags || "-"}</td>
                    <td>{r.profile.contactName || "-"}</td>
                    <td>
                      {r.profile.dealValue != null
                        ? `${r.profile.dealValue.toLocaleString()} 元`
                        : "-"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 画像列表 */}
      <div className="card" style={{ marginTop: 16 }}>
        <h3 style={{ margin: "0 0 12px" }}>
          画像库{profiles.length > 0 ? `（${profiles.length} 条）` : ""}
        </h3>
        {profiles.length === 0 ? (
          <p style={{ color: "#999" }}>
            暂无画像，请上传 CSV 导入历史成交客户语料。
          </p>
        ) : (
          <div className="table-wrap">
            <table className="table profile-table">
              <thead>
                <tr>
                  <th>公司名称</th>
                  <th>行业</th>
                  <th>联系人</th>
                  <th>邮箱</th>
                  <th>成交金额</th>
                  <th>标签</th>
                  <th>描述</th>
                  <th>创建时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {profiles.map((p) => (
                  <tr key={p.id}>
                    <td>{p.companyName}</td>
                    <td>{p.industry || "-"}</td>
                    <td>{p.contactName || "-"}</td>
                    <td>{p.contactEmail || "-"}</td>
                    <td>
                      {p.dealValue != null
                        ? `${p.dealValue.toLocaleString()} 元`
                        : "-"}
                    </td>
                    <td>{p.tags || "-"}</td>
                    <td
                      style={{
                        maxWidth: 220,
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                      title={p.description ?? ""}
                    >
                      {p.description || "-"}
                    </td>
                    <td>
                      {p.createdAt
                        ? p.createdAt.replace("T", " ").slice(0, 16)
                        : "-"}
                    </td>
                    <td>
                      <button
                        className="btn btn-default btn-sm"
                        onClick={() => doDelete(p)}
                      >
                        删除
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
