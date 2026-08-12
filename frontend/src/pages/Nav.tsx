import { useEffect, useRef, useState } from "react";
import { NavLink } from "react-router-dom";
import { getRole, isSystemAdmin } from "../api/client";

interface NavProps {
  current: string;
  onLogout: () => void;
}

/** 邮件管理下拉子菜单项（租户用户全员可见） */
const MAIL_ITEMS = [
  { to: "/inbox", key: "inbox", label: "收件箱" },
  { to: "/drafts", key: "drafts", label: "草稿箱" },
  { to: "/sent", key: "sent", label: "发件箱" },
  { to: "/templates", key: "templates", label: "邮件模板" },
];

/** 邮件管理下拉子菜单项（仅租户管理员可见） */
const MAIL_ITEMS_ADMIN = [{ to: "/unsubs", key: "unsubs", label: "退订管理" }];

/**
 * 顶部导航
 * - 系统管理员（平台级）：工作台（仅登录统计/地理分布）+ 用户管理 + 帮助
 * - 普通管理员（租户级）：业务菜单 + 用户管理 + 系统设置
 * - 普通用户：业务菜单 + 个人设置
 */
export function Nav({ current, onLogout }: NavProps) {
  const isAdmin = getRole() === "admin";
  const sysAdmin = isSystemAdmin();
  /** 横向滚动容器 ref：切换页面后把当前激活项滚动到可视区 */
  const linksRef = useRef<HTMLDivElement>(null);

  /** 邮件管理下拉：展开状态 / 按钮与菜单 ref / 收起计时器 */
  const [mailOpen, setMailOpen] = useState(false);
  const mailBtnRef = useRef<HTMLDivElement>(null);
  const mailMenuRef = useRef<HTMLDivElement>(null);
  const closeTimerRef = useRef<number | null>(null);
  const mailActive =
    MAIL_ITEMS.some((it) => it.key === current) ||
    (isAdmin && !sysAdmin && MAIL_ITEMS_ADMIN.some((it) => it.key === current));

  /** 展开邮件菜单（取消待执行的收起） */
  const openMail = () => {
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    setMailOpen(true);
  };

  /** 延迟收起：鼠标移出按钮/菜单后留出移动时间，避免闪烁 */
  const scheduleCloseMail = () => {
    if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    closeTimerRef.current = window.setTimeout(() => setMailOpen(false), 150);
  };

  // 菜单 fixed 定位到按钮下方；右溢出视口时向左收
  useEffect(() => {
    if (!mailOpen) return;
    const btn = mailBtnRef.current;
    const menu = mailMenuRef.current;
    if (!btn || !menu) return;
    const rect = btn.getBoundingClientRect();
    const mw = menu.offsetWidth;
    const left =
      rect.left + mw > window.innerWidth
        ? Math.max(8, window.innerWidth - mw - 8)
        : rect.left;
    menu.style.left = `${left}px`;
    menu.style.top = `${rect.bottom}px`;
  }, [mailOpen]);

  // 点击导航/页面其他区域时收起（移动端无 hover 的兜底）
  useEffect(() => {
    if (!mailOpen) return;
    const onDocClick = (e: MouseEvent) => {
      const t = e.target as Node;
      if (mailBtnRef.current?.contains(t) || mailMenuRef.current?.contains(t)) {
        return;
      }
      setMailOpen(false);
    };
    document.addEventListener("click", onDocClick);
    return () => document.removeEventListener("click", onDocClick);
  }, [mailOpen]);

  // 卸载时清理收起计时器
  useEffect(
    () => () => {
      if (closeTimerRef.current) window.clearTimeout(closeTimerRef.current);
    },
    [],
  );

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
            alt="拾客 Shike"
            className="nav-logo"
          />
        </a>
        <div className="nav-links" ref={linksRef}>
          {sysAdmin ? (
            <>
              <NavLink
                to="/"
                className={current === "dashboard" ? "active" : ""}
              >
                工作台
              </NavLink>
              <NavLink
                to="/users"
                className={current === "users" ? "active" : ""}
              >
                用户管理
              </NavLink>
              <NavLink
                to="/help"
                className={current === "help" ? "active" : ""}
              >
                帮助
              </NavLink>
            </>
          ) : (
            <>
              <NavLink
                to="/"
                className={current === "dashboard" ? "active" : ""}
              >
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
              <div
                className={`nav-dropdown${mailActive ? " active" : ""}`}
                ref={mailBtnRef}
                onMouseEnter={openMail}
                onMouseLeave={scheduleCloseMail}
                onClick={(e) => {
                  // 只负责打开：mouseenter/click 都是 discrete 事件会各自同步 flush，
                  // 若此处 toggle，mouseenter 先打开并重渲染，click 拿到新闭包又把它关掉
                  e.stopPropagation();
                  openMail();
                }}
              >
                <span className="nav-dropdown-title">
                  邮件管理<span className="nav-caret">▾</span>
                </span>
              </div>
              {mailOpen && (
                <div
                  className="nav-dropdown-menu"
                  ref={mailMenuRef}
                  onMouseEnter={openMail}
                  onMouseLeave={scheduleCloseMail}
                >
                  {MAIL_ITEMS.map((it) => (
                    <NavLink
                      key={it.key}
                      to={it.to}
                      className={current === it.key ? "active" : ""}
                      onClick={() => setMailOpen(false)}
                    >
                      {it.label}
                    </NavLink>
                  ))}
                  {isAdmin &&
                    !sysAdmin &&
                    MAIL_ITEMS_ADMIN.map((it) => (
                      <NavLink
                        key={it.key}
                        to={it.to}
                        className={current === it.key ? "active" : ""}
                        onClick={() => setMailOpen(false)}
                      >
                        {it.label}
                      </NavLink>
                    ))}
                </div>
              )}
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
              <NavLink
                to="/help"
                className={current === "help" ? "active" : ""}
              >
                帮助
              </NavLink>
            </>
          )}
          <div className="spacer" />
          <span className="logout" onClick={onLogout}>
            退出登录
          </span>
        </div>
      </div>
    </>
  );
}
