import http from "k6/http";
import execution from "k6/execution";
import crypto from "k6/crypto";
import encoding from "k6/encoding";
import { Counter, Trend } from "k6/metrics";

// Synthetic lab-only identity. Never use this test key on a deployed server.
const primaryBase = __ENV.BASE_URL;
const secondaryBase = __ENV.SECONDARY_BASE_URL;
if (!/^http:\/\/daynomy-memory-[0-9]+-app:8080$/.test(primaryBase || "")) {
  throw new Error("This workload is restricted to its internal Docker lab");
}
if (secondaryBase && secondaryBase !== primaryBase.replace(/:8080$/, ":8082")) {
  throw new Error(
    "Secondary target must be the same internal lab on port 8082",
  );
}
const duration = Number(__ENV.DURATION_SECONDS || 120);
const failed = new Counter("lab_failures");
const succeeded = new Counter("lab_successes");
const latency = new Trend("lab_duration", true);
const perOperation = Object.fromEntries(
  ["trade", "calculate", "read"].map((name) => [
    name,
    new Trend(`lab_${name}_duration`, true),
  ]),
);
export const options = {
  scenarios: {
    mixed: {
      executor: "constant-arrival-rate",
      rate: 10,
      timeUnit: "1s",
      duration: `${duration}s`,
      preAllocatedVUs: 100,
      maxVUs: 100,
      gracefulStop: "16s",
    },
  },
  thresholds: {
    dropped_iterations: [
      { threshold: "count==0", abortOnFail: true, delayAbortEval: "10s" },
    ],
    lab_failures: [
      { threshold: "count==0", abortOnFail: true, delayAbortEval: "10s" },
    ],
  },
  summaryTrendStats: ["avg", "p(95)", "p(99)", "max"],
};
function token(member) {
  const now = Math.floor(Date.now() / 1000);
  const header = encoding.b64encode(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
    "rawurl",
  );
  const claims = encoding.b64encode(
    JSON.stringify({
      iss: "daynomy",
      sub: String(member),
      iat: now,
      exp: now + 3600,
      token_type: "access",
      role: "USER",
      sid: "00000000-0000-0000-0000-000000000001",
    }),
    "rawurl",
  );
  const data = `${header}.${claims}`;
  const signature = crypto.hmac(
    "sha256",
    "0123456789abcdef0123456789abcdef",
    data,
    "base64rawurl",
  );
  return `${data}.${signature}`;
}
export default function () {
  const sequence = execution.scenario.iterationInTest;
  if (sequence >= duration * 10) return;
  const base =
    secondaryBase && Math.floor(sequence / 20) % 2
      ? secondaryBase
      : primaryBase;
  const member = 920001 + (Math.floor(sequence / 20) % 50);
  const access = token(member);
  const jar = http.cookieJar();
  jar.set(base, "access_token", access);
  const csrf = http.get(`${base}/api/auth/csrf`, {
    timeout: "15s",
    tags: { name: "lab-csrf" },
  });
  const csrfToken = csrf.cookies["XSRF-TOKEN"]?.[0]?.value;
  if (csrf.status !== 200 || !csrfToken) {
    failed.add(1);
    throw new Error("Lab CSRF preparation failed");
  }
  const params = {
    timeout: "15s",
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": decodeURIComponent(csrfToken),
    },
    tags: { name: "lab-portfolio" },
  };
  let operation,
    response,
    ok = false;
  const started = Date.now();
  if (sequence % 20 === 0) {
    operation = "trade";
    const date = new Date();
    date.setUTCDate(date.getUTCDate() - 20);
    response = http.post(
      `${base}/api/v2/users/me/portfolio/transactions`,
      JSON.stringify({
        assetId: 910001,
        type: "BUY",
        tradeDate: date.toISOString().slice(0, 10),
        quantity: 1,
        unitPrice: 10000,
        fee: 0,
        tax: 0,
        exchangeRate: 1,
      }),
      params,
    );
    try {
      ok = response.status === 201 && response.json("transactionId") > 0;
    } catch (_) {}
  } else if (sequence % 20 < 10) {
    operation = "calculate";
    response = http.post(
      `${base}/api/portfolio/calculate`,
      JSON.stringify({
        holdings: Array.from({ length: 30 }, (_, i) => ({
          assetId: 910001 + i,
          quantity: 108,
          averagePurchasePrice: 10000,
        })),
      }),
      params,
    );
    try {
      ok =
        response.status === 200 &&
        response.json("pricedHoldingCount") === 30 &&
        response.json("totalEvaluationAmount") === 35640000;
    } catch (_) {}
  } else {
    operation = "read";
    response = http.get(`${base}/api/users/me/portfolio`, params);
    try {
      ok = response.status === 200 && response.json("holdings").length === 30;
    } catch (_) {}
  }
  const elapsed = Date.now() - started;
  latency.add(elapsed);
  perOperation[operation].add(elapsed);
  if (ok) succeeded.add(1);
  else failed.add(1);
  console.log(
    JSON.stringify({
      kind: "memory_portfolio",
      sequence,
      member,
      operation,
      port: Number(base.split(":").pop()),
      status: response.status,
      ok,
      elapsed_ms: elapsed,
    }),
  );
}
export function handleSummary(data) {
  const values = (name) => data.metrics[name]?.values || {};
  const result = {
    planned: duration * 10,
    successes: values("lab_successes").count || 0,
    failures: values("lab_failures").count || 0,
    dropped: values("dropped_iterations").count || 0,
    operations: Object.fromEntries(
      ["trade", "calculate", "read"].map((x) => [
        x,
        values(`lab_${x}_duration`),
      ]),
    ),
  };
  result.completed_requests = result.successes + result.failures;
  result.dropped_iterations = result.dropped;
  result.complete =
    result.completed_requests === result.planned && result.dropped === 0;
  return {
    [__ENV.SUMMARY_FILE || "/out/summary.json"]: JSON.stringify(
      result,
      null,
      2,
    ),
    stdout: JSON.stringify(result) + "\n",
  };
}
