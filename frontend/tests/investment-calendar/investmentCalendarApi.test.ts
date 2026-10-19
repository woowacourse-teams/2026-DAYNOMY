import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getInvestmentCalendar,
  isInvestmentCalendar,
} from '../../src/features/investment-calendar/api.ts';
import { getMockInvestmentCalendar } from '../../src/features/investment-calendar/mockData.ts';

test('투자 캘린더 목데이터는 백엔드 응답 형식을 따른다', () => {
  const calendar = getMockInvestmentCalendar(2026, 10);

  assert.equal(isInvestmentCalendar(calendar), true);
  assert.equal(calendar.events.length, 3);
  assert.equal(calendar.events[0]?.type, 'US_CPI');
});

test('투자 캘린더 응답에 필수 분석값이 없으면 거부한다', () => {
  const calendar = getMockInvestmentCalendar(2026, 10);
  const invalid = {
    ...calendar,
    events: calendar.events.map((event, index) =>
      index === 0
        ? { ...event, portfolioAnalysis: { ...event.portfolioAnalysis, status: undefined } }
        : event,
    ),
  };

  assert.equal(isInvestmentCalendar(invalid), false);
});

test('목데이터가 없는 월은 빈 일정으로 반환한다', () => {
  assert.deepEqual(getMockInvestmentCalendar(2026, 11), {
    year: 2026,
    month: 11,
    events: [],
  });
});

test('비회원 조회는 세션 보유자산만 공개 API에 전달한다', async () => {
  const originalFetch = globalThis.fetch;
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  const calendar = getMockInvestmentCalendar(2026, 10);

  try {
    globalThis.fetch = async (input, init) => {
      calls.push({ url: String(input), init });
      if (String(input) === '/api/auth/csrf') {
        return new Response(JSON.stringify({ headerName: 'X-CSRF-TOKEN', token: 'test-token' }), {
          headers: { 'content-type': 'application/json' },
        });
      }
      return new Response(JSON.stringify(calendar), {
        headers: { 'content-type': 'application/json' },
      });
    };

    await getInvestmentCalendar(2026, 10, 'PORTFOLIO', {
      isLoggedIn: false,
      guestHoldings: [{ assetId: 100, quantity: 3 }],
    });

    assert.equal(calls[1]?.url, '/api/investment-calendar?year=2026&month=10&scope=PORTFOLIO');
    assert.equal(calls[1]?.init?.method, 'POST');
    assert.equal(calls[1]?.init?.body, '{"holdings":[{"assetId":100,"quantity":3}]}');
    assert.equal(new Headers(calls[1]?.init?.headers).get('X-CSRF-TOKEN'), 'test-token');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('로그인 회원 조회는 기존 회원 API를 사용한다', async () => {
  const originalFetch = globalThis.fetch;
  const calls: string[] = [];

  try {
    globalThis.fetch = async (input) => {
      calls.push(String(input));
      return new Response(JSON.stringify(getMockInvestmentCalendar(2026, 10)), {
        headers: { 'content-type': 'application/json' },
      });
    };

    await getInvestmentCalendar(2026, 10, 'ALL', { isLoggedIn: true });

    assert.deepEqual(calls, ['/api/users/me/investment-calendar?year=2026&month=10&scope=ALL']);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
