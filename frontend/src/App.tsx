import { Navigate, Route, Routes } from "react-router-dom";
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

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/unsubscribe" element={<Unsubscribe />} />
      <Route path="/" element={<Dashboard />} />
      <Route path="/customers" element={<Customers />} />
      <Route path="/prospect" element={<Prospect />} />
      <Route path="/profile" element={<Profile />} />
      <Route path="/inbox" element={<Inbox />} />
      <Route path="/drafts" element={<Drafts />} />
      <Route path="/sent" element={<Sent />} />
      <Route path="/templates" element={<Templates />} />
      <Route path="/users" element={<Users />} />
      <Route path="/settings" element={<Settings />} />
      <Route path="/help" element={<Help />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
