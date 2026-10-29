import http from "k6/http";
import exec from "k6/execution";
import encoding from "k6/encoding";
import { check } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";
import { SharedArray } from "k6/data";

const origin = (__ENV.BASE_URL || "").replace(/\/+$/, "");
const api = __ENV.API || "google";
const rps = Number(__ENV.RPS || 100);
const duration = __ENV.DURATION || "5m";
const match = /^([1-9]\d*)(s|m)$/.exec(duration);
const seconds = match ? Number(match[1]) * (match[2] === "m" ? 60 : 1) : 0;
const vus = Number(__ENV.VUS || Math.ceil(rps * 0.5));
const maxVus = Number(__ENV.MAX_VUS || rps);
const run = __ENV.RUN || "1";
const planned = rps * seconds;
if (
  !/^https:\/\/[^\s/@:?#]+(?::\d+)?$/.test(origin) ||
  !["google", "refresh", "logout"].includes(api) ||
  ![rps, seconds, vus, maxVus, planned].every(Number.isSafeInteger) ||
  rps < 1 ||
  seconds < 10 ||
  seconds > 1800 ||
  vus < 1 ||
  maxVus < vus ||
  !/^[A-Za-z0-9._-]+$/.test(run)
) {
  throw new Error(
    "Check HTTPS BASE_URL origin, API, RPS, DURATION (10s–30m), VUS, MAX_VUS and RUN.",
  );
}

// Never read or print analysis account files. Auth fixtures contain refresh tokens.
const fixture = new SharedArray("auth-v1-sessions", () => {
  if (api === "google") return [];
  if (!__ENV.AUTH_ACCOUNTS_FILE)
    throw new Error(
      "Set AUTH_ACCOUNTS_FILE to a private Auth session JSON file.",
    );
  const rows = JSON.parse(open(__ENV.AUTH_ACCOUNTS_FILE));
  if (!Array.isArray(rows))
    throw new Error("Auth sessions must be a JSON array.");
  const sessions = rows.filter((row) => row.warmup !== true);
  const warmups = rows.filter((row) => row.warmup === true);
  const needed = api === "logout" ? planned : maxVus;
  if (
    warmups.length !== 1 ||
    sessions.length < needed ||
    rows.some(
      (row) =>
        !Number.isSafeInteger(row.memberId) ||
        row.memberId < 1 ||
        typeof row.refreshToken !== "string" ||
        row.refreshToken.split(".").length !== 3,
    ) ||
    new Set(rows.map((row) => row.refreshToken)).size !== rows.length
  ) {
    throw new Error(
      `Prepare ${needed} distinct measurement refresh tokens and one separate warmup token. Logout consumes one token per request.`,
    );
  }
  // Reject expired fixtures before sending HTTP. Signature validity is checked by the server.
  for (const row of rows) {
    let claims;
    try {
      claims = JSON.parse(
        encoding.b64decode(row.refreshToken.split(".")[1], "rawurl", "s"),
      );
    } catch (_) {
      throw new Error("Invalid Auth token format. No token values are logged.");
    }
    if (
      !Number.isFinite(claims?.exp) ||
      claims.exp * 1000 <= Date.now() + (seconds + 60) * 1000
    ) {
      throw new Error(
        "Auth fixture tokens must remain valid through the measurement plus 60 seconds.",
      );
    }
  }
  return [warmups[0], ...sessions];
});

const requests = new Counter("auth_target_requests");
const errors = new Rate("auth_target_http_errors");
const valid = new Rate("auth_target_valid");
const latency = new Trend("auth_target_duration_ms", true);
const path = `/api/v1/auth/${api}`;
const method = api === "google" ? "GET" : "POST";
export const options = {
  scenarios: {
    auth_v1: {
      executor: "constant-arrival-rate",
      rate: rps,
      timeUnit: "1s",
      duration,
      preAllocatedVUs: vus,
      maxVUs: maxVus,
      gracefulStop: "10s",
    },
  },
  setupTimeout: "30s",
  maxRedirects: 0,
  tags: { version: "v1", api: `auth_${api}`, run },
  summaryTrendStats: ["avg", "p(95)", "p(99)", "max"],
  thresholds: {
    auth_target_requests: [`count==${planned}`],
    auth_target_http_errors: ["rate==0"],
    auth_target_valid: ["rate==1"],
    auth_target_duration_ms: ["p(95)<=300", "p(99)<=500"],
    dropped_iterations: ["count==0"],
  },
};

function cookie(response, name) {
  return response.cookies?.[name]?.[0];
}
function request(token, csrf, phase) {
  // Use an isolated jar: rotated or deleted cookies cannot leak between planned sessions.
  const jar = new http.CookieJar();
  if (token) jar.set(origin, "refresh_token", token, { path: "/api/v1/auth" });
  if (csrf) jar.set(origin, "XSRF-TOKEN", csrf.cookie, { path: "/" });
  const params = {
    jar,
    redirects: 0,
    timeout: "10s",
    headers: csrf ? { [csrf.headerName]: csrf.token } : {},
    tags: { phase, name: `auth_v1_${api}` },
  };
  return api === "google"
    ? http.get(`${origin}${path}`, params)
    : http.post(`${origin}${path}`, null, params);
}
function contract(response, previousToken) {
  if (api === "google")
    return (
      response.status === 302 &&
      response.headers.Location === "/oauth2/authorization/google"
    );
  const access = cookie(response, "access_token");
  const refresh = cookie(response, "refresh_token");
  if (
    response.status !== 204 ||
    !access ||
    !refresh ||
    access.path !== "/" ||
    refresh.path !== "/api/v1/auth"
  )
    return false;
  return api === "refresh"
    ? !!access.value && !!refresh.value && refresh.value !== previousToken
    : access.value === "" &&
        refresh.value === "" &&
        Number(access.max_age) === 0 &&
        Number(refresh.max_age) === 0;
}
export function setup() {
  let csrf = null;
  if (api !== "google") {
    const response = http.get(`${origin}/api/auth/csrf`, {
      jar: new http.CookieJar(),
      redirects: 0,
      timeout: "10s",
      tags: { phase: "prepare", name: "auth_csrf" },
    });
    let body;
    try {
      body = response.json();
    } catch (_) {
      /* Fail without logging responses. */
    }
    const csrfCookie = cookie(response, "XSRF-TOKEN");
    if (
      response.status !== 200 ||
      !body?.token ||
      !body?.headerName ||
      !csrfCookie?.value
    ) {
      throw new Error("Auth CSRF preparation failed.");
    }
    csrf = {
      token: body.token,
      headerName: body.headerName,
      cookie: csrfCookie.value,
    };
  }
  const token = fixture[0]?.refreshToken;
  const response = request(token, csrf, "warmup");
  if (!contract(response, token))
    throw new Error(
      "Auth warmup failed: verify deployment, token and cookie contract.",
    );
  return { csrf };
}

// Module state is VU-local. Each refresh VU owns a distinct rotating session.
let currentToken;
export default function (data) {
  const index = exec.scenario.iterationInTest;
  if (index >= planned) return;
  if (api === "refresh" && !currentToken) {
    const session = fixture[exec.vu.idInTest];
    if (!session)
      exec.test.abort("No isolated refresh session available for this VU.");
    currentToken = session.refreshToken;
  }
  const token =
    api === "logout" ? fixture[index + 1].refreshToken : currentToken;
  const response = request(token, data.csrf, "target");
  requests.add(1);
  latency.add(response.timings.duration);
  errors.add(response.status !== (api === "google" ? 302 : 204));
  const passed = check(
    response,
    { "Auth response contract": () => contract(response, token) },
    { phase: "target" },
  );
  valid.add(passed);
  if (api === "refresh") {
    if (!passed)
      exec.test.abort(
        "Refresh failed; stop to avoid replaying a possibly consumed token. Restore sessions before retrying.",
      );
    currentToken = cookie(response, "refresh_token").value;
  }
}

export function handleSummary(data) {
  const value = (name, key) => data.metrics[name]?.values?.[key];
  const actual = value("auth_target_requests", "count") || 0;
  const p95 = value("auth_target_duration_ms", "p(95)");
  const p99 = value("auth_target_duration_ms", "p(99)");
  const errorRate = value("auth_target_http_errors", "rate");
  const checkRate = value("auth_target_valid", "rate");
  const dropped = value("dropped_iterations", "count") || 0;
  const outcome =
    actual === planned &&
    errorRate === 0 &&
    checkRate === 1 &&
    Number.isFinite(p95) &&
    p95 <= 300 &&
    Number.isFinite(p99) &&
    p99 <= 500 &&
    dropped === 0
      ? "PASS"
      : "FAIL";
  const percent = (n) =>
    Number.isFinite(n) ? `${(n * 100).toFixed(2)}%` : "N/A";
  const ms = (n) => (Number.isFinite(n) ? `${n.toFixed(2)}ms` : "N/A");
  const result = {
    api: `${method} ${path}`,
    version: "v1",
    run,
    targetRps: rps,
    duration,
    vus,
    maxVus,
    plannedRequests: planned,
    actualRequests: actual,
    achievedRps: actual / seconds,
    p95,
    p99,
    errorRate,
    checkRate,
    droppedIterations: dropped,
    outcome,
  };
  const filename = `result-auth-v1-${api}-${rps}rps-${run}.json`;
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
