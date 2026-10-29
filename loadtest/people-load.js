import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const minimumPeople = Number(__ENV.MIN_PEOPLE || 1);
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
if (![minimumPeople, targetRps, vus, maxVus].every(Number.isSafeInteger)) {
  throw new Error('MIN_PEOPLE, RPS, VUS and MAX_VUS must be integers.');
}
if (minimumPeople <= 0 || targetRps <= 0 || vus <= 0 || maxVus < vus) {
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
const latency = new Trend('people_load_target_duration_ms', true);
const url = `${baseUrl}/api/people`;

export const options = {
  scenarios: {
    people_load: {
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
  tags: { test_type: 'load', api: 'people', run: runLabel },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    'http_req_failed{phase:target}': ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
    people_load_target_duration_ms: ['p(95)<=300', 'p(99)<=500'],
    'http_reqs{phase:target}': [`count==${plannedRequests}`],
  },
};

function request(phase) {
  return http.get(url, {
    timeout: '10s',
    tags: { name: 'people', phase },
  });
}

function readPeople(response) {
  try {
    const body = response.json();
    if (
      response.status === 200 &&
      Array.isArray(body?.people) &&
      body.people.length >= minimumPeople &&
      body.people.every(
        (person) =>
          Number.isSafeInteger(person?.id) &&
          person.id > 0 &&
          typeof person.name === 'string' &&
          Array.isArray(person.posts) &&
          person.posts.every(
            (post) =>
              Number.isSafeInteger(post?.id) &&
              post.id > 0 &&
              ['YOUTUBE', 'THREADS', 'X'].includes(post.sourceType) &&
              typeof post.title === 'string' &&
              typeof post.url === 'string',
          ),
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
  const body = readPeople(request('preflight'));
  if (!body) {
    throw new Error(
      `Preflight failed: verify at least ${minimumPeople} featured people.`,
    );
  }
  return { peopleCount: body.people.length };
}

export default function () {
  if (exec.scenario.iterationInTest >= plannedRequests) return;
  const response = request('target');
  const body = readPeople(response);
  latency.add(response.timings.duration);
  check(response, {
    'people HTTP 200': (value) => value.status === 200,
    'people response contract': () => body !== null,
  });
}

export function handleSummary(data) {
  const requests = data.metrics['http_reqs{phase:target}']?.values.count || 0;
  const latencyValues =
    data.metrics.people_load_target_duration_ms?.values || {};
  const errorRate =
    data.metrics['http_req_failed{phase:target}']?.values.rate ?? null;
  const checkRate = data.metrics.checks?.values.rate ?? null;
  const dropped = data.metrics.dropped_iterations?.values.count || 0;
  const result = {
    api: 'GET /api/people',
    run: runLabel,
    targetRps,
    duration,
    vus,
    maxVus,
    minimumPeople,
    plannedRequests,
    actualRequests: requests,
    achievedRps: requests / measurementSeconds,
    latency: latencyValues,
    errorRate,
    checkRate,
    droppedIterations: dropped,
  };
  const filename = `result-people-${targetRps}rps-${runLabel}.json`;
  return {
    stdout:
      `People: ${vus}-${maxVus} VUs, ${duration}, target=${targetRps} RPS\n` +
      `target: requests=${requests}/${plannedRequests}, RPS=${result.achievedRps.toFixed(2)}, ` +
      `p95=${latencyValues['p(95)'] ?? 'n/a'}ms (<=300), ` +
      `p99=${latencyValues['p(99)'] ?? 'n/a'}ms (<=500)\n` +
      `HTTP error rate=${errorRate}, checks=${checkRate}, dropped=${dropped}\n` +
      `result: ${filename}\n`,
    [filename]: JSON.stringify(result, null, 2),
  };
}
