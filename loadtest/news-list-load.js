import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const testCase = (__ENV.CASE || 'FIRST').trim().toUpperCase();
const minimumNews = Number(__ENV.MIN_NEWS || 1);
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
if (!['FIRST', 'MIDDLE', 'LAST'].includes(testCase)) {
  throw new Error('CASE must be FIRST, MIDDLE or LAST.');
}
if (![minimumNews, targetRps, vus, maxVus].every(Number.isSafeInteger)) {
  throw new Error('MIN_NEWS, RPS, VUS and MAX_VUS must be integers.');
}
if (minimumNews <= 0 || targetRps <= 0 || vus <= 0 || maxVus < vus) {
  throw new Error(
    'Use positive parameters and MAX_VUS greater than or equal to VUS.',
  );
}
if (!/^[A-Za-z0-9._-]+$/.test(runLabel)) {
  throw new Error(
    'RUN may contain only letters, numbers, dot, underscore and hyphen.',
  );
}

const measurementSeconds = durationSeconds(duration);
const plannedRequests = targetRps * measurementSeconds;
const latency = new Trend('news_list_load_target_duration_ms', true);

export const options = {
  scenarios: {
    news_list_load: {
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
  tags: {
    test_type: 'load',
    api: 'news_list',
    case: testCase.toLowerCase(),
    run: runLabel,
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed{phase:target}': ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    news_list_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${plannedRequests}`],
  },
};

function url(page) {
  return `${baseUrl}/api/news?page=${page}&size=100`;
}

function request(page, phase) {
  return http.get(url(page), {
    timeout: '10s',
    tags: { name: `news_list_${testCase.toLowerCase()}`, phase },
  });
}

function readPage(response, expectedPage) {
  try {
    const body = response.json();
    if (
      response.status === 200 &&
      body?.page === expectedPage &&
      body.size === 100 &&
      Number.isSafeInteger(body.totalElements) &&
      body.totalElements >= minimumNews &&
      Number.isSafeInteger(body.totalPages) &&
      body.totalPages === Math.ceil(body.totalElements / body.size) &&
      body.totalPages >= 1 &&
      body.hasNext === body.page < body.totalPages &&
      Array.isArray(body.items) &&
      body.items.length > 0 &&
      body.items.length <= body.size &&
      body.items.every(
        (item) =>
          Number.isSafeInteger(item?.id) &&
          item.id > 0 &&
          typeof item.title === 'string' &&
          typeof item.category === 'string' &&
          typeof item.publishedAt === 'string',
      )
    ) {
      return body;
    }
  } catch (_) {
    // HTTP, JSON and response-contract failures are reported by preflight or checks.
  }
  return null;
}

function targetPage(totalPages) {
  if (testCase === 'FIRST') return 1;
  if (testCase === 'MIDDLE') return Math.ceil(totalPages / 2);
  return totalPages;
}

export function setup() {
  const first = readPage(request(1, 'preflight'), 1);
  if (!first) {
    throw new Error(
      `Preflight failed: verify HTTP 200 and at least ${minimumNews} published news.`,
    );
  }
  const page = targetPage(first.totalPages);
  if (page !== 1 && !readPage(request(page, 'preflight'), page)) {
    throw new Error(
      `Preflight failed: CASE=${testCase} target page ${page} is not readable.`,
    );
  }
  return {
    page,
    totalElements: first.totalElements,
    totalPages: first.totalPages,
  };
}

export default function (data) {
  if (exec.scenario.iterationInTest >= plannedRequests) return;
  const response = request(data.page, 'target');
  const body = readPage(response, data.page);
  latency.add(response.timings.duration);
  check(response, {
    'news list HTTP 200': (value) => value.status === 200,
    'news list response contract': () => body !== null,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latencyValues =
    data.metrics.news_list_load_target_duration_ms?.values || {};
  const errorRate =
    data.metrics['http_req_failed{phase:target}']?.values.rate ?? null;
  const checkRate = data.metrics.checks?.values.rate ?? null;
  const dropped = data.metrics.dropped_iterations?.values.count || 0;
  const result = {
    api: 'GET /api/news',
    case: testCase,
    run: runLabel,
    targetRps,
    duration,
    vus,
    maxVus,
    minimumNews,
    plannedRequests,
    actualRequests: requests,
    achievedRps: requests / measurementSeconds,
    latency: latencyValues,
    errorRate,
    checkRate,
    droppedIterations: dropped,
  };
  const filename = `result-news-list-${testCase.toLowerCase()}-${targetRps}rps-${runLabel}.json`;
  return {
    stdout:
      `News list ${testCase}: ${vus}-${maxVus} VUs, ${duration}, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${plannedRequests}, RPS=${result.achievedRps.toFixed(2)}, ` +
      `p95=${latencyValues['p(95)'] ?? 'n/a'}ms (<=300), ` +
      `p99=${latencyValues['p(99)'] ?? 'n/a'}ms (<=500)\n` +
      `HTTP error rate=${errorRate}, checks=${checkRate}, dropped=${dropped}\n` +
      `result: ${filename}\n`,
    [filename]: JSON.stringify(result, null, 2),
  };
}
