/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { PortfolioBulkEditor } from '../../src/features/portfolio/components/PortfolioBulkEditor';
import type { PortfolioHoldingInput } from '../../src/features/portfolio/types';
const samsung = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  market: 'KOSPI',
  category: 'STOCK',
} as const;
const apple = {
  ...samsung,
  assetId: 2,
  assetCode: 'AAPL.US',
  name: '애플',
  market: 'NASDAQ',
} as const;
const callbacks = (holdings: PortfolioHoldingInput[] = []) => ({
  mode: 'csv' as const,
  holdings,
  prices: [],
  isLoggedIn: true,
  amountsHidden: false,
  onClose: vi.fn(),
  onSaveHoldings: vi.fn().mockResolvedValue(undefined),
  onAddHoldings: vi.fn().mockResolvedValue(undefined),
  onSaveTransactions: vi.fn().mockResolvedValue(false),
});
const originalShow = HTMLDialogElement.prototype.showModal,
  originalClose = HTMLDialogElement.prototype.close;
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open');
  };
});
afterAll(() => {
  HTMLDialogElement.prototype.showModal = originalShow;
  HTMLDialogElement.prototype.close = originalClose;
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
function mockApi() {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const path = String(input);
    const body = path.includes('/exchange-rates/')
      ? { baseDate: '2026-10-29', rate: 1400 }
      : path.includes('/price')
        ? { ...apple, closePrice: 250, baseDate: '2026-10-29' }
        : { stocks: [samsung, apple] };
    return new Response(JSON.stringify(body), { status: 200 });
  });
}
function upload(view: ReturnType<typeof render>, text: string) {
  const file = new File([''], 'positions.csv', { type: 'text/csv' });
  Object.defineProperty(file, 'text', { value: async () => text });
  Object.defineProperty(file, 'arrayBuffer', {
    value: async () => new TextEncoder().encode(await file.text()).buffer,
  });
  fireEvent.change(view.getByLabelText('CSV 파일 선택'), { target: { files: [file] } });
}
async function preview(view: ReturnType<typeof render>) {
  await view.findByRole('textbox', { name: '1행 수량' });
  await waitFor(() =>
    expect(view.getByRole('button', { name: 'CSV 파일 선택' })).toHaveProperty('disabled', false),
  );
}
describe('보유현황 CSV', () => {
  it('기본 보유현황에서 한영 별칭·쉼표·통화 단위를 연결하고 매수 거래 없이 저장한다', async () => {
    mockApi();
    const props = callbacks();
    const view = render(<PortfolioBulkEditor {...props} />);
    expect(view.getByRole('button', { name: '보유현황' }).getAttribute('aria-pressed')).toBe(
      'true',
    );
    upload(
      view,
      '내 자산 목록\n종목;개수;평단가\n삼성전자;10주;"70,000원"\nAAPL;5;$200.50\n합계;15;',
    );
    await preview(view);
    expect(view.getByRole('textbox', { name: '1행 수량' })).toHaveProperty('value', '10');
    expect(view.getByRole('textbox', { name: '2행 평균 매입가' })).toHaveProperty(
      'value',
      '200.50',
    );
    expect(view.queryByRole('textbox', { name: /적용 환율|세금|수수료|거래일/ })).toBeNull();
    expect(view.getByText('합계·소계 1개 행은 제외했습니다.')).toBeTruthy();
    expect(view.queryByRole('button', { name: '미리보기' })).toBeNull();
    expect(view.container.querySelector('details')).toHaveProperty('open', false);
    expect(props.onAddHoldings).not.toHaveBeenCalled();
    expect(props.onSaveTransactions).not.toHaveBeenCalled();
    const save = view.getByRole('button', { name: '2건 가져오기' });
    expect(save).toHaveProperty('disabled', false);
    fireEvent.click(save);
    await waitFor(() => expect(props.onClose).toHaveBeenCalledOnce());
    expect(props.onAddHoldings).toHaveBeenCalledWith([
      { ...samsung, quantity: 10, averagePurchasePrice: 70000 },
      { ...apple, quantity: 5, averagePurchasePrice: 280700, nativeAveragePurchasePrice: 200.5 },
    ]);
    expect(props.onSaveTransactions).not.toHaveBeenCalled();
  });
  it('모르는 열은 사용자가 연결하고 총 매입금액에서 평단가를 계산한다', async () => {
    mockApi();
    const view = render(<PortfolioBulkEditor {...callbacks()} />);
    upload(view, '내종목,내개수,내원가\n삼성전자,10,700000');
    await view.findByRole('combobox', { name: 'CSV 종목명 열' });
    expect(view.queryByRole('textbox', { name: '1행 수량' })).toBeNull();
    fireEvent.change(view.getByRole('combobox', { name: 'CSV 종목명 열' }), {
      target: { value: '0' },
    });
    fireEvent.change(view.getByRole('combobox', { name: 'CSV 보유 수량 열' }), {
      target: { value: '1' },
    });
    fireEvent.change(view.getByRole('combobox', { name: 'CSV 총 매입금액 열' }), {
      target: { value: '2' },
    });
    await preview(view);
    expect(view.getByRole('textbox', { name: '1행 평균 매입가' })).toHaveProperty('value', '70000');
  });
  it('중복 행과 기존 보유 종목은 전체 저장을 막고 문제 행을 삭제하면 남은 행만 준비한다', async () => {
    mockApi();
    const view = render(
      <PortfolioBulkEditor
        {...callbacks([{ ...samsung, quantity: 1, averagePurchasePrice: 70000 }])}
      />,
    );
    upload(view, '종목,수량,평단가\n삼성전자,1,70000\nAAPL,1,200\nAAPL,2,210');
    await preview(view);
    expect(view.getByRole('button', { name: '3건 가져오기' })).toHaveProperty('disabled', true);
    fireEvent.click(view.getByRole('button', { name: '3행 삭제' }));
    expect(view.getByRole('button', { name: '2건 가져오기' })).toHaveProperty('disabled', true);
    fireEvent.click(view.getByRole('button', { name: '1행 삭제' }));
    expect(view.getByRole('button', { name: '1건 가져오기' })).toHaveProperty('disabled', false);
  });
  it('불확실한 종목은 임의 선택하지 않고 원본 이름으로 재검색해 사용자가 연결한다', async () => {
    mockApi();
    const view = render(<PortfolioBulkEditor {...callbacks()} />);
    upload(view, '종목,수량,평단가\n나만 아는 애플,1,200');
    await preview(view);
    expect(view.getByRole('button', { name: '1건 가져오기' })).toHaveProperty('disabled', true);
    fireEvent.click(view.getByRole('button', { name: '1행 종목 검색' }));
    const search = view.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' });
    expect(search).toHaveProperty('value', '나만 아는 애플');
    fireEvent.change(search, { target: { value: 'AAPL' } });
    fireEvent.click(await view.findByRole('option', { name: /애플.*AAPL.US/ }));
    await waitFor(() =>
      expect(view.getByRole('button', { name: '1건 가져오기' })).toHaveProperty('disabled', false),
    );
  });
  it('원화 가격을 가진 미국 종목은 자동 환산하지 않고 달러 평단가 정정을 요구한다', async () => {
    mockApi();
    const view = render(<PortfolioBulkEditor {...callbacks()} />);
    upload(view, '종목코드,수량,평단가,통화\nAAPL,1,280000,KRW');
    await preview(view);
    fireEvent.click(view.getByRole('button', { name: '1행 종목 검색' }));
    fireEvent.click(await view.findByRole('option', { name: /애플.*AAPL.US/ }));
    expect(view.getByRole('button', { name: '1건 가져오기' })).toHaveProperty('disabled', true);
    expect(view.getAllByText(/파일 가격의 통화는 KRW/).length).toBeGreaterThan(0);
    fireEvent.change(view.getByRole('textbox', { name: '1행 평균 매입가' }), {
      target: { value: '200' },
    });
    await waitFor(() =>
      expect(view.getByRole('button', { name: '1건 가져오기' })).toHaveProperty('disabled', false),
    );
  });
  it('유형 변경 시 확인 후 파일·미리보기·되돌리기 상태를 초기화한다', async () => {
    mockApi();
    const view = render(<PortfolioBulkEditor {...callbacks()} />);
    upload(view, '종목,수량,평단가\n삼성전자,1,70000');
    await preview(view);
    fireEvent.click(view.getByRole('button', { name: '거래내역' }));
    expect(view.getByRole('alert').textContent).toContain('초기화');
    fireEvent.click(view.getByRole('button', { name: '계속 편집' }));
    expect(view.getByRole('textbox', { name: '1행 수량' })).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '거래내역' }));
    fireEvent.click(view.getByRole('button', { name: '유형 변경' }));
    expect(view.queryByRole('textbox', { name: '1행 수량' })).toBeNull();
    expect(view.getByRole('button', { name: '거래내역' }).getAttribute('aria-pressed')).toBe(
      'true',
    );
  });
  it('미국 보유종목의 원화 참고 환율 조회에 실패하면 잘못된 환율로 저장하지 않는다', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input).includes('/stocks?')
        ? new Response(JSON.stringify({ stocks: [apple] }), { status: 200 })
        : new Response('{}', { status: 503 }),
    );
    const props = callbacks();
    const view = render(<PortfolioBulkEditor {...props} />);
    upload(view, '종목,수량,평단가\nAAPL,1,200');
    await preview(view);
    const save = view.getByRole('button', { name: '1건 가져오기' });
    expect(save).toHaveProperty('disabled', true);
    fireEvent.click(save);
    expect(props.onAddHoldings).not.toHaveBeenCalled();
  });
});
