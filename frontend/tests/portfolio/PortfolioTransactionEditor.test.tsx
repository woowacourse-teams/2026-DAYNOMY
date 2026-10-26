/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getLatestStockPrice, searchStocks, getUsdKrwRate } from '../../src/features/stocks/api';
import { PortfolioTransactionEditor } from '../../src/features/portfolio/components/PortfolioTransactionEditor';
import type { PortfolioHoldingHistory } from '../../src/features/portfolio/types';
import type { StockPrice } from '../../src/features/stocks/types';

vi.mock('../../src/features/stocks/api', () => ({
  getLatestStockPrice: vi.fn(),
  searchStocks: vi.fn(),
  getUsdKrwRate: vi.fn(),
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
  it.each(['BUY', 'SELL'] as const)(
    '%s 신규 거래는 기본 수량 1주로 저장할 수 있다',
    async (type) => {
      const save = vi.fn().mockResolvedValue(undefined);
      const view = render(
        <PortfolioTransactionEditor stock={samsung} type={type} onClose={vi.fn()} onSave={save} />,
      );
      expect(view.getByLabelText('수량 (주)')).toHaveProperty('value', '1');
      await waitFor(() =>
        expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '75,200'),
      );
      fireEvent.click(
        view.getByRole('button', { name: type === 'BUY' ? '매수 기록하기' : '매도 기록하기' }),
      );
      await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
      expect(save.mock.calls[0][0]).toMatchObject({ type, quantity: 1, unitPrice: 75200 });
    },
  );

  it('쉼표가 있는 단가를 붙여 넣어도 소수점 자릿수를 유지하고 숫자로 저장한다', async () => {
    const save = vi.fn().mockResolvedValue(undefined);
    const view = render(
      <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={save} />,
    );
    const input = view.getByLabelText('거래 단가 (원)');
    await waitFor(() => expect(input).toHaveProperty('value', '75,200'));
    fireEvent.change(input, { target: { value: '127,000.5000' } });
    expect(input).toHaveProperty('value', '127,000.5000');
    fireEvent.click(view.getByRole('button', { name: '매수 기록하기' }));
    await waitFor(() => expect(save).toHaveBeenCalledTimes(1));
    expect(save.mock.calls[0][0]).toMatchObject({ quantity: 1, unitPrice: 127000.5 });
  });

  it('단가 중간을 수정하거나 쉼표를 지울 때 커서가 끝으로 이동하지 않는다', async () => {
    const view = render(
      <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={vi.fn()} />,
    );
    const input = view.getByLabelText('거래 단가 (원)') as HTMLInputElement;
    await waitFor(() => expect(input.value).toBe('75,200'));
    input.focus();
    fireEvent.change(input, { target: { value: '1,2934.50', selectionStart: 4 } });
    expect(input.value).toBe('12,934.50');
    await waitFor(() => expect(input.selectionStart).toBe(4));
    fireEvent.change(input, { target: { value: '12934.50', selectionStart: 2 } });
    expect(input.value).toBe('12,934.50');
    await waitFor(() => expect(input.selectionStart).toBe(2));
  });

  it('과거 거래일로 바꿔도 최근 종가 참고 단가를 유지한다', async () => {
    const view = render(
      <PortfolioTransactionEditor stock={samsung} onClose={vi.fn()} onSave={vi.fn()} />,
    );
    const input = view.getByLabelText('거래 단가 (원)');
    await waitFor(() => expect(input).toHaveProperty('value', '75,200'));
    fireEvent.change(view.getByLabelText('거래일'), { target: { value: '2026-01-01' } });
    expect(input).toHaveProperty('value', '75,200');
    expect(view.getByText(/과거 거래일에도 참고 단가로 유지됩니다/)).toBeTruthy();
    expect(getLatestStockPrice).toHaveBeenCalledTimes(1);
  });

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
      expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '75,200'),
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
      await waitFor(() => expect(input).toHaveProperty('value', '75,200'));
      fireEvent.change(input, { target: { value: '76000.50' } });
      expect(input).toHaveProperty('value', '76,000.50');
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

  it.each(['78,000', ''])(
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
      expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '193,400'),
    );
    await act(async () => previous.resolve(price));
    expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '193,400');
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
    expect(view.getByLabelText('거래 단가 (원)')).toHaveProperty('value', '70,000');
    expect(view.getByLabelText('수량 (주)')).toHaveProperty('value', '2');
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

it('미국 검색 후 주식·ETF의 USD 종가와 환율을 채우고 수정한 환율로 저장한다', async () => {
  const tesla = {
    ...samsung,
    assetId: 10,
    assetCode: 'TSLA.US',
    name: 'Tesla',
    market: 'NASDAQ',
    currency: 'USD',
  } as const;
  vi.mocked(searchStocks).mockResolvedValue([tesla]);
  vi.mocked(getLatestStockPrice).mockResolvedValue({
    ...tesla,
    baseDate: '2020-10-16',
    closePrice: 1100.1234,
  });
  vi.mocked(getUsdKrwRate).mockResolvedValue({ baseDate: '2020-10-16', rate: 1300 });
  const save = vi.fn().mockResolvedValue(undefined);
  const view = render(<PortfolioTransactionEditor onClose={vi.fn()} onSave={save} />);
  fireEvent.click(view.getByRole('button', { name: '미국' }));
  fireEvent.change(view.getByLabelText('종목 검색'), { target: { value: 'TSLA' } });
  fireEvent.click(await view.findByRole('button', { name: /Tesla/ }));
  await waitFor(() =>
    expect(view.getByLabelText('거래 단가 (USD)')).toHaveProperty('value', '1,100.1234'),
  );
  await waitFor(() =>
    expect(view.getByRole('textbox', { name: /적용 환율/ })).toHaveProperty('value', '1300'),
  );
  expect(vi.mocked(searchStocks).mock.calls.at(-1)?.[2]).toBe('US');
  fireEvent.change(view.getByLabelText('수량 (주)'), { target: { value: '2' } });
  fireEvent.change(view.getByRole('textbox', { name: /적용 환율/ }), { target: { value: '1350' } });
  fireEvent.click(view.getByRole('button', { name: '매수 기록하기' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0]).toMatchObject({
    assetId: 10,
    unitPrice: 1100.1234,
    exchangeRate: 1350,
    quantity: 2,
  });
});

it('늦은 참고 환율이 직접 입력한 환율을 덮어쓰지 않으며 날짜 변경 시 다시 조회한다', async () => {
  const etf = { ...samsung, assetCode: 'VTI.US', market: 'NYSE_ARCA', currency: 'USD' } as const;
  let resolve!: (quote: { baseDate: string; rate: number }) => void;
  vi.mocked(getUsdKrwRate).mockReturnValue(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const view = render(
    <PortfolioTransactionEditor stock={etf} onClose={vi.fn()} onSave={vi.fn()} />,
  );
  const input = view.getByRole('textbox', { name: /적용 환율/ });
  fireEvent.change(input, { target: { value: '1400' } });
  await act(async () => resolve({ baseDate: '2020-10-16', rate: 1300 }));
  expect(input).toHaveProperty('value', '1400');
  vi.mocked(getUsdKrwRate).mockResolvedValue({ baseDate: '2020-10-15', rate: 1290 });
  fireEvent.change(view.getByLabelText('거래일'), { target: { value: '2020-10-15' } });
  await waitFor(() => expect(input).toHaveProperty('value', '1290'));
  expect(getUsdKrwRate).toHaveBeenLastCalledWith('2020-10-15', expect.any(AbortSignal));
});

it('참고 환율 조회가 실패하면 1로 대체하지 않고 직접 입력한 환율로 저장한다', async () => {
  const tesla = { ...samsung, assetCode: 'TSLA.US', market: 'NASDAQ', currency: 'USD' } as const;
  vi.mocked(getUsdKrwRate).mockRejectedValue(new Error('provider unavailable'));
  const save = vi.fn().mockResolvedValue(undefined);
  const view = render(<PortfolioTransactionEditor stock={tesla} onClose={vi.fn()} onSave={save} />);
  await view.findByText(/환율을 가져오지 못했어요/);
  expect(view.getByRole('textbox', { name: /적용 환율/ })).toHaveProperty('value', '');
  fireEvent.change(view.getByLabelText('수량 (주)'), { target: { value: '1' } });
  fireEvent.click(view.getByRole('button', { name: '매수 기록하기' }));
  expect(await view.findByRole('alert')).toBeTruthy();
  expect(save).not.toHaveBeenCalled();
  fireEvent.change(view.getByRole('textbox', { name: /적용 환율/ }), { target: { value: '1350' } });
  fireEvent.click(view.getByRole('button', { name: '매수 기록하기' }));
  await waitFor(() => expect(save).toHaveBeenCalled());
  expect(save.mock.calls[0][0].exchangeRate).toBe(1350);
});
