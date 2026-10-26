import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const scripts = [
  'league-rankings-load.js',
  'league-follow-summary-load.js',
  'league-investor-details-load.js',
];

function loadScript(file, env = {}) {
  const calls = [];
  const checks = [];
  const trends = [];
  const execution = { scenario: { iterationInTest: 0 } };
  const state = { status: 200, malformed: false };
  const body = file.includes('follow-summary')
    ? { investors: [{ publicId: 'test-investor', newReviewCount: 1 }] }
    : file.includes('rankings')
      ? {
          leagueType: 'WEEKLY_RETURN',
          weekStart: '2026-10-19',
          totalCount: 10000,
          rankings: Array(20).fill({}),
        }
      : { publicId: 'test-investor', holdings: [{}], decisions: [{ transactionId: 1, reviews: [] }] };
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    .replace(/^import .*;\n/gm, '')
    .replace(/export default function \((.*?)\)/, 'function run($1)')
    .replaceAll('export function ', 'function ')
    .replace('export const options', 'const options');
  const context = vm.createContext({
    __ENV: {
      BASE_URL: 'https://loadtest.invalid',
      PUBLIC_ID: 'test-investor',
      RPS: '100',
      VUS: '2',
      ...env,
    },
    open: () =>
      JSON.stringify({
        users: [
          { accessToken: 'fake1.payload.signature' },
          { accessToken: 'fake2.payload.signature' },
        ],
      }),
    exec: execution,
    Trend: class {
      add(value) {
        trends.push(value);
      }
    },
    check: (response, predicates) =>
      checks.push(Object.values(predicates).every((predicate) => predicate(response))),
    http: {
      get: (url, options) => {
        calls.push({ url, options });
        return {
          status: state.status,
          timings: { duration: 12 },
          json: () => {
            if (state.malformed) throw new Error('Invalid JSON');
            return body;
          },
        };
      },
    },
  });
  vm.runInContext(
    `${source}\nglobalThis.program = { options, setup, run, handleSummary };`,
    context,
  );
  return { ...context.program, calls, checks, trends, execution, state, body };
}

for (const file of scripts) {
  test(`${file}: invalid inputs stop before HTTP requests`, () => {
    for (const env of [
      { BASE_URL: 'https://user:password@loadtest.invalid' },
      { BASE_URL: 'https://loadtest.invalid/path' },
      { RPS: '0' },
      { VUS: '1.5' },
    ]) {
      assert.throws(() => loadScript(file, env));
    }
    if (file.includes('investor-details'))
      assert.throws(() => loadScript(file, { PUBLIC_ID: '../private' }));
  });

  test(`${file}: fixed arrival rate, latency thresholds and VU sizing`, () => {
    for (const [rps, vus] of [[100, 50], [300, 150], [500, 250], [101, 51]]) {
      const { options } = loadScript(file, { RPS: String(rps), VUS: undefined });
      const scenario = Object.values(options.scenarios)[0];
      assert.equal(scenario.executor, 'constant-arrival-rate');
      assert.equal(scenario.rate, rps);
      assert.equal(scenario.timeUnit, '1s');
      assert.equal(scenario.duration, '5m');
      assert.equal(scenario.preAllocatedVUs, vus);
      assert.equal(scenario.maxVUs, vus);
      const metric = Object.keys(options.thresholds).find((key) => key.endsWith('_target_duration_ms'));
      assert.deepEqual(Array.from(options.thresholds[metric]), ['p(95)<=300', 'p(99)<=500']);
      assert.deepEqual(Array.from(options.thresholds['http_reqs{phase:target}']), [`count==${rps * 300}`]);
      assert.deepEqual(Array.from(options.thresholds.dropped_iterations), ['count==0']);
    }
    assert.equal(Object.values(loadScript(file, { VUS: '75' }).options.scenarios)[0].preAllocatedVUs, 75);
    assert.equal(Object.values(loadScript(file, { RPS: undefined, VUS: undefined }).options.scenarios)[0].rate, 100);
  });

  test(`${file}: preflight, measured requests and achieved RPS`, () => {
    const script = loadScript(file);
    const data = script.setup();
    assert.ok(script.calls.every(({ options }) => options.tags.phase === 'preflight'));
    for (let index = 0; index < 5; index++) {
      script.execution.scenario.iterationInTest = index;
      script.run(data);
      assert.equal(script.calls.at(-1).options.tags.phase, 'target');
    }
    assert.equal(script.checks.length, 5);
    assert.ok(script.checks.every(Boolean));
    assert.equal(script.trends.length, 5);
    if (file.includes('follow-summary')) {
      assert.equal(script.calls[0].options.cookies.access_token, 'fake1.payload.signature');
      assert.equal(script.calls[1].options.cookies.access_token, 'fake2.payload.signature');
      assert.ok(script.calls.every(({ url }) => !url.includes('payload')));
    }
    if (file.includes('rankings')) {
      assert.ok(script.calls.slice(1).every(({ url }) => url.includes('weekStart=2026-10-19')));
    }
    const metrics = { 'http_reqs{phase:target}': { values: { count: 30000, rate: 99 } } };
    const metric = Object.keys(script.options.thresholds).find((key) => key.endsWith('_target_duration_ms'));
    metrics[metric] = { values: { 'p(95)': 80, 'p(99)': 120 } };
    metrics.http_req_duration = { values: { 'p(95)': 900, 'p(99)': 1000 } };
    metrics.http_req_failed = { values: { rate: 0 } };
    metrics.checks = { values: { rate: 1 } };
    const report = script.handleSummary({ metrics });
    assert.match(report.stdout, /target: requests=30000\/30000, RPS=100\.00/);
    assert.match(report.stdout, /p95=80ms \(<=300\), p99=120ms \(<=500\)/);
    assert.deepEqual(Object.keys(report), ['stdout']);
  });

  test(`${file}: failed HTTP, malformed JSON and empty data fail validation`, () => {
    for (const failure of ['http', 'json', 'empty']) {
      const script = loadScript(file);
      if (failure === 'http') script.state.status = 401;
      if (failure === 'json') script.state.malformed = true;
      if (failure === 'empty') {
        if (file.includes('follow-summary')) script.body.investors = [];
        else if (file.includes('rankings')) script.body.rankings = [];
        else script.body.decisions = [];
      }
      assert.throws(() => script.setup(), /Preflight failed/);
      script.run({ weekStart: '2026-10-19' });
      assert.equal(script.checks.at(-1), false);
    }
  });
}

test('rankings: fewer than 10,000 participants stops before the measured load', () => {
  const script = loadScript('league-rankings-load.js');
  script.body.totalCount = 9999;
  assert.throws(() => script.setup(), /10,000 league participants/);
  assert.equal(script.calls.length, 1);
});

test('rankings: the last planned request runs and an extra boundary iteration sends no HTTP request', () => {
  const script = loadScript('league-rankings-load.js');
  const data = script.setup();
  script.execution.scenario.iterationInTest = 29999;
  script.run(data);
  assert.equal(script.calls.length, 2);
  script.execution.scenario.iterationInTest = 30000;
  script.run(data);
  assert.equal(script.calls.length, 2);
  assert.equal(script.checks.length, 1);
  assert.equal(script.trends.length, 1);
});
