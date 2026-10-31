import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const periodDays = Number(__ENV.PERIOD_DAYS || 7);
const minimumRankings = Number(__ENV.MIN_RANKINGS || 1);
const targetRps = Number(__ENV.RPS || 100);
const vus = Number(__ENV.VUS || Math.ceil(targetRps * 0.5));
const maxVus = Number(__ENV.MAX_VUS || Math.max(vus, targetRps));
const duration = (__ENV.DURATION || '5m').trim();
const runLabel = (__ENV.RUN || '1').trim();

function durationSeconds(value) {
  const match = /^(\d+)(s|m)$/.exec(value);
  if (!match)
    throw new Error(
      'DURATION must use seconds or minutes, for example 30s or 5m.',
    );
  const seconds = Number(match[1]) * (match[2] === 'm' ? 60 : 1);
  if (!Number.isSafeInteger(seconds) || seconds < 10 || seconds > 1800) {
    throw new Error('DURATION must be between 10 seconds and 30 minutes.');
  }
  return seconds;
}

if (!/^https?:\/\/[^\s/@?#]+$/.test(baseUrl)) {
  throw new Error(
    'Set BASE_URL to an HTTP(S) origin without credentials, path or query.',
  );
}
if (
  ![periodDays, minimumRankings, targetRps, vus, maxVus].every(
    Number.isSafeInteger,
  )
) {
  throw new Error(
    'PERIOD_DAYS, MIN_RANKINGS, RPS, VUS and MAX_VUS must be integers.',
  );
}
if (
  periodDays < 1 ||
  periodDays > 30 ||
  minimumRankings <= 0 ||
  targetRps <= 0 ||
  vus <= 0 ||
  maxVus < vus
) {
  throw new Error(
    'Use valid positive parameters and PERIOD_DAYS between 1 and 30.',
  );
}
if (!/^[A-Za-z0-9._-]+$/.test(runLabel)) {
  throw new Error(
    'RUN may contain only letters, numbers, dot, underscore and hyphen.',
  );
}

const measurementSeconds = durationSeconds(duration);
const plannedRequests = targetRps * measurementSeconds;
const latency = new Trend('content_rankings_load_target_duration_ms', true);
const url = `${baseUrl}/api/content-rankings?periodDays=${periodDays}&page=1&size=20`;

export const options = {
  scenarios: {
    content_rankings_load: {
      executor: 'constant-arrival-rate',
      rate: targetRps,
      duration,
      timeUnit: '1s',
      preAllocatedVUs: vus,
      maxVUs: maxVus,
      gracefulStop: '10s',
    },
  },
  setupTimeout: '15s',
  maxRedirects: 0,
  tags: { test_type: 'load', api: 'content_rankings', run: runLabel },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed{phase:target}': ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    content_rankings_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${plannedRequests}`],
  },
};

function request(phase) {
  return http.get(url, {
    timeout: '10s',
    tags: { name: 'content_rankings', phase },
  });
}

function readRankings(response) {
  try {
    const body = response.json();
    if (
      response.status === 200 &&
      body?.periodDays === periodDays &&
      body.page === 1 &&
      body.size === 20 &&
      Number.isSafeInteger(body.totalElements) &&
      body.totalElements >= minimumRankings &&
      Number.isSafeInteger(body.totalPages) &&
      body.totalPages === Math.ceil(body.totalElements / body.size) &&
      body.hasNext === body.page < body.totalPages &&
      Array.isArray(body.items) &&
      body.items.length === Math.min(body.size, body.totalElements) &&
      body.items.every(
        (item) =>
          ['NEWS', 'PERSON_POST', 'ASSET_CONTENT'].includes(
            item?.contentType,
          ) &&
          Number.isSafeInteger(item.contentId) &&
          item.contentId > 0 &&
          Number.isSafeInteger(item.score) &&
          item.score > 0 &&
          typeof item.lastInteractedAt === 'string',
      )
    ) {
      return body;
    }
  } catch (_) {
    // HTTP, JSON and response-contract failures are reported by preflight or checks.
  }
  return null;
}

export function setup() {
  const body = readRankings(request('preflight'));
  if (!body) {
    throw new Error(
      `Preflight failed: verify at least ${minimumRankings} ranked contents for ${periodDays} days.`,
    );
  }
  return { totalElements: body.totalElements };
}

export default function () {
  if (exec.scenario.iterationInTest >= plannedRequests) return;
  const response = request('target');
  const body = readRankings(response);
  latency.add(response.timings.duration);
  check(response, {
    'content rankings HTTP 200': (value) => value.status === 200,
    'content rankings response contract': () => body !== null,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latencyValues =
    data.metrics.content_rankings_load_target_duration_ms?.values || {};
  const errorRate =
    data.metrics['http_req_failed{phase:target}']?.values.rate ?? null;
  const checkRate = data.metrics.checks?.values.rate ?? null;
  const dropped = data.metrics.dropped_iterations?.values.count || 0;
  const result = {
    api: 'GET /api/content-rankings',
    periodDays,
    run: runLabel,
    targetRps,
    duration,
    vus,
    maxVus,
    minimumRankings,
    plannedRequests,
    actualRequests: requests,
    achievedRps: requests / measurementSeconds,
    latency: latencyValues,
    errorRate,
    checkRate,
    droppedIterations: dropped,
  };
  const filename = `result-content-rankings-${targetRps}rps-${runLabel}.json`;
  return {
    stdout:
      `Content rankings: ${vus}-${maxVus} VUs, ${duration}, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${plannedRequests}, RPS=${result.achievedRps.toFixed(2)}, ` +
      `p95=${latencyValues['p(95)'] ?? 'n/a'}ms (<=300), ` +
      `p99=${latencyValues['p(99)'] ?? 'n/a'}ms (<=500)\n` +
      `HTTP error rate=${errorRate}, checks=${checkRate}, dropped=${dropped}\n` +
      `result: ${filename}\n`,
    [filename]: JSON.stringify(result, null, 2),
  };
}
