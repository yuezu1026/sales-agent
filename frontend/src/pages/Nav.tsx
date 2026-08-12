import { useEffect, useRef } from "react";
import { NavLink } from "react-router-dom";
import { getRole } from "../api/client";

interface NavProps {
  current: string;
  onLogout: () => void;
}

/** 顶部导航（普通操作员不显示系统设置/用户管理） */
export function Nav({ current, onLogout }: NavProps) {
  const isAdmin = getRole() === "admin";
  /** 横向滚动容器 ref：切换页面后把当前激活项滚动到可视区 */
  const linksRef = useRef<HTMLDivElement>(null);

  // 手机端导航横向滚动时，自动把高亮的当前栏目滚动到可视区，
  // 避免点击「草稿箱/发件箱」等靠后栏目后看不到当前所在页的高亮项
  useEffect(() => {
    const links = linksRef.current;
    const active = links?.querySelector("a.active") as HTMLElement | null;
    if (!links || !active) return;
    const linksRect = links.getBoundingClientRect();
    const activeRect = active.getBoundingClientRect();
    const inView =
      activeRect.left >= linksRect.left - 0.5 &&
      activeRect.right <= linksRect.right + 0.5;
    // 激活项已在可视区内（工作台/客户管理等左侧栏目）则无需滚动
    if (inView) return;
    const target =
      links.scrollLeft +
      activeRect.left -
      linksRect.left -
      (linksRect.width - activeRect.width) / 2;
    // 延迟到布局稳定后直接赋值 scrollLeft（smooth/rAF 在后台标签页会被暂停，不可靠）
    const timer = window.setTimeout(() => {
      links.scrollLeft = Math.max(0, target);
    }, 100);
    return () => window.clearTimeout(timer);
  }, [current]);

  return (
    <>
      <div className="nav">
        {/* Logo 用普通 <a> 整页跳转到网站根路径 /（外层营销落地页），不经过应用内路由 */}
        <a href="/" className="nav-brand" title="返回首页">
          <img
            src={`${import.meta.env.BASE_URL}logo.svg`}
            alt="AI智能获客助手"
            className="nav-logo"
          />
        </a>
        <div className="nav-links" ref={linksRef}>
          <NavLink to="/" className={current === "dashboard" ? "active" : ""}>
            工作台
          </NavLink>
          <NavLink
            to="/customers"
            className={current === "customers" ? "active" : ""}
          >
            客户管理
          </NavLink>
          <NavLink
            to="/prospect"
            className={current === "prospect" ? "active" : ""}
          >
            潜客挖掘
          </NavLink>
          <NavLink
            to="/profile"
            className={current === "profile" ? "active" : ""}
          >
            客户画像
          </NavLink>
          <NavLink to="/inbox" className={current === "inbox" ? "active" : ""}>
            收件箱
          </NavLink>
          <NavLink
            to="/drafts"
            className={current === "drafts" ? "active" : ""}
          >
            草稿箱
          </NavLink>
          <NavLink to="/sent" className={current === "sent" ? "active" : ""}>
            发件箱
          </NavLink>
          <NavLink
            to="/templates"
            className={current === "templates" ? "active" : ""}
          >
            邮件模板
          </NavLink>
          {isAdmin && (
            <>
              <NavLink
                to="/users"
                className={current === "users" ? "active" : ""}
              >
                用户管理
              </NavLink>
              <NavLink
                to="/settings"
                className={current === "settings" ? "active" : ""}
              >
                系统设置
              </NavLink>
            </>
          )}
          {!isAdmin && (
            <NavLink
              to="/settings"
              className={current === "settings" ? "active" : ""}
            >
              个人设置
            </NavLink>
          )}
          <NavLink to="/help" className={current === "help" ? "active" : ""}>
            帮助
          </NavLink>
          <div className="spacer" />
          <span className="logout" onClick={onLogout}>
            退出登录
          </span>
        </div>
      </div>
    </>
  );
}
