import assert from 'node:assert/strict';
import test from 'node:test';
import {
  recordDecision,
  getInvestorDetail,
  getRankings,
  getPublicInvestor,
  getDailyHistory,
  getFollowSummary,
  updateReview,
  updateDecisionReason,
} from '../../src/features/league/api.ts';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

test('순위·일별 집계 일정은 누락과 null을 허용하고 잘못된 날짜·시각·상태를 거부한다', async () => {
  const base = {
    weekStart: '2026-10-19',
    weekEnd: '2026-10-25',
    confirmed: false,
    asOfDate: null,
    eligibleFrom: '2026-10-19',
    weeklyReturnRate: null,
    days: [],
    leagueType: 'WEEKLY_RETURN',
    rankings: [],
    totalCount: 0,
  };
  const aggregation = {
    baseDate: '2026-10-19',
    expectedAt: '2026-10-22T08:20:00Z',
    firstReturnExpectedAt: null,
    reason: 'SNAPSHOT_PENDING',
    delayed: false,
  };
  for (const load of [() => getRankings('WEEKLY_RETURN'), () => getDailyHistory('investor-1')]) {
    for (const value of [undefined, null, aggregation, { ...aggregation, expectedAt: null }]) {
      globalThis.fetch = async () => jsonResponse({ ...base, aggregation: value });
      assert.deepEqual((await load()).aggregation, value);
    }
    for (const invalid of [
      { ...aggregation, baseDate: '2026-02-30' },
      { ...aggregation, expectedAt: '2026-10-22' },
      { ...aggregation, expectedAt: '2026-02-30T08:20:00Z' },
      { ...aggregation, firstReturnExpectedAt: 'not-a-date' },
      { ...aggregation, delayed: 'false' },
    ]) {
      globalThis.fetch = async () => jsonResponse({ ...base, aggregation: invalid });
      await assert.rejects(load(), /응답 형식/);
    }
  }
});

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
          threeWeekReturnRate: 4.2,
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

test('이전 서버의 공개 투자자와 순위 응답도 3주 지표만 비워서 보여준다', async () => {
  const ranking = {
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
  };
  globalThis.fetch = async () =>
    jsonResponse({
      weekStart: '2026-10-12',
      weekEnd: '2026-10-18',
      leagueType: 'WEEKLY_RETURN',
      confirmed: false,
      totalCount: 1,
      asOfDate: '2026-10-13',
      rankings: [ranking],
    });
  assert.equal((await getRankings('WEEKLY_RETURN')).rankings[0]?.threeWeekReturnRate, null);

  const investor = {
    publicId: 'investor-1',
    displayName: '차분한초보',
    bio: '',
    experienceLevel: 'BEGINNER',
    riskProfile: 'BALANCED',
    performance: {
      weeklyReturnRate: 2.5,
      eightWeekReturnRate: 4.2,
      maxDrawdownRate: -1.1,
      volatilityRate: 0.7,
      maxHoldingWeight: 55,
    },
    allocation: null,
    history: [],
    decisionCount: 4,
    reviewCompletionRate: 75,
    followed: false,
    detailAvailable: false,
  };
  globalThis.fetch = async () => jsonResponse(investor);
  assert.equal((await getPublicInvestor('investor-1')).performance.threeWeekReturnRate, null);
  globalThis.fetch = async () =>
    jsonResponse({
      ...investor,
      performance: { ...investor.performance, threeWeekReturnRate: '4.2' },
    });
  await assert.rejects(getPublicInvestor('investor-1'), /투자자 응답 형식/);
});

test('일별 응답은 결측값을 보존하고 주간 경계·정렬·누락 이후 누적값을 검증한다', async () => {
  const valid = {
    weekStart: '2026-09-28',
    weekEnd: '2026-10-04',
    asOfDate: '2026-09-28',
    eligibleFrom: '2026-08-03',
    confirmed: false,
    weeklyReturnRate: 10,
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
    { ...valid, asOfDate: '2026-09-30' },
  ]) {
    globalThis.fetch = async () => jsonResponse(invalid);
    await assert.rejects(() => getDailyHistory('investor-1'), /일별 수익률 응답 형식/);
  }
});

test('집계 예상 시각은 시간대가 있는 날짜와 정상 상태만 허용한다', async () => {
  const aggregation = {
    baseDate: '2026-10-12',
    expectedAt: '2026-10-13T05:30:00Z',
    firstReturnExpectedAt: '2026-10-14T05:30:00Z',
    reason: 'PRICE_COLLECTION_PENDING',
    delayed: false,
  };
  const response = {
    weekStart: '2026-10-12',
    weekEnd: '2026-10-18',
    leagueType: 'WEEKLY_RETURN',
    confirmed: false,
    totalCount: 0,
    rankings: [],
    asOfDate: null,
    aggregation,
  };
  globalThis.fetch = async () => jsonResponse(response);
  assert.deepEqual((await getRankings('WEEKLY_RETURN')).aggregation, aggregation);
  for (const invalid of [
    { ...aggregation, expectedAt: 'tomorrow' },
    { ...aggregation, expectedAt: '2026-02-30T05:30:00Z' },
    { ...aggregation, expectedAt: '2026-10-13T14:30:00' },
    { ...aggregation, firstReturnExpectedAt: 'invalid' },
    { ...aggregation, baseDate: '2026-02-30' },
    { ...aggregation, delayed: 'false' },
  ]) {
    globalThis.fetch = async () => jsonResponse({ ...response, aggregation: invalid });
    await assert.rejects(getRankings('WEEKLY_RETURN'), /리그 응답 형식/);
  }
  const history = {
    weekStart: response.weekStart,
    weekEnd: response.weekEnd,
    asOfDate: null,
    eligibleFrom: '2026-10-12',
    confirmed: false,
    weeklyReturnRate: null,
    days: [],
    aggregation,
  };
  globalThis.fetch = async () => jsonResponse(history);
  assert.deepEqual((await getDailyHistory('investor-1')).aggregation, aggregation);
  globalThis.fetch = async () =>
    jsonResponse({ ...history, aggregation: { ...aggregation, expectedAt: 'invalid' } });
  await assert.rejects(getDailyHistory('investor-1'), /일별 수익률 응답 형식/);
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

test('보유 이유 수정은 해당 기록에 이유만 PUT하고 응답을 검증한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const record = {
    id: 7,
    assetId: 2,
    assetCode: '005930',
    assetName: '삼성전자',
    category: 'STOCK',
    transactionType: 'HOLD',
    quantity: 1,
    unitPrice: 70000,
    fee: 0,
    tradedOn: '2026-10-02',
    reason: '수정한 이유',
    expectedHoldingPeriod: null,
    expectedChange: '',
    invalidationCondition: '',
    maximumAcceptableLossRate: null,
    writtenAfterTrade: true,
    reviews: [],
  };
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    return String(input) === '/api/auth/csrf'
      ? jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' })
      : jsonResponse(record);
  };
  assert.deepEqual(await updateDecisionReason(7, '수정한 이유'), record);
  assert.equal(calls[1]?.url, '/api/users/me/portfolio/decisions/7/reason');
  assert.equal(calls[1]?.init?.method, 'PUT');
  assert.deepEqual(JSON.parse(String(calls[1]?.init?.body)), { reason: '수정한 이유' });
  assert.equal(new Headers(calls[1]?.init?.headers).get('X-XSRF-TOKEN'), 'csrf-token');
  globalThis.fetch = async (input) =>
    String(input) === '/api/auth/csrf'
      ? jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'csrf-token' })
      : jsonResponse({ ...record, reviews: [{}] });
  await assert.rejects(updateDecisionReason(7, '수정한 이유'), /응답 형식/);
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

test('휴장일 0%는 누적과 실제 종가 기준일을 유지하고 잘못된 이월이나 누락일 대체를 거부한다', async () => {
  const history = {
    weekStart: '2026-10-12',
    weekEnd: '2026-10-18',
    asOfDate: '2026-10-16',
    eligibleFrom: '2026-10-12',
    confirmed: false,
    weeklyReturnRate: 10,
    days: Array.from({ length: 7 }, (_, index) => ({
      baseDate: `2026-10-${12 + index}`,
      dailyReturnRate: index === 0 ? 10 : 0,
      cumulativeReturnRate: 10,
      status: 'CALCULATED',
      reason: index >= 5 ? 'MARKET_CLOSED' : null,
    })),
  };
  globalThis.fetch = async () => jsonResponse(history);
  assert.equal((await getDailyHistory('investor-1')).days[6].cumulativeReturnRate, 10);
  for (const invalid of [
    { ...history, asOfDate: '2026-10-18' },
    {
      ...history,
      days: history.days.map((day, i) => (i === 5 ? { ...day, dailyReturnRate: 10 } : day)),
    },
    {
      ...history,
      days: history.days.map((day, i) => (i === 5 ? { ...day, cumulativeReturnRate: 20 } : day)),
    },
    {
      ...history,
      days: history.days.map((day, i) =>
        i === 4
          ? {
              ...day,
              dailyReturnRate: null,
              cumulativeReturnRate: null,
              status: 'PENDING',
              reason: 'PRICE_COLLECTION_PENDING',
            }
          : day,
      ),
    },
    {
      ...history,
      days: history.days.map((day) => ({ ...day, dailyReturnRate: 0, reason: 'MARKET_CLOSED' })),
    },
  ]) {
    globalThis.fetch = async () => jsonResponse(invalid);
    await assert.rejects(getDailyHistory('investor-1'), /일별 수익률 응답 형식/);
  }
});
