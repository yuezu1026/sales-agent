import { useEffect, useState } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { api, clearToken, getRole, isSystemAdmin } from "../api/client";
import { confirmDialog } from "../utils/dialog";
import { Nav } from "./Nav";

interface UnsubscribeItem {
  email: string;
  source: string;
  createdAt: string;
}

/**
 * 退订管理（邮件管理下拉）：已点击邮件退订链接的邮箱在此列出，系统不再向这些邮箱发送营销邮件。
 * 仅租户管理员（role=admin 且 tenantId 非空）可见；普通用户与平台管理员重定向工作台。
 */
export default function UnsubscribeManage() {
  const navigate = useNavigate();
  const isAdmin = getRole() === "admin";
  const sysAdmin = isSystemAdmin();
  const tenantAdmin = isAdmin && !sysAdmin;

  const [unsubList, setUnsubList] = useState<UnsubscribeItem[]>([]);
  const [unsubMsg, setUnsubMsg] = useState<string | null>(null);
  const [unsubLoading, setUnsubLoading] = useState(false);

  const loadUnsub = async () => {
    setUnsubLoading(true);
    setUnsubMsg(null);
    try {
      const data = await api<UnsubscribeItem[]>("/unsubscribe/list");
      setUnsubList(data);
    } catch (e) {
      setUnsubMsg((e as Error).message);
    } finally {
      setUnsubLoading(false);
    }
  };

  useEffect(() => {
    if (tenantAdmin) loadUnsub();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const restore = async (email: string) => {
    if (
      !(await confirmDialog(
        `确认恢复「${email}」？恢复后该邮箱可继续接收营销邮件。`,
      ))
    )
      return;
    try {
      await api(`/unsubscribe/${encodeURIComponent(email)}`, {
        method: "DELETE",
      });
      setUnsubMsg("已恢复，该邮箱可继续接收邮件");
      loadUnsub();
    } catch (e) {
      setUnsubMsg((e as Error).message);
    }
  };

  // 权限守卫：仅租户管理员可访问
  if (!tenantAdmin) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="page">
      <Nav
        current="unsubs"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="container">
        <div className="card">
          <h3>退订管理</h3>
          <p style={{ color: "#666", fontSize: 13, marginTop: 0 }}>
            已点击邮件退订链接的邮箱在此列出，系统不再向这些邮箱发送营销邮件。可手动恢复。
          </p>
          {unsubLoading ? (
            <div className="msg">加载中...</div>
          ) : unsubList.length === 0 ? (
            <div className="msg">暂无退订邮箱</div>
          ) : (
            <div className="table-wrap">
              <table className="table unsub-table">
                <thead>
                  <tr>
                    <th>邮箱</th>
                    <th>来源</th>
                    <th>退订时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {unsubList.map((item) => (
                    <tr key={item.email}>
                      <td>{item.email}</td>
                      <td>
                        {item.source === "link" ? "邮件退订链接" : item.source}
                      </td>
                      <td>
                        {item.createdAt
                          ? item.createdAt.replace("T", " ").slice(0, 19)
                          : "-"}
                      </td>
                      <td>
                        <button
                          className="btn btn-sm"
                          onClick={() => restore(item.email)}
                        >
                          恢复
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {unsubMsg && (
            <div
              className={`msg ${unsubMsg.includes("恢复") ? "success" : "error"}`}
            >
              {unsubMsg}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
