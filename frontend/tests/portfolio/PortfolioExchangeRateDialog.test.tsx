/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, expect, it, vi } from 'vitest';
import { PortfolioExchangeRateDialog } from '../../src/features/portfolio/components/PortfolioExchangeRateDialog';
import { getUsdKrwRate } from '../../src/features/stocks/api';

vi.mock('../../src/features/stocks/api', () => ({ getUsdKrwRate: vi.fn() }));
const originalShow = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function () {
    this.open = false;
  };
});
afterAll(() => {
  HTMLDialogElement.prototype.showModal = originalShow;
  HTMLDialogElement.prototype.close = originalClose;
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
const defaults = {
  currency: 'USD' as const,
  value: '1300.5',
  tradeDate: '2026-10-10',
  baseDate: '2026-10-09',
  loading: false,
  error: '',
  onClose: vi.fn(),
  onConfirm: vi.fn(),
};

it('환율을 쉼표로 표시하고 키패드 소수점 입력을 저장할 때만 적용한다', async () => {
  const confirm = vi.fn();
  const view = render(<PortfolioExchangeRateDialog {...defaults} onConfirm={confirm} />);
  const input = view.getByRole('textbox', { name: /적용 환율/ }) as HTMLInputElement;
  expect(input.value).toBe('1,300.5');
  fireEvent.focus(input);
  const keypad = within(view.getByRole('group', { name: '환율 숫자 입력' }));
  fireEvent.click(keypad.getByRole('button', { name: '1' }));
  await waitFor(() => expect(input.value).toBe('1'));
  fireEvent.click(keypad.getByRole('button', { name: '소수점' }));
  await waitFor(() => expect(input.value).toBe('1.'));
  fireEvent.click(keypad.getByRole('button', { name: '5' }));
  await waitFor(() => expect(input.value).toBe('1.5'));
  expect(confirm).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '저장' }));
  expect(confirm).toHaveBeenCalledWith('1.5');
});

it('국내 종목은 항상 0원을 읽기 전용으로 보여주고 환율을 조회하지 않는다', () => {
  const view = render(<PortfolioExchangeRateDialog {...defaults} currency="KRW" />);
  expect(view.getByRole('textbox', { name: /적용 환율/ })).toHaveProperty('value', '0');
  expect(view.getByRole('textbox', { name: /적용 환율/ })).toHaveProperty('readOnly', true);
  expect(view.queryByRole('button', { name: '거래일 환율 다시 조회' })).toBeNull();
  expect(getUsdKrwRate).not.toHaveBeenCalled();
  expect(view.getByRole('button', { name: '1' })).toHaveProperty('disabled', true);
});

it('늦은 기본 환율 응답은 직접 입력한 값을 덮어쓰지 않으며 닫기는 저장하지 않는다', () => {
  const confirm = vi.fn();
  const close = vi.fn();
  const view = render(
    <PortfolioExchangeRateDialog
      {...defaults}
      value=""
      loading
      onClose={close}
      onConfirm={confirm}
    />,
  );
  const input = view.getByRole('textbox', { name: /적용 환율/ });
  fireEvent.change(input, { target: { value: '1,450.123456' } });
  view.rerender(
    <PortfolioExchangeRateDialog {...defaults} value="1320" onClose={close} onConfirm={confirm} />,
  );
  expect(input).toHaveProperty('value', '1,450.123456');
  fireEvent.click(view.getByRole('button', { name: '환율 창 닫기' }));
  expect(close).toHaveBeenCalledOnce();
  expect(confirm).not.toHaveBeenCalled();
});

it('거래일의 참고 환율을 다시 조회하고, 직접 입력하면 대기 중 조회를 취소한다', async () => {
  vi.mocked(getUsdKrwRate).mockResolvedValueOnce({ baseDate: '2026-10-09', rate: 1350 });
  let resolve!: (quote: { baseDate: string; rate: number }) => void;
  vi.mocked(getUsdKrwRate).mockReturnValueOnce(
    new Promise((done) => {
      resolve = done;
    }),
  );
  const confirm = vi.fn();
  const view = render(<PortfolioExchangeRateDialog {...defaults} onConfirm={confirm} />);
  const reload = view.getByRole('button', { name: '거래일 환율 다시 조회' });
  fireEvent.click(reload);
  const input = view.getByRole('textbox', { name: /적용 환율/ });
  await waitFor(() => expect(input).toHaveProperty('value', '1,350'));
  expect(getUsdKrwRate).toHaveBeenLastCalledWith('2026-10-10', expect.any(AbortSignal));
  fireEvent.click(reload);
  const signal = vi.mocked(getUsdKrwRate).mock.calls.at(-1)![1];
  fireEvent.change(input, { target: { value: '1400' } });
  expect(signal?.aborted).toBe(true);
  await act(async () => resolve({ baseDate: '2026-10-09', rate: 1500 }));
  expect(input).toHaveProperty('value', '1,400');
  expect(confirm).not.toHaveBeenCalled();
});

it('미국 종목의 0원은 저장할 수 없고 조회 실패는 직접 입력으로 복구한다', async () => {
  vi.mocked(getUsdKrwRate).mockRejectedValue(new Error('unavailable'));
  const view = render(<PortfolioExchangeRateDialog {...defaults} value="0" />);
  expect(view.getByRole('button', { name: '저장' })).toHaveProperty('disabled', true);
  fireEvent.click(view.getByRole('button', { name: '거래일 환율 다시 조회' }));
  expect((await view.findByRole('status')).textContent).toContain('직접 입력');
  fireEvent.change(view.getByRole('textbox', { name: /적용 환율/ }), { target: { value: '1350' } });
  expect(view.getByRole('button', { name: '저장' })).toHaveProperty('disabled', false);
});
