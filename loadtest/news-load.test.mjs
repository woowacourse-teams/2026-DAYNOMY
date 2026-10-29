import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function loadScript(env = {}) {
  const calls = [];
  const checks = [];
  const trends = [];
  const execution = { scenario: { iterationInTest: 0 } };
  const state = { status: 200, malformed: false, totalElements: 100000 };
  const source = readFileSync(
    new URL('news-contents-load.js', import.meta.url),
    'utf8',
  )
    .replace(/^import .*;\n/gm, '')
    .replace(/export default function \((.*?)\)/, 'function run($1)')
    .replaceAll('export function ', 'function ')
    .replace('export const options', 'const options');
  const context = vm.createContext({
    __ENV: {
      BASE_URL: 'https://loadtest.invalid',
      CASE: 'FIRST',
      RPS: '100',
      VUS: '75',
      MAX_VUS: '100',
      DURATION: '5m',
      RUN: 'baseline-01',
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
      get: (url, options) => {
        calls.push({ url, options });
        const page = Number(new URL(url).searchParams.get('page'));
        const totalPages = Math.ceil(state.totalElements / 100);
        return {
          status: state.status,
          timings: { duration: 12 },
          json: () => {
            if (state.malformed) throw new Error('Invalid JSON');
            return {
              items: [
                {
                  id: page,
                  assetId: 1,
                  sourceType: 'X',
                  title: `content-${page}`,
                  url: `https://loadtest.invalid/content/${page}`,
                },
              ],
              page,
              size: 100,
              totalPages,
              totalElements: state.totalElements,
              hasNext: page < totalPages,
            };
          },
        };
      },
    },
    URL,
  });
  vm.runInContext(
    `${source}\nglobalThis.program = { options, setup, run, handleSummary };`,
    context,
  );
  return { ...context.program, calls, checks, trends, execution, state };
}

test('fixed arrival rate uses the requested duration, RPS and VU capacity', () => {
  for (const [rps, vus, maxVus, requests] of [
    [100, 75, 100, 30000],
    [300, 225, 300, 90000],
    [500, 375, 500, 150000],
  ]) {
    const script = loadScript({
      RPS: String(rps),
      VUS: String(vus),
      MAX_VUS: String(maxVus),
    });
    const scenario = script.options.scenarios.news_contents_load;
    assert.equal(scenario.executor, 'constant-arrival-rate');
    assert.equal(scenario.rate, rps);
    assert.equal(scenario.duration, '5m');
    assert.equal(scenario.timeUnit, '1s');
    assert.equal(scenario.preAllocatedVUs, vus);
    assert.equal(scenario.maxVUs, maxVus);
    assert.deepEqual(
      Array.from(
        script.options.thresholds.news_contents_load_target_duration_ms,
      ),
      ['p(95)<=300', 'p(99)<=500'],
    );
    assert.deepEqual(
      Array.from(script.options.thresholds['http_reqs{phase:target}']),
      [`count==${requests}`],
    );
  }
});

test('FIRST, MIDDLE and LAST resolve one fixed target page from the preflight', () => {
  for (const [testCase, expectedPage, preflightRequests] of [
    ['FIRST', 1, 1],
    ['MIDDLE', 500, 2],
    ['LAST', 1000, 2],
  ]) {
    const script = loadScript({ CASE: testCase });
    const data = script.setup();
    assert.equal(data.page, expectedPage);
    assert.equal(script.calls.length, preflightRequests);
    assert.ok(
      script.calls.every(({ options }) => options.tags.phase === 'preflight'),
    );
    script.run(data);
    const measured = script.calls.at(-1);
    assert.equal(
      Number(new URL(measured.url).searchParams.get('page')),
      expectedPage,
    );
    assert.equal(measured.options.tags.phase, 'target');
    assert.equal(script.checks.at(-1), true);
    assert.deepEqual(script.trends, [12]);
  }
});

test('invalid environment values stop before sending requests', () => {
  for (const env of [
    { BASE_URL: 'https://user:password@loadtest.invalid' },
    { BASE_URL: 'https://loadtest.invalid/path' },
    { CASE: 'ALL' },
    { RPS: '0' },
    { VUS: '1.5' },
    { VUS: '100', MAX_VUS: '99' },
    { DURATION: '5hours' },
    { DURATION: '9s' },
    { MIN_CONTENTS: '0' },
    { RUN: '../private' },
  ]) {
    assert.throws(() => loadScript(env));
  }
});

test('preflight rejects HTTP, JSON, contract and insufficient-data failures', () => {
  for (const failure of ['http', 'json', 'count']) {
    const script = loadScript();
    if (failure === 'http') script.state.status = 503;
    if (failure === 'json') script.state.malformed = true;
    if (failure === 'count') script.state.totalElements = 99999;
    assert.throws(() => script.setup(), /Preflight failed/);
  }
});

test('the last planned request runs and an extra boundary iteration is ignored', () => {
  const script = loadScript({
    RPS: '10',
    VUS: '5',
    MAX_VUS: '10',
    DURATION: '10s',
  });
  const data = script.setup();
  script.execution.scenario.iterationInTest = 99;
  script.run(data);
  assert.equal(script.calls.length, 2);
  script.execution.scenario.iterationInTest = 100;
  script.run(data);
  assert.equal(script.calls.length, 2);
  assert.equal(script.checks.length, 1);
});

test('summary records requests, achieved RPS, latency and failures as JSON', () => {
  const script = loadScript();
  const report = script.handleSummary({
    metrics: {
      'http_reqs{phase:target}': { values: { count: 30000 } },
      news_contents_load_target_duration_ms: {
        values: { avg: 42, 'p(95)': 80, 'p(99)': 120, max: 150 },
      },
      'http_req_failed{phase:target}': { values: { rate: 0 } },
      checks: { values: { rate: 1 } },
      dropped_iterations: { values: { count: 0 } },
    },
  });
  assert.match(report.stdout, /requests=30000\/30000, RPS=100\.00/);
  assert.match(report.stdout, /p95=80ms \(<=300\), p99=120ms \(<=500\)/);
  const filename = 'result-news-contents-first-100rps-baseline-01.json';
  const result = JSON.parse(report[filename]);
  assert.equal(result.actualRequests, 30000);
  assert.equal(result.achievedRps, 100);
  assert.equal(result.errorRate, 0);
  assert.equal(result.droppedIterations, 0);
});
