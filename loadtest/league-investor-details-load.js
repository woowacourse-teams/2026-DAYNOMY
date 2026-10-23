import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const targetRps = Number(__ENV.RPS || 100);
const vus = Number(__ENV.VUS || Math.ceil(targetRps * 0.5));
const publicId = __ENV.PUBLIC_ID || '';
if (!/^https?:\/\/[^\s/@?#]+$/.test(baseUrl)) {
  throw new Error('Set BASE_URL to an HTTP(S) origin without credentials, path or query.');
}
if (![targetRps, vus].every((value) => Number.isSafeInteger(value) && value > 0)) {
  throw new Error('RPS and VUS must be positive integers.');
}
if (!/^[A-Za-z0-9_-]+$/.test(publicId)) {
  throw new Error('Set PUBLIC_ID to the public investor ID with details enabled.');
}

const url = `${baseUrl}/api/league/investors/${encodeURIComponent(publicId)}/details`;
const latency = new Trend('league_investor_details_load_target_duration_ms', true);

export const options = {
  scenarios: {
    investor_details_load: {
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
  tags: { test_type: 'load', api: 'league_investor_details' },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    league_investor_details_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${targetRps * 300}`],
  },
};

function request(phase) {
  return http.get(url, {
    timeout: '10s',
    tags: { name: 'league_investor_details', phase },
  });
}

function readDetails(response) {
  try {
    const body = response.json();
    if (
      response.status === 200 &&
      body?.publicId === publicId &&
      Array.isArray(body.holdings) &&
      body.holdings.length > 0 &&
      Array.isArray(body.decisions) &&
      body.decisions.length > 0 &&
      body.decisions.every(
        (decision) =>
          Number.isSafeInteger(decision?.transactionId) &&
          decision.transactionId > 0 &&
          Array.isArray(decision.reviews),
      )
    )
      return body;
  } catch (_) {
    // HTTP/JSON/contract failures are recorded as failed checks during the load phase.
  }
  return null;
}

export function setup() {
  const body = readDetails(request('preflight'));
  if (!body) {
    throw new Error(
      'Preflight failed: verify HTTP 200 and public holdings/decisions for PUBLIC_ID.',
    );
  }
  return {
    publicId,
    holdingCount: body.holdings.length,
    decisionCount: body.decisions.length,
    reviewCount: body.decisions.reduce((count, decision) => count + decision.reviews.length, 0),
  };
}

export default function () {
  const response = request('target');
  const body = readDetails(response);
  latency.add(response.timings.duration);
  check(response, {
    'investor details HTTP 200': (value) => value.status === 200,
    'nonempty public holdings and decisions': () => body !== null,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latency = data.metrics.league_investor_details_load_target_duration_ms?.values || {};
  return {
    stdout:
      `League investor details load: ${vus} VUs, 5 minutes, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${targetRps * 300}, RPS=${(requests / 300).toFixed(2)}, ` +
      `p95=${latency['p(95)'] ?? 'n/a'}ms (<=300), p99=${latency['p(99)'] ?? 'n/a'}ms (<=500)\n` +
      `HTTP error rate=${data.metrics.http_req_failed?.values.rate}, ` +
      `checks=${data.metrics.checks?.values.rate}, dropped=${data.metrics.dropped_iterations?.values.count || 0}\n`,
  };
}
