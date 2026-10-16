import assert from 'node:assert/strict';
import test from 'node:test';
import { isStock, searchStocks, getLatestStockPrice } from '../../src/features/stocks/api.ts';

const stock = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
};
function response(value: unknown) {
  return new Response(JSON.stringify(value), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

test('공통 종목 검증은 관리자·포트폴리오에서 동일한 주식·ETF 계약을 사용한다', () => {
  assert.equal(isStock(stock), true);
  assert.equal(isStock({ ...stock, category: 'ETF', market: 'KOSDAQ' }), true);
  for (const value of [
    null,
    {},
    { ...stock, assetId: 0 },
    { ...stock, assetId: 1.2 },
    { ...stock, category: 'CRYPTO' },
    { ...stock, name: null },
  ])
    assert.equal(isStock(value), false);
});

test('공통 검색과 최근 종가 조회는 기존 API와 취소 신호를 유지한다', async () => {
  const signal = new AbortController().signal;
  const calls: Array<{ url: string; signal?: AbortSignal | null }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), signal: init?.signal });
    return response(
      String(input).includes('/price')
        ? { ...stock, baseDate: '2026-10-13', closePrice: 70000 }
        : { stocks: [stock] },
    );
  };
  assert.equal((await searchStocks('삼성', signal))[0].assetCode, '005930');
  assert.equal((await getLatestStockPrice(1, signal)).closePrice, 70000);
  assert.equal(new URL(calls[0].url, 'http://localhost').searchParams.get('q'), '삼성');
  assert.equal(calls[1].url, '/api/stocks/1/price');
  assert.equal(calls[0].signal, signal);
});

test('검색·종가 응답 오류는 호출부에 전달한다', async () => {
  globalThis.fetch = async () => response({ stocks: [{ ...stock, market: 'OTHER' }] });
  await assert.rejects(searchStocks('삼성'), /응답 형식/);
  globalThis.fetch = async () =>
    response({ ...stock, baseDate: '2026-10-13', closePrice: '70000' });
  await assert.rejects(getLatestStockPrice(1), /응답 형식/);
});
