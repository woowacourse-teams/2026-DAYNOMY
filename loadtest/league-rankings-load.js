import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const targetRps = Number(__ENV.RPS || 50);
const vus = Number(__ENV.VUS || targetRps);
if (!/^https?:\/\/[^\s/@?#]+$/.test(baseUrl)) {
  throw new Error('Set BASE_URL to an HTTP(S) origin without credentials, path or query.');
}
if (![targetRps, vus].every((value) => Number.isSafeInteger(value) && value > 0)) {
  throw new Error('RPS and VUS must be positive integers.');
}

const url = `${baseUrl}/api/league/rankings?leagueType=WEEKLY_RETURN&page=0&size=20`;
const lowRps = Math.max(1, Math.round(targetRps / 5));
const mediumRps = Math.max(1, Math.round(targetRps * 0.4));
const warmupRps = Math.min(5, lowRps);
const phases = [
  { name: 'warmup', rps: warmupRps },
  { name: 'low', rps: lowRps },
  { name: 'medium', rps: mediumRps },
  { name: 'target', rps: targetRps },
  { name: 'cooldown', rps: 0 },
];
const stages = [
  { duration: '1m', target: warmupRps },
  { duration: '0s', target: lowRps },
  { duration: '1m', target: lowRps },
  { duration: '0s', target: mediumRps },
  { duration: '1m', target: mediumRps },
  { duration: '0s', target: targetRps },
  { duration: '1m', target: targetRps },
  { duration: '1m', target: 0 },
];
const latencies = Object.fromEntries(
  phases.map(({ name }) => [name, new Trend(`league_rankings_load_${name}_duration_ms`, true)]),
);

export const options = {
  scenarios: {
    rankings_load: {
      executor: 'ramping-arrival-rate',
      startRate: warmupRps,
      timeUnit: '1s',
      preAllocatedVUs: vus,
      maxVUs: vus,
      stages,
      gracefulStop: '10s',
    },
  },
  setupTimeout: '15s',
  maxRedirects: 0,
  tags: { test_type: 'load', api: 'league_rankings' },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    ...Object.fromEntries(phases.map(({ name }) => [`http_reqs{phase:${name}}`, ['count>0']])),
  },
};

function request(phase, weekStart) {
  return http.get(weekStart ? `${url}&weekStart=${encodeURIComponent(weekStart)}` : url, {
    timeout: '10s',
    tags: { name: 'league_rankings', phase },
  });
}

function readRankings(response) {
  try {
    const body = response.json();
    if (
      response.status === 200 &&
      body?.leagueType === 'WEEKLY_RETURN' &&
      /^\d{4}-\d{2}-\d{2}$/.test(body.weekStart) &&
      Number.isSafeInteger(body.totalCount) &&
      body.totalCount > 0 &&
      Array.isArray(body.rankings) &&
      body.rankings.length === Math.min(20, body.totalCount)
    )
      return body;
  } catch (_) {
    // HTTP/JSON/contract failures are recorded as failed checks during the load phase.
  }
  return null;
}

export function setup() {
  const body = readRankings(request('preflight'));
  if (!body) throw new Error('Preflight failed: prepare a nonempty ranking and verify HTTP 200.');
  return { weekStart: body.weekStart, totalCount: body.totalCount };
}

export default function (data) {
  const elapsedMs = Date.now() - exec.scenario.startTime;
  const phase = phases[Math.min(Math.floor(elapsedMs / 60_000), phases.length - 1)];
  const response = request(phase.name, data.weekStart);
  const body = readRankings(response);
  latencies[phase.name].add(response.timings.duration);
  check(response, {
    'rankings HTTP 200': (value) => value.status === 200,
    'nonempty rankings for the selected week': () => body?.weekStart === data.weekStart,
  });
}

export function handleSummary(data) {
  const results = phases.map(({ name, rps }) => {
    const requests = data.metrics[`http_reqs{phase:${name}}`]?.values.count || 0;
    return {
      phase: name,
      durationSeconds: 60,
      startRps: name === 'cooldown' ? targetRps : rps,
      endRps: rps,
      requests,
      // The filtered counter's built-in rate divides by the entire test, not this phase.
      actualRps: requests / 60,
      latency: data.metrics[`league_rankings_load_${name}_duration_ms`]?.values || {},
    };
  });
  const report = {
    durationSeconds: 300,
    targetRps,
    vus,
    stages,
    dataset: data.setup_data || null,
    phases: results,
    failedRequestRate: data.metrics.http_req_failed?.values.rate,
    checkSuccessRate: data.metrics.checks?.values.rate,
    droppedIterations: data.metrics.dropped_iterations?.values.count || 0,
    metrics: data.metrics,
  };
  return {
    stdout:
      `League rankings load: ${vus} VUs, 5 minutes, target=${targetRps} RPS\n` +
      results
        .map(
          (phase) =>
            `${phase.phase}: requests=${phase.requests}, RPS=${phase.actualRps.toFixed(2)}, ` +
            `p95=${phase.latency['p(95)'] ?? 'n/a'}ms, p99=${phase.latency['p(99)'] ?? 'n/a'}ms`,
        )
        .join('\n') +
      `\nHTTP error rate=${report.failedRequestRate}, checks=${report.checkSuccessRate}, dropped=${report.droppedIterations}\n`,
  };
}
