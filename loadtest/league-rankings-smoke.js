import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
if (!/^https?:\/\/[^\s/@?#]+$/.test(baseUrl)) {
  throw new Error('Set BASE_URL to an HTTP(S) origin without credentials, path or query.');
}
const url = `${baseUrl}/api/league/rankings?leagueType=WEEKLY_RETURN&page=0&size=20`;
const stages = [
  { duration: '1m', target: 1 },
  { duration: '0s', target: 5 },
  { duration: '3m', target: 5 },
  { duration: '1m', target: 0 },
];

export const options = {
  scenarios: {
    rankings_smoke: {
      executor: 'ramping-arrival-rate',
      startRate: 1,
      timeUnit: '1s',
      preAllocatedVUs: 5,
      maxVUs: 5,
      stages,
      gracefulStop: '10s',
    },
  },
  maxRedirects: 0,
  tags: { test_type: 'smoke', api: 'league_rankings' },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    'http_reqs{phase:warmup}': ['count>0'],
    'http_reqs{phase:steady}': ['count>0'],
    'http_reqs{phase:cooldown}': ['count>0'],
  },
};

export default function () {
  const elapsedMs = Date.now() - exec.scenario.startTime;
  const phase = elapsedMs < 60_000 ? 'warmup' : elapsedMs < 240_000 ? 'steady' : 'cooldown';
  // One GET per iteration keeps the arrival rate equal to the intended HTTP RPS.
  const response = http.get(url, { timeout: '10s', tags: { name: 'league_rankings', phase } });
  let body;
  try {
    body = response.json();
  } catch (_) {
    // Invalid JSON is counted as a failed check, so the remaining requests can still be measured.
  }
  check(response, {
    'rankings HTTP 200': (value) => value.status === 200,
    'rankings response contract': () =>
      body?.leagueType === 'WEEKLY_RETURN' &&
      Number.isSafeInteger(body.totalCount) &&
      body.totalCount >= 0 &&
      Array.isArray(body.rankings) &&
      body.rankings.length === Math.min(20, body.totalCount),
  });
}

export function handleSummary(data) {
  const phases = Object.entries({ warmup: 60, steady: 180, cooldown: 60 }).map(
    ([phase, durationSeconds]) => {
      const requests = data.metrics[`http_reqs{phase:${phase}}`]?.values.count || 0;
      // Filtered counters use the whole test duration for rate; calculate each phase's RPS here.
      return { phase, durationSeconds, requests, rps: requests / durationSeconds };
    },
  );
  const report = {
    durationSeconds: 300,
    vus: 5,
    stages,
    phases,
    latency: data.metrics.http_req_duration?.values || {},
    failedRequestRate: data.metrics.http_req_failed?.values.rate,
    checkSuccessRate: data.metrics.checks?.values.rate,
    droppedIterations: data.metrics.dropped_iterations?.values.count || 0,
    metrics: data.metrics,
  };
  return {
    stdout:
      'League rankings smoke: 5 VUs, 5 minutes\n' +
      phases
        .map((phase) => `${phase.phase}: requests=${phase.requests}, RPS=${phase.rps.toFixed(2)}`)
        .join('\n') +
      `\np95=${report.latency['p(95)'] ?? 'n/a'}ms, p99=${report.latency['p(99)'] ?? 'n/a'}ms\n` +
      `HTTP error rate=${report.failedRequestRate}, checks=${report.checkSuccessRate}, dropped=${report.droppedIterations}\n`,
    'result-league-rankings-smoke.json': JSON.stringify(report, null, 2),
  };
}
