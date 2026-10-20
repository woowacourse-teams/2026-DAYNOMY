import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const namespace = 'lt601-20261020';
const token = (id, role = 'USER') =>
  `e30.${Buffer.from(
    JSON.stringify({
      sub: String(id),
      role,
      token_type: 'access',
      exp: Date.now() / 1000 + 7200,
    }),
  ).toString('base64url')}.signature`;
function user(id) {
  return {
    memberId: id,
    publicId: `public-${id}`,
    accessToken: token(id),
    profile: {
      displayName: `${namespace}-${id}`,
      bio: '',
      experienceLevel: 'BEGINNER',
      riskProfile: 'BALANCED',
    },
    publication: {
      profilePublic: true,
      leagueEnabled: true,
      allocationPublic: true,
      detailPublic: true,
    },
    holdings: [
      {
        assetId: 1,
        assetName: 'Stock A',
        quantity: 10,
        averagePurchasePrice: 100,
        decision: {
          reason: 'fixture reason',
          expectedHoldingPeriod: 'OVER_SIX_MONTHS',
          expectedChange: '',
          invalidationCondition: '',
          maximumAcceptableLossRate: 10,
        },
      },
    ],
    decisionId: id * 100,
    reviewId: id * 100 + 1,
  };
}

async function fixture(overrides = {}, mutate = () => {}, version = 'v1') {
  const data = {
    schemaVersion: 1,
    namespace,
    baseUrl: 'https://dev.example.com',
    baselineHash: 'a'.repeat(64),
    users: Array.from({ length: 12 }, (_, index) => user(index + 1)),
    warmup: [user(13)],
    targets: [{ publicId: 'public-99', holdingCount: 1, dailyPointCount: 1 }],
    isolated: true,
    admin: { memberId: 999, accessToken: token(999, 'ADMIN') },
  };
  mutate(data);
  const environment = {
    BASE_URL: 'https://dev.example.com',
    DEPLOYED_SHA: 'abcdef1',
    WEEK_START: '2026-10-19',
    EXPECTED_CONFIRMED: 'false',
    EXPECTED_TOTAL: '2',
    DURATION: '10s',
    ...overrides,
  };
  const requests = [],
    metrics = [];
  const execution = {
    scenario: { iterationInTest: 0, startTime: Date.now() },
    test: {
      abort: (message) => {
        throw new Error(message);
      },
    },
  };
  const state = new Map(
    [...data.users, ...data.warmup].map((actor) => [
      actor.memberId,
      {
        actor,
        publication: { ...actor.publication },
        holdings: structuredClone(actor.holdings),
        follows: new Set(),
        records: [
          {
            id: actor.decisionId,
            assetId: 1,
            reason: 'fixture reason',
            reviews: [{ id: actor.reviewId, actualResult: 'before' }],
          },
        ],
      },
    ]),
  );
  let nextId = 10000,
    overrideResponse;
  class CookieJar {
    constructor() {
      this.cookies = {};
    }
    set(_origin, key, value) {
      this.cookies[key] = value;
    }
  }
  function response(status, body) {
    return {
      status,
      json: () => body,
      timings: { duration: 12 },
      cookies: { 'XSRF-TOKEN': [{ value: 'csrf' }] },
    };
  }
  function request(method, url, payload, params = {}) {
    const path = new URL(url).pathname;
    const cookies = params.jar?.cookies || {};
    const memberId = cookies.access_token
      ? Number(JSON.parse(Buffer.from(cookies.access_token.split('.')[1], 'base64url')).sub)
      : null;
    const current = state.get(memberId);
    const body = payload == null ? null : JSON.parse(payload);
    const entry = { method, path, url, body, memberId, params };
    requests.push(entry);
    if (method !== 'GET') {
      assert.equal(cookies['XSRF-TOKEN'], 'csrf');
      assert.equal(params.headers['X-XSRF-TOKEN'], 'csrf');
    }
    const intercepted = overrideResponse?.(entry);
    if (intercepted) return intercepted;
    if (path === '/api/auth/csrf')
      return response(200, { token: 'csrf', headerName: 'X-XSRF-TOKEN' });
    if (path === '/api/admin/league/recalculate') return response(200, { calculatedCount: 100 });
    if (path === '/api/league/weeks')
      return response(200, { weeks: [{ weekStart: environment.WEEK_START }] });
    if (path === '/api/league/rankings')
      return response(200, {
        weekStart: environment.WEEK_START,
        leagueType: 'WEEKLY_RETURN',
        confirmed: false,
        totalCount: 2,
        rankings: [
          { rank: 1, publicId: 'public-99', weeklyReturnRate: 1 },
          { rank: 2, publicId: 'public-100', weeklyReturnRate: -1 },
        ],
      });
    if (path.startsWith('/api/league/investors/')) {
      const publicId = path.split('/')[4];
      const investor = [...state.values()].find((value) => value.actor.publicId === publicId);
      if (investor && !investor.publication.profilePublic)
        return response(404, { code: 'PRIVATE' });
      if (path.endsWith('/daily-history'))
        return response(200, { weekStart: environment.WEEK_START, days: [{}] });
      if (path.endsWith('/details'))
        return response(200, {
          publicId,
          holdings: (investor?.holdings || [{ assetName: 'Stock A' }]).filter((h) => !h.hidden),
          decisions: [],
        });
      return response(200, { publicId, history: [], performance: {}, followed: false });
    }
    assert.ok(current, `Request must authenticate: ${method} ${path}`);
    if (path === '/api/users/me')
      return response(200, { id: memberId, nickname: current.actor.profile.displayName });
    if (path.endsWith('/investor-profile')) {
      if (!current.actor.publicId && method === 'GET') return response(204, null);
      if (!current.actor.publicId && method === 'PUT')
        current.actor.publicId = `created-${memberId}`;
      return response(200, {
        publicId: current.actor.publicId,
        ...current.actor.profile,
        ...current.publication,
      });
    }
    if (path.endsWith('/publication')) {
      current.publication = body;
      return response(200, { publicId: current.actor.publicId, ...body });
    }
    if (path.endsWith('/portfolio')) return response(200, { holdings: current.actor.holdings });
    if (path.includes('/shared-portfolio')) {
      if (path.endsWith('/import'))
        current.holdings = structuredClone(body.holdings).map((h) => ({
          ...h,
          assetName: 'Stock A',
          hidden: false,
        }));
      else if (method === 'DELETE') current.holdings = [];
      else if (method === 'PATCH') current.holdings[0].hidden = body.hidden;
      return response(200, { holdings: current.holdings });
    }
    if (path.includes('/league/follows')) {
      if (path.endsWith('/summary'))
        return response(200, { investors: [...current.follows].map((publicId) => ({ publicId })) });
      const target = path.split('/').at(-1);
      if (method === 'POST') current.follows.add(target);
      else current.follows.delete(target);
      return response(204, null);
    }
    if (path.endsWith('/transactions')) return response(200, { transactions: current.records });
    if (path.endsWith('/decisions')) {
      let record = current.records.find((value) => value.requestKey === body.requestKey);
      if (!record) {
        record = {
          id: nextId++,
          ...body.decision,
          assetId: body.assetId,
          requestKey: body.requestKey,
          reviews: [],
        };
        current.records.push(record);
      }
      return response(201, record);
    }
    const record = current.records.find((value) => value.id === Number(path.split('/')[6]));
    assert.ok(record, `Unknown record for ${path}`);
    if (path.endsWith('/reason')) {
      record.reason = body.reason;
      return response(200, record);
    }
    if (method === 'POST') {
      const review = { id: nextId++, ...body };
      record.reviews.push(review);
      return response(201, review);
    }
    const review = record.reviews.find((value) => value.id === Number(path.split('/').at(-1)));
    Object.assign(review, body);
    return response(200, review);
  }
  class Metric {
    constructor(name) {
      this.name = name;
    }
    add(value, tags = {}) {
      metrics.push({ name: this.name, value, tags });
    }
  }
  const context = vm.createContext({ __ENV: environment, open: () => JSON.stringify(data) });
  const builtins = {
    'k6/http': {
      default: { request, get: (url, params) => request('GET', url, null, params), CookieJar },
    },
    'k6/encoding': {
      default: { b64decode: (value) => Buffer.from(value, 'base64url').toString() },
    },
    k6: { check: (value, checks) => Object.values(checks).every((check) => check(value)) },
    'k6/execution': { default: execution },
    'k6/metrics': { Counter: Metric, Rate: Metric, Trend: Metric },
  };
  const cache = new Map();
  async function load(url) {
    if (cache.has(url.href)) return cache.get(url.href);
    const module = new vm.SourceTextModule(readFileSync(url, 'utf8'), { context });
    cache.set(url.href, module);
    await module.link(async (name) => {
      if (name.startsWith('.')) return load(new URL(name, url));
      const exports = builtins[name];
      assert.ok(exports, `Unknown module ${name}`);
      return new vm.SyntheticModule(
        Object.keys(exports),
        function () {
          for (const [key, value] of Object.entries(exports)) this.setExport(key, value);
        },
        { context },
      );
    });
    return module;
  }
  const entry = await load(new URL(`../league-${version}.js`, import.meta.url));
  await entry.evaluate();
  return {
    script: entry.namespace,
    data,
    requests,
    state,
    metrics,
    execution,
    intercept: (fn) => {
      overrideResponse = fn;
    },
  };
}

const individual = [
  'weeks',
  'rankings',
  'investor',
  'details',
  'daily-history',
  'profile',
  'save-profile',
  'publication',
  'source',
  'shared',
  'import',
  'visibility',
  'remove',
  'decisions',
  'decision',
  'reason',
  'review',
  'revise-review',
  'follow',
  'unfollow',
  'follow-summary',
];
for (const name of [
  ...individual,
  'browse',
  'onboarding',
  'records',
  'follow-flow',
  'privacy',
  'mixed',
  'contention',
]) {
  test(`${name}: authenticates writes, validates responses and completes its flow`, async () => {
    const { script, metrics } = await fixture({ CASE: name });
    const prepared = script.setup();
    const before = metrics.filter((value) => value.tags.phase === 'measurement');
    assert.equal(before.length, 0);
    script.default(prepared);
    assert.equal(metrics.findLast((value) => value.name === 'league_flow_success').value, true);
    assert.ok(
      metrics.filter((value) => value.name === 'league_api_success').every((value) => value.value),
    );
  });
}

test('mixed requests cover all four flow types without counting preflight/warmup as measurement', async () => {
  const { script, execution, metrics, requests } = await fixture({ CASE: 'mixed' });
  const prepared = script.setup();
  for (let index = 0; index <= 10; index++) {
    execution.scenario.iterationInTest = index;
    script.default(prepared);
  }
  assert.equal(metrics.filter((value) => value.name === 'league_flow_started').length, 10);
  const operations = new Set(
    metrics
      .filter((value) => value.tags.phase === 'measurement')
      .map((value) => value.tags.operation),
  );
  for (const operation of [
    'rankings',
    'details',
    'follow',
    'review',
    'publication',
    'private-history',
  ])
    assert.ok(operations.has(operation));
  const privateGet = requests.find((value) => value.path.endsWith('/public-10/daily-history'));
  assert.equal(
    privateGet.memberId,
    null,
    'public privacy checks must not inherit authentication cookies',
  );
});

test('profile creation uses fresh accounts and fails before writes if profiles already exist', async () => {
  const { script, requests } = await fixture({ CASE: 'create-profile' }, (data) => {
    for (const actor of [...data.users, ...data.warmup]) actor.publicId = null;
  });
  script.default(script.setup());
  assert.ok(requests.some((value) => value.method === 'PUT' && value.memberId === 1));
  await assert.rejects(fixture({ CASE: 'create-profile' }), /Invalid test user/);
  await assert.rejects(
    fixture({ CASE: 'create-profile', DURATION: '30s' }, (data) => {
      for (const actor of [...data.users, ...data.warmup]) actor.publicId = null;
    }),
    /one fresh account/,
  );
});
test('V1/V2 use identical request sequence, accounts and payloads', async () => {
  const fixtures = await Promise.all(
    ['v1', 'v2'].map((version) => fixture({ CASE: 'onboarding' }, () => {}, version)),
  );
  for (const { script } of fixtures) script.default(script.setup());
  const comparable = (value) =>
    value.requests.map(({ method, path, body, memberId }) => ({ method, path, body, memberId }));
  assert.deepEqual(comparable(fixtures[0]), comparable(fixtures[1]));
  const options = fixtures.map((value) => JSON.parse(JSON.stringify(value.script.options)));
  assert.equal(options[0].tags.version, 'v1');
  assert.equal(options[1].tags.version, 'v2');
  for (const option of options) delete option.tags.version;
  assert.deepEqual(options[0], options[1]);
});

test('rejects unsafe cohorts, expired/wrong-role tokens, invalid configuration and global recalculation on shared dev', async () => {
  for (const changes of [
    { CASE: 'unknown' },
    { AUTHENTICATED: 'yes' },
    { BASE_URL: 'https://secret@dev.example.com' },
    { WEEK_START: '2026-02-30' },
    { EXPECTED_CONFIRMED: 'FALSE' },
    { RPS: '0' },
    { DURATION: '0s' },
    { VUS: '-1' },
    { RUN: '../file' },
    { CASE: 'privacy', RPS: '50' },
    { CASE: 'recalculate' },
  ])
    await assert.rejects(fixture(changes), { name: 'Error' });
  for (const modify of [
    (data) => {
      data.baseUrl = 'https://other.example.com';
    },
    (data) => {
      data.warmup = [data.users[0]];
    },
    (data) => {
      data.users[0].profile.displayName = 'real-user';
    },
    (data) => {
      data.users[0].accessToken = token(999);
    },
    (data) => {
      data.users[0].accessToken = token(1, 'ADMIN');
    },
    (data) => {
      const claims = { sub: '1', role: 'USER', token_type: 'access', exp: 1 };
      data.users[0].accessToken = `e30.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.signature`;
    },
    (data) => {
      data.targets[0].publicId = data.users[0].publicId;
    },
    (data) => {
      data.users[0].holdings[0].quantity = 0;
    },
    (data) => {
      data.baselineHash = 'invalid';
    },
  ])
    await assert.rejects(fixture({}, modify), { name: 'Error' });
});

test('wrong authenticated profile aborts preflight before any write', async () => {
  const { script, requests, intercept } = await fixture({ CASE: 'onboarding' });
  intercept(({ path }) =>
    path.endsWith('/investor-profile')
      ? { status: 200, json: () => ({ publicId: 'other' }), timings: { duration: 1 } }
      : null,
  );
  assert.throws(() => script.setup(), /League API failed: profile/);
  assert.ok(requests.every((value) => value.method === 'GET'));
});

test('onboarding detects an unintended change to original assets', async () => {
  const { script, intercept, state } = await fixture({ CASE: 'onboarding' });
  const prepared = script.setup();
  intercept(({ path, method, memberId }) => {
    if (method === 'POST' && path.endsWith('/import') && memberId === 1)
      state.get(1).actor.holdings[0].quantity++;
    return null;
  });
  assert.throws(() => script.default(prepared), /League flow failed/);
});

test('incorrect private response fails and restores publication; HTTP/JSON failures cannot pass', async () => {
  const { script, intercept, state, metrics } = await fixture({ CASE: 'privacy' });
  const prepared = script.setup();
  intercept(({ path, memberId }) =>
    path === '/api/league/investors/public-1' && !memberId
      ? { status: 200, json: () => ({ publicId: 'public-1' }), timings: { duration: 1 } }
      : null,
  );
  assert.throws(() => script.default(prepared), /League flow failed/);
  assert.equal(state.get(1).publication.profilePublic, true);
  assert.ok(metrics.some((value) => value.name === 'league_api_success' && value.value === false));
});

test('contention deliberately replays one account and request key without duplicate records', async () => {
  const { script, execution, requests, state } = await fixture({ CASE: 'contention' });
  const prepared = script.setup();
  for (let index = 0; index < 10; index++) {
    execution.scenario.iterationInTest = index;
    script.default(prepared);
  }
  const writes = requests.filter(
    (value) => value.method === 'POST' && value.path.endsWith('/decisions') && value.memberId === 1,
  );
  assert.equal(new Set(writes.map((value) => value.body.requestKey)).size, 1);
  assert.equal(state.get(1).records.length, 2, 'one baseline record plus one replayed decision');
});

test('recalculation runs once in its own scenario and requires an ADMIN cookie', async () => {
  const { script, requests } = await fixture({ CASE: 'mixed-with-recalculate', ISOLATED: 'true' });
  assert.equal(script.options.scenarios.recalculation.iterations, 1);
  assert.equal(script.options.scenarios.recalculation.startTime, '5s');
  const prepared = script.setup();
  script.default(prepared);
  script.recalculate(prepared);
  const batches = requests.filter((value) => value.path === '/api/admin/league/recalculate');
  assert.equal(batches.length, 1);
  assert.equal(batches[0].memberId, 999);
  assert.equal(batches[0].params.timeout, '300s');
});

test('report omits tokens/user identities and cannot pass missing flows, failed requests, slow API or failed batch', async () => {
  const { script } = await fixture({ CASE: 'browse' });
  const metrics = {
    league_flow_started: { values: { count: 10 } },
    league_flow_success: { values: { rate: 1 } },
    'league_api_success{phase:measurement}': { values: { rate: 1 } },
    'league_api_duration_ms{phase:measurement,kind:api}': { values: { 'p(95)': 12 } },
    'league_api_requests{phase:measurement,operation:rankings}': { values: { count: 10 } },
    'league_api_duration_ms{phase:measurement,operation:rankings}': { values: { 'p(95)': 12 } },
  };
  function report(changes = {}) {
    const output = script.handleSummary({ metrics: { ...metrics, ...changes } });
    const text = output['result-league-v1-browse-local.json'];
    assert.ok(!text.includes('signature'));
    assert.ok(!text.includes('public-1'));
    return JSON.parse(text);
  }
  assert.equal(report().passed, true);
  assert.equal(report().startedFlowsPerSecond, 1);
  const drained = report({
    league_measurement_start_ms: { values: { min: 1000 } },
    league_measurement_end_ms: { values: { max: 21000 } },
  });
  assert.equal(drained.completedApiRequestsPerSecond, 0.5);
  assert.equal(drained.measurementSecondsIncludingDrain, 20);
  assert.ok(report().unmeasuredOperations.includes('follow'));
  for (const changes of [
    { league_flow_started: { values: { count: 9 } } },
    { 'league_api_success{phase:measurement}': { values: { rate: 0.99 } } },
    { dropped_iterations: { values: { count: 1 } } },
    {
      'league_api_duration_ms{phase:measurement,operation:rankings}': { values: { 'p(95)': 1000 } },
    },
  ])
    assert.equal(report(changes).passed, false);
});

test('network and invalid JSON failures abort the flow and are not retried', async () => {
  for (const response of [
    { status: 0, json: () => null, timings: { duration: 10000 } },
    {
      status: 200,
      json: () => {
        throw new Error('invalid JSON');
      },
      timings: { duration: 1 },
    },
  ]) {
    const { script, intercept, requests, metrics } = await fixture({ CASE: 'browse' });
    const prepared = script.setup();
    const before = requests.length;
    intercept(() => response);
    assert.throws(() => script.default(prepared), /League flow failed/);
    assert.equal(requests.length - before, 1);
    assert.equal(metrics.findLast((value) => value.name === 'league_flow_success').value, false);
  }
});
