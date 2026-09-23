/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PortfolioPage } from '../../src/features/portfolio/PortfolioPage';
import { PORTFOLIO_STORAGE_KEY } from '../../src/features/portfolio/hooks/usePortfolioHoldings';

const stock = { assetId: 1, assetCode: '005930', name: '삼성전자', market: 'KOSPI' } as const;
const calculation = {
  baseDate: '2026-09-21',
  totalPurchaseAmount: 700000,
  totalEvaluationAmount: 750000,
  totalProfitLoss: 50000,
  totalReturnRate: 7.142857,
  holdings: [
    {
      ...stock,
      baseDate: '2026-09-21',
      quantity: 10,
      averagePurchasePrice: 70000,
      closePrice: 75000,
      purchaseAmount: 700000,
      evaluationAmount: 750000,
      profitLoss: 50000,
      returnRate: 7.142857,
      weight: 100,
    },
  ],
  marketAllocations: [{ market: 'KOSPI', evaluationAmount: 750000, weight: 100 }],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function mockPortfolioApi() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/stocks?')) return jsonResponse({ stocks: [stock] });
      if (url.endsWith('/api/stocks/1/price'))
        return jsonResponse({
          assetId: 1,
          assetCode: '005930',
          name: '삼성전자',
          baseDate: '2026-09-21',
          closePrice: 75000,
        });
      if (url.endsWith('/api/auth/csrf'))
        return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      if (url.endsWith('/api/portfolio/calculate')) return jsonResponse(calculation);
      return jsonResponse({}, 404);
    }),
  );
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe('포트폴리오 화면', () => {
  it('저장된 자산이 없으면 추가 안내를 표시한다', () => {
    const view = render(<PortfolioPage />);
    expect(view.getByRole('heading', { name: '첫 자산을 추가해 보세요' })).toBeTruthy();
    expect(view.getByRole('button', { name: /자산 추가/ })).toBeTruthy();
  });

  it('종목을 검색해 추가하고 계산 결과를 표시한다', async () => {
    mockPortfolioApi();
    const view = render(<PortfolioPage />);
    fireEvent.click(view.getByRole('button', { name: /자산 추가/ }));

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '삼성' } });
    const result = await within(dialog).findByRole('button', { name: /삼성전자/ });
    fireEvent.click(result);
    const averagePriceInput = within(dialog).getByLabelText('평균 매수가') as HTMLInputElement;
    await waitFor(() => expect(averagePriceInput.value).toBe('75000'));
    fireEvent.click(within(dialog).getByRole('button', { name: '평균 매수가 1,000원 올리기' }));
    expect(averagePriceInput.value).toBe('76000');
    fireEvent.click(within(dialog).getByRole('button', { name: '평균 매수가 1,000원 내리기' }));
    expect(averagePriceInput.value).toBe('75000');
    fireEvent.change(within(dialog).getByLabelText('보유수량'), { target: { value: '10' } });
    fireEvent.change(averagePriceInput, { target: { value: '70000' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '추가하기' }));

    expect(await view.findByText('750,000')).toBeTruthy();
    expect(view.getByRole('img', { name: '종목별 수익률' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '시장 구성' })).toBeTruthy();
    expect(view.getAllByText('삼성전자').length).toBeGreaterThan(0);
    expect(localStorage.getItem(PORTFOLIO_STORAGE_KEY)).toContain('005930');
  });

  it('로컬 저장 자산을 복원해 계산하고 삭제한다', async () => {
    localStorage.setItem(
      PORTFOLIO_STORAGE_KEY,
      JSON.stringify([{ ...stock, quantity: 10, averagePurchasePrice: 70000 }]),
    );
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    expect(await view.findByText('750,000')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '삭제' }));
    await waitFor(() =>
      expect(view.getByRole('heading', { name: '첫 자산을 추가해 보세요' })).toBeTruthy(),
    );
    expect(localStorage.getItem(PORTFOLIO_STORAGE_KEY)).toBe('[]');
  });
});
