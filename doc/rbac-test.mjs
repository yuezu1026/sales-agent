/* RBAC 三级角色 API 验证脚本（Node 18+ fetch） */
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

const out = [];
function log(s) {
  out.push(s);
  console.log(s);
}

(async () => {
  // 1. 登录已注册的租户管理员 rbac_a（普通管理员）
  const reg = await call("POST", "/auth/login", null, {
    username: "rbac_a",
    password: "rbac123456",
  });
  const data = reg.json?.data;
  if (reg.status !== 200 || !data?.token) {
    log(`1. 登录 rbac_a 失败: HTTP ${reg.status} ${reg.json?.message}`);
    return;
  }
  const tokA = data.token;
  const tenantId = data.tenantId;
  log(`1. 注册租户管理员 rbac_a tenantId=${tenantId} role=${data.role}`);

  // 2. 系统管理员登录
  const s = await call("POST", "/auth/login", null, {
    username: "admin",
    password: "Admin@123456",
  });
  const tokS = s.json.data.token;
  log(
    `2. 系统管理员 admin 登录 OK role=${s.json.data.role} tenantId=${s.json.data.tenantId}`,
  );

  // 3. 系统管理员看所有租户用户（含 rbac_a 与租户名）
  const all = await call("GET", "/users", tokS);
  const hit = all.json.data.find((u) => u.username === "rbac_a");
  log(
    `3. 系统管理员看用户数=${all.json.data.length}  含 rbac_a: ${hit ? `${hit.role}/tenant=${hit.tenantName}` : "未找到!"}`,
  );

  // 4. 系统管理员创建系统管理员
  const r4 = await call("POST", "/users", tokS, {
    username: "sys2",
    password: "sys123456",
    displayName: "Sys2",
    role: "admin",
  });
  log(
    `4. S创建系统管理员 sys2 => HTTP ${r4.status} ${r4.json?.message || ""} role=${r4.json?.data?.role || "-"}`,
  );

  // 5. 系统管理员创建平台普通用户
  const r5 = await call("POST", "/users", tokS, {
    username: "plat_user",
    password: "plat123456",
    displayName: "Plat",
    role: "operator",
  });
  log(
    `5. S创建平台普通用户 plat_user => HTTP ${r5.status} tenantId=${r5.json?.data?.tenantId ?? "null"}`,
  );

  // 6. 租户管理员看本租户用户
  const usersA = await call("GET", "/users", tokA);
  log(
    `6. 租户管理员看用户数=${usersA.json.data.length} 用户名=${usersA.json.data.map((u) => u.username).join(",")}`,
  );

  // 7. 租户管理员创建本租户普通用户
  const r7 = await call("POST", "/users", tokA, {
    username: "member1",
    password: "member1234",
    displayName: "Member1",
    role: "operator",
  });
  log(
    `7. A创建普通用户 member1 => HTTP ${r7.status} tenantId=${r7.json?.data?.tenantId}`,
  );

  // 8. 租户管理员尝试创建管理员（提权防护）
  const r8 = await call("POST", "/users", tokA, {
    username: "evil_admin",
    password: "evil123456",
    role: "admin",
  });
  log(
    `8. A创建管理员 evil_admin => HTTP ${r8.status} ${r8.json?.message || ""}`,
  );

  // 9. 租户管理员禁用本租户普通用户
  const member1 = usersA.json.data.find((u) => u.username === "member1");
  const r9 = await call("PUT", `/users/${member1.id}/status`, tokA, {
    status: "disabled",
  });
  log(`9. A禁用 member1 => HTTP ${r9.status} ${r9.json?.message || ""}`);

  // 10. 租户管理员操作跨租户用户（sys2 平台系统管理员）
  const sys2 = all.json.data.find((u) => u.username === "sys2");
  const r10 = await call("PUT", `/users/${sys2.id}/status`, tokA, {
    status: "disabled",
  });
  log(`10. A禁跨租户 sys2 => HTTP ${r10.status} ${r10.json?.message || ""}`);

  // 11. 租户管理员禁自己（租户管理员）
  const rbac = usersA.json.data.find((u) => u.username === "rbac_a");
  const r11 = await call("PUT", `/users/${rbac.id}/status`, tokA, {
    status: "disabled",
  });
  log(`11. A禁自己(管理员) => HTTP ${r11.status} ${r11.json?.message || ""}`);

  // 12. 普通用户访问用户管理
  const m = await call("POST", "/auth/login", null, {
    username: "member1",
    password: "member1234",
  });
  const r12 = await call("GET", "/users", m.json.data.token);
  log(
    `12. 普通用户 member1 GET /users => HTTP ${r12.status} ${r12.json?.message || ""}`,
  );

  // 13. 系统管理员禁用/恢复租户管理员
  const r13 = await call("PUT", `/users/${rbac.id}/status`, tokS, {
    status: "disabled",
  });
  const r13b = await call("PUT", `/users/${rbac.id}/status`, tokS, {
    status: "active",
  });
  log(
    `13. S禁用租户管理员 => HTTP ${r13.status} ${r13.json?.message || ""}  恢复 => HTTP ${r13b.status}`,
  );

  // 14. 系统管理员不能禁用其他系统管理员
  const r14 = await call("PUT", `/users/${sys2.id}/status`, tokS, {
    status: "disabled",
  });
  log(
    `14. S禁其他系统管理员 sys2 => HTTP ${r14.status} ${r14.json?.message || ""}`,
  );
})().catch((e) => console.error("脚本异常:", e.message));
