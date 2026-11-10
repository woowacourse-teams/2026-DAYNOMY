import http from "k6/http";
import exec from "k6/execution";
import encoding from "k6/encoding";
import { check } from "k6";
import { SharedArray } from "k6/data";
import { Counter, Rate, Trend } from "k6/metrics";

// Existing Actions: script=loadtest/member-v1-load.js
// params={"API":"get","RPS":100,"VUS":50,"DURATION":"5m",
//         "MEMBER_ACCOUNTS_FILE":"/private/runner/accounts-member-v1.json"}
// The file must already exist on the runner; this script does not fetch Secrets.
// Local execution uses the same parameters and an absolute or relative file path.
const origin = (__ENV.BASE_URL || "").replace(/\/+$/, "");
const api = __ENV.API || "get";
const rps = Number(__ENV.RPS || 100);
const duration = __ENV.DURATION || "5m";
const match = /^([1-9]\d*)(s|m)$/.exec(duration);
const seconds = match ? Number(match[1]) * (match[2] === "m" ? 60 : 1) : 0;
const vus = Number(__ENV.VUS || Math.ceil(rps * 0.5));
const maxVus = Number(__ENV.MAX_VUS || vus);
const run = __ENV.RUN || "1";
const planned = rps * seconds;
const localHttp = /^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(origin);
if (
  (!/^https:\/\/[^\s/@:?#]+(?::\d+)?$/.test(origin) && !localHttp) ||
  !["get", "patch", "delete"].includes(api) ||
  ![rps, seconds, vus, maxVus, planned].every(Number.isSafeInteger) ||
  rps < 1 ||
  rps > 500 ||
  seconds < 10 ||
  seconds > 1800 ||
  vus < 1 ||
  maxVus !== vus ||
  vus > 1000 ||
  !/^[A-Za-z0-9_-]{1,40}$/.test(run)
) {
  throw new Error(
    "Check API=get|patch|delete, HTTPS origin, RPS (1–500), DURATION (10s–30m), fixed VUS=MAX_VUS and RUN. HTTP is allowed only on loopback for isolated verification.",
  );
}
// A lightweight metadata read is not retained in each VU after initialization.
const accounts = new SharedArray("member-v1-accounts", () => {
  if (!__ENV.MEMBER_ACCOUNTS_FILE)
    throw new Error(
      "Set MEMBER_ACCOUNTS_FILE to a private fixture file that exists on this machine/runner.",
    );
  let input;
  try {
    input = JSON.parse(open(__ENV.MEMBER_ACCOUNTS_FILE));
  } catch (_) {
    throw new Error(
      "Cannot read the member fixture. No token or file content is logged.",
    );
  }
  const rows = input?.accounts;
  const required = api === "delete" ? planned : 1000;
  if (
    input?.schemaVersion !== 1 ||
    input.api !== api ||
    input.baseUrl !== origin ||
    typeof input.issuer !== "string" ||
    !input.issuer ||
    !/^lt746-[a-z0-9-]{1,24}$/.test(input.namespace || "") ||
    !/^[a-z0-9]{6}$/.test(input.nicknamePrefix || "") ||
    !Array.isArray(rows) ||
    rows.length !== required + 1 ||
    rows[0]?.warmup !== true ||
    rows.slice(1).some((row) => row.warmup === true) ||
    new Set(rows.map((row) => row.memberId)).size !== rows.length ||
    new Set(rows.map((row) => row.accessToken)).size !== rows.length
  ) {
    throw new Error(
      `Prepare a ${api} fixture for this origin with ${required} distinct measurement members plus one separate warmup member. DELETE consumes a member per request.`,
    );
  }
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    let claims, header;
    try {
      const parts = row.accessToken.split(".");
      if (parts.length !== 3) throw new Error();
      header = JSON.parse(encoding.b64decode(parts[0], "rawurl", "s"));
      claims = JSON.parse(encoding.b64decode(parts[1], "rawurl", "s"));
    } catch (_) {
      throw new Error("Invalid member access token format.");
    }
    if (
      !Number.isSafeInteger(row.memberId) ||
      row.memberId < 1 ||
      row.testNumber !== i ||
      row.status !== "ACTIVE" ||
      row.role !== "USER" ||
      typeof row.email !== "string" ||
      !row.email.endsWith("@example.invalid") ||
      typeof row.nickname !== "string" ||
      !row.nickname ||
      !(row.name === null || typeof row.name === "string") ||
      header.alg !== "HS256" ||
      claims.sub !== String(row.memberId) ||
      claims.iss !== input.issuer ||
      claims.token_type !== "access" ||
      claims.role !== "USER" ||
      !Number.isFinite(claims.exp) ||
      claims.exp * 1000 <= Date.now() + (seconds + 150) * 1000
    ) {
      throw new Error(
        "Fixture must contain active test members and matching USER access tokens valid through setup and measurement.",
      );
    }
  }
  return rows;
});
// Parse once per test, not once per VU: large DELETE fixtures must stay shared.
const metadata = new SharedArray("member-v1-metadata", () => {
  try {
    const input = JSON.parse(open(__ENV.MEMBER_ACCOUNTS_FILE));
    return [
      { namespace: input.namespace, nicknamePrefix: input.nicknamePrefix },
    ];
  } catch (_) {
    throw new Error("Cannot read member fixture metadata.");
  }
});
const fixtureMeta = metadata[0];

const requests = new Counter("member_target_requests");
const errors = new Rate("member_target_http_errors");
const validation = new Rate("member_target_valid");
const latency = new Trend("member_target_duration_ms", true);
const postcheck = new Rate("member_postcheck_valid");
const path = "/api/v1/users/me";
const method = { get: "GET", patch: "PATCH", delete: "DELETE" }[api];
export const options = {
  scenarios: {
    member_v1: {
      executor: "constant-arrival-rate",
      rate: rps,
      timeUnit: "1s",
      duration,
      preAllocatedVUs: vus,
      maxVUs: vus,
      gracefulStop: "10s",
    },
  },
  setupTimeout: "60s",
  teardownTimeout: "30s",
  maxRedirects: 0,
  tags: {
    version: "v1",
    api: `member_${api}`,
    run,
    namespace: fixtureMeta.namespace,
  },
  summaryTrendStats: ["avg", "p(95)", "p(99)", "max"],
  thresholds: {
    member_target_requests: [`count==${planned}`],
    member_target_http_errors: ["rate==0"],
    member_target_valid: ["rate==1"],
    member_postcheck_valid: ["rate==1"],
    member_target_duration_ms: ["p(95)<=300", "p(99)<=500"],
    dropped_iterations: ["count==0"],
  },
};

function call(account, csrf, phase, operation = api, nickname = null) {
  const jar = new http.CookieJar();
  jar.set(origin, "access_token", account.accessToken, { path: "/" });
  if (csrf) jar.set(origin, "XSRF-TOKEN", csrf.cookie, { path: "/" });
  const headers = {};
  if (operation !== "get") {
    headers[csrf.headerName] = csrf.token;
    headers["Content-Type"] = "application/json";
  }
  const params = {
    jar,
    headers,
    redirects: 0,
    timeout: "10s",
    tags: { phase, name: `member_v1_${operation}` },
  };
  if (operation === "get") return http.get(`${origin}${path}`, params);
  if (operation === "patch")
    return http.patch(`${origin}${path}`, JSON.stringify({ nickname }), params);
  return http.del(`${origin}${path}`, null, params);
}
function profile(response, account, nickname = account.nickname) {
  try {
    const body = response.json();
    return (
      response.status === 200 &&
      body.id === account.memberId &&
      body.email === account.email &&
      body.name === account.name &&
      body.nickname === nickname &&
      body.role === account.role
    );
  } catch (_) {
    return false;
  }
}
function deleted(response) {
  const access = response.cookies?.access_token?.[0];
  const refresh = response.cookies?.refresh_token?.[0];
  // k6/Go normalizes the wire's Max-Age=0 to max_age=-1.
  return (
    response.status === 204 &&
    access?.value === "" &&
    access.path === "/" &&
    Number(access.max_age) === -1 &&
    refresh?.value === "" &&
    refresh.path === "/api/auth" &&
    Number(refresh.max_age) === -1
  );
}
function fail(message) {
  exec.test.abort(message);
  throw new Error(message);
}
function nicknameFor(account, index) {
  return `${fixtureMeta.nicknamePrefix}_${account.testNumber}_${index.toString(36)}`;
}
export function setup() {
  let csrf = null;
  if (api !== "get") {
    const response = http.get(`${origin}/api/auth/csrf`, {
      jar: new http.CookieJar(),
      redirects: 0,
      timeout: "10s",
      tags: { phase: "prepare", name: "member_csrf" },
    });
    let body;
    try {
      body = response.json();
    } catch (_) {
      /* Fail without logging authentication material. */
    }
    const cookie = response.cookies?.["XSRF-TOKEN"]?.[0]?.value;
    if (response.status !== 200 || !body?.token || !body?.headerName || !cookie)
      fail("Member CSRF preparation failed.");
    csrf = { token: body.token, headerName: body.headerName, cookie };
  }
  // Check identity and starting nickname before mutations; never log response bodies.
  for (const index of [0, 1, accounts.length - 1]) {
    if (
      !profile(call(accounts[index], null, "prepare", "get"), accounts[index])
    )
      fail(
        "Member preflight failed. Check active members, signatures, baseline nickname and deployment.",
      );
  }
  const warmup = accounts[0];
  if (api === "patch") {
    const nickname = nicknameFor(warmup, planned);
    if (
      nickname.length > 20 ||
      nickname === warmup.nickname ||
      !profile(
        call(warmup, csrf, "warmup", "patch", nickname),
        warmup,
        nickname,
      ) ||
      !profile(call(warmup, null, "verification", "get"), warmup, nickname)
    )
      fail("Member PATCH warmup or persisted nickname verification failed.");
  } else if (api === "delete") {
    if (
      !deleted(call(warmup, csrf, "warmup", "delete")) ||
      call(warmup, null, "verification", "get").status !== 401
    )
      fail(
        "Member DELETE warmup failed: expected deleted cookies and rejected withdrawn access token.",
      );
  }
  return { csrf };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  if (index >= planned) return;
  let offset;
  if (api === "delete") offset = index;
  else if (api === "get") offset = index % (accounts.length - 1);
  else {
    // Uneven partitions (e.g. 1000 members / 150 VUs) must never overlap.
    const vu = exec.vu.idInTest - 1;
    if (vu < 0 || vu >= vus)
      fail("Use a single k6 process with the configured fixed VU count.");
    const owned = Math.floor((accounts.length - 2 - vu) / vus) + 1;
    offset = vu + (__ITER % owned) * vus;
  }
  const account = accounts[offset + 1];
  if (!account) fail("No unused test member available for this request.");
  const nickname = api === "patch" ? nicknameFor(account, index) : null;
  if (nickname && (nickname.length > 20 || nickname === account.nickname))
    fail("Invalid generated nickname or unchanged baseline.");
  const response = call(account, data.csrf, "target", api, nickname);
  requests.add(1);
  latency.add(response.timings.duration);
  errors.add(response.status !== (api === "delete" ? 204 : 200));
  validation.add(
    check(
      response,
      {
        "Member response contract": () =>
          api === "delete"
            ? deleted(response)
            : profile(response, account, nickname || account.nickname),
      },
      { phase: "target" },
    ),
  );
}

export function teardown() {
  // Warmup and the first measured member are sufficient HTTP probes; full DB
  // state/token counts are checked with prepare-member-v1.py's verification SQL.
  if (api === "delete")
    postcheck.add(
      [0, 1].every(
        (i) => call(accounts[i], null, "verification", "get").status === 401,
      ),
    );
  else postcheck.add(true);
}
export function handleSummary(data) {
  const value = (name, key) => data.metrics[name]?.values?.[key];
  const actual = value("member_target_requests", "count") || 0;
  const errorRate = value("member_target_http_errors", "rate");
  const checkRate = value("member_target_valid", "rate");
  const integrityRate = value("member_postcheck_valid", "rate");
  const p95 = value("member_target_duration_ms", "p(95)");
  const p99 = value("member_target_duration_ms", "p(99)");
  const dropped = value("dropped_iterations", "count") || 0;
  const outcome =
    actual === planned &&
    errorRate === 0 &&
    checkRate === 1 &&
    integrityRate === 1 &&
    Number.isFinite(p95) &&
    p95 <= 300 &&
    Number.isFinite(p99) &&
    p99 <= 500 &&
    dropped === 0
      ? "PASS"
      : "FAIL";
  const result = {
    api: `${method} ${path}`,
    namespace: fixtureMeta.namespace,
    run,
    version: "v1",
    targetRps: rps,
    duration,
    vus,
    maxVus: vus,
    plannedRequests: planned,
    actualRequests: actual,
    achievedRps: actual / seconds,
    p95,
    p99,
    errorRate,
    checkRate,
    integrityRate,
    droppedIterations: dropped,
    outcome,
    measurementMembers: accounts.length - 1,
  };
  const percent = (v) =>
    Number.isFinite(v) ? `${(v * 100).toFixed(2)}%` : "N/A";
  const ms = (v) => (Number.isFinite(v) ? `${v.toFixed(2)}ms` : "N/A");
  const filename = `result-member-v1-${api}-${rps}rps-${run}.json`;
  return {
    stdout: [
      `- 대상: ${result.api}`,
      `- 요청: ${actual} / ${planned}건`,
      `- 실제 처리량: ${result.achievedRps.toFixed(2)} RPS`,
      `- p95: ${ms(p95)}`,
      `- p99: ${ms(p99)}`,
      `- HTTP 오류: ${percent(errorRate)}`,
      `- 응답 검증: ${percent(checkRate)}`,
      `- dropped: ${dropped}건`,
      `- 판정: ${outcome}`,
      "",
    ].join("\n"),
    [filename]: JSON.stringify(result, null, 2),
  };
}
