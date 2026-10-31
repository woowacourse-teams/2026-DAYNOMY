import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function digest(value) {
  let hex = '';
  for (let index = 0; index < 64; index += 1) {
    const code = value.charCodeAt(index % value.length) + index;
    hex += (code % 16).toString(16);
  }
  return hex;
}

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
    visitorCookie: true,
  };
  const source = readFileSync(
    new URL('content-interactions-load.js', import.meta.url),
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
      CONTENT_ID: '7',
      CONTENT_TYPE: 'ASSET_CONTENT',
      INTERACTION_TYPE: 'OPEN',
      RPS: '100',
      VUS: '100',
      MAX_VUS: '150',
      DURATION: '5m',
      RUN: 'interactions-100',
      ...env,
    },
    crypto: { sha256: (value) => digest(value) },
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
          cookies: state.visitorCookie
            ? { content_visitor: [{ value: 'visitor-token' }] }
            : {},
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
    const scenario = script.options.scenarios.content_interactions_load;
    assert.equal(scenario.executor, 'constant-arrival-rate');
    assert.equal(scenario.rate, rps);
    assert.equal(scenario.duration, '5m');
    assert.equal(scenario.preAllocatedVUs, vus);
    assert.equal(scenario.maxVUs, maxVus);
    assert.deepEqual(
      Array.from(
        script.options.thresholds.content_interactions_load_target_duration_ms,
      ),
      ['p(95)<=300', 'p(99)<=500'],
    );
    assert.deepEqual(
      Array.from(script.options.thresholds['http_reqs{phase:target}']),
      [`count==${requests}`],
    );
  }
});

test('preflight prepares CSRF and verifies one real anonymous write', () => {
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
  assert.equal(
    script.calls[1].url,
    'https://loadtest.invalid/api/content-interactions',
  );
  assert.equal(script.calls[1].options.tags.phase, 'preflight');
  assert.equal(script.calls[1].options.headers['X-XSRF-TOKEN'], 'csrf-token');
  assert.equal(script.calls[1].options.cookies['XSRF-TOKEN'], 'csrf-cookie');
  assert.deepEqual(JSON.parse(script.calls[1].body), {
    eventId: JSON.parse(script.calls[1].body).eventId,
    contentType: 'ASSET_CONTENT',
    contentId: 7,
    interactionType: 'OPEN',
  });
  assert.match(
    JSON.parse(script.calls[1].body).eventId,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/,
  );
});

test('each measured request uses a fresh anonymous jar and unique event ID', () => {
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
  assert.notEqual(
    JSON.parse(targetCalls[0].body).eventId,
    JSON.parse(targetCalls[1].body).eventId,
  );
  assert.notEqual(targetCalls[0].options.jar, targetCalls[1].options.jar);
  assert.equal(script.jars.length, preflightJarCount + 2);
  for (const call of targetCalls) {
    assert.equal(call.options.jar.cookies.has('content_visitor'), false);
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
    { CONTENT_ID: '0' },
    { CONTENT_TYPE: 'UNKNOWN' },
    { INTERACTION_TYPE: 'LIKE' },
    { RPS: '0' },
    { VUS: '1.5' },
    { VUS: '100', MAX_VUS: '99' },
    { DURATION: '9s' },
    { RUN: '../private' },
  ]) {
    assert.throws(() => loadScript(env));
  }
});

test('CSRF, content and visitor-cookie failures stop before measured load', () => {
  for (const failure of [
    'csrf-http',
    'csrf-json',
    'csrf-cookie',
    'content',
    'visitor-cookie',
  ]) {
    const script = loadScript();
    if (failure === 'csrf-http') script.state.csrfStatus = 503;
    if (failure === 'csrf-json') script.state.csrfJson = false;
    if (failure === 'csrf-cookie') script.state.csrfCookie = false;
    if (failure === 'content') script.state.postStatus = 404;
    if (failure === 'visitor-cookie') script.state.visitorCookie = false;
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

test('summary records write throughput, latency and failures', () => {
  const script = loadScript();
  const result = script.handleSummary({
    metrics: {
      'http_reqs{phase:target}': { values: { count: 30000 } },
      content_interactions_load_target_duration_ms: {
        values: { avg: 10, 'p(95)': 20, 'p(99)': 30, max: 40 },
      },
      'http_req_failed{phase:target}': { values: { rate: 0 } },
      checks: { values: { rate: 1 } },
      dropped_iterations: { values: { count: 0 } },
    },
  });
  const filename = Object.keys(result).find((key) => key !== 'stdout');
  const report = JSON.parse(result[filename]);
  assert.equal(report.contentType, 'ASSET_CONTENT');
  assert.equal(report.contentId, 7);
  assert.equal(report.interactionType, 'OPEN');
  assert.equal(report.achievedRps, 100);
  assert.equal(report.requests, 30000);
  assert.equal(report.droppedIterations, 0);
});
