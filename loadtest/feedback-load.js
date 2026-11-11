import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const targetRps = Number(__ENV.RPS || 100);
const vus = Number(__ENV.VUS || targetRps);
const maxVus = Number(__ENV.MAX_VUS || Math.ceil(targetRps * 1.5));
const duration = (__ENV.DURATION || '5m').trim();
const runLabel = (__ENV.RUN || '1').trim();
const executionId = (__ENV.LOADTEST_ID || runLabel).trim();

function durationSeconds(value) {
  const match = /^(\d+)(s|m)$/.exec(value);
  if (!match) {
    throw new Error(
      'DURATION must use seconds or minutes, for example 30s or 5m.',
    );
  }
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
if (![targetRps, vus, maxVus].every(Number.isSafeInteger)) {
  throw new Error('RPS, VUS and MAX_VUS must be integers.');
}
if (targetRps <= 0 || vus <= 0 || maxVus < vus) {
  throw new Error(
    'Use positive parameters and MAX_VUS greater than or equal to VUS.',
  );
}
if (!/^[A-Za-z0-9._-]+$/.test(runLabel) || runLabel.length > 100) {
  throw new Error(
    'RUN may contain up to 100 letters, numbers, dots, underscores and hyphens.',
  );
}
if (
  !/^[A-Za-z0-9._-]+$/.test(executionId) ||
  executionId.length > 100
) {
  throw new Error(
    'LOADTEST_ID may contain up to 100 letters, numbers, dots, underscores and hyphens.',
  );
}

const measurementSeconds = durationSeconds(duration);
const plannedRequests = targetRps * measurementSeconds;
const latency = new Trend('feedback_load_target_duration_ms', true);
const csrfUrl = `${baseUrl}/api/auth/csrf`;
const feedbackUrl = `${baseUrl}/api/feedback`;

export const options = {
  scenarios: {
    feedback_load: {
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
  tags: { test_type: 'load', api: 'feedback', run: runLabel },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed{phase:target}': ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    feedback_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${plannedRequests}`],
  },
};

function readCsrf(response) {
  try {
    const body = response.json();
    const cookie = response.cookies['XSRF-TOKEN']?.[0]?.value;
    if (
      response.status === 200 &&
      typeof body?.token === 'string' &&
      body.token &&
      typeof body.headerName === 'string' &&
      body.headerName &&
      typeof cookie === 'string' &&
      cookie
    ) {
      return { token: body.token, headerName: body.headerName, cookie };
    }
  } catch (_) {
    // Invalid CSRF responses are reported by setup.
  }
  return null;
}

function feedbackBody(sequence) {
  return JSON.stringify({
    content: `[LOADTEST-FEEDBACK] ${runLabel}/${executionId}/${sequence}`,
    pagePath: `/loadtest/feedback/${runLabel}/${executionId}/${sequence}`,
  });
}

function postFeedback(csrf, sequence, phase) {
  const jar = new http.CookieJar();
  jar.set(baseUrl, 'XSRF-TOKEN', csrf.cookie, { path: '/' });
  return http.post(feedbackUrl, feedbackBody(sequence), {
    jar,
    cookies: { 'XSRF-TOKEN': csrf.cookie },
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    timeout: '10s',
    tags: { name: 'feedback', phase },
  });
}

export function setup() {
  const csrfJar = new http.CookieJar();
  const csrf = readCsrf(
    http.get(csrfUrl, {
      jar: csrfJar,
      timeout: '10s',
      tags: { name: 'csrf_prepare', phase: 'preflight' },
    }),
  );
  if (!csrf) {
    throw new Error(
      'Preflight failed: verify CSRF endpoint and configuration.',
    );
  }

  const response = postFeedback(csrf, 'preflight', 'preflight');
  if (response.status !== 204) {
    throw new Error(
      'Preflight failed: verify public feedback API, CSRF and request contract.',
    );
  }
  return { csrf };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  if (index >= plannedRequests) return;

  const response = postFeedback(data.csrf, index, 'target');
  latency.add(response.timings.duration);
  check(response, {
    'feedback HTTP 204': (value) => value.status === 204,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latencyValues =
    data.metrics.feedback_load_target_duration_ms?.values || {};
  const httpErrorRate =
    data.metrics['http_req_failed{phase:target}']?.values.rate || 0;
  const checkRate = data.metrics.checks?.values.rate || 0;
  const dropped = data.metrics.dropped_iterations?.values.count || 0;
  const achievedRps = requests / measurementSeconds;
  const result = {
    api: 'POST /api/feedback',
    run: runLabel,
    executionId,
    targetRps,
    achievedRps,
    vus,
    maxVus,
    duration,
    expectedRequests: plannedRequests,
    requests,
    expectedInsertedRows: plannedRequests + 1,
    pagePathPrefix: `/loadtest/feedback/${runLabel}/${executionId}/`,
    latency: latencyValues,
    httpErrorRate,
    checkRate,
    droppedIterations: dropped,
  };
  const filename = `result-feedback-${targetRps}rps-${runLabel}.json`;
  return {
    stdout:
      `Feedback: ${vus}-${maxVus} VUs, ${duration}, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${plannedRequests}, RPS=${achievedRps.toFixed(2)}, p95=${latencyValues['p(95)'] || 0}ms (<=300), p99=${latencyValues['p(99)'] || 0}ms (<=500)\n` +
      `HTTP error rate=${httpErrorRate}, checks=${checkRate}, dropped=${dropped}\n` +
      `result: ${filename}\n`,
    [filename]: JSON.stringify(result, null, 2),
  };
}
