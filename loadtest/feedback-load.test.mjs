import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function loadScript(env = {}) {
  const calls = [];
  const checks = [];
  const trends = [];
  const jars = [];
  const execution = { scenario: { iterationInTest: 0 } };
  const state = {
    csrfStatus: 200,
    csrfJson: true,
    csrfCookie: true,
    postStatus: 204,
  };
  const source = readFileSync(
    new URL('feedback-load.js', import.meta.url),
    'utf8',
  )
    .replace(/^import .*;\n/gm, '')
    .replace(/export default function \((.*?)\)/, 'function run($1)')
    .replaceAll('export function ', 'function ')
    .replace('export const options', 'const options');
  class CookieJar {
    constructor() {
      this.cookies = new Map();
      jars.push(this);
    }

    set(url, name, value, options) {
      this.cookies.set(name, { url, value, options });
    }
  }
  const context = vm.createContext({
    __ENV: {
      BASE_URL: 'https://loadtest.invalid',
      LOADTEST_ID: '123-1',
      RPS: '100',
      VUS: '100',
      MAX_VUS: '150',
      DURATION: '5m',
      RUN: 'feedback-100',
      ...env,
    },
    exec: execution,
    Trend: class {
      add(value) {
        trends.push(value);
      }
    },
    check: (response, predicates) =>
      checks.push(
        Object.values(predicates).every((predicate) => predicate(response)),
      ),
    http: {
      CookieJar,
      get: (url, options) => {
        calls.push({ method: 'GET', url, options });
        return {
          status: state.csrfStatus,
          cookies: state.csrfCookie
            ? { 'XSRF-TOKEN': [{ value: 'csrf-cookie' }] }
            : {},
          json: () => {
            if (!state.csrfJson) throw new Error('Invalid JSON');
            return { token: 'csrf-token', headerName: 'X-XSRF-TOKEN' };
          },
        };
      },
      post: (url, body, options) => {
        calls.push({ method: 'POST', url, body, options });
        return {
          status: state.postStatus,
          timings: { duration: 12 },
        };
      },
    },
  });
  vm.runInContext(
    `${source}\nglobalThis.program = { options, setup, run, handleSummary };`,
    context,
  );
  return {
    ...context.program,
    calls,
    checks,
    trends,
    jars,
    execution,
    state,
  };
}

test('fixed arrival rate uses the requested RPS and write-safe VU capacity', () => {
  for (const [rps, vus, maxVus, requests] of [
    [100, 100, 150, 30000],
    [300, 300, 450, 90000],
    [500, 500, 750, 150000],
  ]) {
    const script = loadScript({
      RPS: String(rps),
      VUS: String(vus),
      MAX_VUS: String(maxVus),
    });
    const scenario = script.options.scenarios.feedback_load;
    assert.equal(scenario.executor, 'constant-arrival-rate');
    assert.equal(scenario.rate, rps);
    assert.equal(scenario.duration, '5m');
    assert.equal(scenario.preAllocatedVUs, vus);
    assert.equal(scenario.maxVUs, maxVus);
    assert.deepEqual(
      Array.from(script.options.thresholds.feedback_load_target_duration_ms),
      ['p(95)<=300', 'p(99)<=500'],
    );
    assert.deepEqual(
      Array.from(script.options.thresholds['http_reqs{phase:target}']),
      [`count==${requests}`],
    );
  }
});

test('preflight prepares CSRF and verifies one real feedback write', () => {
  const script = loadScript();
  const data = script.setup();
  assert.deepEqual(JSON.parse(JSON.stringify(data.csrf)), {
    token: 'csrf-token',
    headerName: 'X-XSRF-TOKEN',
    cookie: 'csrf-cookie',
  });
  assert.equal(script.calls.length, 2);
  assert.equal(script.calls[0].method, 'GET');
  assert.equal(script.calls[0].url, 'https://loadtest.invalid/api/auth/csrf');
  assert.equal(script.calls[1].method, 'POST');
  assert.equal(script.calls[1].url, 'https://loadtest.invalid/api/feedback');
  assert.equal(script.calls[1].options.tags.phase, 'preflight');
  assert.equal(script.calls[1].options.headers['X-XSRF-TOKEN'], 'csrf-token');
  assert.equal(script.calls[1].options.cookies['XSRF-TOKEN'], 'csrf-cookie');
  assert.deepEqual(JSON.parse(script.calls[1].body), {
    content: '[LOADTEST-FEEDBACK] feedback-100/123-1/preflight',
    pagePath: '/loadtest/feedback/feedback-100/123-1/preflight',
  });
});

test('each measured request uses a fresh jar and identifiable unique data', () => {
  const script = loadScript();
  const data = script.setup();
  const preflightJarCount = script.jars.length;

  script.execution.scenario.iterationInTest = 0;
  script.run(data);
  script.execution.scenario.iterationInTest = 1;
  script.run(data);

  const targetCalls = script.calls.filter(
    ({ options }) => options.tags.phase === 'target',
  );
  assert.equal(targetCalls.length, 2);
  assert.notDeepEqual(
    JSON.parse(targetCalls[0].body),
    JSON.parse(targetCalls[1].body),
  );
  assert.notEqual(targetCalls[0].options.jar, targetCalls[1].options.jar);
  assert.equal(script.jars.length, preflightJarCount + 2);
  for (const [index, call] of targetCalls.entries()) {
    assert.deepEqual(JSON.parse(call.body), {
      content: `[LOADTEST-FEEDBACK] feedback-100/123-1/${index}`,
      pagePath: `/loadtest/feedback/feedback-100/123-1/${index}`,
    });
    assert.equal(
      call.options.jar.cookies.get('XSRF-TOKEN').value,
      'csrf-cookie',
    );
    assert.equal(call.options.headers['Content-Type'], 'application/json');
  }
  assert.deepEqual(script.checks, [true, true]);
  assert.deepEqual(script.trends, [12, 12]);
});

test('invalid inputs stop before sending HTTP requests', () => {
  for (const env of [
    { BASE_URL: 'https://user:password@loadtest.invalid' },
    { BASE_URL: 'https://loadtest.invalid/path' },
    { RPS: '0' },
    { VUS: '1.5' },
    { VUS: '100', MAX_VUS: '99' },
    { DURATION: '9s' },
    { RUN: '../private' },
    { RUN: 'a'.repeat(101) },
    { LOADTEST_ID: '../private' },
  ]) {
    assert.throws(() => loadScript(env));
  }
});

test('CSRF and feedback failures stop before measured load', () => {
  for (const failure of [
    'csrf-http',
    'csrf-json',
    'csrf-cookie',
    'feedback',
  ]) {
    const script = loadScript();
    if (failure === 'csrf-http') script.state.csrfStatus = 503;
    if (failure === 'csrf-json') script.state.csrfJson = false;
    if (failure === 'csrf-cookie') script.state.csrfCookie = false;
    if (failure === 'feedback') script.state.postStatus = 400;
    assert.throws(() => script.setup(), /Preflight failed/);
    assert.equal(
      script.calls.some(({ options }) => options.tags?.phase === 'target'),
      false,
    );
  }
});

test('the planned request boundary prevents extra database writes', () => {
  const script = loadScript({
    RPS: '10',
    VUS: '10',
    MAX_VUS: '15',
    DURATION: '10s',
  });
  const data = script.setup();
  const preflightCalls = script.calls.length;
  script.execution.scenario.iterationInTest = 99;
  script.run(data);
  assert.equal(script.calls.length, preflightCalls + 1);
  script.execution.scenario.iterationInTest = 100;
  script.run(data);
  assert.equal(script.calls.length, preflightCalls + 1);
});

test('summary records write throughput, rows and cleanup prefix', () => {
  const script = loadScript();
  const result = script.handleSummary({
    metrics: {
      'http_reqs{phase:target}': { values: { count: 30000 } },
      feedback_load_target_duration_ms: {
        values: { avg: 10, 'p(95)': 20, 'p(99)': 30, max: 40 },
      },
      'http_req_failed{phase:target}': { values: { rate: 0 } },
      checks: { values: { rate: 1 } },
      dropped_iterations: { values: { count: 0 } },
    },
  });
  const filename = Object.keys(result).find((key) => key !== 'stdout');
  const report = JSON.parse(result[filename]);
  assert.equal(report.api, 'POST /api/feedback');
  assert.equal(report.achievedRps, 100);
  assert.equal(report.requests, 30000);
  assert.equal(report.expectedInsertedRows, 30001);
  assert.equal(
    report.pagePathPrefix,
    '/loadtest/feedback/feedback-100/123-1/',
  );
  assert.equal(report.droppedIterations, 0);
});
