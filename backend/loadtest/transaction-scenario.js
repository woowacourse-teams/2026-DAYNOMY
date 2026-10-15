import http from "k6/http";
import { check } from "k6";
import exec from "k6/execution";
import { SharedArray } from "k6/data";
import { Counter, Rate, Trend } from "k6/metrics";

// 실행 파일은 버전만 고정한다. 데이터·부하·요청·검증 로직은 공통으로 유지한다.
export function createTransactionScenario(version) {
  const fixture = JSON.parse(
    open(__ENV.FIXTURE_FILE || "./.local/fixture.json"),
  );
  const members = new SharedArray("members", () => fixture.members);
  const baseUrl = __ENV.BASE_URL || "http://127.0.0.1:8496";
  const transactionPaths = {
    v1: "/api/users/me/portfolio/transactions",
    v2: "/api/v2/users/me/portfolio/transactions",
  };
  const scenario = __ENV.SCENARIO || "today";
  const side = __ENV.SIDE || "BUY";
  const vus = Number(__ENV.VUS || 1);
  const iterations = Number(__ENV.ITERATIONS || 5);
  const transactionDuration = new Trend("transaction_duration", true);
  const transactionSuccess = new Counter("transaction_success");
  const transactionErrors = new Rate("transaction_errors");
  let csrf;

  if (!/^http:\/\/(127\.0\.0\.1|localhost):8496$/.test(baseUrl)) {
    throw new Error(
      "This fixture targets only the dedicated local loadtest server",
    );
  }
  if (!Object.prototype.hasOwnProperty.call(transactionPaths, version)) {
    throw new Error("VERSION must be v1 or v2");
  }
  if (!fixture.cases[scenario] || !["BUY", "SELL"].includes(side)) {
    throw new Error("Invalid SCENARIO or SIDE");
  }
  if (
    !Number.isInteger(vus) ||
    vus < 1 ||
    vus > members.length ||
    !Number.isInteger(iterations) ||
    iterations < 1 ||
    iterations > fixture.maxIterations
  ) {
    throw new Error("VUS/ITERATIONS exceed fixture limits");
  }
  if (
    fixture.asOf !==
      new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10) ||
    fixture.tokenExpiresAt <= Date.now() / 1000 + 600
  ) {
    throw new Error("Fixture date or token is stale. Prepare and seed again");
  }

  const options = {
    tags: { version },
    scenarios: {
      // V1/V2에서 회원별 등록 건수를 같게 유지한다.
      transactions: {
        executor: "per-vu-iterations",
        vus,
        iterations,
        maxDuration: "30m",
      },
    },
    noCookiesReset: true,
    summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
    thresholds: {
      checks: ["rate==1"],
      transaction_errors: ["rate==0"],
      transaction_success: [`count==${vus * iterations}`],
      "http_req_failed{operation:transaction}": ["rate==0"],
    },
  };

  function run() {
    // 서로 다른 회원을 사용해 회원별 잠금 경합과 전체 서버 부하를 구분한다.
    const member = members[__VU - 1];
    const jar = http.cookieJar();
    if (!csrf) {
      // CSRF acquisition is anonymous and outside the POST duration metric.
      const response = http.get(`${baseUrl}/api/auth/csrf`, {
        tags: { name: "csrf", operation: "csrf" },
        timeout: "30s",
        redirects: 0,
      });
      if (response.status !== 200) exec.test.abort("CSRF acquisition failed");
      csrf = response.json();
      if (!csrf.token || !csrf.headerName)
        exec.test.abort("Invalid CSRF response");
      jar.set(baseUrl, "access_token", member.accessToken, { path: "/" });
    }

    const response = http.post(
      `${baseUrl}${transactionPaths[version]}`,
      JSON.stringify({
        assetId: fixture.assetId,
        type: side,
        tradeDate: fixture.cases[scenario],
        quantity: 1,
        unitPrice: fixture.unitPrice,
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
          period: scenario,
          side,
        },
        timeout: "180s",
        redirects: 0,
      },
    );
    transactionDuration.add(response.timings.duration);
    let body = null;
    try {
      body = response.json();
    } catch (_) {
      /* Failure is counted below, without logging bodies. */
    }
    const sign = side === "BUY" ? 1 : -1;
    const valid = check(response, {
      "transaction created (201)": (r) => r.status === 201,
      "holding and realized profit match": () =>
        body &&
        Number.isInteger(body.transactionId) &&
        body.transactionId > 0 &&
        body.holding &&
        body.holding.assetId === fixture.assetId &&
        body.holding.quantity ===
          member.initialQuantity + sign * (__ITER + 1) &&
        body.holding.averagePurchasePrice === fixture.unitPrice &&
        body.totalRealizedProfitLoss === 0,
    });
    transactionErrors.add(!valid);
    if (valid) transactionSuccess.add(1);
    // A failed write makes subsequent expected quantities ambiguous; don't report those as samples.
    if (!valid) {
      const reason =
        response.status === 404
          ? `${version.toUpperCase()} API is not implemented or deployed: ${transactionPaths[version]}`
          : `${version.toUpperCase()} registration failed with HTTP ${response.status}`;
      exec.test.abort(reason);
    }
  }

  function handleSummary(data) {
    const output = __ENV.SUMMARY_FILE || `./.local/summary-${version}.json`;
    const latency = data.metrics.transaction_duration?.values;
    const success = data.metrics.transaction_success?.values;
    const errors = data.metrics.transaction_errors?.values;
    const expectedRequests = vus * iterations;
    const successfulRequests = success?.count || 0;
    const validForComparison =
      successfulRequests === expectedRequests && errors?.rate === 0;
    const milliseconds = (value) =>
      Number.isFinite(value) ? `${value.toFixed(2)} ms` : "N/A";
    const text = [
      `${version.toUpperCase()} 거래 등록: ${scenario} / ${side} / ${vus} VUs × ${iterations}건`,
      `측정 결과: ${validForComparison ? "정상 완료" : "실패 — 성능 비교에서 제외"}`,
      `정상 완료: ${successfulRequests} / ${expectedRequests}건`,
      `성공 처리량: ${success ? success.rate.toFixed(2) : "0.00"}건/초`,
      `검증 실패율: ${errors ? (errors.rate * 100).toFixed(2) + "%" : "N/A"}`,
      `POST 평균: ${milliseconds(latency?.avg)}`,
      `POST p95: ${milliseconds(latency?.["p(95)"])}`,
      `POST p99: ${milliseconds(latency?.["p(99)"])}`,
      `결과 파일: ${output}`,
      "",
    ].join("\n");
    return {
      [output]: JSON.stringify(
        {
          config: {
            version,
            transactionPath: transactionPaths[version],
            fixtureHash: fixture.fixtureHash,
            scenario,
            side,
            vus,
            iterationsPerVu: iterations,
            expectedRequests: vus * iterations,
          },
          result: { validForComparison, successfulRequests, expectedRequests },
          metrics: data.metrics,
        },
        null,
        2,
      ),
      stdout: text,
    };
  }

  return { options, run, handleSummary };
}
