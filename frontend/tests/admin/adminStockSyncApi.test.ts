import assert from 'node:assert/strict';
import test from 'node:test';
import { syncAdminStockPrices, syncAdminStocks } from '../../src/features/admin/api.ts';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

test('관리자 종목 정보 동기화 API는 CSRF 토큰과 함께 요청한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }

    return jsonResponse({
      baseDate: '2026-09-28',
      syncedCount: 3500,
      createdCount: 20,
      updatedCount: 3470,
      delistedCount: 10,
    });
  };

  const result = await syncAdminStocks();

  assert.equal(result.syncedCount, 3500);
  assert.equal(calls[1].url, '/api/admin/stocks/sync');
  assert.equal(calls[1].init?.method, 'POST');
  assert.equal(new Headers(calls[1].init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
});

test('관리자 최근 종가 동기화 API는 CSRF 토큰과 함께 요청한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }

    return jsonResponse({
      baseDate: '2026-09-28',
      receivedCount: 3500,
      createdCount: 3400,
      updatedCount: 50,
      skippedCount: 50,
    });
  };

  const result = await syncAdminStockPrices();

  assert.equal(result.receivedCount, 3500);
  assert.equal(calls[1].url, '/api/admin/stocks/prices/sync');
  assert.equal(calls[1].init?.method, 'POST');
  assert.equal(new Headers(calls[1].init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
});
