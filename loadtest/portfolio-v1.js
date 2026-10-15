import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// loadtest 폴더에서 실행: k6 run -e BASE_URL=https://개발서버 -e USERS=1 portfolio-v1.js
// CASE/API_MODE는 기록용이다. DB 준비 상태와 서버의 AI 연결은 별도로 맞춘다.
// 반복 전 새 회원 파일(ACCOUNTS_FILE)을 쓰거나 테스트 분석 상태를 초기화한다.
const BASE_URL = (__ENV.BASE_URL || '').replace(/\/$/, '');
const USERS = Number(__ENV.USERS || 1);
const CASE = __ENV.CASE || 'B1';
const API_MODE = __ENV.API_MODE || 'fake';
const RUN = __ENV.RUN || '1';

if (!/^https?:\/\/[^/?#]+$/.test(BASE_URL)) {
  throw new Error('BASE_URL에 개발 서버 주소를 입력하세요. 예: https://dev.example.com');
}
if (!Number.isSafeInteger(USERS) || USERS < 1) {
  throw new Error('USERS는 1 이상의 정수여야 합니다.');
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
const accounts = accountData.slice(0, USERS);
if (
  accounts.length !== USERS ||
  new Set(accounts.map((a) => a.memberId)).size !== USERS ||
  new Set(accounts.map((a) => a.accessToken)).size !== USERS
) {
  throw new Error('동시 인원만큼 서로 다른 테스트 계정을 준비하세요.');
}
if (accounts.some((a) =>
  !Number.isSafeInteger(a.memberId) || a.memberId < 1 ||
  typeof a.accessToken !== 'string' || a.accessToken.split('.').length !== 3 ||
  !Array.isArray(a.assetNames) || !a.assetNames.length ||
  a.assetNames.some((name) => typeof name !== 'string' || !name.trim()) ||
  new Set(a.assetNames).size !== a.assetNames.length
)) {
  throw new Error('회원 ID·실제 access_token·보유 종목명을 입력하세요.');
}

const requests = new Counter('analysis_requests');
const success = new Rate('analysis_success');
const duration = new Trend('analysis_duration_ms', true);

export const options = {
  scenarios: {
    analysis: {
      executor: 'per-vu-iterations',
      vus: USERS,
      iterations: 1,
      maxDuration: '6m',
      gracefulStop: '0s',
    },
  },
  setupTimeout: '2m',
  maxRedirects: 0,
  tags: { version: 'v1', condition: CASE, api_mode: API_MODE, run: RUN },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
  thresholds: {
    analysis_requests: [`count==${USERS}`],
    analysis_success: ['rate==1'],
  },
};

// 인증 준비는 분석 시간에 포함하지 않는다.
export function setup() {
  const responses = http.batch(
    accounts.map((account) => {
      const jar = new http.CookieJar();
      jar.set(BASE_URL, 'access_token', account.accessToken);
      return {
        method: 'GET',
        url: `${BASE_URL}/api/auth/csrf`,
        params: { jar, timeout: '15s', tags: { name: 'csrf_prepare' } },
      };
    }),
  );
  const csrf = responses.map((response) => {
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
  });
  return { csrf, startAt: Date.now() + 2000 };
}

export default function (data) {
  const account = accounts[__VU - 1];
  const csrf = data.csrf[__VU - 1];
  const wait = (data.startAt - Date.now()) / 1000;
  if (wait > 0) sleep(wait);

  requests.add(1);
  const startedAt = Date.now();
  const response = http.post(
    `${BASE_URL}/api/v1/users/me/portfolio/analysis`,
    null,
    {
      cookies: {
        access_token: account.accessToken,
        'XSRF-TOKEN': csrf.cookie,
      },
      headers: { [csrf.headerName]: csrf.token },
      timeout: '300s',
      tags: { name: 'portfolio_analysis_v1' },
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
  if (!passed) console.error(`v1 검증 실패: HTTP ${response.status}`);
}

export function handleSummary(data) {
  const requestCount = data.metrics.analysis_requests?.values?.count || 0;
  const successValues = data.metrics.analysis_success?.values;
  const latency = data.metrics.analysis_duration_ms?.values;
  const milliseconds = (value) => Number.isFinite(value) ? `${value.toFixed(2)} ms` : 'N/A';
  const failureRate = Number.isFinite(successValues?.rate)
    ? `${((1 - successValues.rate) * 100).toFixed(2)}%`
    : 'N/A';
  const result = JSON.stringify({
    finishedAt: new Date().toISOString(),
    version: 'v1', condition: CASE, apiMode: API_MODE, users: USERS, run: RUN,
    requests: data.metrics.analysis_requests,
    success: data.metrics.analysis_success,
    durationMs: data.metrics.analysis_duration_ms,
  }, null, 2);
  const summary = [
    `포트폴리오 분석 V1 / ${CASE} / ${API_MODE} / ${USERS}명 / 실행 ${RUN}`,
    `정상 완료: ${successValues?.passes || 0} / ${requestCount}건 (예정 ${USERS}건)`,
    `실패율: ${failureRate}`,
    `평균 분석 시간: ${milliseconds(latency?.avg)}`,
    `p95 분석 시간: ${milliseconds(latency?.['p(95)'])}`,
    `최대 분석 시간: ${milliseconds(latency?.max)}`,
    `결과 파일: result-v1-${CASE}-${API_MODE}-${USERS}users-${RUN}.json`,
    '',
  ].join('\n');
  return {
    stdout: summary,
    [`result-v1-${CASE}-${API_MODE}-${USERS}users-${RUN}.json`]: result,
  };
}
