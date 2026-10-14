import assert from 'node:assert/strict';
import test from 'node:test';
import {
  recordDecision,
  getInvestorDetail,
  getRankings,
  getDailyHistory,
  getFollowSummary,
  updateReview,
} from '../../src/features/league/api.ts';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

test('팔로우 비교는 지난 확정 순위의 차이를 검증하고 이전 응답도 읽는다', async () => {
  const entry = {
    publicId: 'investor-1',
    displayName: '공개 닉네임',
    rank: 8,
    previousRank: 10,
    rankChange: 2,
    comparisonWeekStart: '2026-09-28',
    weeklyReturnRate: 2,
    newReviewCount: 1,
  };
  globalThis.fetch = async () => jsonResponse({ investors: [entry] });
  assert.equal((await getFollowSummary())[0].rankChange, 2);
  globalThis.fetch = async () => jsonResponse({ investors: [{ ...entry, rankChange: -2 }] });
  await assert.rejects(getFollowSummary(), /응답 형식/);
  globalThis.fetch = async () =>
    jsonResponse({ investors: [{ ...entry, previousRank: null, rankChange: null }] });
  assert.equal((await getFollowSummary())[0].rankChange, null);
  const { previousRank, rankChange, comparisonWeekStart, ...legacy } = entry;
  void previousRank;
  void rankChange;
  void comparisonWeekStart;
  globalThis.fetch = async () => jsonResponse({ investors: [legacy] });
  assert.equal((await getFollowSummary())[0].rankChange, undefined);
});

test('리그 순위 응답을 런타임에 검증한다', async () => {
  globalThis.fetch = async () =>
    jsonResponse({
      weekStart: '2026-09-28',
      weekEnd: '2026-10-04',
      leagueType: 'WEEKLY_RETURN',
      confirmed: true,
      totalCount: 1,
      asOfDate: '2026-10-02',
      rankings: [
        {
          rank: 1,
          publicId: 'investor-1',
          displayName: '차분한초보',
          experienceLevel: 'BEGINNER',
          riskProfile: 'BALANCED',
          weeklyReturnRate: 2.5,
          eightWeekReturnRate: 4.2,
          maxDrawdownRate: -1.1,
          volatilityRate: 0.7,
          maxHoldingWeight: 55,
          decisionCount: 4,
          reviewCompletionRate: 75,
          followed: false,
        },
      ],
    });

  const response = await getRankings('WEEKLY_RETURN');

  assert.equal(response.rankings[0]?.displayName, '차분한초보');
  assert.equal(response.rankings[0]?.weeklyReturnRate, 2.5);
});

test('일별 응답은 결측값을 보존하고 주간 경계·정렬·누락 이후 누적값을 검증한다', async () => {
  const valid = {
    weekStart: '2026-09-28',
    weekEnd: '2026-10-04',
    asOfDate: '2026-09-30',
    eligibleFrom: '2026-08-03',
    confirmed: false,
    weeklyReturnRate: null,
    days: [
      {
        baseDate: '2026-09-28',
        dailyReturnRate: 10,
        cumulativeReturnRate: 10,
        status: 'CALCULATED',
        reason: null,
      },
      {
        baseDate: '2026-09-29',
        dailyReturnRate: null,
        cumulativeReturnRate: null,
        status: 'EXCLUDED',
        reason: 'MISSING_PRICE',
      },
      {
        baseDate: '2026-09-30',
        dailyReturnRate: -10,
        cumulativeReturnRate: null,
        status: 'CALCULATED',
        reason: null,
      },
    ],
  };
  globalThis.fetch = async (input) => {
    assert.equal(
      String(input),
      '/api/league/investors/investor-1/daily-history?weekStart=2026-09-28',
    );
    return jsonResponse(valid);
  };
  assert.equal(
    (await getDailyHistory('investor-1', valid.weekStart)).days[1].dailyReturnRate,
    null,
  );
  for (const invalid of [
    { ...valid, weekEnd: '2026-10-02' },
    { ...valid, days: [...valid.days].reverse() },
    { ...valid, days: [{ ...valid.days[0], baseDate: '2026-09-21' }] },
    { ...valid, days: [valid.days[0], { ...valid.days[1], dailyReturnRate: 0 }, valid.days[2]] },
    {
      ...valid,
      days: [valid.days[0], valid.days[1], { ...valid.days[2], cumulativeReturnRate: -1 }],
    },
    { ...valid, weeklyReturnRate: 0 },
  ]) {
    globalThis.fetch = async () => jsonResponse(invalid);
    await assert.rejects(() => getDailyHistory('investor-1'), /일별 수익률 응답 형식/);
  }
});

test('최신 집계일 이후 종가 수집 대기가 있어도 집계된 주간 누적을 표시한다', async () => {
  const history = {
    weekStart: '2026-10-12',
    weekEnd: '2026-10-18',
    asOfDate: '2026-10-13',
    eligibleFrom: '2026-10-13',
    trackingStartedOn: '2026-10-13',
    confirmed: false,
    weeklyReturnRate: 0,
    days: [
      {
        baseDate: '2026-10-12',
        dailyReturnRate: null,
        cumulativeReturnRate: null,
        status: 'NOT_PARTICIPATING',
        reason: null,
      },
      {
        baseDate: '2026-10-13',
        dailyReturnRate: 0,
        cumulativeReturnRate: 0,
        status: 'CALCULATED',
        reason: 'TRACKING_BASELINE',
      },
      {
        baseDate: '2026-10-14',
        dailyReturnRate: null,
        cumulativeReturnRate: null,
        status: 'EXCLUDED',
        reason: 'PRICE_COLLECTION_PENDING',
      },
    ],
  };
  for (const status of ['PENDING', 'EXCLUDED']) {
    globalThis.fetch = async () =>
      jsonResponse({
        ...history,
        days: history.days.map((day, index) => (index === 2 ? { ...day, status } : day)),
      });
    const response = await getDailyHistory('investor-1', history.weekStart);
    assert.equal(response.weeklyReturnRate, 0);
    assert.equal(response.days[2].dailyReturnRate, null);
  }
  globalThis.fetch = async () => jsonResponse({ ...history, confirmed: true });
  await assert.rejects(getDailyHistory('investor-1'), /일별 수익률 응답 형식/);
});

test('공개 상세의 중첩된 판단 기록까지 런타임에 검증한다', async () => {
  globalThis.fetch = async () =>
    jsonResponse({
      publicId: 'investor-1',
      asOfDate: '2026-09-26',
      holdings: [],
      decisions: [{ transactionId: 1, assetName: '삼성전자' }],
    });

  await assert.rejects(
    () => getInvestorDetail('investor-1'),
    /투자 상세 응답 형식이 올바르지 않습니다/,
  );
});

test('투자 판단 저장 요청에 CSRF 토큰과 멱등 키를 전송한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' });
    }
    return jsonResponse({
      id: 10,
      assetId: 2,
      assetCode: '005930',
      assetName: '삼성전자',
      category: 'STOCK',
      transactionType: 'HOLD',
      quantity: 1,
      unitPrice: 70000,
      fee: 0,
      tradedOn: '2026-10-02',
      reason: '장기 성장 기대',
      expectedHoldingPeriod: 'OVER_SIX_MONTHS',
      expectedChange: '매출 성장',
      invalidationCondition: '실적 역성장',
      maximumAcceptableLossRate: 10,
      writtenAfterTrade: false,
      reviews: [],
    });
  };

  await recordDecision({
    requestKey: 'request-1',
    assetId: 2,
    decision: {
      reason: '장기 성장 기대',
      expectedHoldingPeriod: 'OVER_SIX_MONTHS',
      expectedChange: '매출 성장',
      invalidationCondition: '실적 역성장',
      maximumAcceptableLossRate: 10,
    },
  });

  assert.equal(calls[1]?.url, '/api/users/me/portfolio/decisions');
  assert.equal(new Headers(calls[1]?.init?.headers).get('X-XSRF-TOKEN'), 'csrf-token');
  assert.equal(JSON.parse(String(calls[1]?.init?.body)).requestKey, 'request-1');
  assert.equal(JSON.parse(String(calls[1]?.init?.body)).quantity, undefined);
});

test('내 복기 수정은 해당 판단과 복기 ID로 PUT 요청한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' });
    }
    return jsonResponse({
      id: 2,
      actualResult: '수정한 결과',
      differenceFromExpectation: '차이',
      nextAction: '다음 행동',
      createdAt: '2026-10-06T00:00:00Z',
      updatedAt: '2026-10-07T00:00:00Z',
    });
  };

  const result = await updateReview(1, 2, {
    actualResult: '수정한 결과',
    differenceFromExpectation: '차이',
    nextAction: '다음 행동',
  });
  assert.equal(calls[1]?.url, '/api/users/me/portfolio/decisions/1/reviews/2');
  assert.equal(calls[1]?.init?.method, 'PUT');
  assert.equal(new Headers(calls[1]?.init?.headers).get('X-XSRF-TOKEN'), 'csrf-token');
  assert.equal(result.createdAt, '2026-10-06T00:00:00Z');
  assert.equal(result.updatedAt, '2026-10-07T00:00:00Z');
});

test('주중 참여는 기준 종가부터 0%로 이번 주 누적 순위에 반영한다', async () => {
  globalThis.fetch = async () =>
    jsonResponse({
      weekStart: '2026-09-21',
      weekEnd: '2026-09-27',
      asOfDate: '2026-09-23',
      eligibleFrom: '2026-09-22',
      trackingStartedOn: '2026-09-22',
      confirmed: false,
      weeklyReturnRate: 10,
      days: [
        {
          baseDate: '2026-09-21',
          dailyReturnRate: null,
          cumulativeReturnRate: null,
          status: 'NOT_PARTICIPATING',
          reason: null,
        },
        {
          baseDate: '2026-09-22',
          dailyReturnRate: 0,
          cumulativeReturnRate: 0,
          status: 'CALCULATED',
          reason: 'TRACKING_BASELINE',
        },
        {
          baseDate: '2026-09-23',
          dailyReturnRate: 10,
          cumulativeReturnRate: 10,
          status: 'CALCULATED',
          reason: null,
        },
      ],
    });
  const response = await getDailyHistory('investor-1', '2026-09-23');
  assert.equal(response.weekStart, '2026-09-21');
  assert.equal(response.weeklyReturnRate, 10);
});
