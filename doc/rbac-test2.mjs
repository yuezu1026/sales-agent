/* RBAC 补充验证：普通用户权限 / 系统管理员跨租户 / 系统管理员互禁 */
const base = "http://localhost:8080/api";

async function call(method, path, token, body) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  const resp = await fetch(base + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await resp.text();
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {}
  return { status: resp.status, json };
}
const log = (s) => console.log(s);

(async () => {
  const s = await call("POST", "/auth/login", null, {
    username: "admin",
    password: "Admin@123456",
  });
  const tokS = s.json.data.token;

  // 先启用 member1
  const r0 = await call("PUT", "/users/7/status", tokS, { status: "active" });
  log(`0. S启用 member1 => HTTP ${r0.status} ${r0.json?.message || ""}`);

  // 12. 普通用户访问用户管理
  const m = await call("POST", "/auth/login", null, {
    username: "member1",
    password: "member1234",
  });
  log(`12. member1 登录 => HTTP ${m.status} role=${m.json?.data?.role}`);
  const r12 = await call("GET", "/users", m.json.data.token);
  log(
    `12. 普通用户 GET /users => HTTP ${r12.status} ${r12.json?.message || ""}`,
  );

  // 12b. 普通用户访问系统设置（业务接口校验权限）
  const r12b = await call("GET", "/settings", m.json.data.token);
  log(
    `12b. 普通用户 GET /settings => HTTP ${r12b.status} ${r12b.json?.message || ""}`,
  );

  // 13. 系统管理员禁用/恢复租户管理员 rbac_a(id=4)
  const r13 = await call("PUT", "/users/4/status", tokS, {
    status: "disabled",
  });
  const r13b = await call("PUT", "/users/4/status", tokS, { status: "active" });
  log(
    `13. S禁用租户管理员 rbac_a => HTTP ${r13.status} ${r13.json?.message || ""}  恢复 => HTTP ${r13b.status}`,
  );

  // 14. 系统管理员不能禁用其他系统管理员 sys2(id=5)
  const r14 = await call("PUT", "/users/5/status", tokS, {
    status: "disabled",
  });
  log(
    `14. S禁其他系统管理员 sys2 => HTTP ${r14.status} ${r14.json?.message || ""}`,
  );

  // 15. 系统管理员重置租户管理员密码（跨租户重置）
  const r15 = await call("PUT", "/users/4/password", tokS, {
    newPassword: "rbac654321",
  });
  log(
    `15. S重置租户管理员 rbac_a 密码 => HTTP ${r15.status} ${r15.json?.message || ""}`,
  );

  // 15b. 用新密码登录验证
  const r15b = await call("POST", "/auth/login", null, {
    username: "rbac_a",
    password: "rbac654321",
  });
  log(
    `15b. rbac_a 新密码登录 => HTTP ${r15b.status} ${r15b.json?.message || ""}`,
  );

  // 16. 普通管理员重置本租户管理员密码（越权）
  const tokA = r15b.json.data.token;
  const r16 = await call("PUT", "/users/4/password", tokA, {
    newPassword: "rbac999999",
  });
  log(
    `16. A重置自己(管理员)密码 => HTTP ${r16.status} ${r16.json?.message || ""}`,
  );

  // 17. 普通管理员重置本租户普通用户密码（允许）
  const r17 = await call("PUT", "/users/7/password", tokA, {
    newPassword: "member4321",
  });
  log(
    `17. A重置 member1 密码 => HTTP ${r17.status} ${r17.json?.message || ""}`,
  );
  const r17b = await call("POST", "/auth/login", null, {
    username: "member1",
    password: "member4321",
  });
  log(
    `17b. member1 新密码登录 => HTTP ${r17b.status} ${r17b.json?.message || ""}`,
  );
})().catch((e) => console.error("脚本异常:", e.message));
