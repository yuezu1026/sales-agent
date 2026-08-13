// M8.8 验证：三个演示账号登录（线上 sales-agent.top）
// 用法：node doc/m88-login-test.mjs
const BASE = "https://sales-agent.top";

const accounts = [
  {
    name: "系统管理员 admin",
    username: "admin",
    password: "Admin@123456",
    expectRole: "admin",
    expectTenant: 0,
  },
  {
    name: "租户管理员 demo_admin",
    username: "demo_admin",
    password: "Demo@123456",
    expectRole: "admin",
    expectTenant: "number",
  },
  {
    name: "普通用户 demo_user",
    username: "demo_user",
    password: "Demo@123456",
    expectRole: "operator",
    expectTenant: "number",
  },
];

let pass = 0,
  fail = 0;

for (const a of accounts) {
  try {
    const res = await fetch(`${BASE}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: a.username, password: a.password }),
    });
    const data = await res.json();
    const ok = res.status === 200 && data.code === 0 && data.data?.token;
    const roleOk = data.data?.role === a.expectRole;
    const tenantOk =
      a.expectTenant === null
        ? data.data?.tenantId === null || data.data?.tenantId === undefined
        : typeof a.expectTenant === "number"
          ? data.data?.tenantId === a.expectTenant
          : typeof data.data?.tenantId === a.expectTenant;
    if (ok && roleOk && tenantOk) {
      console.log(
        `✅ ${a.name} 登录成功 role=${data.data.role} tenantId=${data.data.tenantId}`,
      );
      pass++;
    } else {
      console.log(
        `❌ ${a.name} status=${res.status} role=${data.data?.role} tenantId=${data.data?.tenantId} (期望 role=${a.expectRole})`,
      );
      fail++;
    }
  } catch (e) {
    console.log(`❌ ${a.name} 异常: ${e.message}`);
    fail++;
  }
}

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail > 0 ? 1 : 0);
