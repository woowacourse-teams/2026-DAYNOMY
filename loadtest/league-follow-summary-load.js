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

// LEAGUE_FIXTURE_FILE is a private runner file: { "users": [{ "accessToken": "..." }] }.
let tokens;
try {
  const fixture = JSON.parse(open(__ENV.LEAGUE_FIXTURE_FILE || './accounts-league.json'));
  tokens = fixture.users.slice(0, vus).map((user) => user.accessToken);
} catch (_) {
  throw new Error(
    'Prepare the private account JSON via LEAGUE_FIXTURE_FILE; never put tokens in params.',
  );
}
if (
  !tokens.length ||
  tokens.some((token) => typeof token !== 'string' || !/^[\w-]+\.[\w-]+\.[\w-]+$/.test(token)) ||
  new Set(tokens).size !== tokens.length
) {
  throw new Error(
    'Provide nonempty users with distinct access tokens in the private account file.',
  );
}

const url = `${baseUrl}/api/users/me/league/follows/summary`;
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
  phases.map(({ name }) => [
    name,
    new Trend(`league_follow_summary_load_${name}_duration_ms`, true),
  ]),
);

export const options = {
  scenarios: {
    follow_summary_load: {
      executor: 'ramping-arrival-rate',
      startRate: warmupRps,
      timeUnit: '1s',
      preAllocatedVUs: vus,
      maxVUs: vus,
      stages,
      gracefulStop: '10s',
    },
  },
  setupTimeout: `${tokens.length * 10 + 5}s`,
  maxRedirects: 0,
  tags: { test_type: 'load', api: 'league_follow_summary' },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    ...Object.fromEntries(phases.map(({ name }) => [`http_reqs{phase:${name}}`, ['count>0']])),
  },
};

function request(phase, token) {
  return http.get(url, {
    timeout: '10s',
    cookies: { access_token: token },
    tags: { name: 'league_follow_summary', phase },
  });
}

function readSummary(response) {
  try {
    const body = response.json();
    if (
      response.status === 200 &&
      Array.isArray(body?.investors) &&
      body.investors.length > 0 &&
      body.investors.every(
        (investor) =>
          typeof investor?.publicId === 'string' &&
          investor.publicId.length > 0 &&
          Number.isSafeInteger(investor.newReviewCount) &&
          investor.newReviewCount >= 0,
      )
    )
      return body;
  } catch (_) {
    // HTTP/JSON/contract failures are recorded as failed checks during the load phase.
  }
  return null;
}

export function setup() {
  const followedInvestorCounts = tokens.map((token) => {
    const body = readSummary(request('preflight', token));
    if (!body) {
      throw new Error(
        'Preflight failed: verify login and at least one public follow for every account.',
      );
    }
    return body.investors.length;
  });
  return { accountCount: tokens.length, followedInvestorCounts };
}

export default function () {
  const elapsedMs = Date.now() - exec.scenario.startTime;
  const phase = phases[Math.min(Math.floor(elapsedMs / 60_000), phases.length - 1)];
  const response = request(phase.name, tokens[exec.scenario.iterationInTest % tokens.length]);
  const body = readSummary(response);
  latencies[phase.name].add(response.timings.duration);
  check(response, {
    'follow summary HTTP 200': (value) => value.status === 200,
    'nonempty follow summary': () => body !== null,
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
      latency: data.metrics[`league_follow_summary_load_${name}_duration_ms`]?.values || {},
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
      `League follow summary load: ${vus} VUs, 5 minutes, target=${targetRps} RPS\n` +
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
