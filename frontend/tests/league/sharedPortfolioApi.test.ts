import assert from 'node:assert/strict';
import test from 'node:test';
import {
  changeSharedHoldingVisibility,
  getSharedPortfolio,
  importSharedHoldings,
  readSourceHoldings,
} from '../../src/features/league/sharedPortfolioApi.ts';
import {
  loadFinancialPlans,
  saveFinancialPlan,
} from '../../src/features/finance/financialPlanApi.ts';

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
  const writes = calls.filter((call) => call.init?.method && call.init.method !== 'GET');
  assert.equal(writes.length, 2);
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

test('원본을 읽을 때 저장 쓰기는 호출하지 않고 손상된 데이터를 알린다', () => {
  let raw = JSON.stringify([
    {
      assetId: 1,
      name: '삼성전자',
      assetCode: '005930',
      category: 'STOCK',
      market: 'KOSPI',
      quantity: 2,
      averagePurchasePrice: 70000,
    },
  ]);
  let writes = 0;
  const previous = globalThis.localStorage;
  globalThis.localStorage = {
    getItem: () => raw,
    setItem: () => {
      writes += 1;
    },
  } as unknown as Storage;
  try {
    assert.equal(readSourceHoldings()[0]?.assetName, '삼성전자');
    assert.equal(writes, 0);
    raw = '{broken';
    assert.throws(readSourceHoldings);
    assert.equal(writes, 0);
  } finally {
    globalThis.localStorage = previous;
  }
});

test('로그인 계획은 계정 API에 저장하며 브라우저 원본이나 계획을 덮어쓰지 않는다', async () => {
  const plan = {
    id: 'plan-1',
    topic: '저축·투자 계획' as const,
    title: '월 계획',
    summary: '월 120만원',
    details: [],
    actions: [],
    createdAt: '2026-10-05T00:00:00Z',
    monthlyPlan: {
      monthlyIncome: 3000000,
      monthlyExpenses: 1800000,
      goalAmount: 10000000,
      goalSaved: 1000000,
      goalMonths: 24,
      savings: 420000,
      emergency: 600000,
      investment: 180000,
      debt: 0,
    },
  };
  const calls: string[] = [];
  globalThis.fetch = async (url) => {
    calls.push(String(url));
    if (String(url) === '/api/auth/csrf')
      return json({ headerName: 'X-XSRF-TOKEN', token: 'test' });
    return json(plan);
  };
  assert.deepEqual(await saveFinancialPlan(true, plan), plan);
  assert.equal(calls[1], '/api/users/me/financial-plans/plan-1');
  globalThis.fetch = async () =>
    json({ plans: [{ ...plan, monthlyPlan: { ...plan.monthlyPlan, investment: -1 } }] });
  await assert.rejects(() => loadFinancialPlans(true), /응답 형식/);
});
