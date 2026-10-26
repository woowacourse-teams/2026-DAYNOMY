import { Counter, Trend } from "k6/metrics";

// Warmup metrics are separate; measured POSTs are also grouped by portfolio size.
export function createReport(context) {
  const {
    config,
    version,
    origin,
    tradeDate,
    rps,
    expected,
    summaryFile,
    users,
    all,
    stress,
  } = context;
  const duration = new Trend("transaction_duration", true);
  const warmupDuration = new Trend("warmup_duration", true);
  const warmupCompleted = new Counter("warmup_completed");
  const started = new Counter("transaction_started");
  const success = new Counter("transaction_success");
  const failed = new Counter("transaction_failed");
  const stops = new Counter("safety_stops");
  const measurementEpoch = new Trend("measurement_epoch_ms", true);
  const responseEpoch = new Trend("response_epoch_ms", true);
  const userTags = (user) => ({
    account: `lt-${String(user.testNumber).padStart(3, "0")}`,
    profile: user.profile,
  });
  const stopReasons = [
    "csrf_invalid",
    "setup_missing",
    "response_mismatch",
    "http_failure_or_timeout",
  ];
  const thresholds = {
    "checks{phase:warmup}": ["rate==1"],
    transaction_started: [`count==${Math.round(expected)}`],
    transaction_success: [`count==${Math.round(expected)}`],
    transaction_failed: ["count==0"],
    transaction_duration: [`p(95)<=${config.targetP95Ms}`],
    safety_stops: ["count==0"],
    dropped_iterations: stress
      ? ["count==0"]
      : [{ threshold: "count==0", abortOnFail: true }],
  };
  if (stress) {
    thresholds.stress_verification_checked = ["count==50"];
    thresholds.stress_verification_failed = ["count==0"];
    thresholds.stress_committed = [`count==${expected}`];
    for (const [index, user] of users.entries()) {
      const planned =
        Math.floor(expected / users.length) +
        (index < expected % users.length ? 1 : 0);
      thresholds[`stress_committed{account:${userTags(user).account}}`] = [
        `count==${planned}`,
      ];
    }
  }
  for (const reason of stopReasons)
    thresholds[`safety_stops{reason:${reason}}`] = ["count==0"];
  // Threshold submetrics make account counts (including zero) available to handleSummary.
  for (const user of users) {
    const selector = `{account:${userTags(user).account}}`;
    for (const name of [
      "transaction_started",
      "transaction_success",
      "transaction_failed",
    ])
      thresholds[name + selector] = ["count>=0"];
  }
  for (const profile of ["light", "standard", "heavy"]) {
    thresholds[`warmup_completed{profile:${profile}}`] = ["count>=1"];
    thresholds[`transaction_duration{profile:${profile}}`] = ["p(95)>=0"];
    thresholds[`warmup_duration{profile:${profile}}`] = ["p(95)>=0"];
  }
  function handleSummary(data) {
    const count = (name) => data.metrics[name]?.values.count || 0;
    const latency = data.metrics.transaction_duration?.values || {};
    const accounts = Array.from(users, (user) => {
      const account = userTags(user).account;
      const metric = (name) => count(`${name}{account:${account}}`);
      return {
        account,
        profile: user.profile,
        started: metric("transaction_started"),
        successful: metric("transaction_success"),
        failed: metric("transaction_failed"),
      };
    });
    const sent = count("transaction_started");
    const succeeded = count("transaction_success");
    const errors = count("transaction_failed");
    const dropped = count("dropped_iterations");
    const stopped = count("safety_stops");
    let outcome = "exploratory_pass";
    if (stopped || dropped || sent !== expected || succeeded + errors !== sent)
      outcome = "incomplete";
    else if (
      errors ||
      !Number.isFinite(latency["p(95)"]) ||
      latency["p(95)"] > config.targetP95Ms
    )
      outcome = "target_exceeded";
    let integrity;
    if (stress) {
      const checked = count("stress_verification_checked");
      const failures = count("stress_verification_failed");
      const commits = count("stress_committed");
      for (const account of accounts)
        account.committed = count(
          `stress_committed{account:${account.account}}`,
        );
      const countsMatch = accounts.every(
        (account) =>
          account.committed >= account.successful &&
          account.committed <= account.started,
      );
      const status =
        checked !== users.length
          ? "incomplete"
          : errors
            ? "inconclusive"
            : failures || !countsMatch || commits !== succeeded
              ? "failed"
              : "passed";
      integrity = {
        status,
        checked,
        failures,
        committed: commits,
        requiresServerDrain: errors > 0,
      };
      if (status === "incomplete") outcome = "incomplete";
      else if (status === "failed") outcome = "integrity_failed";
    }
    const profiles = ["light", "standard", "heavy"].map((profile) => {
      const selected = accounts.filter((a) => a.profile === profile);
      return {
        profile,
        started: selected.reduce((n, a) => n + a.started, 0),
        successful: selected.reduce((n, a) => n + a.successful, 0),
        failed: selected.reduce((n, a) => n + a.failed, 0),
        accountsUsed: selected.filter((a) => a.started > 0).length,
        latency:
          data.metrics[`transaction_duration{profile:${profile}}`]?.values ||
          {},
      };
    });
    const measurementStart = data.metrics.measurement_epoch_ms?.values.min;
    const measurementEnd = data.metrics.response_epoch_ms?.values.max;
    const elapsedSeconds = Math.max(
      config.stageSeconds,
      (measurementEnd - measurementStart) / 1000 || 0,
    );
    const report = {
      schemaVersion: 1,
      version,
      ...(stress ? { workload: "stress_hot_accounts", integrity } : {}),
      tradeDate,
      referenceDate: config.referenceDate,
      baseUrl: origin,
      fixtureHash: all[0].fixtureHash,
      config,
      rps,
      expected,
      started: sent,
      successful: succeeded,
      errors,
      droppedIterations: dropped,
      safetyStops: stopped,
      stopReasons: stopReasons.filter(
        (reason) => count(`safety_stops{reason:${reason}}`) > 0,
      ),
      startedPerSecond: sent / config.stageSeconds,
      // This denominator includes draining the final requests, not merely the arrival window.
      successfulPerSecond: succeeded / elapsedSeconds,
      measurementStartedAt: Number.isFinite(measurementStart)
        ? new Date(measurementStart).toISOString()
        : null,
      measurementFinishedAt: Number.isFinite(measurementEnd)
        ? new Date(measurementEnd).toISOString()
        : null,
      measurementSecondsIncludingDrain: elapsedSeconds,
      latency,
      accounts,
      profiles,
      warmup: {
        completed: count("warmup_completed"),
        duration: data.metrics.warmup_duration?.values || {},
        profiles: ["light", "standard", "heavy"].map((profile) => ({
          profile,
          completed: count(`warmup_completed{profile:${profile}}`),
          latency:
            data.metrics[`warmup_duration{profile:${profile}}`]?.values || {},
        })),
      },
      accountsUsed: accounts.filter((a) => a.started > 0).length,
      vusAllocated: config.vus,
      outcome,
      finishedAt: new Date().toISOString(),
      note: "Exploratory result. Low sample percentiles are unstable. POST elapsed time excludes CSRF/warmup. Full snapshot equivalence is checked by backend regression tests, not this load script. Stopping the client does not cancel DB work.",
    };
    return {
      stdout: `${version} ${tradeDate} ${rps} RPS: ${outcome}; started=${sent}/${expected}, success=${succeeded}, errors=${errors}, dropped=${dropped}, p95=${latency["p(95)"] ?? "n/a"}ms\n`,
      [summaryFile]: JSON.stringify(report, null, 2) + "\n",
    };
  }

  return {
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
    thresholds,
    handleSummary,
  };
}
