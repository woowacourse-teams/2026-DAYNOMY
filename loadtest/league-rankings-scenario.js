import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

export function createScenario(version) {
  const config = {
    version,
    baseUrl: (__ENV.BASE_URL || '').replace(/\/$/, ''),
    // shortcut: deployment SHA is supplied manually; automate verification when deployment metadata is exposed.
    deployedSha: __ENV.DEPLOYED_SHA || '',
    weekStart: __ENV.WEEK_START || '',
    leagueType: __ENV.LEAGUE_TYPE || 'WEEKLY_RETURN',
    expectedTotal: Number(__ENV.EXPECTED_TOTAL),
    expectedConfirmed: __ENV.EXPECTED_CONFIRMED === 'true',
    rps: Number(__ENV.RPS || 1),
    duration: __ENV.DURATION || '30s',
    vus: Number(__ENV.VUS || 20),
    targetP95Ms: Number(__ENV.TARGET_P95_MS || 1000),
    run: __ENV.RUN || __ENV.LOADTEST_ID || 'local',
  };
  if (!/^https?:\/\/[^\s/@?#]+$/.test(config.baseUrl)) {
    throw new Error('BASE_URL must be an HTTP(S) origin without credentials, path or query.');
  }
  if (!/^[a-f0-9]{7,40}$/i.test(config.deployedSha)) {
    throw new Error(
      'DEPLOYED_SHA must identify the backend actually deployed, not the script commit.',
    );
  }
  const week = new Date(`${config.weekStart}T00:00:00Z`);
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(config.weekStart) ||
    !Number.isFinite(week.getTime()) ||
    week.getUTCDay() !== 1 ||
    week.toISOString().slice(0, 10) !== config.weekStart
  ) {
    throw new Error('WEEK_START must be a valid Monday in YYYY-MM-DD format.');
  }
  if (
    !['WEEKLY_RETURN', 'STABLE', 'BEGINNER'].includes(config.leagueType) ||
    !['true', 'false'].includes(__ENV.EXPECTED_CONFIRMED)
  ) {
    throw new Error('Set LEAGUE_TYPE and EXPECTED_CONFIRMED (true or false) for the comparison.');
  }
  const durationMatch = /^([1-9]\d*)(s|m)$/.exec(config.duration);
  if (!durationMatch) throw new Error('DURATION must be a positive duration such as 30s or 5m.');
  const seconds = Number(durationMatch[1]) * (durationMatch[2] === 'm' ? 60 : 1);
  const expectedRequests = config.rps * seconds;
  if (
    ![config.expectedTotal, config.rps, config.vus, config.targetP95Ms, expectedRequests].every(
      (value) => Number.isSafeInteger(value) && value > 0,
    )
  ) {
    throw new Error(
      'EXPECTED_TOTAL, RPS, VUS, TARGET_P95_MS and planned requests must be positive integers.',
    );
  }
  if (!/^[A-Za-z0-9_-]{1,64}$/.test(config.run)) throw new Error('Invalid RUN label.');

  const url =
    `${config.baseUrl}/api/league/rankings?leagueType=${config.leagueType}` +
    `&weekStart=${config.weekStart}&page=0&size=20`;
  const requests = new Counter('league_requests');
  const success = new Rate('league_success');
  const duration = new Trend('league_duration_ms', true);
  const request = (phase) =>
    http.get(url, {
      timeout: '10s',
      tags: { name: 'league_rankings', phase },
    });

  function matches(response) {
    if (response.status !== 200) return false;
    let body;
    try {
      body = response.json();
    } catch (_) {
      return false;
    }
    const entries = body?.rankings;
    return (
      body?.weekStart === config.weekStart &&
      body.leagueType === config.leagueType &&
      body.confirmed === config.expectedConfirmed &&
      body.totalCount === config.expectedTotal &&
      Array.isArray(entries) &&
      entries.length === Math.min(20, config.expectedTotal) &&
      entries.every(
        (entry, index) =>
          entry &&
          Number.isSafeInteger(entry.rank) &&
          entry.rank > 0 &&
          typeof entry.publicId === 'string' &&
          entry.publicId.length > 0 &&
          typeof entry.weeklyReturnRate === 'number' &&
          Number.isFinite(entry.weeklyReturnRate) &&
          (index === 0 || entries[index - 1].rank <= entry.rank),
      ) &&
      new Set(entries.map((entry) => entry.publicId)).size === entries.length
    );
  }

  return {
    options: {
      scenarios: {
        rankings: {
          executor: 'constant-arrival-rate',
          rate: config.rps,
          timeUnit: '1s',
          duration: config.duration,
          preAllocatedVUs: config.vus,
          maxVUs: config.vus,
          gracefulStop: '11s',
        },
      },
      setupTimeout: '15s',
      maxRedirects: 0,
      tags: { version, league_type: config.leagueType, run: config.run },
      summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
      thresholds: {
        league_requests: [`count==${expectedRequests}`],
        league_success: ['rate==1'],
        league_duration_ms: [`p(95)<${config.targetP95Ms}`],
        dropped_iterations: [{ threshold: 'count==0', abortOnFail: true }],
      },
    },
    setup() {
      if (!matches(request('preflight'))) {
        throw new Error(
          'Preflight failed: check HTTP status, week, league type, participant count and confirmed state.',
        );
      }
    },
    run() {
      // k6 can start one extra iteration at the duration boundary.
      if (exec.scenario.iterationInTest >= expectedRequests) return;
      requests.add(1);
      const response = request('measurement');
      duration.add(response.timings.duration);
      success.add(check(response, { 'rankings match the comparison fixture': matches }));
    },
    handleSummary(data) {
      const sent = data.metrics.league_requests?.values.count || 0;
      const successRate = data.metrics.league_success?.values.rate;
      const latency = data.metrics.league_duration_ms?.values || {};
      const dropped = data.metrics.dropped_iterations?.values.count || 0;
      const passed =
        sent === expectedRequests &&
        successRate === 1 &&
        dropped === 0 &&
        Number.isFinite(latency['p(95)']) &&
        latency['p(95)'] < config.targetP95Ms;
      const report = {
        config,
        expectedRequests,
        sent,
        successRate,
        dropped,
        latency,
        passed,
        metrics: data.metrics,
      };
      return {
        stdout:
          `${version} (${config.deployedSha}): ${sent}/${expectedRequests} requests, ` +
          `p95=${latency['p(95)'] ?? 'n/a'}ms, passed=${passed}\n`,
        [`result-league-rankings-${version}-${config.run}.json`]: JSON.stringify(report, null, 2),
      };
    },
  };
}
