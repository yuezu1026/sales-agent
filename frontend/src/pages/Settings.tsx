import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken, getRole, isSystemAdmin } from "../api/client";
import { confirmDialog } from "../utils/dialog";
import { Nav } from "./Nav";

interface ConfigItem {
  key: string;
  value: string;
  description: string;
}

interface UnsubscribeItem {
  email: string;
  source: string;
  createdAt: string;
}

/** 系统设置：AI Key / SMTP / 数据源（MVP 先提供 AI 配置）+ 修改密码 + 退订管理。
 *  普通操作员仅显示「个人设置」（修改密码），系统配置与退订管理仅超级管理员可见。 */
export default function Settings() {
  const navigate = useNavigate();
  const isAdmin = getRole() === "admin";
  // 平台管理员（超级管理员）无租户，不显示公司名称字段
  const sysAdmin = isSystemAdmin();
  // 租户管理员（role=admin 且属于某租户）：公司名称仅其可修改
  const tenantAdmin = isAdmin && !sysAdmin;
  const [configs, setConfigs] = useState<ConfigItem[]>([]);
  const [msg, setMsg] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // 个人信息：账号（只读）+ 显示名称/邮箱/微信/电话/公司名称
  const [profile, setProfile] = useState({
    username: "",
    displayName: "",
    email: "",
    wechat: "",
    phone: "",
    companyName: "",
  });
  const [profileMsg, setProfileMsg] = useState<string | null>(null);
  const [profileSaving, setProfileSaving] = useState(false);

  const [unsubList, setUnsubList] = useState<UnsubscribeItem[]>([]);
  const [unsubMsg, setUnsubMsg] = useState<string | null>(null);
  const [unsubLoading, setUnsubLoading] = useState(false);

  const [pwd, setPwd] = useState({ oldPassword: "", newPassword: "" });
  const [pwdMsg, setPwdMsg] = useState<string | null>(null);
  const [pwdSaving, setPwdSaving] = useState(false);

  const load = async () => {
    if (!isAdmin) return;
    try {
      const data = await api<ConfigItem[]>("/config");
      setConfigs(data);
    } catch {
      navigate("/login");
    }
  };

  /** 加载本人资料（/auth/me），回填个人信息表单 */
  const loadProfile = async () => {
    try {
      const data = await api<{
        username: string;
        displayName: string | null;
        email: string | null;
        wechat: string | null;
        phone: string | null;
        companyName: string | null;
      }>("/auth/me");
      setProfile({
        username: data.username ?? "",
        displayName: data.displayName ?? "",
        email: data.email ?? "",
        wechat: data.wechat ?? "",
        phone: data.phone ?? "",
        companyName: data.companyName ?? "",
      });
    } catch {
      // me 失败时保持空表单，不打扰（401 会由 api() 统一跳登录）
    }
  };

  /** 保存本人资料 */
  const saveProfile = async () => {
    if (!profile.displayName.trim()) {
      setProfileMsg("显示名称不能为空");
      return;
    }
    setProfileSaving(true);
    setProfileMsg(null);
    try {
      await api("/auth/profile", {
        method: "PUT",
        body: JSON.stringify({
          displayName: profile.displayName.trim(),
          email: profile.email.trim(),
          wechat: profile.wechat.trim(),
          phone: profile.phone.trim(),
          // 公司名称仅租户管理员可修改，普通用户/平台管理员不提交
          companyName: tenantAdmin ? profile.companyName.trim() : "",
        }),
      });
      setProfileMsg("个人信息保存成功");
    } catch (e) {
      setProfileMsg((e as Error).message);
    } finally {
      setProfileSaving(false);
    }
  };

  const loadUnsub = async () => {
    setUnsubLoading(true);
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
    load();
    loadProfile();
    if (isAdmin) loadUnsub();
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

  const save = async () => {
    setSaving(true);
    setMsg(null);
    try {
      await api("/config", {
        method: "PUT",
        body: JSON.stringify(configs),
      });
      setMsg("保存成功");
    } catch (e) {
      setMsg((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const changePassword = async () => {
    if (!pwd.oldPassword || !pwd.newPassword) {
      setPwdMsg("请填写原密码和新密码");
      return;
    }
    setPwdSaving(true);
    setPwdMsg(null);
    try {
      await api("/auth/change-password", {
        method: "POST",
        body: JSON.stringify(pwd),
      });
      setPwd({ oldPassword: "", newPassword: "" });
      setPwdMsg("密码修改成功");
    } catch (e) {
      setPwdMsg((e as Error).message);
    } finally {
      setPwdSaving(false);
    }
  };

  return (
    <div>
      <Nav
        current="settings"
        onLogout={() => {
          clearToken();
          navigate("/login");
        }}
      />
      <div className="container">
        <h2>{isAdmin ? "系统设置" : "个人设置"}</h2>

        {/* 个人信息：所有角色可见（账号只读，资料可编辑） */}
        <div className="card">
          <h3>个人信息</h3>
          <div className="form-item">
            <label>个人账号</label>
            <input value={profile.username} disabled />
          </div>
          <div className="form-item">
            <label>显示名称</label>
            <input
              value={profile.displayName}
              onChange={(e) =>
                setProfile({ ...profile, displayName: e.target.value })
              }
            />
          </div>
          <div className="form-item">
            <label>邮箱地址</label>
            <input
              type="email"
              placeholder="用于接收系统通知"
              value={profile.email}
              onChange={(e) =>
                setProfile({ ...profile, email: e.target.value })
              }
            />
          </div>
          <div className="form-item">
            <label>微信</label>
            <input
              placeholder="选填"
              value={profile.wechat}
              onChange={(e) =>
                setProfile({ ...profile, wechat: e.target.value })
              }
            />
          </div>
          <div className="form-item">
            <label>电话号码</label>
            <input
              placeholder="选填"
              value={profile.phone}
              onChange={(e) =>
                setProfile({ ...profile, phone: e.target.value })
              }
            />
          </div>
          {/* 公司名称：租户管理员可编辑；普通用户只读显示；平台管理员无租户不显示 */}
          {!sysAdmin && (
            <div className="form-item">
              <label>公司名称</label>
              <input
                value={profile.companyName}
                disabled={!tenantAdmin}
                readOnly={!tenantAdmin}
                title={tenantAdmin ? "" : "公司名称仅租户管理员可修改"}
                onChange={(e) =>
                  setProfile({ ...profile, companyName: e.target.value })
                }
              />
            </div>
          )}
          <button
            className="btn"
            disabled={profileSaving}
            onClick={saveProfile}
          >
            {profileSaving ? "保存中..." : "保存个人信息"}
          </button>
          {profileMsg && (
            <div
              className={`msg ${
                profileMsg.includes("成功") ? "success" : "error"
              }`}
            >
              {profileMsg}
            </div>
          )}
        </div>

        {isAdmin && (
          <div className="card">
            {/* AI 模型配置组（ai.*） */}
            <div className="config-group-title config-group-ai">
              🤖 AI 模型配置
            </div>
            {configs
              .filter((item) => item.key.startsWith("ai."))
              .map((item) => (
                <div className="form-item" key={item.key}>
                  <label>
                    {item.key}
                    {item.description ? (
                      <span
                        style={{ color: "#999", marginLeft: 8, fontSize: 12 }}
                      >
                        {item.description}
                      </span>
                    ) : null}
                  </label>
                  <input
                    value={item.value ?? ""}
                    onChange={(e) =>
                      setConfigs(
                        configs.map((c) =>
                          c.key === item.key
                            ? { ...c, value: e.target.value }
                            : c,
                        ),
                      )
                    }
                  />
                </div>
              ))}
            {/* 邮箱配置组（smtp.* / imap.* / mail.*） */}
            <div className="config-group-title config-group-mail">
              📧 邮箱配置
            </div>
            {configs
              .filter(
                (item) =>
                  item.key.startsWith("smtp.") ||
                  item.key.startsWith("imap.") ||
                  item.key.startsWith("mail."),
              )
              .map((item) => (
                <div className="form-item" key={item.key}>
                  <label>
                    {item.key}
                    {item.description ? (
                      <span
                        style={{ color: "#999", marginLeft: 8, fontSize: 12 }}
                      >
                        {item.description}
                      </span>
                    ) : null}
                  </label>
                  <input
                    value={item.value ?? ""}
                    onChange={(e) =>
                      setConfigs(
                        configs.map((c) =>
                          c.key === item.key
                            ? { ...c, value: e.target.value }
                            : c,
                        ),
                      )
                    }
                  />
                </div>
              ))}
            <button className="btn" disabled={saving} onClick={save}>
              {saving ? "保存中..." : "保存配置"}
            </button>
            {msg && (
              <div
                className={`msg ${msg.includes("成功") ? "success" : "error"}`}
              >
                {msg}
              </div>
            )}
          </div>
        )}

        <div className="card" style={{ marginTop: 20 }}>
          <h3>修改密码</h3>
          <div className="form-item">
            <label>原密码</label>
            <input
              type="password"
              value={pwd.oldPassword}
              onChange={(e) => setPwd({ ...pwd, oldPassword: e.target.value })}
            />
          </div>
          <div className="form-item">
            <label>新密码</label>
            <input
              type="password"
              value={pwd.newPassword}
              onChange={(e) => setPwd({ ...pwd, newPassword: e.target.value })}
            />
          </div>
          <button className="btn" disabled={pwdSaving} onClick={changePassword}>
            {pwdSaving ? "提交中..." : "修改密码"}
          </button>
          {pwdMsg && (
            <div
              className={`msg ${pwdMsg.includes("成功") ? "success" : "error"}`}
            >
              {pwdMsg}
            </div>
          )}
        </div>

        {isAdmin && (
          <div className="card" style={{ marginTop: 20 }}>
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
                          {item.source === "link"
                            ? "邮件退订链接"
                            : item.source}
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
        )}
      </div>
    </div>
  );
}
