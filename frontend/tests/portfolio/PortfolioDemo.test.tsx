/** @vitest-environment jsdom */
import { afterEach, expect, it, vi } from 'vitest';
import {
  calculatePortfolio,
  getPortfolioHistories,
  getSavedPortfolio,
} from '../../src/features/portfolio/api';
import { demoHoldings, portfolioDemoFetch } from '../../src/dev/portfolioDemoData';

afterEach(() => vi.unstubAllGlobals());

it('서버 없이 실제 API 검증을 통과하는 데모 자산·시세·거래 내역을 제공한다', async () => {
  vi.stubGlobal('fetch', portfolioDemoFetch);
  const saved = await getSavedPortfolio();
  expect(saved.holdings).toHaveLength(4);
  expect(saved.holdings.some((holding) => holding.category === 'ETF')).toBe(true);
  const calculation = await calculatePortfolio(saved.holdings);
  expect(calculation.pricedHoldingCount).toBe(3);
  expect(calculation.unpricedHoldingCount).toBe(1);
  expect(calculation.holdings.some((holding) => (holding.profitLoss ?? 0) > 0)).toBe(true);
  expect(calculation.holdings.some((holding) => (holding.profitLoss ?? 0) < 0)).toBe(true);
  expect(
    calculation.holdings.find((holding) => holding.assetCode === '035720')?.evaluationAmount,
  ).toBeNull();
  const histories = await getPortfolioHistories('2026-01-01', '2026-10-29');
  expect(histories.filter((history) => history.assetId === 2)).toHaveLength(5);
});

it('데모의 저장·삭제 요청은 차단하고 예시 자산을 변경하지 않는다', async () => {
  const before = JSON.stringify(demoHoldings);
  for (const method of ['POST', 'PATCH', 'DELETE']) {
    const response = await portfolioDemoFetch('/api/users/me/portfolio/transactions', { method });
    expect(response.status).toBe(405);
    expect((await response.json()).code).toBe('DEMO_READ_ONLY');
  }
  expect(JSON.stringify(demoHoldings)).toBe(before);
  expect((await portfolioDemoFetch('/api/unknown')).status).toBe(404);
});
