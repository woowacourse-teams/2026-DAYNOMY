import assert from 'node:assert/strict';
import test from 'node:test';
import { getKosdaqTopStocks } from '../../src/features/stocks/api.ts';

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
    });
  };

  const response = await getKosdaqTopStocks();

  assert.equal(calls[0], '/api/assets/kosdaq/top');
  assert.deepEqual(response, {
    baseDate: '2026-08-21',
    rankings: [
      {
        rank: 1,
        code: '247540',
        name: '에코프로비엠',
      },
    ],
  });
});

test('코스닥 대표 종목 순위 응답 계약이 맞지 않으면 실패한다', async () => {
  globalThis.fetch = async () =>
    jsonResponse({
      baseDate: null,
    });

  await assert.rejects(() => getKosdaqTopStocks());
});
