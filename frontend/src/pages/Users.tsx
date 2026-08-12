import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken, getRole } from "../api/client";
import { promptDialog } from "../utils/dialog";
import { Nav } from "./Nav";

interface UserItem {
  id: number;
  username: string;
  displayName: string | null;
  role: string;
  status: string;
  createdAt: string;
  lastLoginAt: string | null;
}

const ROLE_LABEL: Record<string, string> = {
  admin: "超级管理员",
  operator: "普通操作员",
};

/** 用户管理（仅超级管理员）：创建操作员账号 / 启用禁用 / 重置密码 */
export default function Users() {
  const navigate = useNavigate();
  const [users, setUsers] = useState<UserItem[]>([]);
  const [msg, setMsg] = useState<{ type: "ok" | "err"; text: string } | null>(
    null,
  );
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    username: "",
    password: "",
    displayName: "",
  });

  const load = async () => {
    try {
      const list = await api<UserItem[]>("/users");
      setUsers(list);
    } catch (e) {
      setMsg({ type: "err", text: (e as Error).message });
    }
  };

  useEffect(() => {
    if (getRole() !== "admin") {
      navigate("/");
      return;
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
        }),
      });
      setMsg({ type: "ok", text: `操作员 ${form.username.trim()} 创建成功` });
      setForm({ username: "", password: "", displayName: "" });
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
        <div className="card">
          <h3>创建操作员账号</h3>
          <div className="form-row">
            <input
              className="input"
              placeholder="用户名（3-32 字符）"
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
            />
            <input
              className="input"
              type="password"
              placeholder="初始密码（至少 8 位）"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
            />
            <input
              className="input"
              placeholder="显示名称（可选）"
              value={form.displayName}
              onChange={(e) =>
                setForm({ ...form, displayName: e.target.value })
              }
            />
            <button className="btn" disabled={creating} onClick={create}>
              {creating ? "创建中..." : "创建操作员"}
            </button>
          </div>
        </div>

        {msg && <div className={`msg ${msg.type}`}>{msg.text}</div>}

        <div className="card">
          <table className="table">
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
                  <td>{ROLE_LABEL[u.role] ?? u.role}</td>
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
                    {u.role !== "admin" && (
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
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
