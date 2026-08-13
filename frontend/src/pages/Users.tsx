import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken, getRole, isSystemAdmin } from "../api/client";
import { promptDialog } from "../utils/dialog";
import { Nav } from "./Nav";

interface UserItem {
  id: number;
  username: string;
  displayName: string | null;
  role: string;
  tenantId: number | null;
  tenantName: string | null;
  status: string;
  createdAt: string;
  lastLoginAt: string | null;
}

/** 三级角色标签：系统管理员(admin+无租户) / 普通管理员(admin+有租户) / 普通用户(operator) */
function roleLabel(u: UserItem): string {
  if (u.role === "operator") return "普通用户";
  return u.tenantId == null || u.tenantId === 0 ? "系统管理员" : "普通管理员";
}

/**
 * 用户管理（M8.6 三视角）：
 * - 系统管理员（平台）：视图下拉（系统用户管理/所有用户管理）+ 创建系统管理员
 * - 普通管理员（租户）：本租户用户列表（不含自己）+ 创建本租户账号 + 重置密码/禁用
 * - 普通用户（operator）：只显示自己一行，「修改信息」跳转个人设置
 */
export default function Users() {
  const navigate = useNavigate();
  const platform = isSystemAdmin();
  /** 租户管理员 = role=admin 且属于某租户（非平台） */
  const isAdmin = getRole() === "admin";
  const [users, setUsers] = useState<UserItem[]>([]);
  /** 所有用户（只读视图，仅系统管理员加载 /users/all） */
  const [allUsers, setAllUsers] = useState<UserItem[]>([]);
  /** 平台视角视图切换：sys=系统用户管理 / all=所有用户管理 */
  const [view, setView] = useState<"sys" | "all">("sys");
  /** 当前登录用户名（/auth/me）：平台视角用于隐藏“重置自己密码”入口 */
  const [me, setMe] = useState<string>("");
  const [msg, setMsg] = useState<{ type: "ok" | "err"; text: string } | null>(
    null,
  );
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    username: "",
    password: "",
    displayName: "",
    // 目标角色：超级管理员只能创建超级管理员；租户管理员创建本租户普通管理员/普通用户
    role: platform ? "admin" : "operator",
  });

  const load = async () => {
    try {
      const list = await api<UserItem[]>("/users");
      setUsers(list);
    } catch (e) {
      setMsg({ type: "err", text: (e as Error).message });
    }
  };

  /** 加载所有用户（只读视图）：仅系统管理员可调 */
  const loadAll = async () => {
    try {
      const list = await api<UserItem[]>("/users/all");
      setAllUsers(list);
    } catch (e) {
      setMsg({ type: "err", text: (e as Error).message });
    }
  };

  useEffect(() => {
    load();
    // 当前登录用户名：平台视角判断“是否本人”以隐藏重置密码入口
    if (platform) {
      api<{ username: string }>("/auth/me")
        .then((m) => setMe(m.username))
        .catch(() => {
          /* 失败不阻塞页面（后端仍有权限校验兑底） */
        });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** 切换视图：切到「所有用户管理」时懒加载 */
  const switchView = (v: "sys" | "all") => {
    setView(v);
    if (v === "all" && allUsers.length === 0) loadAll();
  };

  const create = async () => {
    if (!form.username.trim() || form.password.length < 8) {
      setMsg({ type: "err", text: "用户名不能为空，密码至少 8 位" });
      return;
    }
    setCreating(true);
    setMsg(null);
    try {
      await api("/users", {
        method: "POST",
        body: JSON.stringify({
          username: form.username.trim(),
          password: form.password,
          displayName: form.displayName.trim() || undefined,
          role: form.role,
        }),
      });
      setMsg({ type: "ok", text: `用户 ${form.username.trim()} 创建成功` });
      setForm({
        username: "",
        password: "",
        displayName: "",
        role: platform ? "admin" : "operator",
      });
      load();
    } catch (e) {
      setMsg({ type: "err", text: (e as Error).message });
    } finally {
      setCreating(false);
    }
  };

  const toggleStatus = async (u: UserItem) => {
    const target = u.status === "active" ? "disabled" : "active";
    try {
      await api(`/users/${u.id}/status`, {
        method: "PUT",
        body: JSON.stringify({ status: target }),
      });
      load();
    } catch (e) {
      setMsg({ type: "err", text: (e as Error).message });
    }
  };

  const resetPassword = async (u: UserItem) => {
    const pwd = await promptDialog(
      `为 ${u.username} 设置新密码（至少 8 位）：`,
      { title: "重置密码", placeholder: "新密码（至少 8 位）" },
    );
    if (pwd === null) return;
    if (pwd.length < 8) {
      setMsg({ type: "err", text: "新密码至少 8 位" });
      return;
    }
    try {
      await api(`/users/${u.id}/password`, {
        method: "PUT",
        body: JSON.stringify({ newPassword: pwd }),
      });
      setMsg({ type: "ok", text: `${u.username} 密码已重置` });
    } catch (e) {
      setMsg({ type: "err", text: (e as Error).message });
    }
  };

  return (
    <div>
      <Nav
        current="users"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="page">
        <h2>用户管理</h2>

        {/* 系统管理员：下拉切换「系统用户管理 / 所有用户管理」；租户管理员无下拉（只有本租户视图） */}
        {platform && (
          <div className="form-row" style={{ marginBottom: 12 }}>
            <label
              style={{
                lineHeight: "36px",
                marginRight: 8,
                whiteSpace: "nowrap",
              }}
            >
              视图：
            </label>
            <select
              className="input"
              style={{ width: 200 }}
              value={view}
              onChange={(e) => switchView(e.target.value as "sys" | "all")}
            >
              <option value="sys">系统用户管理</option>
              <option value="all">所有用户管理</option>
            </select>
            <span
              style={{
                lineHeight: "36px",
                marginLeft: 12,
                color: "#888",
                fontSize: 13,
              }}
            >
              {view === "sys"
                ? "仅管理平台系统管理员（可创建/重置密码）"
                : "只读：所有租户所有用户，仅查看"}
            </span>
          </div>
        )}

        {/* ===== 系统用户管理 视图（平台） / 租户管理员视图 / 普通用户视图 ===== */}
        {(!platform || view === "sys") && (
          <>
            {/* 创建账号：仅管理员（系统管理员/租户管理员）；普通用户无创建权限 */}
            {isAdmin && (
              <div className="card">
                <h3>
                  {platform
                    ? "创建账号（超级管理员只能创建超级管理员）"
                    : "创建账号（归属当前租户：普通管理员或普通用户）"}
                </h3>
                <div className="form-row">
                  <input
                    className="input"
                    placeholder="用户名（3-32 字符）"
                    value={form.username}
                    onChange={(e) =>
                      setForm({ ...form, username: e.target.value })
                    }
                  />
                  <input
                    className="input"
                    type="password"
                    placeholder="初始密码（至少 8 位）"
                    value={form.password}
                    onChange={(e) =>
                      setForm({ ...form, password: e.target.value })
                    }
                  />
                  <input
                    className="input"
                    placeholder="显示名称（可选）"
                    value={form.displayName}
                    onChange={(e) =>
                      setForm({ ...form, displayName: e.target.value })
                    }
                  />
                  <select
                    className="input"
                    value={form.role}
                    onChange={(e) => setForm({ ...form, role: e.target.value })}
                  >
                    {platform ? (
                      <option value="admin">超级管理员</option>
                    ) : (
                      <>
                        <option value="operator">普通用户</option>
                        <option value="admin">普通管理员</option>
                      </>
                    )}
                  </select>
                  <button className="btn" disabled={creating} onClick={create}>
                    {creating ? "创建中..." : "创建账号"}
                  </button>
                </div>
              </div>
            )}

            {msg && <div className={`msg ${msg.type}`}>{msg.text}</div>}

            {/* 普通用户提示：只能看到自己，修改信息跳个人设置 */}
            {!isAdmin && (
              <p style={{ color: "#888", fontSize: 13, margin: "0 0 8px" }}>
                您只能查看自己的账号信息；修改资料或密码请点击「修改信息」。
              </p>
            )}

            <div className="card">
              <div className="table-wrap">
                <table className="table user-table">
                  <thead>
                    <tr>
                      <th>用户名</th>
                      <th>显示名称</th>
                      <th>角色</th>
                      <th>状态</th>
                      <th>创建时间</th>
                      <th>最后登录</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {users.map((u) => (
                      <tr key={u.id}>
                        <td>{u.username}</td>
                        <td>{u.displayName || "-"}</td>
                        <td>{roleLabel(u)}</td>
                        <td>
                          {u.status === "active" ? (
                            <span style={{ color: "#16a34a" }}>正常</span>
                          ) : (
                            <span style={{ color: "#e5484d" }}>已禁用</span>
                          )}
                        </td>
                        <td>
                          {u.createdAt
                            ? u.createdAt.replace("T", " ").slice(0, 16)
                            : "-"}
                        </td>
                        <td>
                          {u.lastLoginAt
                            ? u.lastLoginAt.replace("T", " ").slice(0, 16)
                            : "-"}
                        </td>
                        <td>
                          {/* 三视角操作列（M8.6）：
                              平台：列表只有系统管理员 —— admin 演示账号（含本人）及自己无操作，其他系统管理员仅可重置密码
                              租户管理员：本租户列表不含自己（后端已过滤）→ 可重置密码/启停本租户任意账号
                              普通用户：列表只有自己一行 → 「修改信息」跳转个人设置（含改密） */}
                          {platform ? (
                            u.username !== "admin" &&
                            u.username !== me && (
                              <button
                                className="btn btn-sm"
                                onClick={() => resetPassword(u)}
                              >
                                重置密码
                              </button>
                            )
                          ) : isAdmin ? (
                            <>
                              <button
                                className="btn btn-sm"
                                onClick={() => resetPassword(u)}
                              >
                                重置密码
                              </button>{" "}
                              <button
                                className="btn btn-sm"
                                onClick={() => toggleStatus(u)}
                              >
                                {u.status === "active" ? "禁用" : "启用"}
                              </button>
                            </>
                          ) : (
                            <button
                              className="btn btn-sm"
                              onClick={() => navigate("/settings")}
                            >
                              修改信息
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </>
        )}

        {/* ===== 所有用户管理 视图（只读，仅系统管理员） ===== */}
        {platform && view === "all" && (
          <div className="card">
            <div className="table-wrap">
              <table className="table user-table">
                <thead>
                  <tr>
                    <th>用户名</th>
                    <th>显示名称</th>
                    <th>角色</th>
                    <th>所属租户</th>
                    <th>状态</th>
                    <th>创建时间</th>
                    <th>最后登录</th>
                  </tr>
                </thead>
                <tbody>
                  {allUsers.map((u) => (
                    <tr key={u.id}>
                      <td>{u.username}</td>
                      <td>{u.displayName || "-"}</td>
                      <td>{roleLabel(u)}</td>
                      <td>{u.tenantName || "-"}</td>
                      <td>
                        {u.status === "active" ? (
                          <span style={{ color: "#16a34a" }}>正常</span>
                        ) : (
                          <span style={{ color: "#e5484d" }}>已禁用</span>
                        )}
                      </td>
                      <td>
                        {u.createdAt
                          ? u.createdAt.replace("T", " ").slice(0, 16)
                          : "-"}
                      </td>
                      <td>
                        {u.lastLoginAt
                          ? u.lastLoginAt.replace("T", " ").slice(0, 16)
                          : "-"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {allUsers.length === 0 && (
              <p style={{ color: "#888", margin: 12 }}>暂无用户</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
