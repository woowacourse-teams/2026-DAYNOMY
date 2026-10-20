/** @vitest-environment jsdom */
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { usePortfolioHoldings } from '../../src/features/portfolio/hooks/usePortfolioHoldings';
import {
  GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY,
  loadGuestPortfolioHoldings,
} from '../../src/features/portfolio/portfolioHoldingsStorage';
const stock = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
} as const;
const initial = { ...stock, quantity: 10, averagePurchasePrice: 70000 };
const buy = {
  assetId: 1,
  type: 'BUY',
  tradeDate: '2026-01-01',
  quantity: 2,
  unitPrice: 76000,
  fee: 10,
  tax: 2,
} as const;
beforeEach(() => {
  sessionStorage.clear();
  sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, JSON.stringify([initial]));
  vi.stubGlobal(
    'fetch',
    vi.fn(() => {
      throw new Error('회원 API 호출 금지');
    }),
  );
});
afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

it('비회원 매수·매도와 수정·삭제는 원가와 수량을 반영하고 재진입해도 복원된다', async () => {
  const view = renderHook(() => usePortfolioHoldings(false));
  await waitFor(() => expect(view.result.current.loading).toBe(false));
  await act(() => view.result.current.createTransaction(buy, stock));
  expect(view.result.current.holdings[0]).toMatchObject({
    quantity: 12,
    averagePurchasePrice: 71001,
  });
  const id = view.result.current.guestHistories[0].historyId;
  await act(() =>
    view.result.current.createTransaction(
      { ...buy, type: 'SELL', tradeDate: '2026-01-02', quantity: 3 },
      stock,
    ),
  );
  expect(view.result.current.holdings[0].quantity).toBe(9);
  await act(() => view.result.current.updateTransaction(id, 1, { ...buy, quantity: 4 }));
  expect(view.result.current.holdings[0]).toMatchObject({
    quantity: 11,
    averagePurchasePrice: 71715.14,
  });
  await act(() => view.result.current.deleteTransaction(id, 1));
  expect(view.result.current.holdings[0]).toEqual({ ...initial, quantity: 7 });
  view.unmount();
  const restored = renderHook(() => usePortfolioHoldings(false));
  await waitFor(() => expect(restored.result.current.loading).toBe(false));
  expect(restored.result.current.holdings).toEqual([{ ...initial, quantity: 7 }]);
  expect(restored.result.current.guestHistories).toHaveLength(1);
  expect(fetch).not.toHaveBeenCalled();
});

it('과거 거래 수정과 삭제로 후속 매도가 초과되면 기존 저장값과 내역을 보존한다', async () => {
  const view = renderHook(() => usePortfolioHoldings(false));
  await act(() => view.result.current.createTransaction({ ...buy, quantity: 5 }, stock));
  const id = view.result.current.guestHistories[0].historyId;
  await act(() =>
    view.result.current.createTransaction(
      { ...buy, type: 'SELL', tradeDate: '2026-01-02', quantity: 14 },
      stock,
    ),
  );
  const saved = sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY);
  await expect(
    view.result.current.updateTransaction(id, 1, { ...buy, quantity: 1 }),
  ).rejects.toThrow('보유 수량');
  await expect(view.result.current.deleteTransaction(id, 1)).rejects.toThrow('보유 수량');
  expect(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)).toBe(saved);
  expect(view.result.current.holdings[0].quantity).toBe(1);
  expect(view.result.current.guestHistories).toHaveLength(2);
});

it('전량 매도 후 다시 매수할 수 있고 정정·제거 이후에도 거래 내역을 유지한다', async () => {
  const view = renderHook(() => usePortfolioHoldings(false));
  await act(() =>
    view.result.current.createTransaction({ ...buy, type: 'SELL', quantity: 10 }, stock),
  );
  expect(view.result.current.holdings).toEqual([]);
  await act(() => view.result.current.createTransaction(buy, stock));
  await act(() => view.result.current.saveHolding({ ...initial, quantity: 5 }));
  expect(loadGuestPortfolioHoldings()).toEqual([{ ...initial, quantity: 5 }]);
  await act(() => view.result.current.removeHolding(1));
  expect(loadGuestPortfolioHoldings()).toEqual([]);
  expect(view.result.current.guestHistories).toHaveLength(4);
});

it('임시 저장 실패 시 보유 수량과 거래 내역을 변경하지 않고 재시도할 수 있다', async () => {
  const view = renderHook(() => usePortfolioHoldings(false));
  const saved = sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY);
  vi.spyOn(Storage.prototype, 'setItem').mockImplementationOnce(() => {
    throw new DOMException('Storage full', 'QuotaExceededError');
  });
  await expect(view.result.current.createTransaction(buy, stock)).rejects.toThrow('임시 보관');
  expect(view.result.current.holdings).toEqual([initial]);
  expect(view.result.current.guestHistories).toEqual([]);
  expect(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)).toBe(saved);
  await act(() => view.result.current.createTransaction(buy, stock));
  expect(view.result.current.holdings[0].quantity).toBe(12);
});

it('새 종목의 첫 매수와 거래일 순서 반영, 소수 원가 반올림을 지원한다', async () => {
  sessionStorage.clear();
  const view = renderHook(() => usePortfolioHoldings(false));
  await act(() =>
    view.result.current.createTransaction(
      { ...buy, quantity: 1, unitPrice: 0.01, fee: 0, tax: 0 },
      stock,
    ),
  );
  await act(() =>
    view.result.current.createTransaction(
      { ...buy, quantity: 1, unitPrice: 0.02, fee: 0, tax: 0 },
      stock,
    ),
  );
  expect(view.result.current.holdings[0].averagePurchasePrice).toBe(0.02);
  await act(() =>
    view.result.current.createTransaction(
      { ...buy, tradeDate: '2025-12-31', quantity: 1, unitPrice: 100, fee: 0, tax: 0 },
      stock,
    ),
  );
  expect(view.result.current.holdings[0]).toMatchObject({
    quantity: 3,
    averagePurchasePrice: 33.35,
  });
  await act(() =>
    view.result.current.createTransaction(
      { ...buy, type: 'SELL', tradeDate: '2026-01-02', quantity: 3 },
      stock,
    ),
  );
  expect(view.result.current.holdings).toEqual([]);
});

it('손상된 임시 거래 기록은 실행하지 않는다', async () => {
  const view = renderHook(() => usePortfolioHoldings(false));
  await act(() => view.result.current.createTransaction(buy, stock));
  const ledger = JSON.parse(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)!);
  ledger.histories[0].transactionQuantity = '2';
  sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, JSON.stringify(ledger));
  expect(loadGuestPortfolioHoldings()).toEqual([]);
  expect(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)).toBeNull();
});

it('미국 거래의 달러와 환율을 유지하고 원화 원가·실현손익을 재생한다', async () => {
  sessionStorage.clear();
  const tesla = {
    ...stock,
    assetId: 10,
    assetCode: 'TSLA.US',
    name: 'Tesla',
    market: 'NASDAQ',
    currency: 'USD',
  } as const;
  const view = renderHook(() => usePortfolioHoldings(false));
  await waitFor(() => expect(view.result.current.loading).toBe(false));
  await act(async () =>
    view.result.current.createTransaction(
      { ...buy, assetId: 10, quantity: 2, unitPrice: 100.1234, fee: 1, tax: 0, exchangeRate: 1300 },
      tesla,
    ),
  );
  expect(view.result.current.holdings[0].averagePurchasePrice).toBe(130810.42);
  await act(async () =>
    view.result.current.createTransaction(
      {
        ...buy,
        assetId: 10,
        type: 'SELL',
        quantity: 1,
        unitPrice: 110,
        fee: 1,
        tax: 0.5,
        exchangeRate: 1400,
      },
      tesla,
    ),
  );
  expect(view.result.current.holdings[0].quantity).toBe(1);
  expect(view.result.current.totalRealizedProfitLoss).toBe(21089.58);
  expect(loadGuestPortfolioHoldings()[0].averagePurchasePrice).toBe(130810.42);
});

it('달러 금액을 원화로 환산한 중간 값은 반올림하지 않고 평균 원가에서 반올림한다', async () => {
  sessionStorage.clear();
  const tesla = { ...stock, assetCode: 'TSLA.US', market: 'NASDAQ', currency: 'USD' } as const;
  const view = renderHook(() => usePortfolioHoldings(false));
  await waitFor(() => expect(view.result.current.loading).toBe(false));
  await act(async () =>
    view.result.current.createTransaction(
      { ...buy, quantity: 2, unitPrice: 100.1234, fee: 0.01, tax: 0, exchangeRate: 1334.61 },
      tesla,
    ),
  );
  expect(view.result.current.holdings[0].averagePurchasePrice).toBe(133632.36);
});
