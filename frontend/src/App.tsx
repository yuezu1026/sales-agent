import { lazy, ReactNode, Suspense } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { isSystemAdmin } from "./api/client";

/**
 * 路由级代码分割（M8.2 登录页加速）：每个页面独立 chunk，首屏（登录页）只加载 Login chunk，
 * 其余页面按需加载，主 bundle 从 ~362KB(gzip) 大幅缩小。
 */
const Login = lazy(() => import("./pages/Login"));
const Register = lazy(() => import("./pages/Register"));
const Unsubscribe = lazy(() => import("./pages/Unsubscribe"));
const Donate = lazy(() => import("./pages/Donate"));
const Users = lazy(() => import("./pages/Users"));
const Help = lazy(() => import("./pages/Help"));
const Dashboard = lazy(() => import("./pages/Dashboard"));
const Customers = lazy(() => import("./pages/Customers"));
const Prospect = lazy(() => import("./pages/Prospect"));
const Profile = lazy(() => import("./pages/Profile"));
const Inbox = lazy(() => import("./pages/Inbox"));
const Drafts = lazy(() => import("./pages/Drafts"));
const Sent = lazy(() => import("./pages/Sent"));
const Templates = lazy(() => import("./pages/Templates"));
const UnsubscribeManage = lazy(() => import("./pages/UnsubscribeManage"));
const Settings = lazy(() => import("./pages/Settings"));

/**
 * 业务页面守卫：系统管理员（平台级）只能访问 工作台（仅登录统计/地理分布）/用户管理/帮助，
 * 访问其余业务页面（客户/邮件/潜客等）一律重定向到用户管理，
 * 防止手动输入 URL 触达租户数据。
 */
function BizGuard({ children }: { children: ReactNode }) {
  if (isSystemAdmin()) {
    return <Navigate to="/users" replace />;
  }
  return <>{children}</>;
}

export default function App() {
  return (
    <Suspense fallback={<div className="page-loading">加载中…</div>}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/unsubscribe" element={<Unsubscribe />} />
        {/* 捐助拾客 Shike：公开页（免登录），全角色可见 */}
        <Route path="/donate" element={<Donate />} />
        <Route path="/users" element={<Users />} />
        <Route path="/help" element={<Help />} />
        {/* 工作台：系统管理员放行（Dashboard 内按身份只显示登录统计/地理分布），
          其余业务页仍由 BizGuard 拦截 */}
        <Route path="/" element={<Dashboard />} />
        <Route
          path="/customers"
          element={
            <BizGuard>
              <Customers />
            </BizGuard>
          }
        />
        <Route
          path="/prospect"
          element={
            <BizGuard>
              <Prospect />
            </BizGuard>
          }
        />
        <Route
          path="/profile"
          element={
            <BizGuard>
              <Profile />
            </BizGuard>
          }
        />
        <Route
          path="/inbox"
          element={
            <BizGuard>
              <Inbox />
            </BizGuard>
          }
        />
        <Route
          path="/drafts"
          element={
            <BizGuard>
              <Drafts />
            </BizGuard>
          }
        />
        <Route
          path="/sent"
          element={
            <BizGuard>
              <Sent />
            </BizGuard>
          }
        />
        <Route
          path="/templates"
          element={
            <BizGuard>
              <Templates />
            </BizGuard>
          }
        />
        <Route
          path="/unsubs"
          element={
            <BizGuard>
              <UnsubscribeManage />
            </BizGuard>
          }
        />
        <Route
          path="/settings"
          element={
            <BizGuard>
              <Settings />
            </BizGuard>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
