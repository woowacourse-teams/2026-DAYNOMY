import http from 'k6/http';
import { check } from 'k6';
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

const url = `${baseUrl}/api/league/rankings?leagueType=WEEKLY_RETURN&page=0&size=20`;
const latency = new Trend('league_rankings_load_target_duration_ms', true);

export const options = {
  scenarios: {
    rankings_load: {
      executor: 'constant-arrival-rate',
      rate: targetRps,
      duration: '5m',
      timeUnit: '1s',
      preAllocatedVUs: vus,
      maxVUs: vus,
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
    league_rankings_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${targetRps * 300}`],
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
  if (!body || body.totalCount < 10000) {
    throw new Error('Preflight failed: verify HTTP 200 and at least 10,000 league participants.');
  }
  return { weekStart: body.weekStart, totalCount: body.totalCount };
}

export default function (data) {
  const response = request('target', data.weekStart);
  const body = readRankings(response);
  latency.add(response.timings.duration);
  check(response, {
    'rankings HTTP 200': (value) => value.status === 200,
    'nonempty rankings for the selected week': () => body?.weekStart === data.weekStart,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latency = data.metrics.league_rankings_load_target_duration_ms?.values || {};
  return {
    stdout:
      `League rankings load: ${vus} VUs, 5 minutes, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${targetRps * 300}, RPS=${(requests / 300).toFixed(2)}, ` +
      `p95=${latency['p(95)'] ?? 'n/a'}ms (<=300), p99=${latency['p(99)'] ?? 'n/a'}ms (<=500)\n` +
      `HTTP error rate=${data.metrics.http_req_failed?.values.rate}, ` +
      `checks=${data.metrics.checks?.values.rate}, dropped=${data.metrics.dropped_iterations?.values.count || 0}\n`,
  };
}
