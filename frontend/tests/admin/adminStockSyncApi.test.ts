import assert from 'node:assert/strict';
import test from 'node:test';
import {
  backfillAdminStockPrices,
  recalculateAdminLeague,
  syncAdminStockPrices,
  syncAdminStocks,
} from '../../src/features/admin/api.ts';

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
      synchronizedDateCount: 2,
      pendingDateCount: 1,
      failedDateCount: 1,
    });
  };

  const result = await syncAdminStockPrices();

  assert.equal(result.receivedCount, 3500);
  assert.equal(result.synchronizedDateCount, 2);
  assert.equal(result.pendingDateCount, 1);
  assert.equal(calls[1].url, '/api/admin/stocks/prices/sync');
  assert.equal(calls[1].init?.method, 'POST');
  assert.equal(new Headers(calls[1].init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
});

test('기간별 재수집과 리그 재집계는 CSRF를 포함한 POST로 실행한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === '/api/auth/csrf')
      return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
    if (url.includes('/backfill?'))
      return jsonResponse({
        from: '2026-10-01',
        to: '2026-10-31',
        synchronizedDateCount: 20,
        receivedCount: 100,
        createdCount: 0,
        updatedCount: 100,
        skippedCount: 0,
      });
    return jsonResponse({ calculatedCount: 42 });
  };
  assert.equal((await backfillAdminStockPrices('2026-10-01', '2026-10-31')).updatedCount, 100);
  assert.equal((await recalculateAdminLeague()).calculatedCount, 42);
  const writes = calls.filter((call) => call.url !== '/api/auth/csrf');
  assert.deepEqual(
    writes.map((call) => call.url),
    [
      '/api/admin/stocks/prices/backfill?from=2026-10-01&to=2026-10-31',
      '/api/admin/league/recalculate',
    ],
  );
  for (const call of writes) {
    assert.equal(call.init?.method, 'POST');
    assert.equal(new Headers(call.init?.headers).get('X-CSRF-TOKEN'), 'token');
  }
});

test('잘못된 날짜·역전 기간·32일 기간은 요청 전에 거부한다', async () => {
  globalThis.fetch = async () => {
    throw new Error('호출되면 안 됨');
  };
  for (const [from, to] of [
    ['', '2026-10-01'],
    ['2026-02-30', '2026-03-01'],
    ['2026-10-02', '2026-10-01'],
    ['2026-10-01', '2026-11-01'],
  ]) {
    await assert.rejects(backfillAdminStockPrices(from, to), /31일 이내/);
  }
});

test('복구 응답의 누락·음수·문자열 건수를 거부하고 0건은 유지한다', async () => {
  let body: unknown = {};
  globalThis.fetch = async (input) =>
    String(input) === '/api/auth/csrf'
      ? jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' })
      : jsonResponse(body);
  for (const value of [undefined, -1, '1', 1.5]) {
    body = { calculatedCount: value };
    await assert.rejects(recalculateAdminLeague(), /응답 형식/);
  }
  body = { calculatedCount: 0 };
  assert.equal((await recalculateAdminLeague()).calculatedCount, 0);
  const valid = {
    from: '2026-10-01',
    to: '2026-10-02',
    synchronizedDateCount: 0,
    receivedCount: 0,
    createdCount: 0,
    updatedCount: 0,
    skippedCount: 0,
  };
  body = valid;
  assert.equal((await backfillAdminStockPrices(valid.from, valid.to)).synchronizedDateCount, 0);
  for (const invalid of [
    { ...valid, updatedCount: -1 },
    { ...valid, to: '2026-10-03' },
    { ...valid, receivedCount: '1' },
  ]) {
    body = invalid;
    await assert.rejects(backfillAdminStockPrices(valid.from, valid.to), /응답 형식/);
  }
});
