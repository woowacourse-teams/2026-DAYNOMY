/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getLatestStockPrice, searchStocks } from '../../src/features/portfolio/api';
import { PortfolioTransactionEditor } from '../../src/features/portfolio/components/PortfolioTransactionEditor';
import type { PortfolioHoldingHistory, StockPrice } from '../../src/features/portfolio/types';

vi.mock('../../src/features/portfolio/api', () => ({
  getLatestStockPrice: vi.fn(),
  searchStocks: vi.fn(),
}));
const samsung = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
} as const;
const sk = { ...samsung, assetId: 2, assetCode: '000660', name: 'SK하이닉스' };
const price: StockPrice = { ...samsung, baseDate: '2026-10-12', closePrice: 75200 };
function deferredPrice() {
  let resolve!: (value: StockPrice) => void;
  const promise = new Promise<StockPrice>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
beforeEach(() => {
  vi.mocked(getLatestStockPrice).mockResolvedValue(price);
  vi.mocked(searchStocks).mockResolvedValue([sk]);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('거래 단가의 최근 종가 기본값', () => {
  it.each([
    ['거래일', '9999-01-01', '2026-01-01'],
    ['수량 (주)', '0', '1'],
    ['거래 단가 (원)', '0', '70000'],
    ['수수료 (원)', '-1', '0'],
    ['세금 (원)', '-1', '0'],
  ])('%s 오류를 입력창에 연결하고 초점을 옮겨 수정 후 저장한다', async (label, invalid, valid) => {
    const save = vi.fn().mockResolvedValue(undefined);
    const view = render(
      <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={save} />,
    );
    await waitFor(() =>
      expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '75200'),
    );
    fireEvent.change(view.getByLabelText('수량 (주)'), { target: { value: '1' } });
    const input = view.getByLabelText(label);
    fireEvent.change(input, { target: { value: invalid } });
    fireEvent.click(view.getByRole('button', { name: '매수 기록하기' }));
    await waitFor(() => expect(document.activeElement).toBe(input));
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe(view.getByRole('alert').id);
    if (input.closest('details')) expect(input.closest('details')?.open).toBe(true);
    expect(save).not.toHaveBeenCalled();
    fireEvent.change(input, { target: { value: valid } });
    expect(input.getAttribute('aria-invalid')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '매수 기록하기' }));
    await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
  });

  it.each(['BUY', 'SELL'] as const)(
    '%s 거래에 최근 종가를 채우고 직접 수정한 단가로 저장한다',
    async (type) => {
      const save = vi.fn().mockResolvedValue(undefined);
      const view = render(
        <PortfolioTransactionEditor stock={samsung} type={type} onClose={vi.fn()} onSave={save} />,
      );
      const input = view.getByLabelText('거래 단가 (원)');
      await waitFor(() => expect(input).toHaveProperty('value', '75200'));
      fireEvent.change(input, { target: { value: '76000.50' } });
      fireEvent.change(view.getByLabelText('수량 (주)'), { target: { value: '2' } });
      fireEvent.change(view.getByLabelText('거래일'), { target: { value: '2026-10-10' } });
      fireEvent.click(
        view.getByRole('button', { name: type === 'BUY' ? '매수 기록하기' : '매도 기록하기' }),
      );
      await waitFor(() =>
        expect(save).toHaveBeenCalledWith(
          {
            assetId: 1,
            type,
            tradeDate: '2026-10-10',
            quantity: 2,
            unitPrice: 76000.5,
            fee: 0,
            tax: 0,
          },
          samsung,
        ),
      );
      expect(getLatestStockPrice).toHaveBeenCalledTimes(1);
    },
  );

  it.each(['78000', ''])(
    '늦은 종가 응답이 사용자가 입력하거나 지운 값 %j을 덮어쓰지 않는다',
    async (value) => {
      const pending = deferredPrice();
      vi.mocked(getLatestStockPrice).mockReturnValue(pending.promise);
      const view = render(
        <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={vi.fn()} />,
      );
      const input = view.getByLabelText('거래 단가 (원)');
      fireEvent.change(input, { target: { value: '78000' } });
      if (!value) fireEvent.change(input, { target: { value: '' } });
      await act(async () => pending.resolve(price));
      expect(input).toHaveProperty('value', value);
    },
  );

  it('종목을 바꾸면 새 종가를 채우고 이전 종목의 늦은 응답을 무시한다', async () => {
    const previous = deferredPrice();
    vi.mocked(getLatestStockPrice)
      .mockReturnValueOnce(previous.promise)
      .mockResolvedValueOnce({ ...sk, baseDate: '2026-10-12', closePrice: 193400 });
    const view = render(
      <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={vi.fn()} />,
    );
    fireEvent.change(view.getByLabelText('거래 단가 (원)'), { target: { value: '80000' } });
    const previousSignal = vi.mocked(getLatestStockPrice).mock.calls[0][1];
    fireEvent.click(view.getByRole('button', { name: '종목 변경' }));
    expect(previousSignal?.aborted).toBe(true);
    fireEvent.change(view.getByRole('searchbox'), { target: { value: 'SK' } });
    fireEvent.click(await view.findByRole('button', { name: /SK하이닉스/ }));
    await waitFor(() =>
      expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '193400'),
    );
    await act(async () => previous.resolve(price));
    expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '193400');
  });

  it('기존 거래 수정에서는 저장된 단가를 유지하고 종가를 조회하지 않는다', () => {
    const transaction: PortfolioHoldingHistory = {
      historyId: 3,
      assetId: 1,
      assetCode: '005930',
      name: '삼성전자',
      changeType: 'BUY',
      quantity: 2,
      averagePurchasePrice: 70000,
      effectiveDate: '2026-10-10',
      transactionQuantity: 2,
      unitPrice: 70000,
      fee: 0,
      tax: 0,
      occurredAt: '2026-10-10T01:00:00Z',
    };
    const view = render(
      <PortfolioTransactionEditor transaction={transaction} onClose={vi.fn()} onSave={vi.fn()} />,
    );
    expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '70000');
    expect(getLatestStockPrice).not.toHaveBeenCalled();
  });

  it('종가 조회 실패 후에도 단가를 직접 입력해 저장할 수 있다', async () => {
    vi.mocked(getLatestStockPrice).mockRejectedValue(new Error('시세 없음'));
    const save = vi.fn().mockResolvedValue(undefined);
    const view = render(
      <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={save} />,
    );
    await view.findByText('최근 종가를 불러오지 못했습니다. 거래 단가를 직접 입력해 주세요.');
    fireEvent.change(view.getByLabelText('거래 단가 (원)'), { target: { value: '73000' } });
    fireEvent.change(view.getByLabelText('수량 (주)'), { target: { value: '1' } });
    fireEvent.click(view.getByRole('button', { name: '매수 기록하기' }));
    await waitFor(() => expect(save).toHaveBeenCalled());
    expect(save.mock.calls[0][0].unitPrice).toBe(73000);
  });

  it('창을 닫으면 진행 중인 종가 조회를 취소한다', () => {
    vi.mocked(getLatestStockPrice).mockReturnValue(deferredPrice().promise);
    const view = render(
      <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={vi.fn()} />,
    );
    const signal = vi.mocked(getLatestStockPrice).mock.calls[0][1];
    view.unmount();
    expect(signal?.aborted).toBe(true);
  });
});
