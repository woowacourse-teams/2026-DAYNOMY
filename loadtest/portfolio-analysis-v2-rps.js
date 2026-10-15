import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

// loadtest 폴더에서 실행: k6 run -e BASE_URL=https://개발서버 -e RPS=1 -e DURATION=30s portfolio-analysis-v2-rps.js
// CASE/API_MODE는 기록용이다. DB 준비 상태와 서버의 AI 연결은 별도로 맞춘다.
// 워밍업 회원은 본 측정에서 제외한다. 각 분석 요청에는 서로 다른 회원을 사용한다.
// 한 번의 로컬 k6 실행용이다. 회원 부족 시 반복 사용하지 않고 실행 전에 중단한다.
// 반복 전 새 회원 파일(ACCOUNTS_FILE)을 쓰거나 테스트 분석 상태를 초기화한다.
const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const RPS = Number(__ENV.RPS || 1);
const DURATION = __ENV.DURATION || '30s';
const durationMatch = /^([1-9]\d*)(s|m)$/.exec(DURATION);
if (!durationMatch) throw new Error('DURATION은 30s, 5m처럼 초 또는 분 단위의 양의 정수로 지정하세요.');
const DURATION_SECONDS = Number(durationMatch[1]) * (durationMatch[2] === 'm' ? 60 : 1);
const EXPECTED_REQUESTS = RPS * DURATION_SECONDS;
const VUS = Number(__ENV.VUS || Math.min(EXPECTED_REQUESTS, 50));
const CASE = __ENV.CASE || 'B1';
const API_MODE = __ENV.API_MODE || 'fake';
const RUN = __ENV.RUN || '1';

if (!/^https?:\/\/[^/?#]+$/.test(BASE_URL)) {
  throw new Error('BASE_URL에 개발 서버 주소를 입력하세요. 예: https://dev.example.com');
}
if (!Number.isSafeInteger(RPS) || RPS < 1 ||
    !Number.isSafeInteger(EXPECTED_REQUESTS) || EXPECTED_REQUESTS < 1 ||
    !Number.isSafeInteger(VUS) || VUS < 1) {
  throw new Error('RPS·VUS와 예정 요청 수는 1 이상의 안전한 정수여야 합니다.');
}
if (!['B1', 'B2', 'B3', 'B4'].includes(CASE) || !['real', 'fake'].includes(API_MODE)) {
  throw new Error('CASE는 B1~B4, API_MODE는 real 또는 fake로 지정하세요.');
}

let accountData;
try {
  accountData = JSON.parse(open(__ENV.ACCOUNTS_FILE || './accounts.json'));
} catch (_) {
  throw new Error('계정 JSON 파일을 준비하고 형식을 확인하세요.');
}
if (!Array.isArray(accountData)) throw new Error('계정 파일은 JSON 배열이어야 합니다.');
const warmupAccounts = accountData.filter((account) => account.warmup === true);
if (warmupAccounts.length !== 1) {
  throw new Error('계정 파일에 warmup: true인 워밍업 회원을 정확히 1명 지정하세요.');
}
const warmupAccount = warmupAccounts[0];
const accounts = accountData.filter((account) => account.warmup !== true).slice(0, EXPECTED_REQUESTS);
const participants = [warmupAccount, ...accounts];
if (
  accounts.length !== EXPECTED_REQUESTS ||
  new Set(participants.map((a) => a.memberId)).size !== EXPECTED_REQUESTS + 1 ||
  new Set(participants.map((a) => a.accessToken)).size !== EXPECTED_REQUESTS + 1
) {
  throw new Error(`본 측정에 서로 다른 회원 ${EXPECTED_REQUESTS}명과 별도 워밍업 회원 1명이 필요합니다. RPS·DURATION과 회원 파일을 확인하세요.`);
}
if (participants.some((a) =>
  !Number.isSafeInteger(a.memberId) || a.memberId < 1 ||
  typeof a.accessToken !== 'string' || a.accessToken.split('.').length !== 3 ||
  !Array.isArray(a.assetNames) || !a.assetNames.length ||
  a.assetNames.some((name) => typeof name !== 'string' || !name.trim()) ||
  new Set(a.assetNames).size !== a.assetNames.length
)) {
  throw new Error('회원 ID·실제 access_token·보유 종목명을 입력하세요.');
}

if (warmupAccount.assetNames.some((name) => accounts.some((a) => a.assetNames.includes(name)))) {
  throw new Error('워밍업 종목과 본 측정 종목은 겹치면 안 됩니다.');
}

const requests = new Counter('analysis_requests');
const success = new Rate('analysis_success');
const duration = new Trend('analysis_duration_ms', true);

export const options = {
  scenarios: {
    analysis: {
      executor: 'constant-arrival-rate',
      rate: RPS,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: VUS,
      maxVUs: VUS,
      gracefulStop: '310s',
    },
  },
  setupTimeout: '8m',
  maxRedirects: 0,
  tags: { version: 'v2', condition: CASE, api_mode: API_MODE, mode: 'rps', run: RUN },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
  thresholds: {
    analysis_requests: [`count==${EXPECTED_REQUESTS}`],
    analysis_success: ['rate==1'],
    dropped_iterations: ['count==0'],
  },
};

function readCsrf(response) {
  let body;
  try {
    body = response.json();
  } catch (_) {
    throw new Error('CSRF 응답 오류. 서버 주소와 인증 정보를 확인하세요.');
  }
  const cookie = response.cookies['XSRF-TOKEN']?.[0]?.value;
  if (response.status !== 200 || !body?.token || !body?.headerName || !cookie) {
    throw new Error('CSRF 준비 실패. 인증 정보를 확인하세요.');
  }
  return { token: body.token, headerName: body.headerName, cookie };
}

// 워밍업과 인증 준비는 본 측정의 건수·성공률·분석 시간에 포함하지 않는다.
// 반복 시 저장된 워밍업 결과가 재사용될 수 있다. 전체 경로 재실행이 필요하면 전용 분석 결과도 초기화한다.
export function setup() {
  const jar = new http.CookieJar();
  jar.set(BASE_URL, 'access_token', warmupAccount.accessToken);
  const warmupCsrf = readCsrf(http.get(`${BASE_URL}/api/auth/csrf`, {
    jar, timeout: '15s', tags: { name: 'csrf_prepare', phase: 'warmup' },
  }));
  const response = http.post(`${BASE_URL}/api/v2/users/me/portfolio/analysis`, null, {
    jar,
    cookies: { access_token: warmupAccount.accessToken, 'XSRF-TOKEN': warmupCsrf.cookie },
    headers: { [warmupCsrf.headerName]: warmupCsrf.token },
    timeout: '300s',
    tags: { name: 'portfolio_analysis_v2_warmup', phase: 'warmup' },
  });
  let body;
  try { body = response.json(); } catch (_) { /* 정상 분석 응답이 아니면 본 측정을 시작하지 않는다. */ }
  const impacts = body?.analysis?.impacts;
  if (response.status !== 200 || body?.status !== 'READY' ||
      !Number.isSafeInteger(body?.analysisId) || body?.matchesCurrentPortfolio !== true ||
      body?.analysis?.totalAssetCount !== warmupAccount.assetNames.length ||
      !Array.isArray(impacts) || impacts.length !== warmupAccount.assetNames.length ||
      new Set(impacts.map((i) => i.assetName)).size !== impacts.length ||
      impacts.some((i) => !warmupAccount.assetNames.includes(i.assetName))) {
    throw new Error(`워밍업 실패: HTTP ${response.status}. 본 측정은 시작하지 않습니다.`);
  }
  console.info('워밍업 완료. 지금부터 본 측정을 준비합니다.');

  const responses = http.batch(
    accounts.map((account) => {
      const jar = new http.CookieJar();
      jar.set(BASE_URL, 'access_token', account.accessToken);
      return {
        method: 'GET',
        url: `${BASE_URL}/api/auth/csrf`,
        params: { jar, timeout: '15s', tags: { name: 'csrf_prepare', phase: 'measurement' } },
      };
    }),
  );
  const csrf = responses.map(readCsrf);
  return { csrf };
}

export default function (data) {
  const index = exec.scenario.iterationInTest;
  if (index >= accounts.length) {
    exec.test.abort('측정용 회원을 모두 사용했습니다. 같은 회원으로 재요청하지 않습니다.');
    return;
  }
  const account = accounts[index];
  const csrf = data.csrf[index];

  requests.add(1);
  const startedAt = Date.now();
  const response = http.post(
    `${BASE_URL}/api/v2/users/me/portfolio/analysis`,
    null,
    {
      cookies: {
        access_token: account.accessToken,
        'XSRF-TOKEN': csrf.cookie,
      },
      headers: { [csrf.headerName]: csrf.token },
      timeout: '300s',
      tags: { name: 'portfolio_analysis_v2', phase: 'measurement' },
    },
  );
  duration.add(Date.now() - startedAt);

  let body = null;
  try { body = response.json(); } catch (_) { /* JSON이 아닌 오류도 실패로 집계한다. */ }
  const analysis = body?.analysis;
  const impacts = analysis?.impacts;
  const assetsValid = Array.isArray(impacts) &&
    impacts.length === account.assetNames.length &&
    new Set(impacts.map((i) => i.assetName)).size === impacts.length &&
    impacts.every((i) => account.assetNames.includes(i.assetName));
  const weightsValid = assetsValid &&
    impacts.every((i) => typeof i.weight === 'number' && i.weight > 0) &&
    Math.abs(impacts.reduce((sum, i) => sum + i.weight, 0) - 100) < 0.02;

  const passed = check(response, {
    'HTTP 200': (r) => r.status === 200,
    '분석 저장 완료': () => body?.status === 'READY' &&
      Number.isSafeInteger(body?.analysisId) && body?.matchesCurrentPortfolio === true,
    '종목·비중 정상': () => assetsValid && weightsValid &&
      analysis?.totalAssetCount === account.assetNames.length,
    '근거·전체 설명 완료': () => analysis?.coverage === 'COMPLETE' &&
      typeof analysis?.overallImpact === 'string' && analysis.overallImpact.trim().length > 0,
  });
  success.add(passed);
  if (!passed) console.error(`v2 검증 실패: HTTP ${response.status}`);
}

export function handleSummary(data) {
  const requestCount = data.metrics.analysis_requests?.values?.count || 0;
  const successValues = data.metrics.analysis_success?.values;
  const dropped = data.metrics.dropped_iterations?.values?.count || 0;
  const latency = data.metrics.analysis_duration_ms?.values;
  const milliseconds = (value) => Number.isFinite(value) ? `${value.toFixed(2)} ms` : 'N/A';
  const failureRate = Number.isFinite(successValues?.rate)
    ? `${((1 - successValues.rate) * 100).toFixed(2)}%`
    : 'N/A';
  const result = JSON.stringify({
    finishedAt: new Date().toISOString(),
    version: 'v2', mode: 'rps', condition: CASE, apiMode: API_MODE, run: RUN,
    rps: RPS, duration: DURATION, expectedRequests: EXPECTED_REQUESTS, allocatedVUs: VUS,
    requests: data.metrics.analysis_requests,
    success: data.metrics.analysis_success,
    durationMs: data.metrics.analysis_duration_ms,
    droppedIterations: dropped,
  }, null, 2);
  const summary = [
    `포트폴리오 분석 V2 RPS / ${CASE} / ${API_MODE} / ${RPS}건/초 × ${DURATION} / 실행 ${RUN}`,
    `요청 시작: ${requestCount} / 예정 ${EXPECTED_REQUESTS}건`,
    `미발송 요청: ${dropped}건 (0보다 크면 목표 RPS를 달성하지 못한 실행입니다.)`,
    `정상 완료: ${successValues?.passes || 0} / ${requestCount}건 (예정 ${EXPECTED_REQUESTS}건)`,
    '워밍업 요청은 아래 결과에서 제외됩니다.',
    `실패율: ${failureRate}`,
    `평균 분석 시간: ${milliseconds(latency?.avg)}`,
    `p95 분석 시간: ${milliseconds(latency?.['p(95)'])}`,
    `최대 분석 시간: ${milliseconds(latency?.max)}`,
    `결과 파일: result-v2-rps-${CASE}-${API_MODE}-${RPS}rps-${DURATION}-${RUN}.json`,
    '',
  ].join('\n');
  return {
    stdout: summary,
    [`result-v2-rps-${CASE}-${API_MODE}-${RPS}rps-${DURATION}-${RUN}.json`]: result,
  };
}
