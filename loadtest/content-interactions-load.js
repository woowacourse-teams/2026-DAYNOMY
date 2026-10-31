import crypto from 'k6/crypto';
import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const contentId = Number(__ENV.CONTENT_ID);
const contentType = (__ENV.CONTENT_TYPE || 'ASSET_CONTENT').trim();
const interactionType = (__ENV.INTERACTION_TYPE || 'OPEN').trim();
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
if (![contentId, targetRps, vus, maxVus].every(Number.isSafeInteger)) {
  throw new Error('CONTENT_ID, RPS, VUS and MAX_VUS must be integers.');
}
if (contentId <= 0 || targetRps <= 0 || vus <= 0 || maxVus < vus) {
  throw new Error(
    'Use positive parameters and MAX_VUS greater than or equal to VUS.',
  );
}
if (!['NEWS', 'PERSON_POST', 'ASSET_CONTENT'].includes(contentType)) {
  throw new Error('CONTENT_TYPE must be NEWS, PERSON_POST or ASSET_CONTENT.');
}
if (!['OPEN', 'EXPAND', 'OUTBOUND_CLICK'].includes(interactionType)) {
  throw new Error('INTERACTION_TYPE must be OPEN, EXPAND or OUTBOUND_CLICK.');
}
if (!/^[A-Za-z0-9._-]+$/.test(runLabel)) {
  throw new Error(
    'RUN may contain only letters, numbers, dot, underscore and hyphen.',
  );
}
if (!executionId) throw new Error('LOADTEST_ID or RUN must not be empty.');

const measurementSeconds = durationSeconds(duration);
const plannedRequests = targetRps * measurementSeconds;
const latency = new Trend('content_interactions_load_target_duration_ms', true);
const csrfUrl = `${baseUrl}/api/auth/csrf`;
const interactionUrl = `${baseUrl}/api/content-interactions`;

export const options = {
  scenarios: {
    content_interactions_load: {
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
    api: 'content_interactions',
    run: runLabel,
  },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed{phase:target}': ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    content_interactions_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${plannedRequests}`],
  },
};

function eventId(sequence) {
  const hex = crypto.sha256(`${executionId}:${sequence}`, 'hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-8${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
}

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

function interactionBody(sequence) {
  return JSON.stringify({
    eventId: eventId(sequence),
    contentType,
    contentId,
    interactionType,
  });
}

function postInteraction(csrf, sequence, phase) {
  const jar = new http.CookieJar();
  jar.set(baseUrl, 'XSRF-TOKEN', csrf.cookie, { path: '/' });
  return http.post(interactionUrl, interactionBody(sequence), {
    jar,
    cookies: { 'XSRF-TOKEN': csrf.cookie },
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    timeout: '10s',
    tags: { name: 'content_interactions', phase },
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

  const response = postInteraction(csrf, 'preflight', 'preflight');
  if (
    response.status !== 204 ||
    !response.cookies.content_visitor?.[0]?.value
  ) {
    throw new Error(
      `Preflight failed: verify public ${contentType} CONTENT_ID=${contentId}, CSRF and anonymous visitor cookie.`,
    );
  }
  return { csrf };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  if (index >= plannedRequests) return;

  const response = postInteraction(data.csrf, index, 'target');
  latency.add(response.timings.duration);
  check(response, {
    'content interaction HTTP 204': (value) => value.status === 204,
    'content interaction visitor cookie': (value) =>
      Boolean(value.cookies.content_visitor?.[0]?.value),
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latencyValues =
    data.metrics.content_interactions_load_target_duration_ms?.values || {};
  const httpErrorRate =
    data.metrics['http_req_failed{phase:target}']?.values.rate || 0;
  const checkRate = data.metrics.checks?.values.rate || 0;
  const dropped = data.metrics.dropped_iterations?.values.count || 0;
  const achievedRps = requests / measurementSeconds;
  const result = {
    api: 'content_interactions',
    contentType,
    contentId,
    interactionType,
    targetRps,
    achievedRps,
    vus,
    maxVus,
    duration,
    expectedRequests: plannedRequests,
    requests,
    latency: latencyValues,
    httpErrorRate,
    checkRate,
    droppedIterations: dropped,
  };
  const filename = `result-content-interactions-${contentType.toLowerCase()}-${contentId}-${targetRps}rps-${runLabel}.json`;
  return {
    stdout:
      `Content interactions ${contentType}/${contentId}/${interactionType}: ${vus}-${maxVus} VUs, ${duration}, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${plannedRequests}, RPS=${achievedRps.toFixed(2)}, p95=${latencyValues['p(95)'] || 0}ms (<=300), p99=${latencyValues['p(99)'] || 0}ms (<=500)\n` +
      `HTTP error rate=${httpErrorRate}, checks=${checkRate}, dropped=${dropped}\n` +
      `result: ${filename}\n`,
    [filename]: JSON.stringify(result, null, 2),
  };
}
