import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const targetRps = Number(__ENV.RPS || 100);
const vus = Number(__ENV.VUS || Math.ceil(targetRps * 0.5));
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
const latency = new Trend('league_follow_summary_load_target_duration_ms', true);

export const options = {
  scenarios: {
    follow_summary_load: {
      executor: 'constant-arrival-rate',
      rate: targetRps,
      duration: '5m',
      timeUnit: '1s',
      preAllocatedVUs: vus,
      maxVUs: vus,
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
    league_follow_summary_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${targetRps * 300}`],
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
  const response = request('target', tokens[exec.scenario.iterationInTest % tokens.length]);
  const body = readSummary(response);
  latency.add(response.timings.duration);
  check(response, {
    'follow summary HTTP 200': (value) => value.status === 200,
    'nonempty follow summary': () => body !== null,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latency = data.metrics.league_follow_summary_load_target_duration_ms?.values || {};
  return {
    stdout:
      `League follow summary load: ${vus} VUs, 5 minutes, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${targetRps * 300}, RPS=${(requests / 300).toFixed(2)}, ` +
      `p95=${latency['p(95)'] ?? 'n/a'}ms (<=300), p99=${latency['p(99)'] ?? 'n/a'}ms (<=500)\n` +
      `HTTP error rate=${data.metrics.http_req_failed?.values.rate}, ` +
      `checks=${data.metrics.checks?.values.rate}, dropped=${data.metrics.dropped_iterations?.values.count || 0}\n`,
  };
}
