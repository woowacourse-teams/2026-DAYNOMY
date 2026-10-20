import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const environment = {
  BASE_URL: 'https://dev.example.com/',
  DEPLOYED_SHA: 'abcdef1',
  WEEK_START: '2026-10-19',
  EXPECTED_TOTAL: '2',
  EXPECTED_CONFIRMED: 'false',
};
const rankings = () => ({
  weekStart: '2026-10-19',
  leagueType: 'WEEKLY_RETURN',
  confirmed: false,
  totalCount: 2,
  rankings: [
    { rank: 1, publicId: 'public-a', weeklyReturnRate: 3.5 },
    { rank: 3, publicId: 'public-b', weeklyReturnRate: 2 },
  ],
});

async function fixture(version = 'v1', overrides = {}) {
  const requests = [];
  const metrics = {};
  const execution = { scenario: { iterationInTest: 0 } };
  let response = { status: 200, json: rankings, timings: { duration: 12 } };
  class Metric {
    constructor(name) {
      this.name = name;
    }
    add(value) {
      (metrics[this.name] ||= []).push(value);
    }
  }
  const context = vm.createContext({ __ENV: { ...environment, ...overrides } });
  const builtins = {
    'k6/http': {
      default: {
        get: (url, options) => {
          requests.push({ url, options });
          return response;
        },
      },
    },
    k6: { check: (value, checks) => Object.values(checks).every((check) => check(value)) },
    'k6/execution': { default: execution },
    'k6/metrics': { Counter: Metric, Rate: Metric, Trend: Metric },
  };
  const entry = new vm.SourceTextModule(
    readFileSync(new URL(`../league-rankings-${version}.js`, import.meta.url), 'utf8'),
    { context },
  );
  await entry.link(async () => {
    const scenario = new vm.SourceTextModule(
      readFileSync(new URL('../league-rankings-scenario.js', import.meta.url), 'utf8'),
      { context },
    );
    await scenario.link((name) => {
      const exports = builtins[name];
      assert.ok(exports, `Unexpected module: ${name}`);
      return new vm.SyntheticModule(
        Object.keys(exports),
        function () {
          for (const [key, value] of Object.entries(exports)) this.setExport(key, value);
        },
        { context },
      );
    });
    return scenario;
  });
  await entry.evaluate();
  return {
    script: entry.namespace,
    requests,
    metrics,
    execution,
    respond: (value) => {
      response = value;
    },
  };
}

test('V1/V2 send the same anonymous request and workload; only version and deployment labels differ', async () => {
  const v1 = await fixture('v1');
  const v2 = await fixture('v2', { DEPLOYED_SHA: 'abcdef2' });
  for (const { script, requests, metrics } of [v1, v2]) {
    script.setup();
    assert.deepEqual(metrics, {}, 'preflight must not count as measurement');
    script.default();
    assert.equal(requests.length, 2);
    assert.equal(requests[0].options.tags.phase, 'preflight');
    assert.equal(requests[1].options.tags.phase, 'measurement');
    assert.equal(
      requests[1].url,
      'https://dev.example.com/api/league/rankings?leagueType=WEEKLY_RETURN&weekStart=2026-10-19&page=0&size=20',
    );
    assert.equal(requests[1].options.timeout, '10s');
    assert.equal(requests[1].options.cookies, undefined);
    assert.deepEqual(metrics.league_requests, [1]);
    assert.deepEqual(metrics.league_success, [true]);
    assert.deepEqual(metrics.league_duration_ms, [12]);
  }
  const options1 = JSON.parse(JSON.stringify(v1.script.options));
  const options2 = JSON.parse(JSON.stringify(v2.script.options));
  assert.equal(options1.tags.version, 'v1');
  assert.equal(options2.tags.version, 'v2');
  delete options1.tags.version;
  delete options2.tags.version;
  assert.deepEqual(options1, options2);
});

test('invalid inputs fail before making any request', async () => {
  for (const overrides of [
    { BASE_URL: '' },
    { BASE_URL: 'https://secret@dev.example.com' },
    { BASE_URL: 'https://dev.example.com/path' },
    { DEPLOYED_SHA: '' },
    { WEEK_START: '2026-02-30' },
    { WEEK_START: '2026-10-20' },
    { WEEK_START: '' },
    { EXPECTED_TOTAL: '0' },
    { EXPECTED_TOTAL: 'NaN' },
    { EXPECTED_CONFIRMED: '' },
    { EXPECTED_CONFIRMED: 'FALSE' },
    { LEAGUE_TYPE: 'UNKNOWN' },
    { RPS: '0' },
    { RPS: '1.5' },
    { DURATION: '0s' },
    { DURATION: 'infinity' },
    { VUS: '-1' },
    { TARGET_P95_MS: '0' },
    { RUN: '../result' },
  ]) {
    await assert.rejects(fixture('v1', overrides), { name: 'Error' });
  }
});

test('confirmed leagues validate a full first page without requiring consecutive ranks', async () => {
  for (const leagueType of ['STABLE', 'BEGINNER']) {
    const { script, respond, metrics } = await fixture('v2', {
      LEAGUE_TYPE: leagueType,
      EXPECTED_TOTAL: '1000',
      EXPECTED_CONFIRMED: 'true',
    });
    respond({
      status: 200,
      timings: { duration: 20 },
      json: () => ({
        ...rankings(),
        leagueType,
        confirmed: true,
        totalCount: 1000,
        rankings: Array.from({ length: 20 }, (_, index) => ({
          rank: 2 + Math.floor(index / 2),
          publicId: `public-${index}`,
          weeklyReturnRate: -0.5,
        })),
      }),
    });
    script.setup();
    script.default();
    assert.deepEqual(metrics.league_success, [true]);
  }
});

test('preflight rejects HTTP errors, invalid JSON and changed or incomplete comparison data', async () => {
  const values = [
    { status: 503, json: rankings },
    {
      status: 200,
      json: () => {
        throw new Error('not JSON');
      },
    },
    ...[
      { rankings: [] },
      { totalCount: 3 },
      { confirmed: true },
      { weekStart: '2026-10-12' },
      { leagueType: 'STABLE' },
      {
        rankings: [
          { rank: 1, publicId: 'public-a', weeklyReturnRate: null },
          rankings().rankings[1],
        ],
      },
      { rankings: [rankings().rankings[0], rankings().rankings[0]] },
    ].map((changes) => ({ status: 200, json: () => ({ ...rankings(), ...changes }) })),
  ];
  for (const response of values) {
    const { script, respond, metrics } = await fixture();
    respond(response);
    assert.throws(() => script.setup(), /Preflight failed/);
    assert.deepEqual(metrics, {});
  }
});

test('measurement failures count as failures and boundary iterations do not exceed planned requests', async () => {
  const { script, respond, metrics, execution, requests } = await fixture();
  script.setup();
  respond({ status: 0, json: rankings, timings: { duration: 10000 } });
  script.default();
  assert.deepEqual(metrics.league_success, [false]);
  for (let index = 1; index <= 30; index++) {
    execution.scenario.iterationInTest = index;
    script.default();
  }
  assert.equal(metrics.league_requests.length, 30);
  assert.equal(requests.length, 31, 'one preflight plus exactly 30 measured requests');
});

test('summary keeps deployment and workload evidence and fails missing, slow, failed or dropped requests', async () => {
  const { script } = await fixture('v2', { LOADTEST_ID: '123-1' });
  function report(changes = {}) {
    const output = script.handleSummary({
      metrics: {
        league_requests: { values: { count: 30 } },
        league_success: { values: { rate: 1 } },
        league_duration_ms: { values: { 'p(95)': 12, 'p(99)': 20 } },
        ...changes,
      },
    });
    const result = JSON.parse(output['result-league-rankings-v2-123-1.json']);
    assert.ok(!JSON.stringify(output).includes('public-a'));
    return result;
  }
  assert.equal(report().passed, true);
  assert.equal(report().config.deployedSha, 'abcdef1');
  assert.equal(report().config.expectedTotal, 2);
  for (const changes of [
    { league_requests: { values: { count: 29 } } },
    { league_success: { values: { rate: 0.99 } } },
    { league_duration_ms: { values: { 'p(95)': 1000 } } },
    { league_duration_ms: { values: {} } },
    { dropped_iterations: { values: { count: 1 } } },
  ])
    assert.equal(report(changes).passed, false);
});
