import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";

const jwt = (id) =>
  `test.${Buffer.from(JSON.stringify({ exp: 4102444800, id })).toString("base64url")}.fixture`;
function load(env = {}, rows) {
  const calls = [];
  const execution = {
    scenario: { iterationInTest: 0 },
    vu: { idInTest: 1 },
    test: {
      abort: (message) => {
        throw new Error(message);
      },
    },
  };
  const metrics = {};
  const state = {
    status: null,
    badPath: false,
    badLocation: false,
    csrfFailure: false,
  };
  let sequence = 100;
  const fixture = rows || [
    { memberId: 999, refreshToken: jwt(999), warmup: true },
    ...Array.from({ length: 10 }, (_, i) => ({
      memberId: i + 1,
      refreshToken: jwt(i),
    })),
  ];
  const respond = (url, params) => {
    calls.push({ url, params });
    if (url.endsWith("/csrf"))
      return {
        status: state.csrfFailure ? 403 : 200,
        cookies: { "XSRF-TOKEN": [{ value: "csrf-cookie" }] },
        json: () => ({ token: "csrf-header", headerName: "X-XSRF-TOKEN" }),
      };
    if (url.endsWith("/google"))
      return {
        status: state.status ?? 302,
        headers: {
          Location: state.badLocation
            ? "/wrong"
            : "/oauth2/authorization/google",
        },
        timings: { duration: 4 },
      };
    const logout = url.endsWith("/logout");
    return {
      status: state.status ?? 204,
      timings: { duration: 5 },
      cookies: {
        access_token: [
          {
            value: logout ? "" : jwt(sequence),
            path: "/",
            max_age: logout ? 0 : 3600,
          },
        ],
        refresh_token: [
          {
            value: logout ? "" : jwt(sequence++),
            path: state.badPath ? "/api/auth" : "/api/v1/auth",
            max_age: logout ? 0 : 3600,
          },
        ],
      },
    };
  };
  class Metric {
    constructor(name) {
      this.name = name;
      metrics[name] = [];
    }
    add(value) {
      metrics[this.name].push(value);
    }
  }
  const context = vm.createContext({
    __ENV: {
      BASE_URL: "https://loadtest.invalid",
      API: "google",
      RPS: "1",
      DURATION: "10s",
      VUS: "1",
      MAX_VUS: "1",
      AUTH_ACCOUNTS_FILE: "private-fixture",
      ...env,
    },
    open: () => JSON.stringify(fixture),
    SharedArray: function (_, callback) {
      return callback();
    },
    Counter: Metric,
    Rate: Metric,
    Trend: Metric,
    exec: execution,
    encoding: {
      b64decode: (value) => Buffer.from(value, "base64url").toString(),
    },
    check: (response, checks) =>
      Object.values(checks).every((predicate) => predicate(response)),
    http: {
      CookieJar: class {
        constructor() {
          this.values = {};
        }
        set(_, name, value) {
          this.values[name] = value;
        }
      },
      get: respond,
      post: (url, _, params) => respond(url, params),
    },
  });
  const source = readFileSync(
    new URL("./auth-v1-load.js", import.meta.url),
    "utf8",
  )
    .replace(/^import .*;\n/gm, "")
    .replace("export const options", "const options")
    .replace("export default function", "function runScenario")
    .replaceAll("export function", "function");
  vm.runInContext(
    `${source}\nglobalThis.program = { options, setup, run: runScenario, handleSummary };`,
    context,
  );
  return { ...context.program, calls, execution, metrics, state };
}

test("Google measures only the redirect and excludes warmup from target metrics", () => {
  const s = load();
  const data = s.setup();
  assert.equal(s.metrics.auth_target_requests.length, 0);
  s.run(data);
  assert.ok(s.calls.every((c) => c.params.redirects === 0));
  assert.equal(
    s.calls.at(-1).url,
    "https://loadtest.invalid/api/v1/auth/google",
  );
  assert.equal(s.metrics.auth_target_valid[0], true);
  s.execution.scenario.iterationInTest = 10;
  s.run(data);
  assert.equal(s.calls.length, 2);
});
test("Refresh keeps rotating a VU-owned token with CSRF cookie and header", () => {
  const s = load({ API: "refresh" });
  const data = s.setup();
  s.run(data);
  const first = s.calls.at(-1);
  s.execution.scenario.iterationInTest++;
  s.run(data);
  const second = s.calls.at(-1);
  assert.notEqual(
    first.params.jar.values.refresh_token,
    second.params.jar.values.refresh_token,
  );
  assert.equal(first.params.headers["X-XSRF-TOKEN"], "csrf-header");
  assert.equal(first.params.jar.values["XSRF-TOKEN"], "csrf-cookie");
  assert.deepEqual(s.metrics.auth_target_valid, [true, true]);
});
test("Logout consumes one unique token per request without replenishment traffic", () => {
  const s = load({ API: "logout" });
  const data = s.setup();
  for (let i = 0; i < 10; i++) {
    s.execution.scenario.iterationInTest = i;
    s.run(data);
  }
  const measured = s.calls.filter((c) => c.params.tags.phase === "target");
  assert.equal(measured.length, 10);
  assert.equal(
    new Set(measured.map((c) => c.params.jar.values.refresh_token)).size,
    10,
  );
  assert.ok(s.metrics.auth_target_valid.every(Boolean));
});
test("Logout refuses insufficient or duplicate token pools before HTTP", () => {
  assert.throws(
    () => load({ API: "logout", RPS: "100", DURATION: "5m" }),
    /30000/,
  );
  assert.throws(
    () =>
      load({ API: "refresh" }, [
        { memberId: 1, refreshToken: jwt(1), warmup: true },
        { memberId: 2, refreshToken: jwt(1) },
      ]),
    /distinct/,
  );
});
test("bad redirects, CSRF and cookie paths fail warmup", () => {
  const google = load();
  google.state.badLocation = true;
  assert.throws(() => google.setup(), /warmup/);
  const refresh = load({ API: "refresh" });
  refresh.state.badPath = true;
  assert.throws(() => refresh.setup(), /warmup/);
  const csrf = load({ API: "logout" });
  csrf.state.csrfFailure = true;
  assert.throws(() => csrf.setup(), /CSRF/);
});
test("refresh failure is recorded before aborting instead of replaying the old token", () => {
  const s = load({ API: "refresh" });
  const data = s.setup();
  s.state.status = 401;
  assert.throws(() => s.run(data), /possibly consumed/);
  assert.deepEqual(s.metrics.auth_target_http_errors, [true]);
  assert.deepEqual(s.metrics.auth_target_valid, [false]);
});
test("summary emits requested Korean fields and fails missing metrics or dropped requests", () => {
  const s = load();
  const metrics = {
    auth_target_requests: { values: { count: 10 } },
    auth_target_duration_ms: { values: { "p(95)": 4.21, "p(99)": 6.21 } },
    auth_target_http_errors: { values: { rate: 0 } },
    auth_target_valid: { values: { rate: 1 } },
  };
  assert.match(s.handleSummary({ metrics }).stdout, /판정: PASS/);
  assert.match(s.handleSummary({ metrics }).stdout, /p95: 4.21ms/);
  assert.match(s.handleSummary({ metrics: {} }).stdout, /판정: FAIL/);
  metrics.dropped_iterations = { values: { count: 1 } };
  assert.match(s.handleSummary({ metrics }).stdout, /판정: FAIL/);
});
