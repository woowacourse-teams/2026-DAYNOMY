import http from "k6/http";
import exec from "k6/execution";
import { Counter } from "k6/metrics";

// Concurrent writes can finish out of arrival order; verify persisted histories and quantities.
export function createStressVerification(context) {
  const { config, origin, tradeDate, users, expected } = context;
  const checked = new Counter("stress_verification_checked");
  const failed = new Counter("stress_verification_failed");
  const committed = new Counter("stress_committed");

  function read(user) {
    const jar = http.cookieJar();
    jar.set(origin, "access_token", user.accessToken, { path: "/" });
    const options = {
      timeout: `${config.requestTimeoutSeconds}s`,
      redirects: 0,
      tags: {
        phase: "verification",
        operation: "verification",
        name: "portfolio_verification",
      },
    };
    const portfolio = http.get(`${origin}/api/users/me/portfolio`, options);
    const histories = http.get(
      `${origin}/api/users/me/portfolio/histories?from=${tradeDate}&to=${tradeDate}`,
      options,
    );
    try {
      const holdings = portfolio.json()?.holdings;
      const rows = histories.json();
      if (
        portfolio.status !== 200 ||
        histories.status !== 200 ||
        !Array.isArray(holdings) ||
        !Array.isArray(rows) ||
        holdings.some(
          (h) => !Number.isInteger(h.assetId) || !Number.isInteger(h.quantity),
        ) ||
        rows.some((h) => !Number.isInteger(h.historyId)) ||
        new Set(holdings.map((h) => h.assetId)).size !== holdings.length ||
        new Set(rows.map((h) => h.historyId)).size !== rows.length
      )
        return null;
      return {
        holdings: holdings.map((h) => ({
          assetId: h.assetId,
          quantity: h.quantity,
        })),
        rows,
      };
    } catch (_) {
      return null; // Never print response bodies or auth cookies.
    }
  }

  function capture() {
    return users.map((user) => {
      const state = read(user);
      if (
        !state ||
        state.holdings.find((h) => h.assetId === user.assetId)?.quantity !==
          user.initialQuantity
      )
        exec.test.abort(
          "Stress baseline unavailable or changed; restore test data before retrying.",
        );
      return state;
    });
  }

  function verify(baseline) {
    for (const [index, user] of users.entries()) {
      const tags = {
        account: `lt-${String(user.testNumber).padStart(3, "0")}`,
        profile: user.profile,
      };
      const original = baseline?.[index];
      const state = read(user);
      let valid = Boolean(original && state);
      let added = [];
      if (valid) {
        const oldRows = new Map(
          original.rows.map((row) => [row.historyId, row]),
        );
        const currentRows = new Map(
          state.rows.map((row) => [row.historyId, row]),
        );
        added = state.rows.filter((row) => !oldRows.has(row.historyId));
        valid =
          original.rows.every((row) => {
            const current = currentRows.get(row.historyId);
            const keys = Object.keys(row);
            // k6 can reorder JSON keys when transferring setup data to teardown.
            return (
              current &&
              keys.length === Object.keys(current).length &&
              keys.every(
                (key) =>
                  Object.prototype.hasOwnProperty.call(current, key) &&
                  row[key] === current[key],
              )
            );
          }) &&
          added.length <= Math.ceil(expected / users.length) &&
          added.every(
            (row) =>
              row.assetId === user.assetId &&
              row.changeType === "BUY" &&
              row.transactionQuantity === 1 &&
              row.unitPrice === user.unitPrice &&
              row.fee === 0 &&
              row.tax === 0 &&
              row.effectiveDate === tradeDate,
          ) &&
          state.holdings.length === original.holdings.length &&
          original.holdings.every(
            (holding) =>
              state.holdings.find((h) => h.assetId === holding.assetId)
                ?.quantity ===
              holding.quantity +
                (holding.assetId === user.assetId ? added.length : 0),
          );
      }
      checked.add(1, tags);
      failed.add(valid ? 0 : 1, tags);
      committed.add(added.length, tags);
    }
  }
  return { capture, verify };
}
