const TOKEN_KEY = "aic_token";
const ROLE_KEY = "aic_role";
const TENANT_KEY = "aic_tenant";
const REMEMBER_KEY = "aic_remember";

/**
 * 根据"记住我"选择存储位置：
 * - 勾选 → localStorage（持久，关浏览器后仍保持登录）
 * - 不勾选 → sessionStorage（会话级，关浏览器后需重新登录）
 */
function pickStore(): Storage {
  return localStorage.getItem(REMEMBER_KEY) === "1"
    ? localStorage
    : sessionStorage;
}

interface ApiOptions extends RequestInit {
  /** true 时 401 不跳转登录页，把错误抛给调用方（用于登录接口自身） */
  skipAuthRedirect?: boolean;
}

/** 统一 API 请求封装：自动携带 JWT，统一错误提示 */
export async function api<T>(
  path: string,
  options: ApiOptions = {},
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  // cache: "no-store" 防止浏览器启发式缓存 API 响应（Spring 默认无 Cache-Control，
  // 后端重启/异常期间的 401/500 响应可能被缓存，导致后续请求命中旧响应而看不到最新数据）
  const resp = await fetch(`/api${path}`, {
    ...options,
    headers,
    cache: "no-store",
  });

  // 401 未登录 → 跳转登录页（登录接口自身除外，避免密码错误也被重定向）
  // 注意：必须带 /app 前缀！window.location 是浏览器原生跳转，不走 React Router 的 basename，
  // 若用 /login 会被外层 nginx 宣传站 try_files 吞掉落到宣传首页（M7.15）
  if (resp.status === 401 && !options.skipAuthRedirect) {
    clearToken();
    window.location.href = "/app/login";
    throw new Error("未登录或登录已过期");
  }

  const body = await resp.json();
  if (!resp.ok || body.code !== 0) {
    throw new Error(body.message || "请求失败");
  }
  return body.data as T;
}

/** 登录成功后保存 token（remember=true 持久保存，false 仅本次会话有效） */
export function setToken(token: string, remember = true) {
  localStorage.setItem(REMEMBER_KEY, remember ? "1" : "0");
  pickStore().setItem(TOKEN_KEY, token);
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY) ?? sessionStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  sessionStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ROLE_KEY);
  sessionStorage.removeItem(ROLE_KEY);
  localStorage.removeItem(TENANT_KEY);
  sessionStorage.removeItem(TENANT_KEY);
}

/** 仅清除角色（一般无需单独调用，clearToken 已包含） */
export function clearRole() {
  localStorage.removeItem(ROLE_KEY);
  sessionStorage.removeItem(ROLE_KEY);
}

export function getRole(): string | null {
  return localStorage.getItem(ROLE_KEY) ?? sessionStorage.getItem(ROLE_KEY);
}

/** 保存角色（与 token 同存储位置，保持一致） */
export function setRole(role: string) {
  pickStore().setItem(ROLE_KEY, role);
}

/** 保存租户 id（0/null = 系统管理员平台账号，无租户） */
export function setTenantId(tenantId: number | null | undefined) {
  pickStore().setItem(TENANT_KEY, tenantId == null ? "0" : String(tenantId));
}

/** 当前登录账号的租户 id；0 = 系统管理员（平台级） */
export function getTenantId(): number {
  const v =
    localStorage.getItem(TENANT_KEY) ?? sessionStorage.getItem(TENANT_KEY);
  return v == null ? 0 : Number(v);
}

/** 当前登录账号是否为系统管理员（平台级，无租户） */
export function isSystemAdmin(): boolean {
  return getRole() === "admin" && getTenantId() === 0;
}

/** 上次登录是否勾选了"记住我"（默认 true，便于新用户默认免登录） */
export function getRemember(): boolean {
  return localStorage.getItem(REMEMBER_KEY) !== "0";
}
