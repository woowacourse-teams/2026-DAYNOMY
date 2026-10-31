import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const scripts = [
  'asset-contents-load.js',
  'content-rankings-load.js',
  'news-list-load.js',
  'news-detail-load.js',
  'people-load.js',
];

function responseBody(file, url, state) {
  if (file === 'asset-contents-load.js') {
    return {
      assetId: 7,
      assetCode: 'TEST',
      assetName: '테스트 종목',
      contents: Array.from({ length: state.count }, (_, index) => ({
        id: index + 1,
        assetId: 7,
        sourceType: 'X',
        title: `content-${index + 1}`,
        url: `https://loadtest.invalid/content/${index + 1}`,
      })),
    };
  }
  if (file === 'content-rankings-load.js') {
    return {
      items: Array.from({ length: Math.min(20, state.count) }, (_, index) => ({
        contentType: 'ASSET_CONTENT',
        contentId: index + 1,
        score: 100 - index,
        lastInteractedAt: '2026-10-29T00:00:00Z',
      })),
      periodDays: 7,
      page: 1,
      size: 20,
      totalPages: Math.ceil(state.count / 20),
      totalElements: state.count,
      hasNext: state.count > 20,
    };
  }
  if (file === 'news-list-load.js') {
    const page = Number(new URL(url).searchParams.get('page'));
    return {
      items: [
        {
          id: page,
          title: `news-${page}`,
          category: 'STOCK',
          publishedAt: '2026-10-29T00:00:00Z',
        },
      ],
      page,
      size: 100,
      totalPages: Math.ceil(state.count / 100),
      totalElements: state.count,
      hasNext: page < Math.ceil(state.count / 100),
    };
  }
  if (file === 'news-detail-load.js') {
    if (state.count === 0) return {};
    return {
      id: 9,
      title: '뉴스 제목',
      content: '뉴스 본문',
      sources: [],
      category: 'STOCK',
      publishedAt: '2026-10-29T00:00:00Z',
    };
  }
  return {
    people: Array.from({ length: state.count }, (_, index) => ({
      id: index + 1,
      name: `person-${index + 1}`,
      posts: [
        {
          id: index + 1,
          sourceType: 'X',
          title: 'post',
          url: 'https://x.com/post',
        },
      ],
    })),
  };
}

function loadScript(file, env = {}) {
  const calls = [];
  const checks = [];
  const trends = [];
  const execution = { scenario: { iterationInTest: 0 } };
  const state = {
    status: 200,
    malformed: false,
    count:
      file === 'asset-contents-load.js'
        ? 500
        : file === 'news-list-load.js'
          ? 200
          : 20,
  };
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    .replace(/^import .*;\n/gm, '')
    .replace(/export default function \((.*?)\)/, 'function run($1)')
    .replaceAll('export function ', 'function ')
    .replace('export const options', 'const options');
  const context = vm.createContext({
    __ENV: {
      BASE_URL: 'https://loadtest.invalid',
      ASSET_ID: '7',
      NEWS_ID: '9',
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
        return {
          status: state.status,
          timings: { duration: 12 },
          json: () => {
            if (state.malformed) throw new Error('Invalid JSON');
            return responseBody(file, url, state);
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

for (const file of scripts) {
  test(`${file}: fixed arrival rate and latency thresholds`, () => {
    for (const [rps, vus, maxVus, requests] of [
      [100, 75, 100, 30000],
      [300, 225, 300, 90000],
      [500, 375, 500, 150000],
    ]) {
      const script = loadScript(file, {
        RPS: String(rps),
        VUS: String(vus),
        MAX_VUS: String(maxVus),
      });
      const scenario = Object.values(script.options.scenarios)[0];
      assert.equal(scenario.executor, 'constant-arrival-rate');
      assert.equal(scenario.rate, rps);
      assert.equal(scenario.duration, '5m');
      assert.equal(scenario.preAllocatedVUs, vus);
      assert.equal(scenario.maxVUs, maxVus);
      const metric = Object.keys(script.options.thresholds).find((key) =>
        key.endsWith('_target_duration_ms'),
      );
      assert.deepEqual(Array.from(script.options.thresholds[metric]), [
        'p(95)<=300',
        'p(99)<=500',
      ]);
      assert.deepEqual(
        Array.from(script.options.thresholds['http_reqs{phase:target}']),
        [`count==${requests}`],
      );
    }
  });

  test(`${file}: preflight and one measured request validate the response`, () => {
    const script = loadScript(file);
    const data = script.setup();
    assert.ok(
      script.calls.every(({ options }) => options.tags.phase === 'preflight'),
    );
    script.run(data);
    assert.equal(script.calls.at(-1).options.tags.phase, 'target');
    assert.equal(script.checks.at(-1), true);
    assert.deepEqual(script.trends, [12]);
  });

  test(`${file}: invalid common inputs stop before HTTP`, () => {
    for (const env of [
      { BASE_URL: 'https://user:password@loadtest.invalid' },
      { BASE_URL: 'https://loadtest.invalid/path' },
      { RPS: '0' },
      { VUS: '1.5' },
      { VUS: '100', MAX_VUS: '99' },
      { DURATION: '9s' },
      { RUN: '../private' },
    ]) {
      assert.throws(() => loadScript(file, env));
    }
  });

  test(`${file}: HTTP, JSON and insufficient data fail preflight`, () => {
    for (const failure of ['http', 'json', 'empty']) {
      const script = loadScript(file);
      if (failure === 'http') script.state.status = 503;
      if (failure === 'json') script.state.malformed = true;
      if (failure === 'empty') script.state.count = 0;
      assert.throws(() => script.setup(), /Preflight failed/);
    }
  });

  test(`${file}: the planned request boundary prevents extra HTTP`, () => {
    const script = loadScript(file, {
      RPS: '10',
      VUS: '5',
      MAX_VUS: '10',
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
}

test('news list FIRST, MIDDLE and LAST resolve fixed pages', () => {
  for (const [testCase, expectedPage] of [
    ['FIRST', 1],
    ['MIDDLE', 1],
    ['LAST', 2],
  ]) {
    const script = loadScript('news-list-load.js', { CASE: testCase });
    const data = script.setup();
    assert.equal(data.page, expectedPage);
  }
});

test('endpoint-specific identifiers and minimums are required', () => {
  assert.throws(() =>
    loadScript('asset-contents-load.js', { ASSET_ID: undefined }),
  );
  assert.throws(() =>
    loadScript('asset-contents-load.js', { MIN_ASSET_CONTENTS: '0' }),
  );
  assert.throws(() =>
    loadScript('news-detail-load.js', { NEWS_ID: undefined }),
  );
  assert.throws(() =>
    loadScript('content-rankings-load.js', { PERIOD_DAYS: '31' }),
  );
  assert.throws(() => loadScript('people-load.js', { MIN_PEOPLE: '0' }));
  assert.throws(() => loadScript('news-list-load.js', { CASE: 'ALL' }));
});
