import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const targetRps = Number(__ENV.RPS || 100);
const vus = Number(__ENV.VUS || Math.ceil(targetRps * 0.5));
const query = (__ENV.QUERY || '').trim();
if (!/^https?:\/\/[^\s/@?#]+$/.test(baseUrl)) {
  throw new Error('Set BASE_URL to an HTTP(S) origin without credentials, path or query.');
}
if (![targetRps, vus].every((value) => Number.isSafeInteger(value) && value > 0)) {
  throw new Error('RPS and VUS must be positive integers.');
}
if (!query || query.length > 100) {
  throw new Error('Set QUERY to a search keyword of 1 to 100 characters with matching content.');
}

const url = `${baseUrl}/api/search/contents?q=${encodeURIComponent(query)}&source=ALL&page=1&size=12&sort=LATEST`;
const latency = new Trend('content_search_load_target_duration_ms', true);

export const options = {
  scenarios: {
    content_search_load: {
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
  tags: { test_type: 'load', api: 'content_search' },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    content_search_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${targetRps * 300}`],
  },
};

function request(phase) {
  return http.get(url, {
    timeout: '10s',
    tags: { name: 'content_search', phase },
  });
}

function readSearch(response) {
  try {
    const body = response.json();
    if (
      response.status === 200 &&
      body?.page === 1 &&
      body.size === 12 &&
      Number.isSafeInteger(body.totalElements) &&
      body.totalElements > 0 &&
      body.totalPages === Math.ceil(body.totalElements / 12) &&
      Array.isArray(body.items) &&
      body.items.length === Math.min(12, body.totalElements) &&
      body.items.every(
        (item) =>
          Number.isSafeInteger(item?.id) &&
          item.id > 0 &&
          typeof item.title === 'string' &&
          ['NEWS', 'PERSON_POST', 'ASSET_CONTENT'].includes(item.kind),
      )
    )
      return body;
  } catch (_) {
    // HTTP/JSON/contract failures are recorded as failed checks during the load phase.
  }
  return null;
}

export function setup() {
  const body = readSearch(request('preflight'));
  if (!body) {
    throw new Error('Preflight failed: verify HTTP 200 and matching search results for QUERY.');
  }
  return { query, totalElements: body.totalElements };
}

export default function () {
  if (exec.scenario.iterationInTest >= targetRps * 300) return;
  const response = request('target');
  const body = readSearch(response);
  latency.add(response.timings.duration);
  check(response, {
    'content search HTTP 200': (value) => value.status === 200,
    'nonempty search results': () => body !== null,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latency = data.metrics.content_search_load_target_duration_ms?.values || {};
  return {
    stdout:
      `Content search load: ${vus} VUs, 5 minutes, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${targetRps * 300}, RPS=${(requests / 300).toFixed(2)}, ` +
      `p95=${latency['p(95)'] ?? 'n/a'}ms (<=300), p99=${latency['p(99)'] ?? 'n/a'}ms (<=500)\n` +
      `HTTP error rate=${data.metrics.http_req_failed?.values.rate}, ` +
      `checks=${data.metrics.checks?.values.rate}, dropped=${data.metrics.dropped_iterations?.values.count || 0}\n`,
  };
}
