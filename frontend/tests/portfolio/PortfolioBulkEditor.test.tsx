/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { PortfolioBulkEditor } from '../../src/features/portfolio/components/PortfolioBulkEditor';
import type { PortfolioHoldingResult } from '../../src/features/portfolio/types';

const first = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
  quantity: 2,
  averagePurchasePrice: 70000,
} as const;
const second = { ...first, assetId: 2, assetCode: '000660', name: 'SK하이닉스' };
const price = (holding: typeof first | typeof second): PortfolioHoldingResult => ({
  ...holding,
  priceAvailable: true,
  baseDate: '2026-10-01',
  closePrice: 75000,
  purchaseAmount: 140000,
  evaluationAmount: 150000,
  profitLoss: 10000,
  returnRate: 7.14,
  weight: 50,
});
const originalShow = HTMLDialogElement.prototype.showModal,
  originalClose = HTMLDialogElement.prototype.close;
beforeAll(() => {
  vi.stubGlobal('PointerEvent', MouseEvent);
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open');
  };
});
afterAll(() => {
  vi.unstubAllGlobals();
  HTMLDialogElement.prototype.showModal = originalShow;
  HTMLDialogElement.prototype.close = originalClose;
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
const props = () => ({
  mode: 'holdings' as const,
  holdings: [first, second],
  prices: [price(first), price(second)],
  isLoggedIn: true,
  amountsHidden: false,
  onClose: vi.fn(),
  onSaveHoldings: vi.fn().mockResolvedValue(undefined),
  onAddHoldings: vi.fn().mockResolvedValue(undefined),
  onSaveTransactions: vi.fn().mockResolvedValue(false),
});
describe('일괄 편집', () => {
  it('미국 종목은 달러 평단가와 현재가로 환차손익 없는 달러 평가를 계산한다', () => {
    const holding = {
      ...first,
      assetCode: 'AAPL.US',
      name: '애플',
      market: 'NASDAQ' as const,
      quantity: 1,
      averagePurchasePrice: 340000,
      nativeAveragePurchasePrice: 240,
    };
    const view = render(
      <PortfolioBulkEditor
        {...props()}
        holdings={[holding]}
        prices={[
          {
            ...price(first),
            ...holding,
            closePrice: 250,
            evaluationAmount: 350000,
            purchaseAmount: 340000,
            profitLoss: 10000,
          },
        ]}
      />,
    );
    expect(view.getAllByText('$ 250')).toHaveLength(2);
    fireEvent.change(view.getByRole('textbox', { name: '애플 수량' }), { target: { value: '2' } });
    expect(view.getByText('$ 500')).toBeTruthy();
    expect(view.getByText('$ 20')).toBeTruthy();
    expect(view.getByText('(+4.17%)')).toBeTruthy();
  });
  it('외화 원가가 없는 기존 종목의 평단가를 추정하지 않고 다른 종목 편집은 허용한다', async () => {
    const legacy = {
      ...first,
      assetId: 9,
      assetCode: 'AAPL.US',
      name: '애플',
      market: 'NASDAQ' as const,
      averagePurchasePrice: 340000,
    };
    const callbacks = props();
    const view = render(
      <PortfolioBulkEditor
        {...callbacks}
        holdings={[first, legacy]}
        prices={[price(first), { ...price(first), ...legacy, closePrice: 250 }]}
      />,
    );
    expect(view.getByRole('textbox', { name: '애플 평균 매입가' })).toHaveProperty('value', '');
    expect(view.queryByText('$ 340,000')).toBeNull();
    fireEvent.change(view.getByRole('textbox', { name: '삼성전자 수량' }), {
      target: { value: '3' },
    });
    fireEvent.click(view.getByRole('button', { name: '변경사항 저장' }));
    await waitFor(() =>
      expect(callbacks.onSaveHoldings).toHaveBeenCalledWith([{ ...first, quantity: 3 }]),
    );
  });
  it('수량·평균 매입가만 입력 가능하고 현재가·손익은 읽기 전용이다', () => {
    const view = render(<PortfolioBulkEditor {...props()} />);
    const table = view.getByRole('table');
    expect(within(table).getAllByRole('textbox')).toHaveLength(4);
    expect(view.queryByRole('textbox', { name: /현재가|평가금액|손익|비중/ })).toBeNull();
    expect(view.getByRole('button', { name: '변경사항 저장' })).toHaveProperty('disabled', true);
    fireEvent.change(view.getByRole('textbox', { name: '삼성전자 수량' }), {
      target: { value: '3' },
    });
    expect(view.getByText('₩ 225,000')).toBeTruthy();
    expect(view.getByText('₩ 15,000')).toBeTruthy();
    expect(view.queryByRole('columnheader', { name: '비중' })).toBeNull();
  });
  it('여러 행을 붙여넣고 되돌린 뒤 변경한 행만 한 번에 저장한다', async () => {
    const callbacks = props(),
      view = render(<PortfolioBulkEditor {...callbacks} />);
    fireEvent.paste(view.getByRole('textbox', { name: '삼성전자 수량' }), {
      clipboardData: { getData: () => '3\t71,000\n4\t72000' },
    });
    expect(view.getByRole('textbox', { name: 'SK하이닉스 수량' })).toHaveProperty('value', '4');
    fireEvent.click(view.getByRole('button', { name: '되돌리기' }));
    expect(view.getByRole('textbox', { name: 'SK하이닉스 수량' })).toHaveProperty('value', '2');
    fireEvent.click(view.getByRole('button', { name: '다시 실행' }));
    fireEvent.click(view.getByRole('button', { name: '변경사항 저장' }));
    await waitFor(() => expect(callbacks.onClose).toHaveBeenCalledOnce());
    expect(callbacks.onSaveHoldings).toHaveBeenCalledWith([
      { ...first, quantity: 3, averagePurchasePrice: 71000 },
      { ...second, quantity: 4, averagePurchasePrice: 72000 },
    ]);
  });
  it('역방향 드래그로 TSV 복사하고 다른 행에 붙여넣은 뒤 한 번에 되돌린다', () => {
    const third = { ...first, assetId: 3, name: '세 번째 종목', quantity: 8 };
    const fourth = { ...first, assetId: 4, name: '네 번째 종목', quantity: 9 };
    const view = render(
      <PortfolioBulkEditor {...props()} holdings={[first, second, third, fourth]} />,
    );
    const region = view.getByRole('region', { name: /일괄 편집 표/ });
    const firstQuantity = view.getByRole('textbox', { name: '삼성전자 수량' });
    const secondAverage = view.getByRole('textbox', { name: 'SK하이닉스 평균 매입가' });
    fireEvent.pointerDown(secondAverage, { button: 0 });
    fireEvent.pointerMove(firstQuantity, { buttons: 1 });
    fireEvent.pointerUp(document);
    expect(region.querySelectorAll('[data-bulk-selected]')).toHaveLength(4);
    const setData = vi.fn();
    fireEvent.copy(region, { clipboardData: { setData } });
    expect(setData).toHaveBeenCalledWith('text/plain', '2\t70000\n2\t70000');
    const destination = view.getByRole('textbox', { name: '세 번째 종목 수량' });
    fireEvent.focus(destination);
    fireEvent.paste(destination, { clipboardData: { getData: () => setData.mock.calls[0][1] } });
    expect(view.getByRole('textbox', { name: '네 번째 종목 수량' })).toHaveProperty('value', '2');
    fireEvent.click(view.getByRole('button', { name: '되돌리기' }));
    expect(destination).toHaveProperty('value', '8');
    expect(view.getByRole('textbox', { name: '네 번째 종목 수량' })).toHaveProperty('value', '9');
    fireEvent.click(view.getByRole('button', { name: '다시 실행' }));
    expect(destination).toHaveProperty('value', '2');
  });
  it('키보드 범위에 한 값을 채우며 읽기 전용 칸이나 표 밖 붙여넣기는 전체 거부한다', () => {
    const view = render(<PortfolioBulkEditor {...props()} />);
    const region = view.getByRole('region', { name: /일괄 편집 표/ });
    const quantity = view.getByRole('textbox', { name: '삼성전자 수량' });
    fireEvent.focus(quantity);
    fireEvent.keyDown(quantity, { key: 'ArrowDown', shiftKey: true });
    fireEvent.paste(region, { clipboardData: { getData: () => '7' } });
    expect(quantity).toHaveProperty('value', '7');
    expect(view.getByRole('textbox', { name: 'SK하이닉스 수량' })).toHaveProperty('value', '7');
    fireEvent.focus(quantity);
    fireEvent.paste(quantity, { clipboardData: { getData: () => '3\t71000\t999' } });
    expect(view.getByRole('alert').textContent).toContain('수정 가능한');
    expect(quantity).toHaveProperty('value', '7');
    expect(view.getByRole('textbox', { name: '삼성전자 평균 매입가' })).toHaveProperty(
      'value',
      '70000',
    );
    fireEvent.paste(quantity, { clipboardData: { getData: () => '1\n2\n3' } });
    expect(view.getByRole('alert').textContent).toContain('행 수 안에서');
    expect(quantity).toHaveProperty('value', '7');
  });
  it('계산값도 범위 복사는 가능하고 입력칸의 텍스트 선택 복사는 유지한다', () => {
    const view = render(<PortfolioBulkEditor {...props()} />);
    const region = view.getByRole('region', { name: /일괄 편집 표/ });
    const dataRows = region.querySelectorAll('tbody tr');
    fireEvent.pointerDown(dataRows[0].children[6], { button: 0 });
    fireEvent.pointerMove(dataRows[1].children[8], { buttons: 1 });
    fireEvent.pointerUp(document);
    const setData = vi.fn();
    fireEvent.copy(region, { clipboardData: { setData } });
    expect(setData).toHaveBeenCalledWith(
      'text/plain',
      '₩ 75,000\t₩ 150,000\t₩ 10,000 (+7.14%)\n₩ 75,000\t₩ 150,000\t₩ 10,000 (+7.14%)',
    );
    const input = view.getByRole('textbox', { name: '삼성전자 평균 매입가' }) as HTMLInputElement;
    fireEvent.focus(input);
    input.setSelectionRange(0, 2);
    setData.mockClear();
    fireEvent.copy(input, { clipboardData: { setData } });
    expect(setData).not.toHaveBeenCalled();
  });
  it('일괄 추가의 새 행에 범위를 붙여넣고 기존 보유 행은 덮어쓰지 않는다', () => {
    const view = render(<PortfolioBulkEditor {...props()} mode="add" />);
    fireEvent.click(view.getByRole('button', { name: '새 자산 추가' }));
    const quantity = view.getByRole('textbox', { name: '1행 수량' });
    const last = view.getByRole('textbox', { name: '2행 평균 매입가' });
    fireEvent.pointerDown(quantity, { button: 0 });
    fireEvent.pointerMove(last, { buttons: 1 });
    fireEvent.pointerUp(document);
    const region = view.getByRole('region', { name: /일괄 편집 표/ });
    fireEvent.paste(region, { clipboardData: { getData: () => '3\t70000\n4\t200' } });
    expect(quantity).toHaveProperty('value', '3');
    expect(last).toHaveProperty('value', '200');
    fireEvent.click(view.getByRole('button', { name: '되돌리기' }));
    expect(quantity).toHaveProperty('value', '');
    expect(last).toHaveProperty('value', '');
    const existingQuantity = region.querySelector('tbody tr')!.children[4];
    fireEvent.pointerDown(existingQuantity, { button: 0 });
    fireEvent.pointerUp(document);
    fireEvent.paste(region, { clipboardData: { getData: () => '10' } });
    expect(view.getByRole('alert').textContent).toContain('수정 가능한');
    expect(existingQuantity.textContent).toBe('2');
  });
  it('행 삭제 후 낡은 범위는 제거하고 검색창의 기본 복붙을 가로채지 않는다', () => {
    const view = render(<PortfolioBulkEditor {...props()} mode="add" />);
    const quantity = view.getByRole('textbox', { name: '1행 수량' });
    fireEvent.focus(quantity);
    fireEvent.click(view.getByRole('button', { name: '1행 삭제' }));
    const region = view.getByRole('region', { name: /일괄 편집 표/ });
    expect(region.querySelectorAll('[data-bulk-selected]')).toHaveLength(0);
    fireEvent.click(view.getByRole('button', { name: '새 자산 추가' }));
    fireEvent.click(view.getByRole('button', { name: '1행 종목 검색' }));
    const search = view.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' });
    expect(fireEvent.paste(search, { clipboardData: { getData: () => '삼성' } })).toBe(true);
    const setData = vi.fn();
    fireEvent.copy(search, { clipboardData: { setData } });
    expect(setData).not.toHaveBeenCalled();
  });
  it('범위를 채우고 키보드 선택이 행 번호나 표 바깥으로 나가지 않는다', () => {
    const view = render(<PortfolioBulkEditor {...props()} />);
    const region = view.getByRole('region', { name: /일괄 편집 표/ });
    const quantity = view.getByRole('textbox', { name: '삼성전자 수량' });
    fireEvent.change(quantity, { target: { value: '5' } });
    fireEvent.focus(quantity);
    fireEvent.keyDown(quantity, { key: 'ArrowDown', shiftKey: true });
    fireEvent.keyDown(region, { key: 'd', ctrlKey: true });
    expect(view.getByRole('textbox', { name: 'SK하이닉스 수량' })).toHaveProperty('value', '5');
    fireEvent.click(view.getByRole('button', { name: '되돌리기' }));
    expect(view.getByRole('textbox', { name: 'SK하이닉스 수량' })).toHaveProperty('value', '2');
    const stock = region.querySelector('tbody th')!;
    fireEvent.pointerDown(stock, { button: 0 });
    fireEvent.pointerUp(document);
    fireEvent.keyDown(region, { key: 'ArrowLeft', shiftKey: true });
    fireEvent.keyDown(region, { key: 'ArrowUp', shiftKey: true });
    expect(region.querySelectorAll('[data-bulk-selected]')).toHaveLength(1);
    const setData = vi.fn();
    fireEvent.copy(region, { clipboardData: { setData } });
    expect(setData).toHaveBeenCalledWith('text/plain', '005930 삼성전자');
    fireEvent.keyDown(region, { key: 'Escape' });
    expect(region.querySelectorAll('[data-bulk-selected]')).toHaveLength(0);
  });
  it('CSV 범위 붙여넣기는 미국 환율만 허용하고 국내 행이 섞이면 일부도 변경하지 않는다', async () => {
    const apple = { ...first, assetId: 3, assetCode: 'AAPL.US', name: '애플', market: 'NASDAQ' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      async () => new Response(JSON.stringify({ stocks: [first, apple] }), { status: 200 }),
    );
    const view = render(<PortfolioBulkEditor {...props()} mode="csv" />);
    fireEvent.click(view.getByRole('button', { name: '거래내역' }));
    const file = new File([''], 'trades.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', {
      value: async () =>
        'assetCode,market,type,tradeDate,quantity,unitPrice,fee,tax,currency,exchangeRate\nAAPL.US,NASDAQ,BUY,2026-01-02,1,250,0,0,USD,1400\n005930,KOSPI,BUY,2026-01-02,3,70000,0,0,KRW,',
    });
    Object.defineProperty(file, 'arrayBuffer', {
      value: async () => new TextEncoder().encode(await file.text()).buffer,
    });
    fireEvent.change(view.getByLabelText('CSV 파일 선택'), { target: { files: [file] } });
    const rate = await view.findByRole('textbox', { name: '1행 적용 환율' });
    fireEvent.paste(rate, { clipboardData: { getData: () => '1500\n1600' } });
    expect(view.getByRole('alert').textContent).toContain('미국 거래의 환율만');
    expect(rate).toHaveProperty('value', '1400');
    fireEvent.paste(rate, { clipboardData: { getData: () => '1500' } });
    expect(rate).toHaveProperty('value', '1500');
  });
  it('금액 숨기기 상태에서는 화면에 가려진 계산값만 복사한다', () => {
    const view = render(<PortfolioBulkEditor {...props()} amountsHidden />);
    const region = view.getByRole('region', { name: /일괄 편집 표/ });
    const dataRows = region.querySelectorAll('tbody tr');
    fireEvent.pointerDown(dataRows[0].children[6], { button: 0 });
    fireEvent.pointerMove(dataRows[1].children[8], { buttons: 1 });
    fireEvent.pointerUp(document);
    const setData = vi.fn();
    fireEvent.copy(region, { clipboardData: { setData } });
    expect(setData).toHaveBeenCalledWith(
      'text/plain',
      '••••••\t••••••\t••••••\n••••••\t••••••\t••••••',
    );
  });
  it('유효하지 않은 셀은 저장을 막고 서버 실패 때 입력을 유지한다', async () => {
    const callbacks = props();
    callbacks.onSaveHoldings.mockRejectedValueOnce(new Error('저장 실패'));
    const view = render(<PortfolioBulkEditor {...callbacks} />);
    const quantity = view.getByRole('textbox', { name: '삼성전자 수량' });
    fireEvent.change(quantity, { target: { value: '1.5' } });
    expect(quantity).toHaveProperty('ariaInvalid', 'true');
    expect(view.getByRole('button', { name: '변경사항 저장' })).toHaveProperty('disabled', true);
    fireEvent.change(quantity, { target: { value: '3' } });
    fireEvent.click(view.getByRole('button', { name: '변경사항 저장' }));
    await waitFor(() => expect(view.getByRole('alert').textContent).toBe('저장 실패'));
    expect(quantity).toHaveProperty('value', '3');
    expect(callbacks.onClose).not.toHaveBeenCalled();
    fireEvent.click(view.getByRole('button', { name: '취소' }));
    expect(view.getByText('저장하지 않은 변경사항이 있어요. 편집을 종료할까요?')).toBeTruthy();
  });
  it('CSV는 전체 미리보기를 확인한 뒤 단일 요청으로 등록한다', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ stocks: [first] }), { status: 200 }),
    );
    const callbacks = props(),
      view = render(<PortfolioBulkEditor {...callbacks} mode="csv" />);
    fireEvent.click(view.getByRole('button', { name: '거래내역' }));
    const file = new File([''], 'trades.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', {
      value: async () =>
        'assetCode,market,type,tradeDate,quantity,unitPrice,fee,tax,currency,exchangeRate\n005930,KOSPI,BUY,2026-01-02,3,70000,0,0,KRW,',
    });
    Object.defineProperty(file, 'arrayBuffer', {
      value: async () => new TextEncoder().encode(await file.text()).buffer,
    });
    fireEvent.change(view.getByLabelText('CSV 파일 선택'), { target: { files: [file] } });
    const save = await view.findByRole('button', { name: '1건 가져오기' });
    expect(view.getByText('삼성전자')).toBeTruthy();
    fireEvent.click(save);
    await waitFor(() => expect(callbacks.onSaveTransactions).toHaveBeenCalledOnce());
    expect(callbacks.onSaveTransactions.mock.calls[0][1]).toBe(true);
  });
  it('보유 종목과 다른 행의 종목은 선택을 막고 보유 상태가 바뀌어도 중복 등록하지 않는다', async () => {
    const third = { ...first, assetId: 3, assetCode: '035420', name: 'NAVER' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      async () => new Response(JSON.stringify({ stocks: [first, third] }), { status: 200 }),
    );
    const callbacks = props();
    const view = render(<PortfolioBulkEditor {...callbacks} mode="add" />);
    fireEvent.click(view.getByRole('button', { name: '1행 종목 검색' }));
    const search = view.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' });
    expect(document.activeElement).toBe(search);
    fireEvent.change(search, { target: { value: '검색' } });
    expect(await view.findByRole('option', { name: /삼성전자.*보유 중/ })).toHaveProperty(
      'disabled',
      true,
    );
    fireEvent.click(view.getByRole('option', { name: /NAVER/ }));
    fireEvent.change(view.getByRole('textbox', { name: '1행 수량' }), {
      target: { value: '1' },
    });
    fireEvent.change(view.getByRole('textbox', { name: '1행 평균 매입가' }), {
      target: { value: '100' },
    });
    expect(view.getByRole('button', { name: '1건 등록' })).toHaveProperty('disabled', false);
    fireEvent.click(view.getByRole('button', { name: '새 자산 추가' }));
    fireEvent.click(view.getByRole('button', { name: '2행 종목 검색' }));
    fireEvent.change(view.getByRole('combobox', { name: '2행 종목명 또는 코드 검색' }), {
      target: { value: '검색' },
    });
    expect(await view.findByRole('option', { name: /NAVER.*추가됨/ })).toHaveProperty(
      'disabled',
      true,
    );
    fireEvent.keyDown(view.getByRole('combobox', { name: '2행 종목명 또는 코드 검색' }), {
      key: 'Escape',
    });
    fireEvent.click(view.getByRole('button', { name: '2행 삭제' }));
    view.rerender(
      <PortfolioBulkEditor {...callbacks} mode="add" holdings={[first, second, third]} />,
    );
    const save = view.getByRole('button', { name: '1건 등록' });
    expect(save).toHaveProperty('disabled', true);
    fireEvent.click(save);
    expect(callbacks.onAddHoldings).not.toHaveBeenCalled();
    expect(callbacks.onSaveTransactions).not.toHaveBeenCalled();
  });
  it('국내·미국 구분 없이 함께 검색하고 같은 종목은 한 번만 표시한다', async () => {
    const apple = { ...first, assetId: 3, assetCode: 'AAPL.US', name: '애플', market: 'NASDAQ' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      async (url) =>
        new Response(
          JSON.stringify({ stocks: String(url).includes('country=US') ? [first, apple] : [first] }),
          { status: 200 },
        ),
    );
    const view = render(<PortfolioBulkEditor {...props()} mode="add" />);
    fireEvent.click(view.getByRole('button', { name: '1행 종목 검색' }));
    expect(view.queryByRole('group', { name: '검색 시장' })).toBeNull();
    fireEvent.change(view.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' }), {
      target: { value: '종목' },
    });
    expect(await view.findByRole('option', { name: /애플.*AAPL.US.*NASDAQ/ })).toHaveProperty(
      'disabled',
      false,
    );
    expect(view.getAllByRole('option', { name: /삼성전자.*005930/ })).toHaveLength(1);
    expect(view.getByRole('option', { name: /삼성전자.*보유 중/ })).toHaveProperty(
      'disabled',
      true,
    );
  });
  it('일괄 추가는 환율 입력 없이 USD 평단가를 원화 참고 원가와 별도로 저장한다', async () => {
    const apple = { ...first, assetId: 3, assetCode: 'AAPL.US', name: '애플', market: 'NASDAQ' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      async (url) =>
        new Response(
          JSON.stringify(
            String(url).includes('/exchange-rates/')
              ? { baseDate: '2026-10-28', rate: 1400 }
              : { stocks: [apple] },
          ),
          { status: 200 },
        ),
    );
    const callbacks = props();
    const view = render(<PortfolioBulkEditor {...callbacks} mode="add" />);
    fireEvent.click(view.getByRole('button', { name: '1행 종목 검색' }));
    fireEvent.change(view.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' }), {
      target: { value: '애플' },
    });
    fireEvent.click(await view.findByRole('option', { name: /애플/ }));
    for (const name of ['매매', '거래일', '수수료', '세금'])
      expect(view.queryByRole('columnheader', { name })).toBeNull();
    expect(view.queryByRole('textbox', { name: '1행 적용 환율' })).toBeNull();
    const columns = within(view.getByRole('table'))
      .getAllByRole('columnheader')
      .map((item) => item.textContent);
    expect(columns.slice(3, 6)).toEqual(['통화', '수량', '평균 매입가']);
    fireEvent.change(view.getByRole('textbox', { name: '1행 수량' }), { target: { value: '1' } });
    fireEvent.change(view.getByRole('textbox', { name: '1행 평균 매입가' }), {
      target: { value: '250' },
    });
    await waitFor(() =>
      expect(view.getByRole('button', { name: '1건 등록' })).toHaveProperty('disabled', false),
    );
    fireEvent.click(view.getByRole('button', { name: '1건 등록' }));
    await waitFor(() => expect(callbacks.onAddHoldings).toHaveBeenCalledOnce());
    expect(callbacks.onAddHoldings.mock.calls[0][0][0]).toMatchObject({
      assetId: 3,
      quantity: 1,
      averagePurchasePrice: 350000,
      nativeAveragePurchasePrice: 250,
    });
    expect(callbacks.onSaveTransactions).not.toHaveBeenCalled();
  });
  it('늦게 도착한 기준 환율은 사용자가 입력한 달러 평단가를 바꾸지 않는다', async () => {
    const apple = { ...first, assetId: 3, assetCode: 'AAPL.US', name: '애플', market: 'NASDAQ' };
    let resolveRate!: (value: Response) => void;
    const pendingRate = new Promise<Response>((resolve) => {
      resolveRate = resolve;
    });
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) =>
      String(url).includes('/exchange-rates/')
        ? pendingRate
        : new Response(JSON.stringify({ stocks: [apple] }), { status: 200 }),
    );
    const view = render(<PortfolioBulkEditor {...props()} mode="add" />);
    fireEvent.click(view.getByRole('button', { name: '1행 종목 검색' }));
    fireEvent.change(view.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' }), {
      target: { value: '애플' },
    });
    fireEvent.click(await view.findByRole('option', { name: /애플/ }));
    const average = view.getByRole('textbox', { name: '1행 평균 매입가' });
    fireEvent.change(average, { target: { value: '123.4567' } });
    await act(async () =>
      resolveRate(
        new Response(JSON.stringify({ baseDate: '2026-10-28', rate: 1400 }), { status: 200 }),
      ),
    );
    expect(average).toHaveProperty('value', '123.4567');
    expect(view.queryByRole('textbox', { name: '1행 적용 환율' })).toBeNull();
  });
  it('CSV 미리보기에서 미국 거래의 환율을 수정해 가져올 수 있다', async () => {
    const apple = { ...first, assetId: 3, assetCode: 'AAPL.US', name: '애플', market: 'NASDAQ' };
    vi.spyOn(globalThis, 'fetch').mockImplementation(
      async () => new Response(JSON.stringify({ stocks: [apple] }), { status: 200 }),
    );
    const callbacks = props();
    const view = render(<PortfolioBulkEditor {...callbacks} mode="csv" />);
    fireEvent.click(view.getByRole('button', { name: '거래내역' }));
    const file = new File([''], 'us.csv', { type: 'text/csv' });
    Object.defineProperty(file, 'text', {
      value: async () =>
        'assetCode,market,type,tradeDate,quantity,unitPrice,fee,tax,currency,exchangeRate\nAAPL.US,NASDAQ,BUY,2026-01-02,1,250,0,0,USD,1400',
    });
    Object.defineProperty(file, 'arrayBuffer', {
      value: async () => new TextEncoder().encode(await file.text()).buffer,
    });
    fireEvent.change(view.getByLabelText('CSV 파일 선택'), { target: { files: [file] } });
    const rate = await view.findByRole('textbox', { name: '1행 적용 환율' });
    fireEvent.change(rate, { target: { value: '1450' } });
    fireEvent.click(view.getByRole('button', { name: '1건 가져오기' }));
    await waitFor(() => expect(callbacks.onSaveTransactions).toHaveBeenCalledOnce());
    expect(callbacks.onSaveTransactions.mock.calls[0][0][0].transaction.exchangeRate).toBe(1450);
  });
  it('일괄 추가는 종목 검색·행 추가·행 삭제를 제공한다', () => {
    const view = render(<PortfolioBulkEditor {...props()} mode="add" />);
    expect(view.getByRole('button', { name: '1행 종목 검색' })).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '새 자산 추가' }));
    expect(view.getByRole('button', { name: '2행 종목 검색' })).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '1행 삭제' }));
    expect(view.queryByRole('button', { name: '2행 종목 검색' })).toBeNull();
  });
});
