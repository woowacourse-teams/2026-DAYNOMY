import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { CASES, OPERATIONS, WRITE_CASES, runCase } from './league-cases.js';

export function createScenario(version) {
  const config = {
    version,
    case: __ENV.CASE || 'browse',
    baseUrl: (__ENV.BASE_URL || '').replace(/\/$/, ''),
    deployedSha: __ENV.DEPLOYED_SHA || '',
    weekStart: __ENV.WEEK_START || '',
    leagueType: __ENV.LEAGUE_TYPE || 'WEEKLY_RETURN',
    page: Number(__ENV.PAGE || 0),
    expectedTotal: Number(__ENV.EXPECTED_TOTAL),
    expectedConfirmed: __ENV.EXPECTED_CONFIRMED === 'true',
    authenticated: __ENV.AUTHENTICATED === 'true',
    rps: Number(__ENV.RPS || 1),
    duration: __ENV.DURATION || '30s',
    vus: Number(__ENV.VUS || 20),
    timeoutSeconds: Number(__ENV.REQUEST_TIMEOUT_SECONDS || 10),
    batchTimeoutSeconds: Number(__ENV.BATCH_TIMEOUT_SECONDS || 300),
    targetP95Ms: Number(__ENV.TARGET_P95_MS || 1000),
    run: __ENV.RUN || __ENV.LOADTEST_ID || 'local',
  };
  const ensure = (condition, message) => {
    if (!condition) throw new Error(message);
  };
  ensure(CASES.includes(config.case), 'Unknown CASE');
  ensure(
    ['true', 'false'].includes(__ENV.AUTHENTICATED || 'false'),
    'AUTHENTICATED must be true or false',
  );
  ensure(
    /^https?:\/\/[^\s/@?#]+$/.test(config.baseUrl),
    'Use an HTTP(S) origin without credentials, path or query',
  );
  ensure(/^[a-f0-9]{7,40}$/i.test(config.deployedSha), 'Set actual backend DEPLOYED_SHA');
  ensure(/^[A-Za-z0-9_-]{1,32}$/.test(config.run), 'Invalid RUN');
  const week = new Date(`${config.weekStart}T00:00:00Z`);
  ensure(
    /^\d{4}-\d{2}-\d{2}$/.test(config.weekStart) &&
      Number.isFinite(week.getTime()) &&
      week.getUTCDay() === 1 &&
      week.toISOString().slice(0, 10) === config.weekStart,
    'Set a valid Monday WEEK_START',
  );
  ensure(
    ['WEEKLY_RETURN', 'STABLE', 'BEGINNER'].includes(config.leagueType) &&
      ['true', 'false'].includes(__ENV.EXPECTED_CONFIRMED),
    'Set league type and EXPECTED_CONFIRMED',
  );
  const match = /^([1-9]\d*)(s|m)$/.exec(config.duration);
  ensure(match, 'Use a positive DURATION such as 30s or 2m');
  const seconds = Number(match[1]) * (match[2] === 'm' ? 60 : 1);
  const planned = config.rps * seconds;
  ensure(
    [
      config.rps,
      config.vus,
      config.timeoutSeconds,
      config.batchTimeoutSeconds,
      config.targetP95Ms,
      planned,
    ].every((value) => Number.isSafeInteger(value) && value > 0) &&
      Number.isSafeInteger(config.page) &&
      config.page >= 0,
    'Invalid rate, duration, timeout, VU, page or p95 setting',
  );
  ensure(
    seconds <= 1200 && config.timeoutSeconds <= 60 && config.batchTimeoutSeconds <= 1200,
    'Use duration/batch timeout <=20m and request timeout <=60s',
  );
  if (config.case === 'rankings')
    ensure(
      Number.isSafeInteger(config.expectedTotal) && config.expectedTotal > config.page * 20,
      'EXPECTED_TOTAL must include the selected nonempty page',
    );

  let fixture;
  try {
    fixture = JSON.parse(open(__ENV.ACCOUNTS_FILE || './accounts-league.json'));
  } catch (_) {
    throw new Error('Prepare the private league accounts file');
  }
  ensure(
    fixture.schemaVersion === 1 &&
      fixture.baseUrl === config.baseUrl &&
      /^lt601-\d{8}$/.test(fixture.namespace) &&
      /^[a-f0-9]{64}$/.test(fixture.baselineHash),
    'Invalid league fixture, baseline hash or API origin',
  );
  ensure(
    Array.isArray(fixture.users) &&
      fixture.users.length > 0 &&
      Array.isArray(fixture.warmup) &&
      fixture.warmup.length > 0 &&
      Array.isArray(fixture.targets) &&
      fixture.targets.length > 0,
    'Prepare measurement users, separate warmup users and stable targets',
  );
  const users = [...fixture.users, ...fixture.warmup];
  const creating = config.case === 'create-profile';
  if (creating)
    ensure(
      planned <= fixture.users.length,
      'Profile creation needs one fresh account per iteration',
    );
  const positive = (n) => Number.isSafeInteger(n) && n > 0;
  const token = (value, memberId, role) => {
    let claims;
    try {
      claims = JSON.parse(encoding.b64decode(value.split('.')[1], 'rawurl', 's'));
    } catch (_) {
      throw new Error('Invalid access token');
    }
    ensure(
      String(claims.sub) === String(memberId) &&
        claims.role === role &&
        claims.token_type === 'access' &&
        Number.isFinite(claims.exp) &&
        claims.exp * 1000 > Date.now() + (seconds + config.batchTimeoutSeconds + 120) * 1000,
      'Wrong token identity, role or expiration',
    );
  };
  ensure(
    new Set(users.map((user) => user.memberId)).size === users.length &&
      (creating || new Set(users.map((user) => user.publicId)).size === users.length) &&
      new Set(users.map((user) => user.accessToken)).size === users.length,
    'Users and warmup must have unique identities/tokens',
  );
  for (const user of users) {
    ensure(
      positive(user.memberId) &&
        (creating
          ? user.publicId === null
          : typeof user.publicId === 'string' && user.publicId.length > 0) &&
        user.profile?.displayName?.startsWith(`${fixture.namespace}-`) &&
        user.profile.displayName.length <= 20 &&
        typeof user.profile.bio === 'string' &&
        ['BEGINNER', 'ONE_TO_THREE_YEARS', 'OVER_THREE_YEARS'].includes(
          user.profile.experienceLevel,
        ) &&
        ['CONSERVATIVE', 'BALANCED', 'AGGRESSIVE'].includes(user.profile.riskProfile) &&
        ['profilePublic', 'leagueEnabled', 'allocationPublic', 'detailPublic'].every(
          (key) => typeof user.publication?.[key] === 'boolean',
        ) &&
        user.publication.profilePublic &&
        user.publication.detailPublic &&
        Array.isArray(user.holdings) &&
        user.holdings.length > 0 &&
        user.holdings.length <= 100 &&
        new Set(user.holdings.map((holding) => holding.assetId)).size === user.holdings.length &&
        user.holdings.every(
          (holding) =>
            positive(holding.assetId) &&
            positive(holding.quantity) &&
            typeof holding.assetName === 'string' &&
            holding.assetName.length > 0 &&
            Number.isFinite(holding.averagePurchasePrice) &&
            holding.averagePurchasePrice > 0 &&
            typeof holding.decision?.reason === 'string' &&
            holding.decision.reason.trim().length > 0,
        ) &&
        positive(user.decisionId) &&
        positive(user.reviewId),
      'Invalid test user profile, publication, holdings or record IDs',
    );
    token(user.accessToken, user.memberId, 'USER');
  }
  ensure(
    new Set(fixture.targets.map((target) => target.publicId)).size === fixture.targets.length &&
      fixture.targets.every(
        (target) =>
          typeof target.publicId === 'string' &&
          target.publicId.length > 0 &&
          !users.some((user) => user.publicId === target.publicId) &&
          positive(target.holdingCount) &&
          Number.isSafeInteger(target.dailyPointCount) &&
          target.dailyPointCount >= 0,
      ),
    'Use separate, nonempty public targets',
  );
  const batch = ['recalculate', 'mixed-with-recalculate'].includes(config.case);
  if (batch) {
    ensure(
      fixture.isolated === true &&
        __ENV.ISOLATED === 'true' &&
        positive(fixture.admin?.memberId) &&
        !users.some((user) => user.memberId === fixture.admin.memberId),
      'Global recalculation requires an isolated backend/DB fixture and ISOLATED=true',
    );
    token(fixture.admin.accessToken, fixture.admin.memberId, 'ADMIN');
  }
  if (WRITE_CASES.includes(config.case) && !['recalculate', 'contention'].includes(config.case)) {
    // At most 32 sequential HTTP calls per flow; do not reuse an account while a prior flow may be alive.
    ensure(
      planned <= fixture.users.length ||
        config.rps * (32 * config.timeoutSeconds + 1) < fixture.users.length,
      'Not enough exclusive accounts for this rate/timeout; prepare more or shorten the run',
    );
  }

  const started = new Counter('league_flow_started');
  const completed = new Rate('league_flow_success');
  const flowDuration = new Trend('league_flow_duration_ms', true);
  const requests = new Counter('league_api_requests');
  const success = new Rate('league_api_success');
  const latency = new Trend('league_api_duration_ms', true);
  const batchSuccess = new Rate('league_batch_success');
  const batchDuration = new Trend('league_batch_duration_ms', true);
  const measurementStart = new Trend('league_measurement_start_ms');
  const measurementEnd = new Trend('league_measurement_end_ms');
  const thresholds = { 'league_api_success{phase:measurement}': ['rate==1'] };
  if (config.case !== 'recalculate')
    Object.assign(thresholds, {
      league_flow_started: [`count==${planned}`],
      league_flow_success: ['rate==1'],
      'league_api_duration_ms{phase:measurement,kind:api}': [`p(95)<${config.targetP95Ms}`],
      dropped_iterations: [{ threshold: 'count==0', abortOnFail: true }],
    });
  if (batch) thresholds.league_batch_success = ['rate==1'];
  for (const operation of OPERATIONS) {
    thresholds[`league_api_requests{phase:measurement,operation:${operation}}`] = ['count>=0'];
    thresholds[`league_api_duration_ms{phase:measurement,operation:${operation}}`] = [
      operation === 'recalculate' ? 'p(95)>=0' : `p(95)<${config.targetP95Ms}`,
    ];
  }

  function call(phase, csrf, operation, method, path, payload, actor, status, validate) {
    const jar = new http.CookieJar();
    if (actor) jar.set(config.baseUrl, 'access_token', actor.accessToken);
    const headers = { 'Content-Type': 'application/json' };
    if (method !== 'GET') {
      jar.set(config.baseUrl, 'XSRF-TOKEN', csrf.token);
      headers[csrf.headerName] = csrf.token;
    }
    const tags = {
      phase,
      operation,
      kind: operation === 'recalculate' ? 'batch' : 'api',
      name: `league_${operation}`,
    };
    const response = http.request(
      method,
      `${config.baseUrl}${path}`,
      payload == null ? null : JSON.stringify(payload),
      {
        jar,
        headers,
        timeout: `${operation === 'recalculate' ? config.batchTimeoutSeconds : config.timeoutSeconds}s`,
        redirects: 0,
        tags,
      },
    );
    let body = null;
    if (response.status !== 204) {
      try {
        body = response.json();
      } catch (_) {
        /* Count non-JSON responses as failed contracts. */
      }
    }
    let passed = false;
    try {
      passed = response.status === status && Boolean(validate(body));
    } catch (_) {
      /* Invalid shape is a failure. */
    }
    requests.add(1, tags);
    success.add(passed, tags);
    latency.add(response.timings.duration, tags);
    check(response, { 'league API contract': () => passed }, tags);
    if (!passed) throw new Error(`League API failed: ${operation}, HTTP ${response.status}`);
    return body;
  }
  const invoke = (name, actor, index, phase, csrf) =>
    runCase(name, {
      actor,
      target: fixture.targets[index % fixture.targets.length],
      index,
      phase,
      config,
      call: (...args) => call(phase, csrf, ...args),
    });
  const selectedCase = config.case === 'mixed-with-recalculate' ? 'mixed' : config.case;
  const options = {
    scenarios: {},
    setupTimeout: '10m',
    maxRedirects: 0,
    // Remove the raw URL tag as well as grouping names, so public IDs do not reach Prometheus.
    systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
    tags: { version, case: config.case, league_type: config.leagueType, run: config.run },
    summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
    thresholds,
  };
  if (config.case !== 'recalculate')
    options.scenarios.league = {
      executor: 'constant-arrival-rate',
      exec: 'default',
      rate: config.rps,
      timeUnit: '1s',
      duration: config.duration,
      preAllocatedVUs: config.vus,
      maxVUs: config.vus,
      gracefulStop: `${32 * config.timeoutSeconds + 1}s`,
    };
  if (batch)
    options.scenarios.recalculation = {
      executor: 'shared-iterations',
      exec: 'recalculate',
      vus: 1,
      iterations: 1,
      startTime: config.case === 'recalculate' ? '0s' : `${Math.floor(seconds / 2)}s`,
      maxDuration: `${config.batchTimeoutSeconds + 1}s`,
      gracefulStop: '1s',
    };

  return {
    options,
    setup() {
      const response = http.get(`${config.baseUrl}/api/auth/csrf`, {
        timeout: `${config.timeoutSeconds}s`,
        tags: { phase: 'preflight', operation: 'csrf', name: 'league_csrf' },
      });
      let csrf;
      try {
        csrf = response.json();
      } catch (_) {
        throw new Error('Invalid CSRF response');
      }
      ensure(
        response.status === 200 &&
          typeof csrf?.token === 'string' &&
          csrf.token.length > 0 &&
          typeof csrf.headerName === 'string' &&
          csrf.headerName.length > 0 &&
          response.cookies?.['XSRF-TOKEN']?.[0]?.value === csrf.token,
        'CSRF preparation failed',
      );
      for (const user of users) {
        if (creating) {
          call(
            'preflight',
            csrf,
            'profile',
            'GET',
            '/api/users/me',
            null,
            user,
            200,
            (body) =>
              body?.id === user.memberId &&
              typeof body.nickname === 'string' &&
              body.nickname.startsWith(`${fixture.namespace}-`),
          );
          call(
            'preflight',
            csrf,
            'profile',
            'GET',
            '/api/users/me/investor-profile',
            null,
            user,
            204,
            () => true,
          );
        } else
          call(
            'preflight',
            csrf,
            'profile',
            'GET',
            '/api/users/me/investor-profile',
            null,
            user,
            200,
            (body) =>
              body?.publicId === user.publicId && body.displayName === user.profile.displayName,
          );
      }
      if (config.case !== 'recalculate') {
        // Warmup only uses its own accounts; do not mutate measurement accounts before timing starts.
        const warmupCases =
          selectedCase === 'mixed'
            ? ['browse', 'follow-flow', 'records', 'privacy']
            : [selectedCase];
        for (const [index, user] of fixture.warmup.entries())
          for (const name of warmupCases) invoke(name, user, index, 'warmup', csrf);
      }
      return { csrf };
    },
    run(data) {
      const index = exec.scenario.iterationInTest;
      if (index >= planned) return;
      started.add(1);
      measurementStart.add(exec.scenario.startTime);
      const before = Date.now();
      const actor = fixture.users[config.case === 'contention' ? 0 : index % fixture.users.length];
      let passed = false;
      try {
        // All contention iterations deliberately replay the same member/request key.
        invoke(
          selectedCase,
          actor,
          config.case === 'contention' ? 0 : index,
          'measurement',
          data.csrf,
        );
        passed = true;
      } catch (failure) {
        exec.test.abort(
          'League flow failed; inspect operation metrics and restore only after server/DB work ends',
        );
      } finally {
        completed.add(passed);
        flowDuration.add(Date.now() - before);
        measurementEnd.add(Date.now());
      }
    },
    recalculate(data) {
      const before = Date.now();
      measurementStart.add(exec.scenario.startTime);
      let passed = false;
      try {
        call(
          'measurement',
          data.csrf,
          'recalculate',
          'POST',
          '/api/admin/league/recalculate',
          null,
          fixture.admin,
          200,
          (body) => Number.isSafeInteger(body?.calculatedCount) && body.calculatedCount > 0,
        );
        passed = true;
      } catch (_) {
        exec.test.abort('League recalculation failed; inspect batch metrics and server/DB work');
      } finally {
        batchSuccess.add(passed);
        batchDuration.add(Date.now() - before);
        measurementEnd.add(Date.now());
      }
    },
    handleSummary(data) {
      const metric = (name) => data.metrics[name]?.values || {};
      const sent = metric('league_flow_started').count || 0;
      const apiRate = metric('league_api_success{phase:measurement}').rate;
      const apiLatency = metric('league_api_duration_ms{phase:measurement,kind:api}');
      const dropped = metric('dropped_iterations').count || 0;
      const operations = OPERATIONS.map((operation) => ({
        operation,
        requests:
          metric(`league_api_requests{phase:measurement,operation:${operation}}`).count || 0,
        latency: metric(`league_api_duration_ms{phase:measurement,operation:${operation}}`),
      }));
      const elapsed = Math.max(
        seconds,
        (metric('league_measurement_end_ms').max - metric('league_measurement_start_ms').min) /
          1000 || 0,
      );
      const apiRequests = operations.reduce((sum, item) => sum + item.requests, 0);
      const passed =
        apiRate === 1 &&
        (config.case === 'recalculate' ||
          (sent === planned &&
            metric('league_flow_success').rate === 1 &&
            dropped === 0 &&
            Number.isFinite(apiLatency['p(95)']) &&
            apiLatency['p(95)'] < config.targetP95Ms)) &&
        operations.every(
          (item) =>
            !item.requests ||
            item.operation === 'recalculate' ||
            (Number.isFinite(item.latency['p(95)']) && item.latency['p(95)'] < config.targetP95Ms),
        ) &&
        (!batch || metric('league_batch_success').rate === 1);
      const report = {
        config,
        namespace: fixture.namespace,
        baselineHash: fixture.baselineHash,
        expectedFlows: config.case === 'recalculate' ? 0 : planned,
        startedFlows: sent,
        startedFlowsPerSecond: sent / seconds,
        completedApiRequests: apiRequests,
        completedApiRequestsPerSecond: apiRequests / elapsed,
        measurementSecondsIncludingDrain: elapsed,
        successRate: apiRate,
        droppedIterations: dropped,
        apiLatency,
        flowLatency: metric('league_flow_duration_ms'),
        batchLatency: metric('league_batch_duration_ms'),
        operations,
        unmeasuredOperations: operations
          .filter((item) => !item.requests)
          .map((item) => item.operation),
        passed,
      };
      return {
        stdout: `${version} ${config.case}: flows=${sent}, passed=${passed}; unmeasured API operations=${report.unmeasuredOperations.length}\n`,
        [`result-league-${version}-${config.case}-${config.run}.json`]: JSON.stringify(
          report,
          null,
          2,
        ),
      };
    },
  };
}
