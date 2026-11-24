import http from 'k6/http';
import { Counter } from 'k6/metrics';
import execution from 'k6/execution';

// Run outside the deployment server so a deployment cannot stop the observer.
const baseUrl = (__ENV.BASE_URL || '').replace(/\/$/, '');
if (!/^https?:\/\/[^/?#]+$/.test(baseUrl)) {
  throw new Error('BASE_URL must contain only the HTTP(S) origin');
}
const rate = Number(__ENV.RPS || 10);
const duration = Number(__ENV.DURATION_SECONDS || 180);
if (!Number.isInteger(rate) || rate < 1 || rate > 20 ||
    !Number.isInteger(duration) || duration < 1 || duration > 600) {
  throw new Error('Use integer RPS 1..20 and DURATION_SECONDS 1..600');
}
const failures = new Counter('probe_failures');
const successes = new Counter('probe_successes');
export const options = {
  scenarios: {
    availability: {
      executor: 'constant-arrival-rate', rate, timeUnit: '1s',
      duration: `${duration}s`, preAllocatedVUs: rate * 6,
      maxVUs: rate * 6, gracefulStop: '6s',
    },
  },
  // Backend failures are observations in the baseline, not reasons to stop.
  thresholds: { dropped_iterations: ['count==0'] },
  summaryTrendStats: ['avg', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  // k6 can start an iteration on the duration boundary; cap actual HTTP requests.
  if (execution.scenario.iterationInTest >= rate * duration) return;
  const started = Date.now();
  const response = http.get(`${baseUrl}/api/news?page=1&size=1`, {
    timeout: '5s', redirects: 0,
    headers: { 'Cache-Control': 'no-cache', Accept: 'application/json' },
    tags: { name: 'deployment-news-probe' },
  });
  let valid = false;
  try {
    const body = response.json();
    valid = response.status === 200 && Array.isArray(body.items) && body.page === 1;
  } catch (_) {
    // A proxy HTML error page must not count as a healthy API response.
  }
  if (valid) successes.add(1);
  else failures.add(1);
  // Do not record response bodies, cookies, credentials or request headers.
  console.log(JSON.stringify({
    kind: 'deployment_probe', started_ms: started, finished_ms: Date.now(),
    slot: response.headers['X-Daynomy-Slot'] || null,
    status: response.status, ok: valid, error_code: response.error_code || 0,
  }));
}

export function handleSummary(data) {
  const metric = (name) => data.metrics[name]?.values || {};
  const dropped = metric('dropped_iterations').count || 0;
  const passed = metric('probe_successes').count || 0;
  const failed = metric('probe_failures').count || 0;
  const summary = {
    base_url: baseUrl, requested_rps: rate, duration_seconds: duration,
    planned_requests: rate * duration, completed_requests: passed + failed,
    successes: passed, failures: failed, dropped_iterations: dropped,
    complete: dropped === 0 && passed + failed === rate * duration,
    request_duration_ms: metric('http_req_duration'),
  };
  return {
    [__ENV.SUMMARY_FILE || 'deployment-summary.json']: JSON.stringify(summary, null, 2),
    stdout: `${JSON.stringify(summary, null, 2)}\n`,
  };
}
