import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api, clearToken, isSystemAdmin } from "../api/client";
import TrendChart from "../components/TrendChart";
import ChinaMap, { GeoPoint } from "../components/ChinaMap";
import { Nav } from "./Nav";

interface UsageSummary {
  today: { calls: number; tokens: number; cost: number };
  total: { calls: number; tokens: number; cost: number };
  byScene: { scene: string; calls: number; tokens: number; cost: number }[];
}

interface LeadStats {
  total: number;
  byStatus: Record<string, number>;
}

/** 邮件效果统计（M4-6 打开率追踪） */
interface EmailStats {
  sent: number;
  opened: number;
  openRate: number;
  clicked: number;
  clickRate: number;
}

/** 系统登录统计（M7.9，与后端 /api/auth/login-stats 对齐） */
interface LoginStats {
  totalLogins: number;
  todayLogins: number;
  todayUsers: number;
}

/** 系统登录趋势（M7.10 曲线图，与后端 /api/auth/login-trend 对齐） */
interface LoginTrend {
  range: string;
  points: { label: string; count: number }[];
}

/** 访问者地理分布（M7.13 地图散点，与后端 /api/auth/login-geo 对齐） */
interface LoginGeo {
  points: GeoPoint[];
}

/** 趋势图时间范围（日/周/月/年） */
const TREND_RANGES = [
  { key: "daily", label: "每日" },
  { key: "weekly", label: "每周" },
  { key: "monthly", label: "每月" },
  { key: "yearly", label: "每年" },
] as const;
type TrendRange = (typeof TREND_RANGES)[number]["key"];

const LEAD_STATUS_LABEL: Record<string, string> = {
  new: "新线索",
  contacted: "已触达",
  interested: "有意向",
  converted: "已转化",
  invalid: "无效",
};

/** 工作台：客户统计 + AI 个性化邮件生成（潜客挖掘在 M2 里程碑） */
export default function Dashboard() {
  const navigate = useNavigate();
  // 系统管理员（平台级）：工作台只展示平台级统计（登录统计/地理分布），
  // 不请求租户级数据（客户/邮件/AI 用量），避免无谓 400
  const sysAdmin = isSystemAdmin();
  const [usage, setUsage] = useState<UsageSummary | null>(null);
  const [leadStats, setLeadStats] = useState<LeadStats | null>(null);
  const [emailStats, setEmailStats] = useState<EmailStats | null>(null);
  const [loginStats, setLoginStats] = useState<LoginStats | null>(null);
  const [loginTrend, setLoginTrend] = useState<LoginTrend | null>(null);
  const [loginGeo, setLoginGeo] = useState<LoginGeo | null>(null);
  const [trendRange, setTrendRange] = useState<TrendRange>("daily");
  const [trendLoading, setTrendLoading] = useState(false);

  useEffect(() => {
    api("/auth/me").catch(() => navigate("/login"));
    if (!sysAdmin) {
      api<UsageSummary>("/ai/usage")
        .then(setUsage)
        .catch(() => setUsage(null));
      api<LeadStats>("/leads/stats")
        .then(setLeadStats)
        .catch(() => setLeadStats(null));
      api<EmailStats>("/email-stats")
        .then(setEmailStats)
        .catch(() => setEmailStats(null));
    }
    api<LoginStats>("/auth/login-stats", { skipAuthRedirect: true })
      .then(setLoginStats)
      .catch(() => setLoginStats(null));
    // M7.13：访问者地理分布（地图散点）
    api<LoginGeo>("/auth/login-geo")
      .then(setLoginGeo)
      .catch(() => setLoginGeo(null));
  }, [navigate, sysAdmin]);

  // M7.10：登录次数趋势曲线（随日/周/月/年切换重新请求）
  useEffect(() => {
    setTrendLoading(true);
    api<LoginTrend>(`/auth/login-trend?range=${trendRange}`)
      .then(setLoginTrend)
      .catch(() => setLoginTrend(null))
      .finally(() => setTrendLoading(false));
  }, [trendRange]);

  return (
    <div>
      <Nav
        current="dashboard"
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
            gap: 12,
            flexWrap: "wrap",
          }}
        >
          <h2 style={{ margin: 0 }}>工作台</h2>
        </div>

        {!sysAdmin && leadStats && (
          <div className="card" style={{ marginBottom: 20 }}>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                flexWrap: "wrap",
                gap: 12,
              }}
            >
              <h3 style={{ margin: 0 }}>客户概览</h3>
              <button
                className="btn btn-sm btn-default"
                onClick={() => navigate("/customers")}
              >
                进入客户管理
              </button>
            </div>
            <div className="stat-grid" style={{ gap: 12, marginTop: 16 }}>
              <div className="stat-box">
                <div className="stat-value">{leadStats.total}</div>
                <div className="stat-label">客户总数</div>
              </div>
              {Object.entries(LEAD_STATUS_LABEL).map(([k, v]) => (
                <div className="stat-box" key={k}>
                  <div className="stat-value">{leadStats.byStatus[k] ?? 0}</div>
                  <div className="stat-label">{v}</div>
                </div>
              ))}
            </div>
          </div>
        )}

        {!sysAdmin && emailStats && (
          <div className="card" style={{ marginTop: 20 }}>
            <h3>邮件效果</h3>
            <div className="stat-grid-5" style={{ gap: 12, marginTop: 16 }}>
              <div className="stat-box">
                <div className="stat-value">{emailStats.sent}</div>
                <div className="stat-label">已发送</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">{emailStats.opened}</div>
                <div className="stat-label">已打开</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">
                  {Number(emailStats.openRate).toFixed(1)}%
                </div>
                <div className="stat-label">打开率</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">{emailStats.clicked}</div>
                <div className="stat-label">已点击</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">
                  {Number(emailStats.clickRate).toFixed(1)}%
                </div>
                <div className="stat-label">点击率</div>
              </div>
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: "#888" }}>
              打开/点击由邮件内追踪像素与链接回传统计（需在系统设置中配置
              mail.track_url）
            </div>
          </div>
        )}

        {!sysAdmin && usage && (
          <div className="card" style={{ marginTop: 20 }}>
            <h3>AI 用量统计</h3>
            <div className="stat-grid-3" style={{ gap: 12 }}>
              <div className="stat-box">
                <div className="stat-value">{usage.today.calls}</div>
                <div className="stat-label">今日调用次数</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">{usage.today.tokens}</div>
                <div className="stat-label">今日 Token</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">
                  ¥{Number(usage.today.cost).toFixed(4)}
                </div>
                <div className="stat-label">今日成本</div>
              </div>
            </div>
            <div className="stat-grid-3" style={{ gap: 12, marginTop: 12 }}>
              <div className="stat-box">
                <div className="stat-value">{usage.total.calls}</div>
                <div className="stat-label">累计调用</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">{usage.total.tokens}</div>
                <div className="stat-label">累计 Token</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">
                  ¥{Number(usage.total.cost).toFixed(4)}
                </div>
                <div className="stat-label">累计成本</div>
              </div>
            </div>
            {usage.byScene.length > 0 && (
              <table className="table" style={{ marginTop: 16 }}>
                <thead>
                  <tr>
                    <th>场景</th>
                    <th>调用次数</th>
                    <th>Token</th>
                    <th>成本</th>
                  </tr>
                </thead>
                <tbody>
                  {usage.byScene.map((s) => (
                    <tr key={s.scene}>
                      <td>{s.scene}</td>
                      <td>{s.calls}</td>
                      <td>{s.tokens}</td>
                      <td>¥{Number(s.cost).toFixed(4)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        {loginStats && (
          <div className="card" style={{ marginTop: 20 }}>
            <h3>系统登录统计</h3>
            <div className="stat-grid-3" style={{ gap: 12, marginTop: 16 }}>
              <div className="stat-box">
                <div className="stat-value">{loginStats.totalLogins}</div>
                <div className="stat-label">累计登录</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">{loginStats.todayLogins}</div>
                <div className="stat-label">今日登录次数</div>
              </div>
              <div className="stat-box">
                <div className="stat-value">{loginStats.todayUsers}</div>
                <div className="stat-label">今日登录人数</div>
              </div>
            </div>
            {/* M7.10：登录次数趋势曲线（日/周/月/年切换） */}
            <div
              className="range-switch"
              role="group"
              aria-label="统计时间范围"
            >
              {TREND_RANGES.map((r) => (
                <button
                  key={r.key}
                  className={`range-btn${trendRange === r.key ? " active" : ""}`}
                  onClick={() => setTrendRange(r.key)}
                >
                  {r.label}
                </button>
              ))}
            </div>
            <div className="trend-chart">
              {trendLoading && !loginTrend ? (
                <div className="trend-chart-empty">加载中…</div>
              ) : loginTrend ? (
                <TrendChart points={loginTrend.points} />
              ) : (
                <div className="trend-chart-empty">暂无数据</div>
              )}
            </div>
          </div>
        )}

        {/* M7.13：访问者地理分布（中国地图散点） */}
        {loginGeo && (
          <div className="card" style={{ marginTop: 20 }}>
            <h3>访问者地理分布</h3>
            <div style={{ marginTop: 8, fontSize: 12, color: "#888" }}>
              按登录 IP 离线定位归属地（仅统计新版本上线后的登录）
            </div>
            <ChinaMap points={loginGeo.points} />
          </div>
        )}
      </div>
    </div>
  );
}
