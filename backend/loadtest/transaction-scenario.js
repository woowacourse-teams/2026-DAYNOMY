import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { SharedArray } from "k6/data";
import { Counter, Trend } from "k6/metrics";

export function createTransactionScenario(version) {
  const baseUrl = (__ENV.BASE_URL || "").replace(/\/$/, "");
  const tradeDate = __ENV.TRADE_DATE || "";
  const side = __ENV.SIDE || "BUY";
  const vus = Number(__ENV.VUS || 1);
  const iterations = Number(__ENV.ITERATIONS || 1);
  const users = new SharedArray("users", () =>
    JSON.parse(open(__ENV.USERS_FILE || "./.local/users.json")),
  );
  const paths = {
    v1: "/api/users/me/portfolio/transactions",
    v2: "/api/v2/users/me/portfolio/transactions",
  };
  if (!/^https?:\/\/[^/?#]+$/.test(baseUrl) || !paths[version]) {
    throw new Error("Set BASE_URL to the API origin (without an /api suffix)");
  }
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(tradeDate) ||
    Number.isNaN(Date.parse(tradeDate)) ||
    new Date(tradeDate).toISOString().slice(0, 10) !== tradeDate ||
    !["BUY", "SELL"].includes(side)
  ) {
    throw new Error("Set TRADE_DATE=YYYY-MM-DD and SIDE=BUY or SELL");
  }
  if (
    !Number.isInteger(vus) ||
    vus < 1 ||
    vus > users.length ||
    !Number.isInteger(iterations) ||
    iterations < 1
  ) {
    throw new Error("VUS must fit the users file; ITERATIONS must be positive");
  }
  const selected = Array.from({ length: vus }, (_, index) => users[index]);
  if (
    new Set(selected.map((user) => user.memberId)).size !== vus ||
    selected.some(
      (user) =>
        !Number.isInteger(user.memberId) ||
        user.memberId <= 0 ||
        !user.accessToken ||
        !Number.isInteger(user.assetId) ||
        user.assetId <= 0 ||
        !Number.isFinite(user.unitPrice) ||
        user.unitPrice <= 0 ||
        (user.initialQuantity !== undefined &&
          (!Number.isInteger(user.initialQuantity) ||
            user.initialQuantity < 0 ||
            (side === "SELL" && user.initialQuantity < iterations))),
    )
  ) {
    throw new Error(
      "Provide distinct test accounts with valid asset/price/quantity data",
    );
  }

  const duration = new Trend("transaction_duration", true);
  const success = new Counter("transaction_success");
  let csrf;

  function run() {
    const user = users[__VU - 1];
    if (!csrf) {
      const response = http.get(`${baseUrl}/api/auth/csrf`, {
        tags: { name: "csrf", operation: "csrf" },
        timeout: "30s",
        redirects: 0,
      });
      if (response.status !== 200) exec.test.abort("CSRF acquisition failed");
      csrf = response.json();
      if (!csrf.token || !csrf.headerName)
        exec.test.abort("Invalid CSRF response");
      http
        .cookieJar()
        .set(baseUrl, "access_token", user.accessToken, { path: "/" });
    }
    const response = http.post(
      `${baseUrl}${paths[version]}`,
      JSON.stringify({
        assetId: user.assetId,
        type: side,
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
        tags: {
          name: `${version}_transaction_registration`,
          operation: "transaction",
          side,
        },
        timeout: "180s",
        redirects: 0,
      },
    );
    duration.add(response.timings.duration);
    let body;
    try {
      body = response.json();
    } catch (_) {
      /* Count invalid responses below. */
    }
    const expectedQuantity =
      user.initialQuantity === undefined
        ? undefined
        : user.initialQuantity + (side === "BUY" ? 1 : -1) * (__ITER + 1);
    const valid = check(response, {
      "transaction created (201)": (r) => r.status === 201,
      "transaction response matches": () =>
        body &&
        Number.isInteger(body.transactionId) &&
        body.transactionId > 0 &&
        (body.holding == null
          ? side === "SELL"
          : body.holding.assetId === user.assetId) &&
        (expectedQuantity === undefined ||
          (expectedQuantity === 0
            ? body.holding === null
            : body.holding?.quantity === expectedQuantity)),
    });
    if (!valid)
      exec.test.abort(
        `${version.toUpperCase()} transaction validation failed (HTTP ${response.status})`,
      );
    success.add(1);
  }

  return {
    options: {
      tags: { version, side, trade_date: tradeDate },
      scenarios: {
        transactions: {
          executor: "per-vu-iterations",
          vus,
          iterations,
          maxDuration: "30m",
        },
      },
      noCookiesReset: true,
      summaryTrendStats: ["avg", "med", "p(95)", "p(99)", "max"],
      thresholds: {
        checks: ["rate==1"],
        transaction_success: [`count==${vus * iterations}`],
        "http_req_failed{operation:transaction}": ["rate==0"],
      },
    },
    run,
  };
}
