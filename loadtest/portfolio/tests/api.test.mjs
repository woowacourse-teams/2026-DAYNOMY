import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import test from "node:test";
import vm from "node:vm";
import os from "node:os";
import { gzipSync } from "node:zlib";
import { createHash } from "node:crypto";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

async function fixture(operation = "get", environment = {}, modify = () => {}) {
  let clock = Date.now();
  const evaluationDate = new Date(clock + 9 * 3600000)
    .toISOString()
    .slice(0, 10);
  const rows = Array.from({ length: 1003 }, (_, i) => {
    const profile =
      i < 1000
        ? ["light", "standard", "light", "standard", "heavy"][i % 5]
        : ["light", "standard", "heavy"][i - 1000];
    const holdings = Array.from(
      { length: { light: 5, standard: 15, heavy: 30 }[profile] },
      (_, j) => ({
        assetId: j + 1,
        quantity: 100,
        averagePurchasePrice: 13000,
      }),
    );
    return {
      testNumber: i + 1,
      memberId: i + 1,
      portfolioId: i + 1,
      profile,
      namespace: "lt1000-20261029",
      baseUrl: "https://dev.daynomy.com",
      referenceDate: "2026-10-29",
      evaluationDate,
      baselineHash: "a".repeat(64),
      assetId: 1,
      initialQuantity: 100,
      unitPrice: 10,
      exchangeRate: 1300,
      currency: "USD",
      holdings,
      expectedRealizedProfitLoss: 0,
      country: ["KR", "US", "MIXED"][i % 3],
      transactionBaselines: Object.fromEntries(
        [
          [0, "2026-10-29"],
          [1, "2026-09-29"],
          [6, "2026-04-29"],
        ].map(([n, d]) => [n, { date: d, historyCount: 0 }]),
      ),
      performancePeriods: Object.fromEntries(
        [1, 6, 12].map((n) => [
          n,
          {
            from: "2025-01-01",
            to: evaluationDate,
            pointCount: 2,
            baseDate: "2026-10-28",
          },
        ]),
      ),
      accessToken: `test.${Buffer.from(JSON.stringify({ sub: String(i + 1), role: "USER", token_type: "access", exp: clock / 1000 + 10000 })).toString("base64url")}.test`,
    };
  });
  modify(rows);
  const metricValues = new Map();
  class Metric {
    constructor(name) {
      this.name = name;
    }
    add(n, tags = {}) {
      const v = metricValues.get(this.name) || [];
      v.push({ value: Number(n), tags });
      metricValues.set(this.name, v);
    }
  }
  class CookieJar {
    constructor() {
      this.values = {};
    }
    set(_origin, key, value) {
      this.values[key] = value;
    }
  }
  const requests = [];
  let status = 200;
  let invalid = false;
  let verificationBad = false;
  const execution = {
    scenario: { iterationInTest: 0 },
    test: {
      abort: (message) => {
        throw new Error(message);
      },
    },
  };
  function response(url, payload, params) {
    clock += 10;
    if (url.endsWith("/api/auth/csrf"))
      return {
        status: 200,
        cookies: { "XSRF-TOKEN": [{ value: "csrf" }] },
        json: () => ({ token: "csrf", headerName: "X-XSRF-TOKEN" }),
      };
    const member = Number(
      JSON.parse(
        Buffer.from(
          params.jar.values.access_token.split(".")[1],
          "base64url",
        ).toString(),
      ).sub,
    );
    const account = rows[member - 1];
    const phase = params.tags.phase;
    const planned =
      Number(environment.RPS || 100) *
      (/m$/.test(environment.DURATION || "5m")
        ? parseInt(environment.DURATION || "5m") * 60
        : parseInt(environment.DURATION));
    const added =
      phase === "verify"
        ? Math.floor(planned / 1000) + (member - 1 < planned % 1000 ? 1 : 0)
        : 0;
    requests.push({ url, payload, params, member });
    if (payload)
      assert.equal(
        params.jar.values["XSRF-TOKEN"],
        params.headers["X-XSRF-TOKEN"],
      );
    let body;
    if (url.includes("/histories?"))
      body = Array.from(
        { length: verificationBad ? added + 1 : added },
        () => ({}),
      );
    else if (url.endsWith("/transactions"))
      body = {
        transactionId: requests.length,
        holding: { assetId: account.assetId, quantity: 101 },
      };
    else if (url.endsWith("/calculate"))
      body = {
        pricingStatus: "MIXED_DATES",
        unpricedHoldingCount: 0,
        pricedHoldingCount: account.holdings.length,
        holdings: account.holdings,
        totalEvaluationAmount: 1000,
      };
    else if (url.includes("/performance?"))
      body = {
        status: "READY",
        baseDate: "2026-10-28",
        points: [{ baseDate: "2026-10-27" }, { baseDate: "2026-10-28" }],
      };
    else
      body = {
        holdings: account.holdings.map((h) => ({
          ...h,
          quantity: h.quantity + (h.assetId === 1 ? added : 0),
        })),
        totalRealizedProfitLoss: 0,
      };
    if (invalid) body = {};
    return {
      status: status === 200 && url.endsWith("/transactions") ? 201 : status,
      json: () => body,
    };
  }
  const http = {
    CookieJar,
    get: (url, params) => response(url, null, params),
    post: (url, payload, params) => response(url, JSON.parse(payload), params),
    batch: (batch) =>
      batch.map(([_method, url, payload, params]) =>
        response(url, payload, params),
      ),
  };
  class Clock extends Date {
    constructor(...args) {
      super(...(args.length ? args : [clock]));
    }
    static now() {
      return clock;
    }
  }
  const context = vm.createContext({
    __ENV: { BASE_URL: "https://dev.daynomy.com", ...environment },
    Date: Clock,
    open: () => JSON.stringify(rows),
  });
  const mocks = {
    "k6/http": { default: http },
    k6: {
      check: (_r, checks) => Object.values(checks).every((f) => f(_r)),
      sleep: (s) => {
        clock += s * 1000;
      },
    },
    "k6/execution": { default: execution },
    "k6/encoding": {
      default: { b64decode: (s) => Buffer.from(s, "base64url").toString() },
    },
    "k6/data": {
      SharedArray: class {
        constructor(_name, fn) {
          return fn();
        }
      },
    },
    "k6/metrics": { Counter: Metric, Rate: Metric, Trend: Metric },
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
  const entry = load(path.join(root, "api", `${operation === "get" ? "get-portfolio" : operation}.js`));
  await entry.link((name, parent) => {
    if (name.startsWith("."))
      return load(path.resolve(path.dirname(parent.identifier), name));
    if (!modules.has(name)) {
      const values = mocks[name];
      assert.ok(values, name);
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
    }
    return modules.get(name);
  });
  await entry.evaluate();
  return {
    scenario: entry.namespace,
    requests,
    metricValues,
    execution,
    rows,
    setStatus: (s) => {
      status = s;
    },
    setInvalid: () => {
      invalid = true;
    },
    setVerificationBad: () => {
      verificationBad = true;
    },
  };
}

for (const operation of [
  "get",
  "calculate",
  "transactions-v2",
  "performance",
]) {
  test(`${operation}: defaults to one independent 100 RPS / 5 minute scenario`, async () => {
    const { scenario, requests } = await fixture(operation, {
      WARMUP_DURATION: "1s",
    });
    assert.equal(scenario.options.scenarios.measure.rate, 100);
    assert.equal(scenario.options.scenarios.measure.duration, "300s");
    assert.equal(
      scenario.options.scenarios.measure.executor,
      "constant-arrival-rate",
    );
    assert.equal(Object.keys(scenario.options.scenarios).length, 1);
    const data = scenario.setup();
    scenario.default(data);
    const request = requests.find((r) => r.params.tags.phase === "measure");
    assert.equal(request.member, 1);
    const paths = {
      get: "/api/users/me/portfolio",
      calculate: "/api/portfolio/calculate",
      "transactions-v2": "/api/v2/users/me/portfolio/transactions",
      performance: "/api/users/me/portfolio/performance?",
    };
    assert.ok(request.url.includes(paths[operation]));
    assert.deepEqual(
      Array.from(scenario.options.thresholds.portfolio_duration),
      ["p(95)<=300", "p(99)<=500"],
    );
  });
}

test("V2 sends USD rate and the selected past date, and rotates 1000 measurement members", async () => {
  const { scenario, requests, execution } = await fixture("transactions-v2", {
    WARMUP_DURATION: "1s",
    TRADE_MONTHS_AGO: "6",
  });
  const data = scenario.setup();
  for (const index of [0, 999, 1000]) {
    execution.scenario.iterationInTest = index;
    scenario.default(data);
  }
  const measured = requests.filter((r) => r.params.tags.phase === "measure");
  assert.deepEqual(
    measured.map((r) => r.member),
    [1, 1000, 1],
  );
  assert.deepEqual(JSON.parse(JSON.stringify(measured[0].payload)), {
    assetId: 1,
    type: "BUY",
    tradeDate: "2026-04-29",
    quantity: 1,
    unitPrice: 10,
    exchangeRate: 1300,
    fee: 0,
    tax: 0,
  });
  assert.ok(measured.every((r) => r.url.includes("/api/v2/")));
  assert.ok(
    requests
      .filter((r) => r.params.tags.phase === "warmup")
      .every((r) => r.member > 1000),
  );
});

test("calculate sends prepared holdings without an extra GET inside measured iterations", async () => {
  const { scenario, requests } = await fixture("calculate", {
    WARMUP_DURATION: "1s",
  });
  const data = scenario.setup();
  const before = requests.length;
  scenario.default(data);
  assert.equal(requests.length, before + 1);
  assert.equal(requests.at(-1).payload.holdings.length, 5);
});

test("401 aborts; 500 remains measurable and is recorded as a failure", async () => {
  const f = await fixture("get", { WARMUP_DURATION: "1s" });
  const data = f.scenario.setup();
  f.setStatus(500);
  assert.doesNotThrow(() => f.scenario.default(data));
  assert.equal(f.metricValues.get("portfolio_failure_rate").at(-1).value, 1);
  f.setStatus(401);
  assert.throws(() => f.scenario.default(data), /Authentication\/CSRF/);
});

test("valid HTTP with invalid body is a data mismatch", async () => {
  const f = await fixture("get", { WARMUP_DURATION: "1s" });
  const data = f.scenario.setup();
  f.setInvalid();
  f.scenario.default(data);
  assert.equal(f.metricValues.get("portfolio_data_mismatch").at(-1).value, 1);
});

test("rejects wrong account count, duplicate tokens, expired claims, target, or evaluation day", async () => {
  await assert.rejects(
    () => fixture("get", {}, (rows) => rows.pop()),
    /1000|1,000/,
  );
  await assert.rejects(
    () =>
      fixture("get", {}, (rows) => {
        rows[1].accessToken = rows[0].accessToken;
      }),
    /Duplicate/,
  );
  await assert.rejects(
    () =>
      fixture("get", {}, (rows) => {
        rows[0].accessToken =
          "x." +
          Buffer.from(
            JSON.stringify({
              sub: "1",
              role: "USER",
              token_type: "access",
              exp: 0,
            }),
          ).toString("base64url") +
          ".x";
      }),
    /lifetime/,
  );
  await assert.rejects(
    () => fixture("get", { BASE_URL: "https://daynomy.com" }),
    /dev API/,
  );
  await assert.rejects(
    () =>
      fixture("get", {}, (rows) => {
        rows[0].evaluationDate = "2000-01-01";
      }),
    /evaluation date/,
  );
});

test("300 and 500 RPS remain explicit choices; no automatic next stage", async () => {
  for (const rate of [300, 500]) {
    const { scenario } = await fixture("get", { RPS: String(rate) });
    assert.equal(scenario.options.scenarios.measure.rate, rate);
    assert.equal(scenario.options.scenarios.measure.duration, "300s");
    assert.equal(Object.keys(scenario.options.scenarios).length, 1);
  }
});

test("V2 verifies every member using independent authenticated jars and catches mismatched counts", async () => {
  const f = await fixture("transactions-v2", {
    RPS: "1",
    DURATION: "5s",
    WARMUP_DURATION: "1s",
  });
  const data = f.scenario.setup();
  f.scenario.teardown(data);
  assert.equal(
    f.metricValues
      .get("portfolio_integrity_checked")
      .reduce((s, r) => s + r.value, 0),
    1000,
  );
  assert.equal(
    f.metricValues
      .get("portfolio_integrity_failed")
      .reduce((s, r) => s + r.value, 0),
    0,
  );
  f.setVerificationBad();
  f.scenario.teardown(data);
  assert.equal(
    f.metricValues.get("portfolio_integrity_failed").at(-1).value,
    1,
  );
});

test("report never calls a dropped or p99-failing run passed", async () => {
  const { scenario } = await fixture("get");
  const metrics = {
    portfolio_started: { values: { count: 30000 } },
    portfolio_succeeded: { values: { count: 30000 } },
    portfolio_failure_rate: { values: { rate: 0 } },
    portfolio_duration: { values: { "p(95)": 200, "p(99)": 400 } },
    dropped_iterations: { values: { count: 0 } },
  };
  const report = () =>
    JSON.parse(
      Object.values(scenario.handleSummary({ metrics })).find((v) =>
        v.startsWith("{"),
      ),
    );
  assert.equal(report().outcome, "passed");
  metrics.dropped_iterations.values.count = 1;
  assert.equal(report().outcome, "incomplete");
  metrics.dropped_iterations.values.count = 0;
  metrics.portfolio_duration.values["p(99)"] = 501;
  assert.equal(report().outcome, "target_exceeded");
});

test("Actions passes the generated 1003-account file and refuses broad file permissions", () => {
  const workflow = fs.readFileSync(
    path.join(root, "../../.github/workflows/k6-dev.yml"),
    "utf8",
  );
  const source = workflow
    .split("python3 - <<'PY'\n")[1]
    .split("\n          PY")[0]
    .split("\n")
    .map((line) => line.slice(10))
    .join("\n");
  const harness = `
import json,os,subprocess
from types import SimpleNamespace
from unittest.mock import patch
source,mode=json.loads(input())
calls=[]
def run(args):
 calls.append(args)
 return subprocess.CompletedProcess(args,0)
with patch.dict(os.environ,{'PORTFOLIO_TEST':'false','PORTFOLIO_1000_TEST':'true','K6_GENERATED_ACCOUNTS_FILE':'/private/fixtures/accounts-1000.json','K6_SCRIPT_NAME':'portfolio/api/get-portfolio.js','LOADTEST_ID':'test-1000','BASE_URL':'https://dev.daynomy.com','K6_PARAMS_JSON':'{"RPS":100,"DURATION":"5m"}'}),patch('os.path.isfile',return_value=True),patch('os.stat',return_value=SimpleNamespace(st_mode=mode)),patch('subprocess.run',run):
 try:exec(source)
 except SystemExit as e:print(json.dumps({'code':e.code,'calls':calls}))
`;
  const run = (mode) => {
    const result = spawnSync("python3", ["-c", harness], {
      input: JSON.stringify([source, mode]),
      encoding: "utf8",
    });
    assert.equal(result.status, 0, result.stderr);
    return JSON.parse(result.stdout.trim().split("\n").at(-1));
  };
  const valid = run(0o100600);
  assert.equal(valid.code, 0);
  assert.equal(valid.calls.length, 1);
  assert.ok(
    valid.calls[0].includes(
      "ACCOUNTS_FILE=/private/fixtures/accounts-1000.json",
    ),
  );
  assert.ok(valid.calls[0].includes("RPS=100"));
  assert.equal(valid.calls[0].at(-1), "portfolio/api/get-portfolio.js");
  const invalid = run(0o100644);
  assert.equal(invalid.calls.length, 0);
  assert.match(invalid.code, /0600/);
});


test("Actions reconstructs split Secrets, cleans temporary accounts, and preserves k6 failures", () => {
  const workflow = fs.readFileSync(
    path.join(root, "../../.github/workflows/k6-dev.yml"), "utf8",
  );
  const shell = workflow.split("        run: |\n")[1]
    .split("\n      - name: Summarize transaction results")[0]
    .split("\n").map((line) => line.slice(10)).join("\n");
  const payload = [{ memberId: 1, accessToken: "fixture-token-only" }];
  const secret = gzipSync(JSON.stringify(payload)).toString("base64");
  const first = secret.slice(0, 12);
  const second = secret.slice(12);
  const manifest = `2:${createHash("sha256").update(secret).digest("hex")}`;
  const cases = [
    ...["get-portfolio", "calculate", "transactions-v2", "performance"].map(
      (name) => ({ name, secret, k6Code: 0, expected: 0 }),
    ),
    { name: "get-portfolio", secret: first, second, manifest, k6Code: 0, expected: 0, split: true },
    { name: "get-portfolio", secret: first, manifest, k6Code: 0, expected: 1 },
    { name: "get-portfolio", secret: first, second: "corrupt", manifest, k6Code: 0, expected: 1 },
    { name: "get-portfolio", secret: "", k6Code: 0, expected: 1 },
    { name: "get-portfolio", secret: Buffer.from("not gzip").toString("base64"), k6Code: 0, expected: 1 },
    { name: "get-portfolio", secret, k6Code: 99, expected: 99 },
  ];
  for (const row of cases) {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), "portfolio-secret-test-"));
    try {
      const runner = path.join(directory, "runner");
      const bin = path.join(directory, "bin");
      const resultFile = path.join(directory, "result.json");
      fs.mkdirSync(runner);
      fs.mkdirSync(bin);
      fs.writeFileSync(path.join(bin, "k6"), `#!/usr/bin/env python3
import json,os,stat,sys
args=sys.argv[1:]
account_path=next(arg.split('=',1)[1] for arg in args if arg.startswith('ACCOUNTS_FILE='))
with open(account_path) as source:
    accounts=json.load(source)
with open(os.environ['FAKE_RESULT_FILE'],'w') as output:
    json.dump({'accounts':accounts,'path':account_path,'mode':stat.S_IMODE(os.stat(account_path).st_mode),'secretVisible':'K6_ACCOUNTS_JSON_GZ_BASE64' in os.environ,'script':args[-1]},output)
sys.exit(int(os.environ['FAKE_K6_EXIT']))
`, { mode: 0o700 });
      const result = spawnSync("bash", ["-c", shell], {
        cwd: path.resolve(root, "../.."),
        env: {
          ...process.env,
          PATH: `${bin}:${process.env.PATH}`,
          GITHUB_WORKSPACE: path.resolve(root, "../.."),
          RUNNER_TEMP: runner,
          K6_SCRIPT: `loadtest/portfolio/api/${row.name}.js`,
          K6_PARAMS_JSON: '{"RPS":100,"DURATION":"5m"}',
          K6_ACCOUNTS_JSON_GZ_BASE64: row.secret,
          K6_ACCOUNTS_JSON_GZ_BASE64_PART_02: row.second || "",
          K6_ACCOUNTS_JSON_GZ_BASE64_MANIFEST: row.manifest || "",
          BASE_URL: "https://dev.daynomy.com",
          K6_PROMETHEUS_RW_SERVER_URL: "http://metrics.invalid/api/v1/write",
          LOADTEST_ID: "secret-fixture",
          FAKE_RESULT_FILE: resultFile,
          FAKE_K6_EXIT: String(row.k6Code),
        },
        encoding: "utf8",
      });
      assert.equal(result.status, row.expected, result.stderr);
      assert.deepEqual(fs.readdirSync(runner), []);
      assert.ok(!result.stdout.includes("fixture-token-only"));
      if (row.secret === secret || row.split) {
        const actual = JSON.parse(fs.readFileSync(resultFile, "utf8"));
        assert.deepEqual(actual.accounts, payload);
        assert.equal(actual.mode, 0o600);
        assert.equal(actual.secretVisible, false);
        assert.equal(actual.script, `portfolio/api/${row.name}.js`);
        assert.equal(path.dirname(actual.path), runner);
        assert.equal(fs.existsSync(actual.path), false);
      } else {
        assert.equal(fs.existsSync(resultFile), false);
      }
    } finally {
      fs.rmSync(directory, { recursive: true, force: true });
    }
  }
});
