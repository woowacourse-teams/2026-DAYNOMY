import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import vm from "node:vm";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const template = {
  baseUrl: "https://dev.daynomy.com",
  referenceDate: "2026-10-16",
  tradeMonthsAgo: 1,
  rps: 0.1,
  stageSeconds: 120,
  warmupSeconds: 120,
  vus: 50,
  requestTimeoutSeconds: 10,
  targetP95Ms: 3000,
};

function readReport(scenario, metrics) {
  const output = scenario.handleSummary({ metrics });
  const filename = Object.keys(output).find((name) =>
    name.startsWith("result-transactions-"),
  );
  assert.ok(filename, "a result JSON must be written for Actions artifacts");
  return JSON.parse(output[filename]);
}

async function fixture(
  overrides = {},
  modifyUsers = () => {},
  environment = {},
  entryVersion = overrides.version || "v1",
) {
  const config = { ...template, ...overrides };
  const tokens = Array.from({ length: 53 }, (_, i) => {
    const number = i + 1;
    const claims = {
      sub: String(number),
      role: "USER",
      token_type: "access",
      exp: Date.now() / 1000 + 10000,
    };
    return {
      testNumber: number,
      memberId: number,
      accessToken: `test.${Buffer.from(JSON.stringify(claims)).toString("base64url")}.test`,
      assetId: number,
      unitPrice: 10000,
      initialQuantity: 100,
      baseUrl: config.baseUrl,
      referenceDate: config.referenceDate,
      fixtureHash: "a".repeat(64),
      profile:
        i < 50
          ? ["light", "standard", "light", "standard", "heavy"][i % 5]
          : ["light", "standard", "heavy"][i - 50],
      earliestHistoryDate: "2025-01-01",
      latestSnapshotDate: "2026-10-14",
    };
  });
  modifyUsers(tokens);
  let clock = Date.now();
  class TestDate extends Date {
    constructor(...args) {
      super(...(args.length ? args : [clock]));
    }
    static now() {
      return clock;
    }
  }
  const counters = new Map();
  class Metric {
    constructor(name) {
      this.name = name;
    }
    add(n, tags = {}) {
      const key = this.name;
      counters.set(key, (counters.get(key) || 0) + n);
      if (tags.account) {
        const account = `${key}{account:${tags.account}}`;
        counters.set(account, (counters.get(account) || 0) + n);
      }
    }
  }
  const requests = [];
  const cookies = {};
  const quantities = new Map();
  const histories = new Map();
  const exec = {
    scenario: { iterationInTest: 0, startTime: Date.now() },
    test: {
      abort: (s) => {
        throw new Error(s);
      },
    },
  };
  const http = {
    cookieJar: () => ({
      set: (_origin, name, value) => {
        cookies[name] = value;
      },
    }),
    get: (url) => {
      if (url.endsWith("/api/auth/csrf"))
        return {
          status: 200,
          cookies: { "XSRF-TOKEN": [{ value: "csrf" }] },
          json: () => ({ token: "csrf", headerName: "X-XSRF-TOKEN" }),
        };
      const member = Number(
        JSON.parse(Buffer.from(cookies.access_token.split(".")[1], "base64url"))
          .sub,
      );
      return {
        status: 200,
        json: () =>
          url.includes("/histories?")
            ? structuredClone(histories.get(member) || [])
            : {
                holdings: [
                  { assetId: member, quantity: quantities.get(member) || 100 },
                ],
              },
      };
    },
    post: (url, payload, options) => {
      assert.equal(cookies["XSRF-TOKEN"], options.headers["X-XSRF-TOKEN"]);
      clock += 10;
      const body = JSON.parse(payload);
      const claims = JSON.parse(
        Buffer.from(cookies.access_token.split(".")[1], "base64url"),
      );
      const member = Number(claims.sub);
      const quantity = (quantities.get(member) || 100) + 1;
      quantities.set(member, quantity);
      requests.push({ member, url, body, options });
      const rows = histories.get(member) || [];
      rows.push({
        historyId: requests.length,
        assetId: body.assetId,
        changeType: "BUY",
        transactionQuantity: 1,
        unitPrice: body.unitPrice,
        fee: 0,
        tax: 0,
        effectiveDate: body.tradeDate,
      });
      histories.set(member, rows);
      return {
        status: 201,
        json: () => ({
          transactionId: requests.length,
          holding: { assetId: body.assetId, quantity },
        }),
      };
    },
  };
  const files = {
    "./.local/accounts.json": tokens,
    "custom-accounts.json": tokens,
  };
  const context = vm.createContext({
    __ENV: {
      BASE_URL: config.baseUrl,
      REFERENCE_DATE: config.referenceDate,
      TRADE_MONTHS_AGO: String(config.tradeMonthsAgo),
      RPS: String(config.rps),
      DURATION: `${config.stageSeconds}s`,
      WARMUP_DURATION: `${config.warmupSeconds}s`,
      VUS: String(config.vus),
      REQUEST_TIMEOUT_SECONDS: String(config.requestTimeoutSeconds),
      TARGET_P95_MS: String(config.targetP95Ms),
      ...environment,
    },
    Date: TestDate,
    console,
    open: (filename) => JSON.stringify(files[filename]),
  });
  const imports = {
    "k6/http": { default: http },
    k6: {
      check: (response, checks) =>
        Object.values(checks).every((f) => f(response)),
      sleep: (seconds) => {
        clock += seconds * 1000;
      },
    },
    "k6/execution": { default: exec },
    "k6/encoding": {
      default: { b64decode: (s) => Buffer.from(s, "base64url").toString() },
    },
    "k6/data": {
      SharedArray: class {
        constructor(_name, callback) {
          return callback();
        }
      },
    },
    "k6/metrics": { Counter: Metric, Trend: Metric },
  };
  const modules = new Map();
  function load(filename) {
    if (!modules.has(filename))
      modules.set(
        filename,
        new vm.SourceTextModule(fs.readFileSync(filename, "utf8"), {
          context,
          identifier: filename,
        }),
      );
    return modules.get(filename);
  }
  const module = load(path.join(root, `transactions-${entryVersion}.js`));
  await module.link((name, parent) => {
    if (name.startsWith("./"))
      return load(path.resolve(path.dirname(parent.identifier), name));
    const values = imports[name];
    assert.ok(values, name);
    if (!modules.has(name))
      modules.set(
        name,
        new vm.SyntheticModule(
          Object.keys(values),
          function () {
            for (const [key, value] of Object.entries(values))
              this.setExport(key, value);
          },
          { context },
        ),
      );
    return modules.get(name);
  });
  await module.evaluate();
  return {
    build: () => ({
      options: module.namespace.options,
      setup: module.namespace.setup,
      teardown: module.namespace.teardown,
      run: module.namespace.default,
      handleSummary: module.namespace.handleSummary,
    }),
    config,
    exec,
    counters,
    requests,
    http,
    quantities,
    histories,
  };
}

test("one fixed-rate stage uses 50 VUs; calendar subtraction clamps month ends", async () => {
  const f = await fixture({
    referenceDate: "2026-03-31",
  });
  const scenario = f.build();
  assert.equal(scenario.options.scenarios.transactions.preAllocatedVUs, 50);
  assert.equal(
    scenario.options.scenarios.transactions.executor,
    "constant-arrival-rate",
  );
  assert.equal(scenario.options.scenarios.transactions.rate, 6);
  assert.equal(scenario.options.tags.trade_date, "2026-02-28");
});

test("account selection depends on request index, not which VU is free; each cycle stays balanced", async () => {
  const f = await fixture({ rps: 1 });
  const scenario = f.build();
  for (let index = 0; index < 120; index++) {
    f.exec.scenario.iterationInTest = index;
    scenario.run({ csrf: { token: "csrf", headerName: "X-XSRF-TOKEN" } });
  }
  assert.deepEqual(
    f.requests.map((r) => r.member),
    Array.from({ length: 120 }, (_, i) => (i % 50) + 1),
  );
  assert.equal(f.counters.get("transaction_started{account:lt-001}"), 3);
  assert.equal(f.counters.get("transaction_success{account:lt-050}"), 2);
  const firstCycle = f.requests.slice(0, 50);
  assert.equal(
    firstCycle.filter((r) => [1, 3].includes(r.member % 5)).length,
    20,
  );
  assert.equal(
    firstCycle.filter((r) => [2, 4].includes(r.member % 5)).length,
    20,
  );
  assert.equal(firstCycle.filter((r) => r.member % 5 === 0).length, 10);
  assert.equal(f.counters.get("transaction_failed"), 0);
});

test("entry filenames select V1/V2 endpoints with identical payloads/accounts", async () => {
  const runs = [];
  for (const version of ["v1", "v2"]) {
    const f = await fixture(
      { version: version === "v1" ? "v2" : "v1" },
      () => {},
      {},
      version,
    );
    const scenario = f.build();
    assert.equal(scenario.options.tags.version, version);
    scenario.run({ csrf: { token: "csrf", headerName: "X-XSRF-TOKEN" } });
    runs.push(f.requests[0]);
  }
  assert.deepEqual(runs[0].body, runs[1].body);
  assert.equal(runs[0].member, runs[1].member);
  assert.match(runs[0].url, /\/api\/users\/me\//);
  assert.match(runs[1].url, /\/api\/v2\/users\/me\//);
});

test("an extra arrival at the duration boundary cannot exceed the planned POST count", async () => {
  for (const version of ["v1", "v2"]) {
    const f = await fixture({ version });
    const scenario = f.build();
    for (let index = 0; index <= 12; index++) {
      f.exec.scenario.iterationInTest = index;
      scenario.run({ csrf: { token: "csrf", headerName: "X-XSRF-TOKEN" } });
    }
    assert.equal(f.requests.length, 12);
    assert.equal(f.counters.get("transaction_started"), 12);
    assert.equal(f.counters.get("transaction_success"), 12);
    assert.equal(f.counters.get("transaction_failed"), 0);
    const report = readReport(scenario, {
      transaction_started: { values: { count: 12 } },
      transaction_success: { values: { count: 12 } },
      transaction_duration: { values: { "p(95)": 20 } },
    });
    assert.equal(report.outcome, "exploratory_pass");
  }
});

test("invalid token identities, missing cohorts, excessive rates and non-dev targets fail before HTTP", async () => {
  for (const overrides of [
    { baseUrl: "https://daynomy.com" },
    { rps: 5 },
    { stageSeconds: 0 },
  ]) {
    await assert.rejects(() => fixture(overrides));
  }
  await assert.rejects(
    () =>
      fixture({}, (users) => {
        users[0].memberId = 999;
      }),
    /Token identity/,
  );
  await assert.rejects(
    () =>
      fixture({}, (users) => {
        users[1].memberId = 1;
      }),
    /Duplicate/,
  );
});

test("reports zero-use accounts, excludes tokens, and cannot pass missing/dropped requests", async () => {
  const f = await fixture();
  const scenario = f.build();
  const metrics = {
    transaction_started: { values: { count: 12 } },
    transaction_success: { values: { count: 12 } },
    transaction_duration: { values: { "p(95)": 20 } },
    dropped_iterations: { values: { count: 1 } },
  };
  const report = readReport(scenario, metrics);
  assert.equal(report.outcome, "incomplete");
  assert.equal(report.accounts.length, 50);
  assert.equal(report.accounts[49].started, 0);
  assert.ok(!JSON.stringify(report).includes("accessToken"));
  assert.ok(!JSON.stringify(report).includes("test.ey"));
  metrics.dropped_iterations.values.count = 0;
  metrics.transaction_duration.values["p(95)"] = 4000;
  assert.equal(readReport(scenario, metrics).outcome, "target_exceeded");
  metrics.transaction_duration.values["p(95)"] = 20;
  metrics.transaction_started.values.count = 11;
  metrics.transaction_success.values.count = 11;
  assert.equal(readReport(scenario, metrics).outcome, "incomplete");
});

test("warmup uses only its three accounts and never enters measured POST counters", async () => {
  const f = await fixture();
  const scenario = f.build();
  const data = scenario.setup();
  assert.deepEqual([...new Set(f.requests.map((r) => r.member))], [51, 52, 53]);
  assert.ok(data.warmupCounts.every((count) => count > 0));
  assert.equal(f.counters.get("transaction_started"), undefined);
  assert.equal(f.counters.get("warmup_completed"), f.requests.length);
  assert.equal(data.csrf.token, "csrf");
});

test("reports measured successes/errors and warmup latency separately for each portfolio size", async () => {
  const f = await fixture();
  const scenario = f.build();
  const report = readReport(scenario, {
    transaction_started: { values: { count: 12 } },
    transaction_success: { values: { count: 11 } },
    transaction_failed: { values: { count: 1 } },
    transaction_duration: { values: { "p(95)": 900 } },
    "transaction_started{account:lt-001}": { values: { count: 2 } },
    "transaction_success{account:lt-001}": { values: { count: 1 } },
    "transaction_failed{account:lt-001}": { values: { count: 1 } },
    "transaction_duration{profile:light}": { values: { "p(95)": 500 } },
    "warmup_completed{profile:light}": { values: { count: 3 } },
    "warmup_duration{profile:light}": { values: { "p(95)": 1500 } },
  });
  const light = report.profiles.find((p) => p.profile === "light");
  assert.equal(light.started, 2);
  assert.equal(light.successful, 1);
  assert.equal(light.failed, 1);
  assert.equal(light.accountsUsed, 1);
  assert.equal(light.latency["p(95)"], 500);
  const heavy = report.profiles.find((p) => p.profile === "heavy");
  assert.equal(heavy.accountsUsed, 0);
  assert.equal(heavy.successful, 0);
  assert.deepEqual(heavy.latency, {});
  assert.equal(report.warmup.profiles[0].completed, 3);
  assert.equal(report.warmup.profiles[0].latency["p(95)"], 1500);
  assert.equal(report.latency["p(95)"], 900);
  assert.equal(report.outcome, "target_exceeded");
});

test("Actions testid keeps version, date and rate tags on the same run", async () => {
  const f = await fixture({ version: "v2", rps: 1 }, () => {}, {
    LOADTEST_ID: "actions-stage-id",
  });
  const tags = f.build().options.tags;
  assert.equal(tags.version, "v2");
  assert.equal(tags.trade_date, "2026-09-16");
  assert.equal(tags.rps, "1");
  assert.equal(tags.testid, "actions-stage-id");
  assert.equal(tags.environment, "dev");
});

test("Actions scalar params select period, RPS and duration without a config file", async () => {
  const f = await fixture({}, () => {}, {
    ACCOUNTS_FILE: "custom-accounts.json",
    RPS: "1.5",
    DURATION: "5m",
    WARMUP_DURATION: "1m",
    TRADE_MONTHS_AGO: "6",
    VUS: "50",
    RUN: "boundary-2",
  });
  const scenario = f.build();
  const options = scenario.options.scenarios.transactions;
  assert.equal(options.rate, 90);
  assert.equal(options.timeUnit, "1m");
  assert.equal(options.duration, "300s");
  assert.equal(scenario.options.tags.trade_date, "2026-04-16");
  assert.equal(scenario.options.tags.run, "boundary-2");
  assert.ok(
    Object.keys(scenario.handleSummary({ metrics: {} })).some(
      (name) =>
        name ===
        "result-transactions-v1-2026-10-16-6m-1.5rps-300s-boundary-2.json",
    ),
  );
  for (const invalid of [
    { ACTION: "plan" },
    { RPS_STEPS: "[1,2]" },
    { CONFIG_FILE: "old.json" },
    { DURATION: "0s" },
    { DURATION: "11m" },
    { REFERENCE_DATE: "" },
    { RUN: "../../escape" },
    { RPS: "NaN" },
  ])
    await assert.rejects(() => fixture({}, () => {}, invalid));
});

test("request errors or unexpected quantities stop the test without retrying", async () => {
  for (const mismatch of [false, true]) {
    const f = await fixture();
    f.http.post = () => ({
      status: mismatch ? 201 : 503,
      json: () => ({
        transactionId: 1,
        holding: { assetId: 1, quantity: 999 },
      }),
    });
    assert.throws(
      () =>
        f.build().run({ csrf: { token: "csrf", headerName: "X-XSRF-TOKEN" } }),
      mismatch ? /response_mismatch/ : /http_failure_or_timeout/,
    );
    assert.equal(f.counters.get("transaction_failed"), 1);
    assert.equal(f.counters.get("transaction_success"), 0);
  }
});

test("stress mode permits five high rates with 50 accounts; default mode still rejects overlaps", async () => {
  for (const rps of [10, 20, 50, 100, 200]) {
    const f = await fixture(
      { rps, vus: 500 },
      () => {},
      { MODE: "stress" },
      "v2",
    );
    const scenario = f.build();
    assert.equal(scenario.options.scenarios.transactions.rate, rps * 60);
    assert.equal(scenario.options.scenarios.transactions.maxVUs, 500);
    assert.equal(scenario.options.tags.workload, "stress_hot_accounts");
    assert.deepEqual(
      [...scenario.options.thresholds.stress_committed],
      [`count==${rps * 120}`],
    );
  }
  await assert.rejects(() =>
    fixture({ rps: 201 }, () => {}, { MODE: "stress" }),
  );
  await assert.rejects(() => fixture({ rps: 10 }));
  await assert.rejects(() => fixture({}, () => {}, { MODE: "invalid" }));
});

test("stress accepts out-of-order requests for one account and verifies persisted writes", async () => {
  const f = await fixture(
    { rps: 10, warmupSeconds: 1 },
    () => {},
    { MODE: "stress" },
    "v2",
  );
  const scenario = f.build();
  const data = scenario.setup();
  for (const index of [50, 0]) {
    f.exec.scenario.iterationInTest = index;
    scenario.run(data);
  }
  scenario.teardown(data);
  assert.equal(f.counters.get("transaction_success"), 2);
  assert.equal(f.counters.get("transaction_failed"), 0);
  assert.equal(f.counters.get("stress_verification_checked"), 50);
  assert.equal(f.counters.get("stress_verification_failed"), 0);
  assert.equal(f.counters.get("stress_committed{account:lt-001}"), 2);
});

test("stress records overload errors once and continues, but malformed successes and auth errors abort", async () => {
  const f = await fixture({ rps: 10 }, () => {}, { MODE: "stress" });
  const scenario = f.build();
  const original = f.http.post;
  const data = { csrf: { token: "csrf", headerName: "X-XSRF-TOKEN" } };
  let calls = 0;
  f.http.post = () => {
    calls++;
    return { status: 503, json: () => ({}) };
  };
  scenario.run(data);
  assert.equal(calls, 1);
  assert.equal(f.counters.get("transaction_failed"), 1);
  f.http.post = original;
  f.exec.scenario.iterationInTest = 1;
  scenario.run(data);
  assert.equal(f.counters.get("transaction_success"), 1);
  for (const status of [401, 403, 201]) {
    f.http.post = () => ({ status, json: () => ({}) });
    assert.throws(() => scenario.run(data), /Stopped:/);
  }
});

test("stress rejects changed initial quantities before submitting trades", async () => {
  const f = await fixture({ rps: 10 }, () => {}, { MODE: "stress" });
  f.quantities.set(1, 200);
  assert.throws(() => f.build().setup(), /baseline unavailable or changed/);
  assert.equal(f.requests.length, 0);
});

test("stress detects lost quantities, modified original histories and unavailable verification reads", async () => {
  for (const failure of ["quantity", "history", "read"]) {
    const f = await fixture({ rps: 10, warmupSeconds: 1 }, () => {}, {
      MODE: "stress",
    });
    f.histories.set(1, [
      { historyId: 999999, assetId: 1, transactionQuantity: 1 },
    ]);
    const scenario = f.build();
    const data = scenario.setup();
    scenario.run(data);
    if (failure === "quantity") f.quantities.set(1, 100);
    if (failure === "history") f.histories.get(1)[0].transactionQuantity = 2;
    if (failure === "read")
      f.http.get = () => ({ status: 503, json: () => ({}) });
    scenario.teardown(data);
    assert.ok(f.counters.get("stress_verification_failed") > 0);
    assert.equal(f.counters.get("stress_verification_checked"), 50);
  }
});

test("stress cannot pass lost commits, missing verification, drops or uncertain server outcomes", async () => {
  const f = await fixture({ rps: 10 }, () => {}, { MODE: "stress" });
  const scenario = f.build();
  const metrics = {
    transaction_started: { values: { count: 1200 } },
    transaction_success: { values: { count: 1200 } },
    transaction_duration: { values: { "p(95)": 500 } },
    stress_verification_checked: { values: { count: 50 } },
    stress_verification_failed: { values: { count: 0 } },
    stress_committed: { values: { count: 1200 } },
  };
  for (let i = 1; i <= 50; i++) {
    const account = `lt-${String(i).padStart(3, "0")}`;
    for (const name of [
      "transaction_started",
      "transaction_success",
      "stress_committed",
    ])
      metrics[`${name}{account:${account}}`] = { values: { count: 24 } };
  }
  let report = readReport(scenario, metrics);
  assert.equal(report.outcome, "exploratory_pass");
  assert.equal(report.integrity.status, "passed");
  assert.equal(report.accounts[49].committed, 24);
  assert.ok(!JSON.stringify(report).includes("accessToken"));
  metrics.stress_committed.values.count = 1199;
  assert.equal(readReport(scenario, metrics).outcome, "integrity_failed");
  metrics.stress_committed.values.count = 1200;
  metrics.dropped_iterations = { values: { count: 1 } };
  assert.equal(readReport(scenario, metrics).outcome, "incomplete");
  delete metrics.dropped_iterations;
  metrics.transaction_success.values.count = 1199;
  metrics.transaction_failed = { values: { count: 1 } };
  report = readReport(scenario, metrics);
  assert.equal(report.outcome, "target_exceeded");
  assert.equal(report.integrity.status, "inconclusive");
  assert.equal(report.integrity.requiresServerDrain, true);
  delete metrics.stress_verification_checked;
  assert.equal(readReport(scenario, metrics).outcome, "incomplete");
});

test("Actions forwards MODE=stress and all five saved presets initialize successfully", async () => {
  const workflow = fs.readFileSync(
    path.join(root, "../../.github/workflows/k6-dev.yml"),
    "utf8",
  );
  const script = workflow
    .split(
      '          PORTFOLIO_TEST="$portfolio" K6_SCRIPT_NAME="$script_name" python3 - <<\'PY\'\n',
    )[1]
    .split("          PY")[0]
    .split("\n")
    .map((line) => line.slice(10))
    .join("\n");
  const wrapper =
    `import os, sys, json\nfrom unittest.mock import patch\n` +
    `with patch('subprocess.run') as run:\n run.return_value.returncode = 0\n try:\n  exec(${JSON.stringify(script)})\n except SystemExit as error:\n  if error.code: raise\n print(json.dumps(run.call_args.args[0]))\n`;
  for (const rps of [10, 20, 50, 100, 200]) {
    const params = JSON.parse(
      fs.readFileSync(
        path.join(root, `params/stress-v2-${rps}rps.json`),
        "utf8",
      ),
    );
    const result = spawnSync("python3", ["-c", wrapper], {
      encoding: "utf8",
      env: {
        ...process.env,
        PORTFOLIO_TEST: "true",
        K6_SCRIPT_NAME: "transactions-v2.js",
        K6_PARAMS_JSON: JSON.stringify(params),
        BASE_URL: template.baseUrl,
        LOADTEST_ID: "stress-workflow-test",
      },
    });
    assert.equal(result.status, 0, result.stderr);
    const args = JSON.parse(result.stdout);
    assert.ok(args.includes("MODE=stress"));
    assert.ok(args.includes("testid=stress-workflow-test"));
    assert.equal(args.at(-1), "transactions-v2.js");
    const f = await fixture({}, () => {}, params, "v2");
    assert.equal(f.build().options.scenarios.transactions.rate, rps * 60);
  }
});
