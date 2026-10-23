import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const scripts = ['league-follow-summary-load.js', 'league-investor-details-load.js'];

function loadScript(file, env = {}) {
  const calls = [];
  const checks = [];
  const trends = [];
  const clock = { now: 0 };
  const execution = { scenario: { startTime: 0, iterationInTest: 0 } };
  const state = { status: 200, malformed: false };
  const body = file.includes('follow-summary')
    ? { investors: [{ publicId: 'test-investor', newReviewCount: 1 }] }
    : { publicId: 'test-investor', holdings: [{}], decisions: [{ transactionId: 1, reviews: [] }] };
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    .replace(/^import .*;\n/gm, '')
    .replace('export default function ()', 'function run()')
    .replaceAll('export function ', 'function ')
    .replace('export const options', 'const options');
  const context = vm.createContext({
    __ENV: {
      BASE_URL: 'https://loadtest.invalid',
      PUBLIC_ID: 'test-investor',
      RPS: '50',
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
    Date: class extends Date {
      static now() {
        return clock.now;
      }
    },
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
  return { ...context.program, calls, checks, trends, clock, execution, state, body };
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

  test(`${file}: preflight, phases and per-phase RPS`, () => {
    const script = loadScript(file);
    script.setup();
    assert.ok(script.calls.every(({ options }) => options.tags.phase === 'preflight'));
    for (const [index, phase] of ['warmup', 'low', 'medium', 'target', 'cooldown'].entries()) {
      script.clock.now = index * 60000;
      script.execution.scenario.iterationInTest = index;
      script.run();
      assert.equal(script.calls.at(-1).options.tags.phase, phase);
    }
    assert.equal(script.checks.length, 5);
    assert.ok(script.checks.every(Boolean));
    assert.equal(script.trends.length, 5);
    if (file.includes('follow-summary')) {
      assert.equal(script.calls[0].options.cookies.access_token, 'fake1.payload.signature');
      assert.equal(script.calls[1].options.cookies.access_token, 'fake2.payload.signature');
      assert.ok(script.calls.every(({ url }) => !url.includes('payload')));
    }
    const metrics = Object.fromEntries(
      ['warmup', 'low', 'medium', 'target', 'cooldown'].map((phase) => [
        `http_reqs{phase:${phase}}`,
        { values: { count: 120, rate: 0.4 } },
      ]),
    );
    metrics.http_req_failed = { values: { rate: 0 } };
    metrics.checks = { values: { rate: 1 } };
    const report = script.handleSummary({ metrics });
    assert.match(report.stdout, /target: requests=120, RPS=2\.00/);
    assert.deepEqual(Object.keys(report), ['stdout']);
  });

  test(`${file}: failed HTTP, malformed JSON and empty data fail validation`, () => {
    for (const failure of ['http', 'json', 'empty']) {
      const script = loadScript(file);
      if (failure === 'http') script.state.status = 401;
      if (failure === 'json') script.state.malformed = true;
      if (failure === 'empty') {
        if (file.includes('follow-summary')) script.body.investors = [];
        else script.body.decisions = [];
      }
      assert.throws(() => script.setup(), /Preflight failed/);
      script.run();
      assert.equal(script.checks.at(-1), false);
    }
  });
}
