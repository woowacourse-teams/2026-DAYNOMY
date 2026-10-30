import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import encoding from "k6/encoding";
import { SharedArray } from "k6/data";
import { Counter, Rate, Trend } from "k6/metrics";

const paths = {
  get: "/api/users/me/portfolio",
  calculate: "/api/portfolio/calculate",
  "transactions-v2": "/api/v2/users/me/portfolio/transactions",
  performance: "/api/users/me/portfolio/performance",
};
const integer = (n, min, max) => Number.isInteger(n) && n >= min && n <= max;
const require = (condition, message) => {
  if (!condition) throw new Error(message);
};
const seoulDate = () =>
  new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10);

export function createPortfolio1000Scenario(operation) {
  require(Object.prototype.hasOwnProperty.call(
    paths,
    operation,
  ), "Unknown portfolio operation");
  const seconds = (value) => {
    const match = /^(\d+)(s|m)$/.exec(value);
    require(match, "Use a duration such as 5m or 30s");
    return Number(match[1]) * (match[2] === "m" ? 60 : 1);
  };
  const origin = (__ENV.BASE_URL || "").replace(/\/$/, "");
  const local =
    __ENV.ALLOW_LOCAL === "true" &&
    /^http:\/\/(localhost|127\.0\.0\.1):\d+$/.test(origin);
  require(local ||
    /^https:\/\/[a-z0-9.-]*\bdev\b[a-z0-9.-]*(?::\d+)?$/.test(
      origin,
    ), "Use the HTTPS dev API origin");
  const rps = Number(__ENV.RPS || 100);
  const duration = seconds(__ENV.DURATION || "5m");
  const warmup = seconds(__ENV.WARMUP_DURATION || "1m");
  const vus = Number(__ENV.VUS || Math.max(100, rps * 2));
  const timeout = Number(__ENV.REQUEST_TIMEOUT_SECONDS || 10);
  const months = Number(__ENV.TRADE_MONTHS_AGO || 1);
  const period = Number(__ENV.PERFORMANCE_MONTHS || 1);
  const run = __ENV.RUN || __ENV.LOADTEST_ID || "1";
  require(integer(rps, 1, 500) &&
    integer(duration, 5, 300) &&
    integer(warmup, 1, 120), "Invalid rate or duration");
  require(integer(vus, 1, 2000) &&
    integer(timeout, 1, 30), "Invalid VU or timeout setting");
  require([0, 1, 6].includes(months) &&
    [1, 6, 12].includes(
      period,
    ), "Unsupported transaction or performance period");
  require(/^[a-zA-Z0-9_-]{1,64}$/.test(run), "Invalid RUN label");
  const expected = rps * duration;
  const accounts = new SharedArray("portfolio-1000-accounts", () => {
    try {
      const parsed = JSON.parse(
        open(__ENV.ACCOUNTS_FILE || "../.local/accounts-1000.json"),
      );
      require(Array.isArray(parsed), "Account file must be an array");
      return parsed.sort((a, b) => a.testNumber - b.testNumber);
    } catch (_) {
      throw new Error("Unable to read the private 1,003-account JSON file");
    }
  });
  require(accounts.length ===
    1003, "Prepare exactly 1,000 measurement and 3 warmup accounts");
  const baseline = accounts[0].baselineHash;
  const evaluationDate = accounts[0].evaluationDate;
  const ids = new Set();
  const tokens = new Set();
  for (const [i, account] of accounts.entries()) {
    const profile =
      i < 1000
        ? ["light", "standard", "light", "standard", "heavy"][i % 5]
        : ["light", "standard", "heavy"][i - 1000];
    require(account.testNumber === i + 1 &&
      account.profile === profile, "Wrong test member order or profile");
    require(account.baseUrl === origin &&
      account.evaluationDate === evaluationDate &&
      evaluationDate ===
        seoulDate(), "Account origin or Seoul evaluation date mismatch; issue fresh accounts");
    require(/^[a-f0-9]{64}$/.test(baseline) &&
      account.baselineHash === baseline &&
      /^lt1000-\d{8}$/.test(
        account.namespace,
      ), "Account baseline metadata mismatch");
    require(integer(account.memberId, 1, Number.MAX_SAFE_INTEGER) &&
      !ids.has(account.memberId) &&
      !tokens.has(account.accessToken), "Duplicate or invalid member/token");
    ids.add(account.memberId);
    tokens.add(account.accessToken);
    require(Array.isArray(account.holdings) &&
      account.holdings.length ===
        { light: 5, standard: 15, heavy: 30 }[
          profile
        ], "Account holding profile mismatch");
    require(account.holdings.every(
      (h) =>
        integer(h.assetId, 1, Number.MAX_SAFE_INTEGER) &&
        integer(h.quantity, 1, Number.MAX_SAFE_INTEGER) &&
        Number.isFinite(h.averagePurchasePrice) &&
        h.averagePurchasePrice > 0,
    ), "Invalid holding input");
    require(integer(account.assetId, 1, Number.MAX_SAFE_INTEGER) &&
      integer(account.initialQuantity, 1, Number.MAX_SAFE_INTEGER) &&
      account.holdings.some(
        (h) =>
          h.assetId === account.assetId &&
          h.quantity === account.initialQuantity,
      ), "Transaction holding mismatch");
    require(["USD", "KRW"].includes(account.currency) &&
      Number.isFinite(account.unitPrice) &&
      account.unitPrice > 0 &&
      Number.isFinite(account.exchangeRate) &&
      account.exchangeRate > 0 &&
      (account.currency === "USD" ||
        account.exchangeRate === 1), "Invalid transaction price or FX rate");
    require(Number.isFinite(
      account.expectedRealizedProfitLoss,
    ), "Missing expected realized profit/loss");
    require(["KR", "US", "MIXED"].includes(
      account.country,
    ), "Missing portfolio country group");
    require(account.transactionBaselines?.[months]?.date &&
      integer(
        account.transactionBaselines[months].historyCount,
        0,
        Number.MAX_SAFE_INTEGER,
      ), "Missing transaction baseline");
    require(account.performancePeriods?.[period]?.to === evaluationDate &&
      account.performancePeriods[period].pointCount >=
        2, "Missing performance baseline");
    let claims;
    try {
      claims = JSON.parse(
        encoding.b64decode(account.accessToken.split(".")[1], "rawurl", "s"),
      );
    } catch (_) {
      throw new Error("Invalid access token format; issue fresh accounts");
    }
    require(claims.sub === String(account.memberId) &&
      claims.role === "USER" &&
      claims.token_type === "access" &&
      claims.exp >
        Date.now() / 1000 +
          warmup +
          duration +
          timeout +
          660, "Token identity or remaining lifetime is insufficient");
  }
  ids.clear();
  tokens.clear();
  // Keep the 1,000 records in SharedArray instead of copying the full file into every VU.
  const users = accounts;
  const warmups = accounts.slice(1000);
  const write = operation === "transactions-v2";
  const started = new Counter("portfolio_started");
  const succeeded = new Counter("portfolio_succeeded");
  const failures = new Rate("portfolio_failure_rate");
  const mismatch = new Counter("portfolio_data_mismatch");
  const latency = new Trend("portfolio_duration", true);
  const integrityChecked = new Counter("portfolio_integrity_checked");
  const integrityFailed = new Counter("portfolio_integrity_failed");

  function jarFor(account, csrf) {
    const jar = new http.CookieJar();
    jar.set(origin, "access_token", account.accessToken, { path: "/" });
    if (csrf) jar.set(origin, "XSRF-TOKEN", csrf.token, { path: "/" });
    return jar;
  }
  function holdingsMatch(body, account, added = 0) {
    return (
      Array.isArray(body?.holdings) &&
      body.holdings.length === account.holdings.length &&
      account.holdings.every((h) =>
        body.holdings.some(
          (actual) =>
            actual.assetId === h.assetId &&
            actual.quantity ===
              h.quantity + (h.assetId === account.assetId ? added : 0),
        ),
      )
    );
  }
  function request(account, csrf, phase) {
    const params = {
      jar: jarFor(account, csrf),
      redirects: 0,
      timeout: `${timeout}s`,
      tags: {
        phase,
        operation,
        profile: account.profile,
        country: account.country,
        currency: account.currency,
        name: `portfolio_1000_${operation}`,
      },
      headers: {
        "Content-Type": "application/json",
        [csrf.headerName]: csrf.token,
      },
    };
    let url = origin + paths[operation];
    if (operation === "performance") {
      const range = account.performancePeriods[period];
      url += `?from=${range.from}&to=${range.to}`;
    }
    const payload =
      operation === "calculate"
        ? { holdings: account.holdings }
        : {
            assetId: account.assetId,
            type: "BUY",
            tradeDate: account.transactionBaselines[months].date,
            quantity: 1,
            unitPrice: account.unitPrice,
            exchangeRate: account.exchangeRate,
            fee: 0,
            tax: 0,
          };
    if (phase === "measure") started.add(1, params.tags);
    const sentAt = Date.now();
    const response =
      operation === "calculate" || write
        ? http.post(url, JSON.stringify(payload), params)
        : http.get(url, params);
    const elapsed = Date.now() - sentAt;
    const correctStatus = response.status === (write ? 201 : 200);
    let body;
    try {
      body = response.json();
    } catch (_) {
      /* Never print response contents or credentials. */
    }
    let valid = false;
    if (operation === "get")
      valid =
        holdingsMatch(body, account) &&
        Math.abs(
          body.totalRealizedProfitLoss - account.expectedRealizedProfitLoss,
        ) < 0.011;
    if (operation === "calculate")
      valid =
        ["COMPLETE", "MIXED_DATES"].includes(body?.pricingStatus) &&
        body.unpricedHoldingCount === 0 &&
        body.pricedHoldingCount === account.holdings.length &&
        Array.isArray(body.holdings) &&
        body.holdings.length === account.holdings.length &&
        Number.isFinite(body.totalEvaluationAmount);
    if (write)
      valid =
        integer(body?.transactionId, 1, Number.MAX_SAFE_INTEGER) &&
        body.holding?.assetId === account.assetId &&
        integer(
          body.holding?.quantity,
          account.initialQuantity + 1,
          Number.MAX_SAFE_INTEGER,
        );
    if (operation === "performance") {
      const range = account.performancePeriods[period];
      valid =
        body?.status === "READY" &&
        Array.isArray(body.points) &&
        body.points.length === range.pointCount &&
        body.baseDate === range.baseDate &&
        body.points.every(
          (p, i) =>
            p.baseDate >= range.from &&
            p.baseDate < range.to &&
            (i === 0 || body.points[i - 1].baseDate < p.baseDate),
        );
    }
    check(
      response,
      {
        "expected HTTP status": () => correctStatus,
        "valid response data": () => correctStatus && valid,
      },
      params.tags,
    );
    if (phase === "measure") {
      latency.add(elapsed, params.tags);
      failures.add(!correctStatus || !valid, params.tags);
      succeeded.add(correctStatus && valid ? 1 : 0, params.tags);
      mismatch.add(correctStatus && !valid ? 1 : 0, params.tags);
    }
    if (response.status === 401 || response.status === 403)
      exec.test.abort(
        "Authentication/CSRF failed; issue fresh matching tokens before retrying.",
      );
    if (phase === "warmup" && (!correctStatus || !valid))
      exec.test.abort(
        "Warmup failed; inspect API status and data prerequisites before measuring.",
      );
  }

  function setup() {
    integrityChecked.add(0);
    integrityFailed.add(0);
    const response = http.get(origin + "/api/auth/csrf", {
      timeout: `${timeout}s`,
      redirects: 0,
      tags: { phase: "setup", name: "portfolio_1000_csrf" },
    });
    let csrf;
    try {
      csrf = response.json();
    } catch (_) {
      exec.test.abort("CSRF response is invalid");
    }
    require(response.status === 200 &&
      csrf?.token &&
      /^[A-Za-z0-9-]+$/.test(csrf.headerName) &&
      response.cookies?.["XSRF-TOKEN"]?.[0]?.value ===
        csrf.token, "CSRF cookie/header mismatch");
    const deadline = Date.now() + warmup * 1000;
    let index = 0;
    while (Date.now() < deadline || index < 3) {
      request(warmups[index % 3], csrf, "warmup");
      index++;
      if (Date.now() < deadline)
        sleep(Math.min(0.25, (deadline - Date.now()) / 1000));
    }
    return { csrf: { token: csrf.token, headerName: csrf.headerName } };
  }

  function runIteration(data) {
    if (seoulDate() !== evaluationDate)
      exec.test.abort(
        "Seoul date changed; issue new account metadata before measuring.",
      );
    const index = exec.scenario.iterationInTest;
    if (index < expected) request(users[index % 1000], data.csrf, "measure");
  }

  function teardown() {
    if (!write) return;
    // HTTP timeout does not cancel server work; any HTTP failure makes final integrity inconclusive.
    sleep(timeout * 2);
    for (let offset = 0; offset < 1000; offset += 10) {
      const batch = users.slice(offset, Math.min(offset + 10, 1000));
      const requests = batch.flatMap((account) => {
        const date = account.transactionBaselines[months].date;
        const params = {
          jar: jarFor(account),
          redirects: 0,
          timeout: `${timeout}s`,
          tags: { phase: "verify", name: "portfolio_1000_integrity" },
        };
        return [
          ["GET", origin + paths.get, null, params],
          [
            "GET",
            `${origin}/api/users/me/portfolio/histories?from=${date}&to=${date}`,
            null,
            params,
          ],
        ];
      });
      const responses = http.batch(requests);
      for (const [i, account] of batch.entries()) {
        const index = offset + i;
        const additions =
          Math.floor(expected / 1000) + (index < expected % 1000 ? 1 : 0);
        let valid = false;
        try {
          const saved = responses[i * 2];
          const history = responses[i * 2 + 1];
          valid =
            saved.status === 200 &&
            history.status === 200 &&
            holdingsMatch(saved.json(), account, additions) &&
            Array.isArray(history.json()) &&
            history.json().length ===
              account.transactionBaselines[months].historyCount + additions;
        } catch (_) {
          /* Keep verification failures out of raw response logs. */
        }
        integrityChecked.add(1);
        integrityFailed.add(valid ? 0 : 1);
      }
    }
  }

  const thresholds = {
    portfolio_started: [`count==${expected}`],
    portfolio_failure_rate: ["rate<0.01"],
    portfolio_data_mismatch: ["count==0"],
    portfolio_duration: ["p(95)<=300", "p(99)<=500"],
    dropped_iterations: ["count==0"],
  };
  for (const profile of ["light", "standard", "heavy"])
    thresholds[`portfolio_duration{profile:${profile}}`] = [
      "p(95)>=0",
      "p(99)>=0",
    ];
  for (const country of ["KR", "US", "MIXED"])
    thresholds[`portfolio_duration{country:${country}}`] = [
      "p(95)>=0",
      "p(99)>=0",
    ];
  if (write) {
    thresholds.portfolio_integrity_checked = ["count==1000"];
    thresholds.portfolio_integrity_failed = ["count==0"];
  }
  const options = {
    tags: {
      operation,
      run,
      baseline: baseline.slice(0, 12),
      ...(__ENV.LOADTEST_ID
        ? { testid: __ENV.LOADTEST_ID, environment: "dev", service: "k6" }
        : {}),
    },
    summaryTrendStats: ["min", "avg", "med", "p(95)", "p(99)", "max"],
    setupTimeout: `${warmup + 4 * timeout + 30}s`,
    teardownTimeout: "10m",
    batch: 20,
    batchPerHost: 20,
    scenarios: {
      measure: {
        executor: "constant-arrival-rate",
        rate: rps,
        timeUnit: "1s",
        duration: `${duration}s`,
        preAllocatedVUs: vus,
        maxVUs: vus,
        gracefulStop: `${timeout + 2}s`,
      },
    },
    thresholds,
  };

  function handleSummary(data) {
    const values = (name) => data.metrics[name]?.values || {};
    const count = (name) => values(name).count || 0;
    const timing = values("portfolio_duration");
    const measured = count("portfolio_started");
    const successful = count("portfolio_succeeded");
    const dropped = count("dropped_iterations");
    const failed = measured - successful;
    const integrity = !write
      ? "not_applicable"
      : failed || dropped || measured !== expected
        ? "inconclusive"
        : count("portfolio_integrity_checked") !== 1000
          ? "incomplete"
          : count("portfolio_integrity_failed")
            ? "failed"
            : "passed";
    const validRun = measured === expected && dropped === 0;
    const pass =
      validRun &&
      (values("portfolio_failure_rate").rate ?? 1) < 0.01 &&
      count("portfolio_data_mismatch") === 0 &&
      Number.isFinite(timing["p(95)"]) &&
      timing["p(95)"] <= 300 &&
      Number.isFinite(timing["p(99)"]) &&
      timing["p(99)"] <= 500 &&
      (!write || integrity === "passed");
    const report = {
      operation,
      rps,
      durationSeconds: duration,
      expected,
      started: measured,
      successful,
      failed,
      dropped,
      successfulRps: successful / duration,
      latency: timing,
      failureRate: values("portfolio_failure_rate").rate ?? null,
      profileLatency: Object.fromEntries(
        ["light", "standard", "heavy"].map((p) => [
          p,
          values(`portfolio_duration{profile:${p}}`),
        ]),
      ),
      countryLatency: Object.fromEntries(
        ["KR", "US", "MIXED"].map((p) => [
          p,
          values(`portfolio_duration{country:${p}}`),
        ]),
      ),
      tradeDate: write ? users[0].transactionBaselines[months].date : null,
      evaluationDate,
      performanceMonths: operation === "performance" ? period : null,
      outcome: !validRun ? "incomplete" : pass ? "passed" : "target_exceeded",
      integrity,
      verification: write
        ? "Holding quantities and per-day history counts; not a full monetary snapshot audit"
        : null,
      restoreRequired: write,
    };
    const filename = `result-portfolio-1000-${operation}-${rps}rps-${duration}s-${run}.json`;
    return {
      [filename]: JSON.stringify(report, null, 2) + "\n",
      stdout: `${operation}: ${report.outcome}; success ${successful}/${expected}; dropped ${dropped}; p95=${timing["p(95)"] ?? "n/a"}ms p99=${timing["p(99)"] ?? "n/a"}ms; integrity=${integrity}\n`,
    };
  }
  return { options, setup, run: runIteration, teardown, handleSummary };
}
