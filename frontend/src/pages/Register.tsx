import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, setRole, setTenantId, setToken } from "../api/client";

/** 注册成功即登录，返回结构与登录一致 + tenantId */
interface RegisterResult {
  token: string;
  username: string;
  displayName: string;
  role: string;
  tenantId: number;
}

/**
 * SaaS 注册页：开放注册，创建独立租户 + 租户管理员，注册即登录。
 * 用户名 3-32 位字母/数字/下划线，密码至少 8 位；显示名/公司名选填。
 */
export default function Register() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [companyName, setCompanyName] = useState("");
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);

  const register = async () => {
    const uname = username.trim();
    if (!uname) {
      setMsg("请输入用户名");
      return;
    }
    if (!/^[a-zA-Z0-9_]{3,32}$/.test(uname)) {
      setMsg("用户名需 3-32 位，仅支持字母、数字、下划线");
      return;
    }
    if (!password || password.length < 8) {
      setMsg("密码至少 8 位");
      return;
    }
    if (password !== confirm) {
      setMsg("两次输入的密码不一致");
      return;
    }
    setLoading(true);
    setMsg(null);
    try {
      const data = await api<RegisterResult>("/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username: uname,
          password,
          displayName: displayName.trim() || undefined,
          companyName: companyName.trim() || undefined,
        }),
        skipAuthRedirect: true,
      });
      // 注册即登录：保存 token/角色后直达工作台
      setToken(data.token, true);
      setRole(data.role);
      setTenantId(data.tenantId);
      navigate("/");
    } catch (e) {
      setMsg((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  /** 清空表单，方便重新填写 */
  const reset = () => {
    setUsername("");
    setPassword("");
    setConfirm("");
    setDisplayName("");
    setCompanyName("");
    setMsg(null);
  };

  return (
    <div className="auth-page">
      <div className="auth-box card">
        <div className="auth-header">
          <img
            src={`${import.meta.env.BASE_URL}logo.svg`}
            alt="AI智能获客助手"
            className="auth-logo"
          />
          <h2 className="auth-title">AI智能获客助手 · 注册</h2>
        </div>
        <div className="form-item">
          <label>用户名 *</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="3-32 位字母、数字、下划线"
          />
        </div>
        <div className="form-item">
          <label>密码 *</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="至少 8 位"
          />
        </div>
        <div className="form-item">
          <label>确认密码 *</label>
          <input
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && register()}
            placeholder="再次输入密码"
          />
        </div>
        <div className="form-item">
          <label>显示名（选填）</label>
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="例如：张经理"
          />
        </div>
        <div className="form-item">
          <label>公司名（选填）</label>
          <input
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
            placeholder="例如：某某科技有限公司"
          />
        </div>
        <div className="btn-row">
          <button className="btn" disabled={loading} onClick={register}>
            {loading ? "注册中..." : "注 册"}
          </button>
          <button
            type="button"
            className="btn btn-default"
            disabled={loading}
            onClick={reset}
          >
            重 置
          </button>
        </div>
        {msg && <div className="msg error">{msg}</div>}
        <div className="auth-switch-row">
          已有账号？
          <Link to="/login" className="auth-switch-link">
            直接登录
          </Link>
        </div>
        <a className="auth-home-link" href="/">
          ← 返回首页
        </a>
      </div>
    </div>
  );
}
