import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken, getRole, isSystemAdmin } from "../api/client";
import { Nav } from "./Nav";

interface TenantItem {
  id: number;
  name: string;
  ownerUserId: number | null;
  ownerUsername: string | null;
  plan: string;
  status: string;
  createdAt: string;
  expireAt: string | null;
  userCount: number;
}

/** 租户管理（仅系统管理员，平台级）：只读列表，免费试用阶段无停用/启用等操作 */
export default function Tenants() {
  const navigate = useNavigate();
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [msg, setMsg] = useState<{ type: "ok" | "err"; text: string } | null>(
    null,
  );

  const load = async () => {
    try {
      const list = await api<TenantItem[]>("/tenants");
      setTenants(list);
    } catch (e) {
      setMsg({ type: "err", text: (e as Error).message });
    }
  };

  useEffect(() => {
    // 仅系统管理员（平台级账号）可访问；普通管理员/普通用户跳转工作台
    if (getRole() !== "admin" || !isSystemAdmin()) {
      navigate("/");
      return;
    }
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div>
      <Nav
        current="tenants"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="page">
        <h2>租户管理</h2>
        {msg && <div className={`msg ${msg.type}`}>{msg.text}</div>}
        <div className="card">
          <div className="table-wrap">
            <table className="table user-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>租户名称</th>
                  <th>租户管理员</th>
                  <th>套餐</th>
                  <th>状态</th>
                  <th>用户数</th>
                  <th>创建时间</th>
                  <th>到期时间</th>
                </tr>
              </thead>
              <tbody>
                {tenants.map((t) => (
                  <tr key={t.id}>
                    <td>{t.id}</td>
                    <td>{t.name}</td>
                    <td>{t.ownerUsername || "-"}</td>
                    <td>{t.plan === "free" ? "免费版" : t.plan}</td>
                    <td>
                      {t.status === "active" ? (
                        <span style={{ color: "#16a34a" }}>正常</span>
                      ) : (
                        <span style={{ color: "#e5484d" }}>已停用</span>
                      )}
                    </td>
                    <td>{t.userCount}</td>
                    <td>
                      {t.createdAt
                        ? t.createdAt.replace("T", " ").slice(0, 16)
                        : "-"}
                    </td>
                    <td>
                      {t.expireAt
                        ? t.expireAt.replace("T", " ").slice(0, 16)
                        : "-"}
                    </td>
                  </tr>
                ))}
                {tenants.length === 0 && (
                  <tr>
                    <td colSpan={8} style={{ textAlign: "center" }}>
                      暂无租户
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
