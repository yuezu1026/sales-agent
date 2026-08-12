import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  api,
  getRemember,
  getToken,
  setRole,
  setTenantId,
  setToken,
} from "../api/client";

interface LoginResult {
  token: string;
  username: string;
  displayName: string;
  role: string;
  tenantId: number | null;
}

/** 系统登录统计（与后端 /api/auth/login-stats 返回对齐） */
interface LoginStats {
  totalLogins: number;
  todayLogins: number;
  todayUsers: number;
}

export default function Login() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  /** "记住我"：勾选后 token 持久保存，关闭浏览器后仍保持登录 */
  const [remember, setRemember] = useState(getRemember());
  /** 系统登录统计（累计/今日次数/今日人数），登录表单下方展示 */
  const [loginStats, setLoginStats] = useState<LoginStats | null>(null);

  // 挂载时查询系统登录统计：登录页直接展示（免登录接口）
  useEffect(() => {
    let cancelled = false;
    api<LoginStats>("/auth/login-stats", { skipAuthRedirect: true })
      .then((s) => {
        if (!cancelled) setLoginStats(s);
      })
      .catch(() => {
        /* 接口异常不阻塞登录页，静默忽略 */
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // 已登录（token 有效）时自动跳转工作台：
  // 解决"关闭浏览器重开后停在登录页 URL，误以为记住我失效"的问题
  useEffect(() => {
    let cancelled = false;
    if (getToken()) {
      api("/auth/me", { skipAuthRedirect: true })
        .then(() => {
          if (!cancelled) navigate("/");
        })
        .catch(() => {
          /* token 无效/过期：停留在登录页重新登录 */
        });
    }
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const login = async () => {
    const uname = username.trim();
    if (!uname || !password) {
      setMsg("请输入用户名和密码");
      return;
    }
    setLoading(true);
    setMsg(null);
    try {
      const data = await api<LoginResult>("/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: uname, password }),
        skipAuthRedirect: true,
      });
      setToken(data.token, remember);
      setRole(data.role);
      setTenantId(data.tenantId);
      navigate("/");
    } catch (e) {
      setMsg((e as Error).message);
    } finally {
      setLoading(false);
    }
  };

  /** 清空用户名/密码/错误提示，方便输错后重新输入 */
  const reset = () => {
    setUsername("");
    setPassword("");
    setMsg(null);
  };

  /** 一键填入默认测试账号，免手动输入（admin / Admin@123456） */
  const fillTestAccount = () => {
    setUsername("admin");
    setPassword("Admin@123456");
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
          <h2 className="auth-title">AI智能获客助手 · 登录</h2>
        </div>
        <div className="form-item">
          <label>用户名</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="请输入用户名"
          />
        </div>
        <div className="form-item">
          <label>密码</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && login()}
            placeholder="请输入密码"
          />
        </div>
        <div className="form-item remember-row">
          <label className="remember-label">
            <input
              type="checkbox"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
            />
            <span>记住我（30 天内免登录）</span>
          </label>
        </div>
        <div className="test-account-tip">
          <span>
            测试账号：<code>admin</code> / <code>Admin@123456</code>
          </span>
          <button type="button" className="btn-xs" onClick={fillTestAccount}>
            一键填入
          </button>
        </div>
        {loginStats && (
          <div className="login-stats-tip">
            📊 系统累计登录 {loginStats.totalLogins} 次 · 今日{" "}
            {loginStats.todayLogins} 次 · {loginStats.todayUsers} 人登录
          </div>
        )}
        <div className="btn-row">
          <button className="btn" disabled={loading} onClick={login}>
            {loading ? "登录中..." : "登 录"}
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
          还没有账号？
          <Link to="/register" className="auth-switch-link">
            免费注册，立即开始
          </Link>
        </div>
        <a className="auth-home-link" href="/">
          ← 返回首页
        </a>
      </div>
    </div>
  );
}
