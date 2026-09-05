import assert from 'node:assert/strict';
import test from 'node:test';
import { getKosdaqTopStocks, searchKosdaqTopStocks } from '../../src/features/stocks/api.ts';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

test('코스닥 대표 종목 순위 API 응답을 매핑한다', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input) => {
    calls.push(String(input));
    return jsonResponse({
      baseDate: '2026-08-21',
      rankings: [
        {
          rank: 1,
          code: '247540',
          name: '에코프로비엠',
        },
      ],
      page: 1,
      size: 20,
      totalPages: 8,
      totalElements: 150,
      hasNext: true,
    });
  };

  const response = await getKosdaqTopStocks();

  assert.equal(calls[0], '/api/assets/kosdaq/top?page=1&size=10');
  assert.deepEqual(response, {
    baseDate: '2026-08-21',
    rankings: [
      {
        rank: 1,
        code: '247540',
        name: '에코프로비엠',
      },
    ],
    page: 1,
    size: 20,
    totalPages: 8,
    totalElements: 150,
    hasNext: true,
  });
});

test('검색어로 코스닥 대표 종목을 조회한다', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input) => {
    calls.push(String(input));
    return jsonResponse({
      baseDate: '2026-08-21',
      rankings: [],
      page: 1,
      size: 100,
      totalPages: 0,
      totalElements: 0,
      hasNext: false,
    });
  };

  await searchKosdaqTopStocks('삼성 전자');

  assert.equal(
    calls[0],
    '/api/assets/kosdaq/top?q=%EC%82%BC%EC%84%B1+%EC%A0%84%EC%9E%90&page=1&size=100',
  );
});

test('코스닥 대표 종목 순위 응답 계약이 맞지 않으면 실패한다', async () => {
  globalThis.fetch = async () =>
    jsonResponse({
      baseDate: null,
    });

  await assert.rejects(() => getKosdaqTopStocks());
});
