import { ReactNode } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { isSystemAdmin } from "./api/client";
import Customers from "./pages/Customers";
import Dashboard from "./pages/Dashboard";
import Drafts from "./pages/Drafts";
import Help from "./pages/Help";
import Inbox from "./pages/Inbox";
import Login from "./pages/Login";
import Profile from "./pages/Profile";
import Prospect from "./pages/Prospect";
import Register from "./pages/Register";
import Sent from "./pages/Sent";
import Settings from "./pages/Settings";
import Templates from "./pages/Templates";
import Unsubscribe from "./pages/Unsubscribe";
import Users from "./pages/Users";

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
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/unsubscribe" element={<Unsubscribe />} />
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
        path="/settings"
        element={
          <BizGuard>
            <Settings />
          </BizGuard>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
