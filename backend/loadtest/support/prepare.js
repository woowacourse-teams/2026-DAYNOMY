import encoding from "k6/encoding";

// Validate the fixed workload and private fixture before any HTTP request.
export function prepare(config, users, warmups) {
  const version = config.version;
  const require = (condition, message) => {
    if (!condition) throw new Error(message);
  };
  const integer = (n, min, max) => Number.isInteger(n) && n >= min && n <= max;
  const localPath = (s) =>
    typeof s === "string" &&
    /^\.local\/[A-Za-z0-9_./-]+$/.test(s) &&
    !s.split("/").includes("..");
  const origin = config.baseUrl;
  require(/^https:\/\/[a-z0-9.-]*\bdev\b[a-z0-9.-]*(?::\d+)?$/.test(
    origin,
  ), "Use an HTTPS dev API origin (no credentials, path or query)");
  require(["v1", "v2"].includes(version), "version must be v1 or v2");
  require(/^\d{4}-\d{2}-\d{2}$/.test(config.referenceDate) &&
    new Date(config.referenceDate).toISOString().slice(0, 10) ===
      config.referenceDate, "Invalid referenceDate");
  require(integer(
    config.tradeMonthsAgo,
    1,
    6,
  ), "tradeMonthsAgo must be within 1..6");
  const reference = new Date(`${config.referenceDate}T00:00:00Z`);
  const month = new Date(
    Date.UTC(
      reference.getUTCFullYear(),
      reference.getUTCMonth() - config.tradeMonthsAgo,
      1,
    ),
  );
  const lastDay = new Date(
    Date.UTC(month.getUTCFullYear(), month.getUTCMonth() + 1, 0),
  ).getUTCDate();
  month.setUTCDate(Math.min(reference.getUTCDate(), lastDay));
  const tradeDate = month.toISOString().slice(0, 10);
  const rps = Number(__ENV.RUN_RPS || config.rpsSteps?.[0]);
  const rpm = Math.round(rps * 60);
  require(Array.isArray(config.rpsSteps) &&
    config.rpsSteps.length >= 1 &&
    config.rpsSteps.length <= 10 &&
    config.rpsSteps.every(
      (rate, i) =>
        Number.isFinite(rate) &&
        rate > 0 &&
        rate <= 20 &&
        Math.abs(rate * 60 - Math.round(rate * 60)) < 1e-8 &&
        (i === 0 || rate > config.rpsSteps[i - 1]),
    ) &&
    config.rpsSteps.includes(
      rps,
    ), "Use increasing rpsSteps, expressible as whole requests/minute");
  require(integer(config.stageSeconds, 10, 3600) &&
    integer(config.warmupSeconds, 1, 600) &&
    integer(config.vus, 1, 500) &&
    integer(config.requestTimeoutSeconds, 1, 60) &&
    integer(
      config.targetP95Ms,
      1,
      config.requestTimeoutSeconds * 1000,
    ), "Invalid duration, VU or response-time settings");
  const planned = rps * config.stageSeconds;
  const expected = Math.round(planned);
  require(Math.abs(planned - expected) < 1e-8 &&
    expected <=
      5000, "Each stage must plan a whole number of requests, at most 5000");
  const summaryFile = __ENV.SUMMARY_FILE || ".local/stage-result.json";
  require(localPath(summaryFile) &&
    summaryFile.endsWith(".json"), "Invalid summary output path");
  require(users.length === 50 &&
    Array.isArray(warmups) &&
    warmups.length ===
      3, "Prepare 50 measurement accounts and 3 warmup accounts");
  // No account may be selected again while its previous client request is outstanding.
  require(rps * (config.requestTimeoutSeconds + 1) <
    users.length, "Rate/timeout would overlap accounts; reduce rate or timeout before extending this 50-account fixture");
  const profiles = ["light", "standard", "light", "standard", "heavy"];
  const all = [...users, ...warmups];
  require(new Set(all.map((u) => u.memberId)).size === 53 &&
    new Set(all.map((u) => u.accessToken)).size ===
      53, "Duplicate accounts/tokens");
  for (const [index, user] of all.entries()) {
    require(user.testNumber === index + 1 &&
      user.profile ===
        (index < 50
          ? profiles[index % 5]
          : ["light", "standard", "heavy"][
              index - 50
            ]), "Account order/profile does not match the 50+3 fixture");
    require(integer(user.memberId, 1, Number.MAX_SAFE_INTEGER) &&
      integer(user.assetId, 1, Number.MAX_SAFE_INTEGER) &&
      integer(user.initialQuantity, 1, Number.MAX_SAFE_INTEGER) &&
      Number.isFinite(user.unitPrice) &&
      user.unitPrice > 0 &&
      user.baseUrl === origin &&
      user.referenceDate === config.referenceDate &&
      /^[a-f0-9]{64}$/.test(user.fixtureHash) &&
      user.fixtureHash === all[0].fixtureHash &&
      user.earliestHistoryDate <= tradeDate &&
      user.latestSnapshotDate >=
        tradeDate, "Invalid account data, baseline, reference date or history/snapshot coverage");
    let claims;
    try {
      claims = JSON.parse(
        encoding.b64decode(user.accessToken.split(".")[1], "rawurl", "s"),
      );
    } catch (_) {
      throw new Error(
        "Invalid access token; issue fresh tokens with the preparation tool",
      );
    }
    require(claims.sub === String(user.memberId) &&
      claims.role === "USER" &&
      claims.token_type === "access" &&
      claims.exp >
        Date.now() / 1000 +
          config.warmupSeconds +
          config.stageSeconds +
          2 * config.requestTimeoutSeconds +
          60, "Token identity or remaining lifetime is insufficient");
  }

  return {
    config,
    version,
    origin,
    tradeDate,
    rps,
    rpm,
    expected,
    summaryFile,
    users,
    warmups,
    all,
  };
}
