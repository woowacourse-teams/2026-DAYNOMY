import assert from 'node:assert/strict';
import test from 'node:test';
import {
  changeSharedHoldingVisibility,
  getSharedPortfolio,
  importSharedHoldings,
  readSourceHoldings,
  removeSharedHolding,
} from '../../src/features/league/sharedPortfolioApi.ts';

const response = {
  holdings: [],
  totalPurchaseAmount: 0,
  totalEvaluationAmount: 0,
  totalReturnRate: null,
  pricesComplete: true,
};
const json = (value: unknown) =>
  new Response(JSON.stringify(value), { headers: { 'content-type': 'application/json' } });

test('공유용 쓰기는 원본 API 대신 별도 API와 CSRF를 사용한다', async () => {
  const calls: { url: string; init?: RequestInit }[] = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    return String(url) === '/api/auth/csrf'
      ? json({ headerName: 'X-XSRF-TOKEN', token: 'test-csrf' })
      : json(response);
  };
  await importSharedHoldings(
    [
      {
        assetId: 1,
        assetName: '삼성전자',
        assetCode: '005930',
        category: 'STOCK',
        market: 'KOSPI',
        quantity: 2,
        averagePurchasePrice: 70000,
      },
    ],
    false,
  );
  await changeSharedHoldingVisibility(1, true);
  await removeSharedHolding(1);
  const writes = calls.filter((call) => call.init?.method && call.init.method !== 'GET');
  assert.equal(writes.length, 3);
  assert.equal(writes[2].init?.method, 'DELETE');
  assert.equal(writes[2].url, '/api/users/me/shared-portfolio/holdings/1');
  assert(writes.every((call) => call.url.startsWith('/api/users/me/shared-portfolio')));
  assert(
    writes.every((call) => new Headers(call.init?.headers).get('X-XSRF-TOKEN') === 'test-csrf'),
  );
  assert.deepEqual(JSON.parse(String(writes[0].init?.body)), {
    holdings: [{ assetId: 1, quantity: 2, averagePurchasePrice: 70000 }],
    overwriteExisting: false,
  });
  assert.equal(writes[1].url, '/api/users/me/shared-portfolio/holdings/1/visibility');
  assert.equal(writes[1].init?.method, 'PATCH');
  assert.deepEqual(JSON.parse(String(writes[1].init?.body)), { hidden: true });
});

test('잘못된 공유용 응답은 빈 자산이나 성공으로 바꾸지 않는다', async () => {
  globalThis.fetch = async () => json({ ...response, holdings: [{ assetId: 1 }] });
  await assert.rejects(() => getSharedPortfolio(), /응답을 확인할 수 없습니다/);
});

test('공개·비공개 종목을 함께 읽고 보유 이유 대기를 집계 제외로 바꾸지 않는다', async () => {
  const holding = {
    assetId: 1,
    assetCode: '005930',
    assetName: '삼성전자',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 2,
    averagePurchasePrice: 100,
    hidden: true,
    pendingReason: true,
    reason: '',
    baseDate: '2026-10-23',
    closePrice: 110,
    evaluationAmount: 220,
    returnRate: 10,
  };
  globalThis.fetch = async () =>
    json({
      ...response,
      holdings: [
        holding,
        {
          ...holding,
          assetId: 2,
          hidden: false,
          pendingReason: false,
          reason: '공개 이유',
        },
      ],
    });
  const result = await getSharedPortfolio();
  assert.equal(result.holdings.length, 2);
  assert.equal(result.holdings[0].hidden, true);
  assert.equal(result.holdings[1].hidden, false);
  assert.equal(result.holdings[0].evaluationAmount, 220);
});

test('계정의 서버 원본만 읽고 브라우저 원본은 읽거나 변경하지 않는다', async () => {
  const holdings = [
    {
      assetId: 1,
      name: '삼성전자',
      assetCode: '005930',
      category: 'STOCK',
      market: 'KOSPI',
      quantity: 2,
      averagePurchasePrice: 70000,
    },
  ];
  let reads = 0;
  let writes = 0;
  const calls: { url: string; init?: RequestInit }[] = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url: String(url), init });
    return json({ holdings, totalRealizedProfitLoss: 0 });
  };
  const previous = globalThis.localStorage;
  globalThis.localStorage = {
    getItem: () => {
      reads += 1;
      return '{다른 계정의 이전 원본}';
    },
    setItem: () => {
      writes += 1;
    },
  } as unknown as Storage;
  try {
    assert.equal((await readSourceHoldings())[0]?.assetName, '삼성전자');
    assert.deepEqual(
      calls.map((call) => call.url),
      ['/api/users/me/portfolio'],
    );
    assert(calls.every((call) => !call.init?.method || call.init.method === 'GET'));
    assert.equal(calls[0].init?.credentials, 'include');
    assert.equal(reads, 0);
    assert.equal(writes, 0);
    globalThis.fetch = async () =>
      json({ holdings: [{ ...holdings[0], quantity: -1 }], totalRealizedProfitLoss: 0 });
    await assert.rejects(readSourceHoldings, /응답 형식/);
    globalThis.fetch = async () =>
      json({ holdings: [holdings[0], holdings[0]], totalRealizedProfitLoss: 0 });
    await assert.rejects(readSourceHoldings, /중복 종목/);
    assert.equal(writes, 0);
  } finally {
    globalThis.localStorage = previous;
  }
});
