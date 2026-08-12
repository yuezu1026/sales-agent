// 验证系统管理员（平台级）无法访问/操作租户业务数据（后端 TenantContext.require 拦截）
// 运行：node doc/rbac-sysadmin-api.mjs
const BASE = "http://localhost/api";

async function login(username, password) {
  const resp = await fetch(`${BASE}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  const json = await resp.json();
  if (json.code !== 0) throw new Error(`登录失败 ${username}: ${json.message}`);
  return json.data.token;
}

const sysToken = await login("admin", "Admin@123456");
const H = {
  Authorization: `Bearer ${sysToken}`,
  "Content-Type": "application/json",
};

const cases = [
  ["GET  /leads                 (看租户客户列表)", "GET", "/leads"],
  ["GET  /emails/inbox          (看租户收件箱)", "GET", "/emails/inbox"],
  ["GET  /email-drafts          (看租户草稿箱)", "GET", "/email-drafts"],
  ["GET  /config                (看租户系统配置)", "GET", "/config"],
  ["POST /emails/inbox/sync     (替租户收信)", "POST", "/emails/inbox/sync"],
  [
    "POST /leads                 (替租户创建客户)",
    "POST",
    "/leads",
    { companyName: "测试", contactEmail: "t@t.com" },
  ],
  [
    "POST /prospect/search       (替租户挖潜客)",
    "POST",
    "/prospect/search",
    { keyword: "x" },
  ],
];

let pass = 0;
for (const [name, method, path, body] of cases) {
  const resp = await fetch(BASE + path, {
    method,
    headers: H,
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await resp.json().catch(() => ({}));
  const ok = resp.status === 400 && /无租户上下文/.test(json.message || "");
  console.log(
    `${ok ? "✅" : "❌"} ${name} → ${resp.status} ${json.message || ""}`,
  );
  if (ok) pass++;
}
console.log(`\n${pass}/${cases.length} 项通过（预期全部 400 拒绝）`);
