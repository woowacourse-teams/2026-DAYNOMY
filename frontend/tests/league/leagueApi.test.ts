import assert from 'node:assert/strict';
import test from 'node:test';
import { recordDecision, getInvestorDetail, getRankings } from '../../src/features/league/api.ts';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

test('리그 순위 응답을 런타임에 검증한다', async () => {
  globalThis.fetch = async () =>
    jsonResponse({
      weekStart: '2026-09-28',
      weekEnd: '2026-10-02',
      leagueType: 'WEEKLY_RETURN',
      confirmed: true,
      totalCount: 1,
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
