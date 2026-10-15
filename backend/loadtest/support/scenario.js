import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";
import { prepare } from "./prepare.js";
import { createReport } from "./report.js";

// Both entry files use the same workload, validation and stop conditions.
export function createScenario(config, users, warmups) {
  const context = prepare(config, users, warmups);
  const { version, origin, tradeDate, rps, rpm } = context;
  const report = createReport(context);
  const {
    duration,
    warmupDuration,
    warmupCompleted,
    started,
    success,
    failed,
    stops,
    measurementEpoch,
    responseEpoch,
    userTags,
  } = report;

  const paths = {
    v1: "/api/users/me/portfolio/transactions",
    v2: "/api/v2/users/me/portfolio/transactions",
  };
  function abort(reason) {
    stops.add(1, { reason });
    exec.test.abort(
      `Stopped: ${reason}. Check server/DB before any restore or retry.`,
    );
  }
  function register(user, csrf, phase, completed) {
    const jar = http.cookieJar();
    jar.set(origin, "access_token", user.accessToken, { path: "/" });
    jar.set(origin, "XSRF-TOKEN", csrf.token, { path: "/" });
    const tags = {
      ...userTags(user),
      phase,
      operation: "transaction",
      name: `${version}_transaction_registration`,
    };
    const sentAt = Date.now();
    if (phase === "measure") {
      measurementEpoch.add(exec.scenario.startTime);
      started.add(1, tags);
    }
    const response = http.post(
      `${origin}${paths[version]}`,
      JSON.stringify({
        assetId: user.assetId,
        type: "BUY",
        tradeDate,
        quantity: 1,
        unitPrice: user.unitPrice,
        fee: 0,
        tax: 0,
      }),
      {
        headers: {
          "Content-Type": "application/json",
          [csrf.headerName]: csrf.token,
        },
        timeout: `${config.requestTimeoutSeconds}s`,
        redirects: 0,
        tags,
      },
    );
    // Includes connection setup as part of the caller's elapsed POST time.
    const elapsed = Date.now() - sentAt;
    if (phase === "measure") {
      duration.add(elapsed, tags);
      responseEpoch.add(Date.now());
    } else warmupDuration.add(elapsed, tags);
    let body;
    try {
      body = response.json();
    } catch (_) {
      /* Never print response bodies or tokens. */
    }
    const valid = check(
      response,
      {
        "transaction created": (r) => r.status === 201,
        "holding quantity matches": () =>
          body &&
          Number.isInteger(body.transactionId) &&
          body.transactionId > 0 &&
          body.holding?.assetId === user.assetId &&
          body.holding?.quantity === user.initialQuantity + completed + 1,
      },
      tags,
    );
    if (phase === "measure") {
      failed.add(valid ? 0 : 1, tags);
      success.add(valid ? 1 : 0, tags);
    }
    if (phase === "warmup" && valid) warmupCompleted.add(1, tags);
    if (!valid)
      abort(
        response.status === 201
          ? "response_mismatch"
          : "http_failure_or_timeout",
      );
  }
  function setup() {
    stops.add(0);
    const response = http.get(`${origin}/api/auth/csrf`, {
      timeout: `${config.requestTimeoutSeconds}s`,
      redirects: 0,
      tags: { phase: "warmup", operation: "csrf", name: "csrf" },
    });
    let csrf;
    try {
      csrf = response.json();
    } catch (_) {
      abort("csrf_invalid");
    }
    if (
      response.status !== 200 ||
      !csrf?.token ||
      !csrf?.headerName ||
      response.cookies["XSRF-TOKEN"]?.[0]?.value !== csrf.token
    )
      abort("csrf_invalid");
    // CookieCsrfTokenRepository is cookie-based, not member-session based.
    // Each VU receives the matching CSRF cookie/header; auth cookies are switched per planned account.
    const deadline = Date.now() + config.warmupSeconds * 1000;
    const counts = [0, 0, 0];
    let i = 0;
    while (Date.now() < deadline || i < 3) {
      const index = i % 3;
      register(warmups[index], csrf, "warmup", counts[index]);
      counts[index]++;
      i++;
      if (Date.now() < deadline)
        sleep(Math.min(1, (deadline - Date.now()) / 1000));
    }
    return {
      csrf: { token: csrf.token, headerName: csrf.headerName },
      warmupCounts: counts,
      measurementStartedAt: new Date().toISOString(),
    };
  }
  function run(data) {
    if (!data?.csrf) abort("setup_missing");
    const index = exec.scenario.iterationInTest;
    // Global iteration index, independent of which VU is free and how fast V1/V2 responds.
    register(
      users[index % users.length],
      data.csrf,
      "measure",
      Math.floor(index / users.length),
    );
  }

  const options = {
    tags: {
      version,
      trade_date: tradeDate,
      rps: String(rps),
      run: config.run,
      ...(__ENV.LOADTEST_ID
        ? { testid: __ENV.LOADTEST_ID, environment: "dev", service: "k6" }
        : {}),
    },
    setupTimeout: `${config.warmupSeconds + 4 * config.requestTimeoutSeconds + 10}s`,
    noCookiesReset: true,
    summaryTrendStats: ["min", "avg", "med", "p(95)", "p(99)", "max"],
    scenarios: {
      transactions: {
        executor: "constant-arrival-rate",
        rate: rpm,
        timeUnit: "1m",
        duration: `${config.stageSeconds}s`,
        preAllocatedVUs: config.vus,
        maxVUs: config.vus,
        gracefulStop: `${config.requestTimeoutSeconds + 2}s`,
      },
    },
    thresholds: report.thresholds,
  };
  return { setup, run, options, handleSummary: report.handleSummary };
}
