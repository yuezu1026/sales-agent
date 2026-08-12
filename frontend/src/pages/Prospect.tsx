import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken } from "../api/client";
import { confirmDialog } from "../utils/dialog";
import { Nav } from "./Nav";

/** 挖掘命中的潜客候选（与后端 ProspectCompany record 对齐） */
interface ProspectCompany {
  companyName: string;
  contactName: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  industry: string | null;
  region: string | null;
  scale: string | null;
  website: string | null;
  address: string | null;
  sourceType: string;
  sourceId: string | null;
  inLibrary: boolean;
}

/** 数据源（api_key 已脱敏） */
interface DataSourceView {
  id: number;
  name: string;
  type: string;
  apiBaseUrl: string | null;
  apiKeyMasked: string | null;
  enabled: boolean;
  createdAt: string;
}

const EMPTY_QUERY = { industry: "", region: "", scale: "", keyword: "" };

/** 潜客挖掘（M2-2）：条件检索数据源 → 勾选入库（source_type=api）+ 数据源管理 */
export default function Prospect() {
  const navigate = useNavigate();
  const [query, setQuery] = useState(EMPTY_QUERY);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<ProspectCompany[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [msg, setMsg] = useState<{ type: "ok" | "err"; text: string } | null>(
    null,
  );

  // 数据源管理
  const [sources, setSources] = useState<DataSourceView[]>([]);
  const [dsModal, setDsModal] = useState(false);
  const [dsForm, setDsForm] = useState({
    id: 0,
    name: "",
    type: "",
    apiBaseUrl: "",
    apiKey: "",
    enabled: false,
  });
  const [dsSaving, setDsSaving] = useState(false);

  const toast = (type: "ok" | "err", text: string) => {
    setMsg({ type, text });
    window.setTimeout(() => setMsg(null), 4000);
  };

  const loadSources = useCallback(async () => {
    try {
      const data = await api<DataSourceView[]>("/data-sources");
      setSources(data);
    } catch {
      navigate("/login");
    }
  }, [navigate]);

  useEffect(() => {
    loadSources();
  }, [loadSources]);

  const keyOf = (c: ProspectCompany) => c.sourceId ?? c.companyName;

  const doSearch = async () => {
    setLoading(true);
    setMsg(null);
    try {
      const data = await api<ProspectCompany[]>("/prospect/search", {
        method: "POST",
        body: JSON.stringify({ ...query, limit: 20 }),
      });
      setResults(data);
      setSelected(new Set());
      if (data.length === 0) {
        toast("err", "没有匹配的潜客，换个条件试试");
      }
    } catch (e) {
      toast("err", (e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const toggle = (key: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  const toggleAll = () => {
    const pickable = results.filter((c) => !c.inLibrary);
    if (selected.size === pickable.length && pickable.length > 0) {
      setSelected(new Set());
    } else {
      setSelected(new Set(pickable.map(keyOf)));
    }
  };

  const doImport = async () => {
    const chosen = results.filter((c) => selected.has(keyOf(c)));
    if (chosen.length === 0) {
      toast("err", "请先勾选要入库的潜客");
      return;
    }
    try {
      const data = await api<{
        success: number;
        duplicate: number;
        errors: unknown[];
      }>("/prospect/import", {
        method: "POST",
        body: JSON.stringify({ companies: chosen }),
      });
      toast(
        "ok",
        `入库完成：成功 ${data.success} 条，跳过重复 ${data.duplicate} 条` +
          (data.errors.length > 0 ? `，失败 ${data.errors.length} 条` : ""),
      );
      setResults(
        results.map((c) => ({
          ...c,
          inLibrary: c.inLibrary || selected.has(keyOf(c)),
        })),
      );
      setSelected(new Set());
    } catch (e) {
      toast("err", (e as Error).message);
    }
  };

  // ==================== 数据源管理 ====================
  const openDsCreate = () => {
    setDsForm({
      id: 0,
      name: "",
      type: "",
      apiBaseUrl: "",
      apiKey: "",
      enabled: false,
    });
    setDsModal(true);
  };

  const openDsEdit = (s: DataSourceView) => {
    setDsForm({
      id: s.id,
      name: s.name,
      type: s.type,
      apiBaseUrl: s.apiBaseUrl ?? "",
      apiKey: "",
      enabled: s.enabled,
    });
    setDsModal(true);
  };

  const saveDs = async () => {
    if (!dsForm.name.trim() || !dsForm.type.trim()) {
      toast("err", "名称和类型不能为空");
      return;
    }
    setDsSaving(true);
    try {
      const payload = {
        name: dsForm.name.trim(),
        type: dsForm.type.trim(),
        apiBaseUrl: dsForm.apiBaseUrl.trim() || null,
        apiKey: dsForm.apiKey || null,
        enabled: dsForm.enabled,
      };
      if (dsForm.id) {
        await api(`/data-sources/${dsForm.id}`, {
          method: "PUT",
          body: JSON.stringify(payload),
        });
      } else {
        await api("/data-sources", {
          method: "POST",
          body: JSON.stringify(payload),
        });
      }
      setDsModal(false);
      toast("ok", "数据源已保存");
      await loadSources();
    } catch (e) {
      toast("err", (e as Error).message);
    } finally {
      setDsSaving(false);
    }
  };

  const toggleDs = async (s: DataSourceView) => {
    try {
      await api(`/data-sources/${s.id}/enabled`, {
        method: "PUT",
        body: JSON.stringify({ enabled: !s.enabled }),
      });
      await loadSources();
    } catch (e) {
      toast("err", (e as Error).message);
    }
  };

  const deleteDs = async (s: DataSourceView) => {
    if (
      !(await confirmDialog(`确定删除数据源「${s.name}」？`, { danger: true }))
    )
      return;
    try {
      await api(`/data-sources/${s.id}`, { method: "DELETE" });
      toast("ok", "数据源已删除");
      await loadSources();
    } catch (e) {
      toast("err", (e as Error).message);
    }
  };

  return (
    <div className="page">
      <Nav
        current="prospect"
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

      <h2 className="page-title">潜客挖掘</h2>

      {/* 挖掘条件 */}
      <div className="card">
        <div className="form-row">
          <div className="form-item">
            <label>行业</label>
            <input
              placeholder="如 SaaS、金融科技"
              value={query.industry}
              onChange={(e) => setQuery({ ...query, industry: e.target.value })}
            />
          </div>
          <div className="form-item">
            <label>地区</label>
            <input
              placeholder="如 深圳、上海"
              value={query.region}
              onChange={(e) => setQuery({ ...query, region: e.target.value })}
            />
          </div>
          <div className="form-item">
            <label>规模</label>
            <input
              placeholder="如 50-200人"
              value={query.scale}
              onChange={(e) => setQuery({ ...query, scale: e.target.value })}
            />
          </div>
          <div className="form-item">
            <label>关键词</label>
            <input
              placeholder="公司名/行业关键词"
              value={query.keyword}
              onChange={(e) => setQuery({ ...query, keyword: e.target.value })}
            />
          </div>
          <div className="form-item form-item-btn">
            <label>&nbsp;</label>
            <button
              className="btn btn-primary"
              onClick={doSearch}
              disabled={loading}
            >
              {loading ? "挖掘中…" : "开始挖掘"}
            </button>
          </div>
        </div>
      </div>

      {/* 挖掘结果 */}
      <div className="card" style={{ marginTop: 16 }}>
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
            挖掘结果{results.length > 0 ? `（${results.length} 家）` : ""}
          </h3>
          {results.length > 0 && (
            <div style={{ display: "flex", gap: 8 }}>
              <button className="btn btn-default btn-sm" onClick={toggleAll}>
                {selected.size > 0 ? "取消全选" : "全选未入库"}
              </button>
              <button
                className="btn btn-primary btn-sm"
                onClick={doImport}
                disabled={selected.size === 0}
              >
                导入所选（{selected.size}）
              </button>
            </div>
          )}
        </div>
        {results.length === 0 && !loading ? (
          <p style={{ color: "#999" }}>
            输入条件后点击「开始挖掘」拉取潜客候选；已入库的条目会置灰标记。
          </p>
        ) : (
          <div className="table-wrap">
            <table className="table prospect-table">
              <thead>
                <tr>
                  <th style={{ width: 36 }}>
                    <input
                      type="checkbox"
                      checked={
                        results.length > 0 &&
                        selected.size ===
                          results.filter((c) => !c.inLibrary).length
                      }
                      onChange={toggleAll}
                    />
                  </th>
                  <th>公司名称</th>
                  <th>联系人</th>
                  <th>邮箱</th>
                  <th>电话</th>
                  <th>行业</th>
                  <th>地区</th>
                  <th>规模</th>
                  <th>官网</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                {results.map((c) => {
                  const key = keyOf(c);
                  return (
                    <tr
                      key={key}
                      style={c.inLibrary ? { opacity: 0.55 } : undefined}
                    >
                      <td>
                        <input
                          type="checkbox"
                          checked={selected.has(key)}
                          disabled={c.inLibrary}
                          onChange={() => toggle(key)}
                        />
                      </td>
                      <td>{c.companyName}</td>
                      <td>{c.contactName ?? "-"}</td>
                      <td>{c.contactEmail ?? "-"}</td>
                      <td>{c.contactPhone ?? "-"}</td>
                      <td>{c.industry ?? "-"}</td>
                      <td>{c.region ?? "-"}</td>
                      <td>{c.scale ?? "-"}</td>
                      <td>
                        {c.website ? (
                          <a href={c.website} target="_blank" rel="noreferrer">
                            {c.website}
                          </a>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td>
                        {c.inLibrary ? (
                          <span className="badge badge-converted">已入库</span>
                        ) : (
                          <span className="badge badge-new">待入库</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 数据源管理 */}
      <div className="card" style={{ marginTop: 16 }}>
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
          <h3 style={{ margin: 0 }}>数据源管理</h3>
          <button className="btn btn-sm btn-default" onClick={openDsCreate}>
            新增数据源
          </button>
        </div>
        <div className="table-wrap">
          <table className="table data-source-table">
            <thead>
              <tr>
                <th>名称</th>
                <th>类型</th>
                <th>接口地址</th>
                <th>API Key</th>
                <th>状态</th>
                <th style={{ width: 200 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {sources.length === 0 ? (
                <tr>
                  <td colSpan={6} style={{ color: "#999" }}>
                    暂无数据源
                  </td>
                </tr>
              ) : (
                sources.map((s) => (
                  <tr key={s.id}>
                    <td>{s.name}</td>
                    <td>{s.type}</td>
                    <td>{s.apiBaseUrl ?? "-"}</td>
                    <td>{s.apiKeyMasked ?? "未配置"}</td>
                    <td>
                      <button
                        className={`btn btn-xs ${s.enabled ? "btn-primary" : "btn-default"}`}
                        onClick={() => toggleDs(s)}
                      >
                        {s.enabled ? "已启用" : "已停用"}
                      </button>
                    </td>
                    <td>
                      <button
                        className="btn btn-xs btn-default"
                        onClick={() => openDsEdit(s)}
                      >
                        编辑
                      </button>{" "}
                      <button
                        className="btn btn-xs btn-danger"
                        onClick={() => deleteDs(s)}
                      >
                        删除
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
        <p style={{ color: "#999", marginTop: 8, fontSize: 13 }}>
          挖掘默认使用已启用的数据源（无启用数据源时回退内置演示数据）。企查查等真实数据源需配置接口地址与
          API Key。
        </p>
      </div>

      {/* 数据源编辑弹窗 */}
      {dsModal && (
        <div className="modal-mask" onClick={() => setDsModal(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{dsForm.id ? "编辑数据源" : "新增数据源"}</h3>
            <div className="form-item">
              <label>名称</label>
              <input
                value={dsForm.name}
                onChange={(e) => setDsForm({ ...dsForm, name: e.target.value })}
              />
            </div>
            <div className="form-item">
              <label>类型</label>
              <input
                value={dsForm.type}
                disabled={dsForm.id > 0}
                placeholder="如 qichacha / tianyancha"
                onChange={(e) => setDsForm({ ...dsForm, type: e.target.value })}
              />
            </div>
            <div className="form-item">
              <label>接口地址（API Base URL）</label>
              <input
                value={dsForm.apiBaseUrl}
                placeholder="https://api.qcc.com"
                onChange={(e) =>
                  setDsForm({ ...dsForm, apiBaseUrl: e.target.value })
                }
              />
            </div>
            <div className="form-item">
              <label>API Key{dsForm.id ? "（留空保持不变）" : ""}</label>
              <input
                type="password"
                value={dsForm.apiKey}
                placeholder={dsForm.id ? "••••••" : ""}
                onChange={(e) =>
                  setDsForm({ ...dsForm, apiKey: e.target.value })
                }
              />
            </div>
            <div className="form-item">
              <label>
                <input
                  type="checkbox"
                  checked={dsForm.enabled}
                  onChange={(e) =>
                    setDsForm({ ...dsForm, enabled: e.target.checked })
                  }
                />{" "}
                启用
              </label>
            </div>
            <div className="modal-actions">
              <button
                className="btn btn-default"
                onClick={() => setDsModal(false)}
              >
                取消
              </button>
              <button
                className="btn btn-primary"
                onClick={saveDs}
                disabled={dsSaving}
              >
                {dsSaving ? "保存中…" : "保存"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
