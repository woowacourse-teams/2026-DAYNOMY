import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || '').replace(/\/+$/, '');
const targetRps = Number(__ENV.RPS || 50);
const vus = Number(__ENV.VUS || targetRps);
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
    new Trend(`league_investor_details_load_${name}_duration_ms`, true),
  ]),
);

export const options = {
  scenarios: {
    investor_details_load: {
      executor: 'ramping-arrival-rate',
      startRate: warmupRps,
      timeUnit: '1s',
      preAllocatedVUs: vus,
      maxVUs: vus,
      stages,
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
    ...Object.fromEntries(phases.map(({ name }) => [`http_reqs{phase:${name}}`, ['count>0']])),
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
  const elapsedMs = Date.now() - exec.scenario.startTime;
  const phase = phases[Math.min(Math.floor(elapsedMs / 60_000), phases.length - 1)];
  const response = request(phase.name);
  const body = readDetails(response);
  latencies[phase.name].add(response.timings.duration);
  check(response, {
    'investor details HTTP 200': (value) => value.status === 200,
    'nonempty public holdings and decisions': () => body !== null,
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
      latency: data.metrics[`league_investor_details_load_${name}_duration_ms`]?.values || {},
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
      `League investor details load: ${vus} VUs, 5 minutes, target=${targetRps} RPS\n` +
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
