/** @vitest-environment jsdom */
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { usePortfolioHoldings } from '../../src/features/portfolio/hooks/usePortfolioHoldings';

const first = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK' as const,
  market: 'KOSPI' as const,
  quantity: 2,
  averagePurchasePrice: 70000,
};
const added = {
  ...first,
  assetId: 2,
  assetCode: 'AAPL.US',
  name: '애플',
  market: 'NASDAQ' as const,
  quantity: 1,
  averagePurchasePrice: 362500,
};
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
it('로그인 사용자의 현재 보유잔고를 거래 정보 없이 한 요청으로 등록하고 중복을 거부한다', async () => {
  const calls: { path: string; body: unknown }[] = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      const respond = (body: unknown) =>
        new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'content-type': 'application/json' },
        });
      if (path.endsWith('/api/auth/csrf'))
        return respond({ token: 'test', headerName: 'X-CSRF-TOKEN' });
      if (path.endsWith('/api/portfolio/holdings/bulk') && init?.method === 'POST') {
        calls.push({ path, body: JSON.parse(String(init.body)) });
        return respond({ holdings: [first, added], totalRealizedProfitLoss: 0 });
      }
      return respond({ holdings: [first], totalRealizedProfitLoss: 0 });
    }),
  );
  const view = renderHook(() => usePortfolioHoldings(true));
  await waitFor(() => expect(view.result.current.loading).toBe(false));
  await act(async () => {
    await view.result.current.addHoldings([added]);
  });
  expect(calls).toEqual([
    {
      path: expect.stringContaining('/api/portfolio/holdings/bulk'),
      body: { holdings: [{ assetId: 2, quantity: 1, averagePurchasePrice: 362500 }] },
    },
  ]);
  expect(view.result.current.holdings).toEqual([first, added]);
  await act(async () => {
    await expect(view.result.current.addHoldings([added])).rejects.toThrow('중복');
  });
  expect(calls).toHaveLength(1);
});
