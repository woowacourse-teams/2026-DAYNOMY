/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { trackPortfolioEvent } from '../../src/features/portfolio/analytics';
import { PortfolioPage } from '../../src/features/portfolio/PortfolioPage';
import type {
  PortfolioHoldingInput,
  PortfolioHoldingHistory,
  PortfolioTransactionRequest,
} from '../../src/features/portfolio/types';

vi.mock('../../src/features/portfolio/analytics', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../src/features/portfolio/analytics')>()),
  trackPortfolioEvent: vi.fn(),
}));

const stock = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
} as const;
const holding = { ...stock, quantity: 10, averagePurchasePrice: 70000 };
const buy: PortfolioHoldingHistory = {
  historyId: 21,
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  changeType: 'BUY',
  quantity: 0,
  averagePurchasePrice: 0,
  effectiveDate: '2026-10-01',
  transactionQuantity: 10,
  unitPrice: 70000,
  fee: 100,
  tax: 0,
  occurredAt: '2026-10-01T01:00:00Z',
};
const historical = {
  baseDate: '2026-10-02',
  totalPurchaseAmount: 700000,
  totalEvaluationAmount: 750000,
  totalProfitLoss: 50000,
  totalReturnRate: 7.14,
  dailyProfitLoss: null,
  dailyReturnRate: null,
};
let holdings: PortfolioHoldingInput[];
let histories: PortfolioHoldingHistory[];
let realized: number;
let pricing: 'COMPLETE' | 'PARTIAL' | 'MIXED_DATES';
let rejectMutation: boolean;
let failedPerformance: boolean;
let failedHistories: boolean;
let earlierPerformancePoints: (typeof historical)[];
let performancePoint: typeof historical;
const originalShow = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;
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
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}
beforeEach(() => {
  vi.mocked(trackPortfolioEvent).mockClear();
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-12T01:00:00Z'));
  holdings = [{ ...holding }];
  histories = [{ ...buy }];
  realized = 0;
  pricing = 'COMPLETE';
  rejectMutation = false;
  failedPerformance = false;
  failedHistories = false;
  earlierPerformancePoints = [];
  performancePoint = { ...historical };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input),
        method = init?.method ?? 'GET';
      if (url.endsWith('/api/auth/csrf'))
        return json({ token: 'test', headerName: 'X-CSRF-TOKEN' });
      if (url.endsWith('/api/users/me/portfolio'))
        return json({ holdings, totalRealizedProfitLoss: realized });
      if (url.includes('/api/stocks?')) return json({ stocks: [stock] });
      if (url.includes('/api/portfolio/histories?'))
        return failedHistories ? json({ message: '거래 조회 실패' }, 503) : json(histories);
      if (url.includes('/api/users/me/portfolio/performance?'))
        return failedPerformance
          ? json({ message: '추이 조회 실패' }, 503)
          : json({
              status: 'INSUFFICIENT_DATA',
              reason: 'SNAPSHOT_DATA_INSUFFICIENT',
              baseDate: performancePoint.baseDate,
              previousBaseDate: null,
              points: [...earlierPerformancePoints, performancePoint],
              currentPoint: null,
            });
      if (url.endsWith('/api/portfolio/calculate')) {
        const partial = pricing === 'PARTIAL';
        return json({
          baseDate: partial ? null : '2026-10-08',
          pricingStatus: pricing,
          pricedHoldingCount: partial ? 0 : holdings.length,
          unpricedHoldingCount: partial ? holdings.length : 0,
          totalPurchaseAmount: 700000,
          totalEvaluationAmount: partial ? 0 : 750000,
          dailyProfitLoss: null,
          dailyReturnRate: null,
          totalProfitLoss: partial ? null : 50000,
          totalReturnRate: partial ? null : 7.14,
          holdings: holdings.map((item) => ({
            ...item,
            priceAvailable: !partial,
            baseDate: partial ? null : '2026-10-08',
            closePrice: partial ? null : 75000,
            purchaseAmount: item.quantity * item.averagePurchasePrice,
            evaluationAmount: partial ? null : item.quantity * 75000,
            profitLoss: partial ? null : 50000,
            returnRate: partial ? null : 7.14,
            weight: partial ? null : 100,
          })),
          marketAllocations: partial
            ? []
            : [{ market: 'KOSPI', evaluationAmount: 750000, weight: 100 }],
        });
      }
      if (url.includes('/transactions')) {
        if (rejectMutation)
          return json(
            {
              code: 'INSUFFICIENT_PORTFOLIO_QUANTITY',
              message: '매도 수량이 해당 시점의 보유 수량보다 많습니다.',
            },
            400,
          );
        if (method === 'POST') {
          const body = JSON.parse(String(init?.body)) as PortfolioTransactionRequest;
          holdings = body.type === 'SELL' ? [] : [{ ...holding, quantity: 15 }];
          realized = body.type === 'SELL' ? 45000 : 0;
          histories = [
            ...histories,
            {
              ...buy,
              historyId: 22,
              changeType: body.type,
              effectiveDate: body.tradeDate,
              transactionQuantity: body.quantity,
              unitPrice: body.unitPrice,
              fee: body.fee,
              tax: body.tax,
            },
          ];
        } else if (method === 'PATCH') {
          const body = JSON.parse(String(init?.body));
          histories = histories.map((item) => ({
            ...item,
            effectiveDate: body.tradeDate,
            transactionQuantity: body.quantity,
            unitPrice: body.unitPrice,
            fee: body.fee,
            tax: body.tax,
          }));
        } else if (method === 'DELETE') {
          histories = [];
          holdings = [{ ...holding }];
          realized = 0;
        }
        return json({
          transactionId: method === 'POST' ? 22 : 21,
          holding: holdings[0] ?? null,
          totalRealizedProfitLoss: realized,
        });
      }
      return json({}, 404);
    }),
  );
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});
async function ready() {
  const view = render(<PortfolioPage />);
  await view.findByRole('button', { name: '보유 자산 1' });
  await waitFor(() => expect(view.queryByText('포트폴리오를 계산하고 있습니다.')).toBeNull());
  return view;
}
function fill(dialog: HTMLElement, quantity = '5') {
  fireEvent.change(within(dialog).getByLabelText('수량 (주)'), { target: { value: quantity } });
  fireEvent.change(within(dialog).getByLabelText('거래 단가 (원)'), {
    target: { value: '75000.50' },
  });
}
function performanceRequests() {
  return vi.mocked(fetch).mock.calls.filter(([url]) => String(url).includes('/performance?'))
    .length;
}

describe('거래 원장 화면', () => {
  it('전체 종목을 거래일 최신순으로 섞어 표시하고 같은 날짜는 실제 기록 시각과 ID로 정렬한다', async () => {
    histories = [
      { ...buy, historyId: 99, name: '삼성전자', effectiveDate: '2026-10-01' },
      {
        ...buy,
        historyId: 23,
        assetId: 2,
        name: 'SK하이닉스',
        effectiveDate: '2026-10-03',
        occurredAt: '2026-10-03T06:00:00Z',
      },
      {
        ...buy,
        historyId: 22,
        name: '삼성전자',
        effectiveDate: '2026-10-03',
        occurredAt: '2026-10-03T16:00:00+09:00',
      },
      {
        ...buy,
        historyId: 24,
        assetId: 3,
        name: 'KODEX 200',
        effectiveDate: '2026-10-03',
        occurredAt: '2026-10-03T07:00:00Z',
      },
    ];
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    const history = view.getByRole('region', { name: '거래 내역' });
    await waitFor(() => expect(history.querySelectorAll('.portfolio-history-row')).toHaveLength(4));
    expect(
      [...history.querySelectorAll('.portfolio-history-name strong')].map(
        (item) => item.textContent,
      ),
    ).toEqual(['KODEX 200', '삼성전자', 'SK하이닉스', '삼성전자']);
    expect(within(history).getByText('전체 종목 · 거래일 최신순')).toBeTruthy();
  });

  it('자산 추이 제목으로 상세를 열고 음수 손익과 수익률도 0 기준선 아래에 표시한다', async () => {
    earlierPerformancePoints = [historical, { ...historical, baseDate: '2026-10-03' }];
    performancePoint = {
      ...historical,
      baseDate: '2026-10-12',
      totalEvaluationAmount: 650000,
      totalProfitLoss: -50000,
      totalReturnRate: -7.14,
    };
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '자산 추이' }));
    const dialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    const assetChart = within(dialog).getByRole('img', { name: /평가금액 650,000원/ });
    const coordinates = assetChart
      .querySelector('.portfolio-return-line.principal')!
      .getAttribute('points')!
      .split(' ')
      .map((pair) => pair.split(',').map(Number));
    // 1일과 9일의 공백을 같은 너비로 그리지 않는다.
    expect(
      (coordinates[1][0] - coordinates[0][0]) / (coordinates[2][0] - coordinates[0][0]),
    ).toBeCloseTo(0.1);
    fireEvent.click(within(dialog).getByRole('button', { name: '평가손익' }));
    const profitChart = within(dialog).getByRole('img', { name: /평가손익 −50,000원/ });
    const baselineY = Number(
      profitChart.querySelector('.principal')!.getAttribute('points')!.split(' ')[0].split(',')[1],
    );
    const lastProfitY = Number(
      profitChart.querySelector('.portfolio-return-active-point .evaluation')!.getAttribute('cy'),
    );
    expect(lastProfitY).toBeGreaterThan(baselineY);
    fireEvent.click(within(dialog).getByRole('button', { name: '평가수익률' }));
    expect(within(dialog).getByRole('img', { name: /평가수익률 −7.14%/ })).toBeTruthy();
    expect(within(dialog).getByText('0 기준선')).toBeTruthy();
  });

  it('자산 추이에 매수·매도 내역과 거래점을 표시하지 않고 날짜별 평가값만 조회한다', async () => {
    earlierPerformancePoints = [{ ...historical, baseDate: '2026-10-01' }];
    histories = [buy, { ...buy, historyId: 22, effectiveDate: '2026-10-03' }];
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '자산 추이' }));
    const dialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    const detail = within(dialog).getByRole('region', { name: '선택 날짜의 자산' });
    expect(dialog.querySelector('.portfolio-trend-trade-marker')).toBeNull();
    expect(dialog.querySelector('.portfolio-trend-day-trades')).toBeNull();
    expect(within(dialog).queryByText('매수·매도')).toBeNull();
    for (const metric of ['평가금액', '평가손익', '평가수익률']) {
      fireEvent.click(within(dialog).getByRole('button', { name: metric }));
      fireEvent.change(within(detail).getByLabelText('자산 추이 조회 날짜'), {
        target: { value: '2026-10-01' },
      });
      expect(within(detail).getByText('+50,000원')).toBeTruthy();
      expect(within(detail).queryByText('삼성전자')).toBeNull();
      expect(dialog.querySelector('.portfolio-trend-trade-marker')).toBeNull();
    }
    fireEvent.change(within(detail).getByLabelText('자산 추이 조회 날짜'), {
      target: { value: '2026-10-03' },
    });
    expect(within(detail).getByText('평가 데이터 없음')).toBeTruthy();
    expect(detail.querySelector('.portfolio-trend-date-values')).toBeNull();
    expect(within(detail).queryByText('삼성전자')).toBeNull();
    fireEvent.change(within(detail).getByLabelText('자산 추이 조회 날짜'), {
      target: { value: '2026-10-02' },
    });
    expect(within(detail).getByText('+50,000원')).toBeTruthy();
  });

  it('숨긴 금액은 새 지표·날짜별 자산과 차트 설명에서도 노출하지 않는다', async () => {
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '금액 숨기기' }));
    fireEvent.click(view.getByRole('button', { name: '자산 추이' }));
    const dialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    fireEvent.click(within(dialog).getByRole('button', { name: '평가수익률' }));
    expect(dialog.textContent).not.toMatch(/750,000|700,000|50,000|7\.14/);
    expect(within(dialog).getByRole('img').getAttribute('aria-label')).toContain('금액 숨김');
    fireEvent.change(within(dialog).getByLabelText('자산 추이 조회 날짜'), {
      target: { value: '2026-10-01' },
    });
    expect(dialog.textContent).not.toContain('70,000');
    expect(dialog.querySelector('.portfolio-trend-day-trades')).toBeNull();
  });

  it('자산 추이는 거래 내역을 요청하지 않고 거래 조회 오류도 표시하지 않는다', async () => {
    failedHistories = true;
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '자산 추이' }));
    const dialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(within(dialog).getByRole('img', { name: /평가금액 750,000원/ })).toBeTruthy();
    expect(within(dialog).queryByText('거래 조회 실패')).toBeNull();
    expect(within(dialog).queryByRole('button', { name: '거래 표시 다시 시도' })).toBeNull();
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) => String(input).includes('/api/portfolio/histories?')),
    ).toBe(false);
    fireEvent.click(within(dialog).getByRole('button', { name: '자산 추이 새로고침' }));
    await waitFor(() =>
      expect(within(dialog).getByRole('img', { name: /평가금액 750,000원/ })).toBeTruthy(),
    );
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) => String(input).includes('/api/portfolio/histories?')),
    ).toBe(false);
  });

  it('내역 탭·필터는 변경만 수집하고 조회 날짜 대신 유효한 기간 길이를 보낸다', async () => {
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    const history = view.getByRole('region', { name: '거래 내역' });
    const filter = within(history).getByRole('combobox');
    fireEvent.change(filter, { target: { value: 'BUY' } });
    fireEvent.change(filter, { target: { value: 'BUY' } });
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_tab_changed'),
    ).toEqual([['portfolio_tab_changed', { tab: 'history' }]]);
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_history_filter_changed'),
    ).toEqual([['portfolio_history_filter_changed', { filter: 'BUY' }]]);
    fireEvent.change(within(history).getByLabelText('시작일'), { target: { value: '2026-10-01' } });
    expect(trackPortfolioEvent).toHaveBeenCalledWith('portfolio_history_range_changed', {
      range_days: 12,
    });
    fireEvent.change(within(history).getByLabelText('시작일'), { target: { value: '2026-10-13' } });
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_history_range_changed'),
    ).toHaveLength(1);
    expect(JSON.stringify(vi.mocked(trackPortfolioEvent).mock.calls)).not.toContain('2026-10-01');
  });

  it('검색한 기존 종목에 추가 매수하고 비용을 API에 전달한다', async () => {
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '거래 기록' }));
    const dialog = view.getByRole('dialog', { name: '거래 기록' });
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '삼성' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /삼성전자/ }));
    fill(dialog);
    fireEvent.change(within(dialog).getByLabelText('수수료 (원)'), { target: { value: '100.25' } });
    expect(within(dialog).getByText('375,102.75원')).toBeTruthy();
    fireEvent.click(within(dialog).getByRole('button', { name: '매수 기록하기' }));
    await waitFor(() => expect(view.queryByRole('dialog')).toBeNull());
    expect(await view.findByText('15주')).toBeTruthy();
    const [, init] = vi
      .mocked(fetch)
      .mock.calls.find(
        ([url, options]) => String(url).endsWith('/transactions') && options?.method === 'POST',
      )!;
    expect(JSON.parse(String(init?.body))).toEqual({
      assetId: 1,
      type: 'BUY',
      tradeDate: '2026-10-12',
      quantity: 5,
      unitPrice: 75000.5,
      fee: 100.25,
      tax: 0,
    });
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test');
    expect(trackPortfolioEvent).toHaveBeenCalledWith('portfolio_save_succeeded', {
      operation: 'transaction_create',
      storage: 'server',
      transaction_type: 'BUY',
    });
    expect(JSON.stringify(vi.mocked(trackPortfolioEvent).mock.calls)).not.toMatch(
      /삼성|005930|quantity|unitPrice|tradeDate|75000/,
    );
  });
  it('보유수량이 그대로인 과거 거래일 수정 후 재계산된 추이와 내역을 표시한다', async () => {
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    await view.findByRole('button', { name: /2026\.10\.01.*매수.*삼성전자/ });
    fireEvent.change(view.getByLabelText('시작일'), { target: { value: '2026-09-01' } });
    fireEvent.click(await view.findByRole('button', { name: /2026\.10\.01.*매수.*삼성전자/ }));
    const dialog = view.getByRole('dialog', { name: '거래 수정' });
    expect(within(dialog).getByText('매수 거래')).toBeTruthy();
    expect(within(dialog).queryByRole('button', { name: '매도' })).toBeNull();
    expect(within(dialog).getByLabelText('수량 (주)')).toHaveProperty('value', '10');
    expect(within(dialog).getByLabelText('거래 단가 (원)')).toHaveProperty('value', '70000');
    expect(within(dialog).queryByRole('searchbox')).toBeNull();
    const before = performanceRequests();
    performancePoint = {
      ...historical,
      totalPurchaseAmount: 650000,
      totalEvaluationAmount: 780000,
      totalProfitLoss: 130000,
      totalReturnRate: 20,
    };
    fireEvent.change(within(dialog).getByLabelText('거래일'), { target: { value: '2026-09-30' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '수정하기' }));
    await waitFor(() => expect(view.queryByRole('dialog')).toBeNull());
    await waitFor(() => expect(performanceRequests()).toBeGreaterThan(before));
    expect(
      await view.findByRole('img', { name: /평가금액 780,000원, 매입원금 650,000원/ }),
    ).toBeTruthy();
    expect(view.queryByRole('img', { name: /평가금액 750,000원/ })).toBeNull();
    expect(await view.findByText('2026.09.30')).toBeTruthy();
    expect(view.getByLabelText('시작일')).toHaveProperty('value', '2026-09-01');
    const [, init] = vi
      .mocked(fetch)
      .mock.calls.find(
        ([url, options]) => String(url).endsWith('/transactions/21') && options?.method === 'PATCH',
      )!;
    expect(JSON.parse(String(init?.body))).not.toHaveProperty('type');
    expect(JSON.parse(String(init?.body))).not.toHaveProperty('assetId');
  });
  it('거래를 바로 열고 삭제를 취소하면 입력 중이던 값으로 돌아온다', async () => {
    histories = [
      buy,
      {
        ...buy,
        historyId: 22,
        changeType: 'OPENING_BALANCE',
        quantity: 10,
        averagePurchasePrice: 70000,
        transactionQuantity: null,
        unitPrice: null,
      },
    ];
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    const row = await view.findByRole('button', { name: /2026\.10\.01.*매수.*삼성전자/ });
    expect(row.getAttribute('aria-haspopup')).toBe('dialog');
    expect(within(row).getByText('수정')).toBeTruthy();
    const history = view.getByRole('region', { name: '거래 내역' });
    expect(history.querySelector('details')).toBeNull();
    expect(history.querySelectorAll('.portfolio-history-row')).toHaveLength(1);
    expect(within(history).queryByText('초기 보유 등록')).toBeNull();
    expect(within(history).queryByText('기록 시각')).toBeNull();
    fireEvent.click(row);
    const editor = view.getByRole('dialog', { name: '거래 수정' });
    fireEvent.change(within(editor).getByLabelText('수량 (주)'), { target: { value: '12' } });
    fireEvent.click(within(editor).getByRole('button', { name: '거래 삭제' }));
    const confirm = view.getByRole('dialog', { name: '거래 삭제' });
    expect(within(confirm).queryByRole('button', { name: '수정하기' })).toBeNull();
    fireEvent.click(within(confirm).getByRole('button', { name: '취소' }));
    expect(view.getByRole('dialog', { name: '거래 수정' })).toBe(editor);
    expect(within(editor).getByLabelText('수량 (주)')).toHaveProperty('value', '12');
    fireEvent.click(within(editor).getByRole('button', { name: '닫기' }));
    expect(view.queryByRole('dialog')).toBeNull();
    expect(histories[0].transactionQuantity).toBe(10);
  });

  it.each(['OPENING_BALANCE', 'ADDED', 'ADJUSTED', 'UPDATED', 'REMOVED'] as const)(
    '%s 기록은 대시보드·종목 상세·거래 목록에서 제외하고 보유자산은 유지한다',
    async (changeType) => {
      histories = [{ ...buy, changeType, transactionQuantity: null, unitPrice: null }];
      const view = await ready();
      fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
      const history = view.getByRole('region', { name: '거래 내역' });
      expect(await within(history).findByText('이 기간에 표시할 내역이 없어요')).toBeTruthy();
      expect(history.querySelectorAll('.portfolio-history-row')).toHaveLength(0);
      expect(
        [...within(history).getByLabelText('내역 유형').querySelectorAll('option')].map(
          (option) => option.value,
        ),
      ).toEqual(['ALL', 'BUY', 'SELL']);
      fireEvent.click(view.getByRole('button', { name: '보유 자산 1' }));
      fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
      const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
      expect(await within(detail).findByText('이 기간에 표시할 내역이 없어요')).toBeTruthy();
      expect(detail.querySelectorAll('.portfolio-history-row')).toHaveLength(0);
      expect(within(detail).getByText('10주')).toBeTruthy();
      expect(histories).toHaveLength(1);
      expect(vi.mocked(fetch).mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
    },
  );

  it('삭제 실패 시 내역과 확인창을 유지하고 재시도 성공 시 자산을 복원한다', async () => {
    histories = [{ ...buy, changeType: 'SELL' }];
    rejectMutation = true;
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    fireEvent.click(await view.findByRole('button', { name: /2026\.10\.01.*매도.*삼성전자/ }));
    fireEvent.click(view.getByRole('button', { name: '거래 삭제' }));
    const dialog = view.getByRole('dialog', { name: '거래 삭제' });
    fireEvent.click(within(dialog).getByRole('button', { name: '거래 삭제' }));
    expect(await within(dialog).findByRole('alert')).toHaveProperty(
      'textContent',
      '매도 수량이 해당 시점의 보유 수량보다 많습니다.',
    );
    expect(histories).toHaveLength(1);
    rejectMutation = false;
    performancePoint = {
      ...historical,
      totalPurchaseAmount: 600000,
      totalEvaluationAmount: 720000,
      totalProfitLoss: 120000,
      totalReturnRate: 20,
    };
    fireEvent.click(within(dialog).getByRole('button', { name: '거래 삭제' }));
    await waitFor(() => expect(view.queryByRole('dialog')).toBeNull());
    expect(await view.findByText('이 기간에 표시할 내역이 없어요')).toBeTruthy();
    expect(
      await view.findByRole('img', { name: /평가금액 720,000원, 매입원금 600,000원/ }),
    ).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '보유 자산 1' }));
    expect(await view.findByText('10주')).toBeTruthy();
  });
  it('전량 매도 후 보유 종목이 없어도 실현손익·과거 추이·거래 내역을 유지한다', async () => {
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    fireEvent.click(
      within(view.getByRole('dialog', { name: '삼성전자 보유 상세' })).getByRole('button', {
        name: '매도',
      }),
    );
    const dialog = view.getByRole('dialog', { name: '거래 기록' });
    fill(dialog, '10');
    fireEvent.click(within(dialog).getByRole('button', { name: '매도 기록하기' }));
    await waitFor(() => expect(view.queryByRole('dialog', { name: '거래 기록' })).toBeNull());
    expect(await view.findByRole('button', { name: '보유 자산 0' })).toBeTruthy();
    expect(view.getByText('+45,000원')).toBeTruthy();
    expect(await view.findByRole('img', { name: /평가금액 750,000원/ })).toBeTruthy();
    expect(
      within(view.getByRole('dialog', { name: '삼성전자 보유 상세' })).getByText('0주'),
    ).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '보유 상세 닫기' }));
    expect(view.getByRole('button', { name: '거래 기록하기' })).toBeTruthy();
    expect(view.queryByRole('button', { name: '첫 거래 기록하기' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    expect(await view.findByText('2026.10.12')).toBeTruthy();
  });
  it('시세가 없어도 종목을 관리하고 null 오늘 값 없이 과거 추이를 표시한다', async () => {
    pricing = 'PARTIAL';
    const view = await ready();
    expect(view.getByText('시세 확인된 자산 평가금액')).toBeTruthy();
    const holdingRow = view.getByRole('button', { name: '삼성전자 보유 상세' }).closest('tr')!;
    expect(
      within(holdingRow)
        .getAllByRole('cell')
        .slice(3)
        .map((cell) => cell.textContent),
    ).toEqual(['-', '-', '-', '-']);
    expect(view.queryByText('시세 없음')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    expect(within(detail).getByRole('button', { name: '매수' })).toBeTruthy();
    expect(
      within(detail).getByText('시세를 확인할 수 없어 평가금액과 손익을 표시하지 않았어요.'),
    ).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '보유 상세 닫기' }));
    expect(view.queryByText('포트폴리오 계산 응답 형식이 올바르지 않습니다.')).toBeNull();
    expect(await view.findByRole('img', { name: /평가금액 750,000원/ })).toBeTruthy();
    expect(view.queryByRole('button', { name: '분석하기' })).toBeNull();
    const summary = within(view.getByLabelText('자산 요약'));
    expect(
      summary.getByText('시세 확인된 자산 평가금액').parentElement?.parentElement?.textContent,
    ).toContain('—');
  });
  it('상세 패널은 선택한 종목의 내역만 표시하고 거래 수정 후에도 유지된다', async () => {
    histories.push({ ...buy, historyId: 99, assetId: 2, name: '다른 종목', assetCode: '000660' });
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    await within(detail).findByText('2026.10.01');
    expect(within(detail).queryByText('다른 종목')).toBeNull();
    fireEvent.click(within(detail).getByText('2026.10.01'));
    const editor = view.getByRole('dialog', { name: '거래 수정' });
    expect(editor).toBe(detail);
    expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
    expect(view.queryByRole('dialog', { name: '삼성전자 보유 상세' })).toBeNull();
    fireEvent.change(within(editor).getByLabelText('거래일'), { target: { value: '2026-09-30' } });
    fireEvent.click(within(editor).getByRole('button', { name: '수정하기' }));
    await waitFor(() => expect(view.queryByRole('dialog', { name: '거래 수정' })).toBeNull());
    expect(await within(detail).findByText('2026.09.30')).toBeTruthy();
    expect(document.body.style.overflow).toBe('hidden');
    fireEvent.click(within(detail).getByRole('button', { name: '보유 상세 닫기' }));
    expect(document.body.style.overflow).not.toBe('hidden');
  });
  it('매수·매도 입력을 같은 패널에서 전환하고 취소하면 상세로 돌아온다', async () => {
    const view = await ready();
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const panel = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    for (const name of ['매수', '매도']) {
      fireEvent.click(within(panel).getByRole('button', { name }));
      expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
      expect(view.getByRole('dialog', { name: '거래 기록' })).toBe(panel);
      expect(within(panel).getByRole('button', { name: `${name} 기록하기` })).toBeTruthy();
      fireEvent.click(within(panel).getByRole('button', { name: '취소' }));
      expect(view.getByRole('dialog', { name: '삼성전자 보유 상세' })).toBe(panel);
    }
  });
  it('수량 변경 없이 백엔드에서 갱신된 스냅샷을 새로고침해 확대 그래프에도 표시한다', async () => {
    const view = await ready();
    await view.findByRole('img', { name: /평가금액 750,000원/ });
    performancePoint = {
      ...historical,
      totalEvaluationAmount: 840000,
      totalProfitLoss: 140000,
      totalReturnRate: 20,
    };
    fireEvent.click(view.getByRole('button', { name: '자산 추이 새로고침' }));
    expect(await view.findByRole('img', { name: /평가금액 840,000원/ })).toBeTruthy();
    const [, init] = vi
      .mocked(fetch)
      .mock.calls.filter(([url]) => String(url).includes('/performance?'))
      .at(-1)!;
    expect(init?.cache).toBe('no-store');
    fireEvent.click(view.getByRole('button', { name: '자산 추이 크게 보기' }));
    const expanded = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(within(expanded).getByRole('img', { name: /평가금액 840,000원/ })).toBeTruthy();
    performancePoint = {
      ...historical,
      totalEvaluationAmount: 910000,
      totalProfitLoss: 210000,
      totalReturnRate: 30,
    };
    fireEvent.click(within(expanded).getByRole('button', { name: '자산 추이 새로고침' }));
    expect(await within(expanded).findByRole('img', { name: /평가금액 910,000원/ })).toBeTruthy();
  });

  it('재조회가 겹쳐도 늦게 도착한 이전 응답으로 최신 그래프를 덮어쓰지 않는다', async () => {
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    const view = await ready();
    await view.findByRole('img', { name: /평가금액 750,000원/ });
    let resolvePrevious!: (response: Response) => void;
    vi.mocked(fetch).mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          resolvePrevious = resolve;
        }),
    );
    const before = performanceRequests();
    fireEvent.click(view.getByRole('button', { name: '자산 추이 새로고침' }));
    await waitFor(() => expect(performanceRequests()).toBeGreaterThan(before));
    performancePoint = {
      ...historical,
      totalEvaluationAmount: 770000,
      totalProfitLoss: 70000,
      totalReturnRate: 10,
    };
    fireEvent(document, new Event('visibilitychange'));
    await view.findByRole('img', { name: /평가금액 770,000원/ });
    await act(async () =>
      resolvePrevious(
        json({
          status: 'INSUFFICIENT_DATA',
          reason: 'SNAPSHOT_DATA_INSUFFICIENT',
          baseDate: historical.baseDate,
          previousBaseDate: null,
          points: [historical],
          currentPoint: null,
        }),
      ),
    );
    expect(view.getByRole('img', { name: /평가금액 770,000원/ })).toBeTruthy();
    expect(view.queryByRole('img', { name: /평가금액 750,000원/ })).toBeNull();
  });

  it('숨긴 탭으로는 요청하지 않고 복귀하면 재계산된 스냅샷을 조회한다', async () => {
    const visibility = vi.spyOn(document, 'visibilityState', 'get');
    visibility.mockReturnValue('visible');
    const view = await ready();
    await view.findByRole('img', { name: /평가금액 750,000원/ });
    const before = performanceRequests();
    visibility.mockReturnValue('hidden');
    fireEvent(document, new Event('visibilitychange'));
    expect(performanceRequests()).toBe(before);
    performancePoint = {
      ...historical,
      totalEvaluationAmount: 770000,
      totalProfitLoss: 70000,
      totalReturnRate: 10,
    };
    visibility.mockReturnValue('visible');
    fireEvent(document, new Event('visibilitychange'));
    expect(await view.findByRole('img', { name: /평가금액 770,000원/ })).toBeTruthy();
    expect(performanceRequests()).toBeGreaterThan(before);
  });

  it('추이 오류를 자산 계산 성공으로 숨기지 않고 해당 영역에서 재시도한다', async () => {
    failedPerformance = true;
    const view = await ready();
    expect(await view.findByText('추이 조회 실패')).toBeTruthy();
    expect(view.getByText('10주')).toBeTruthy();
    failedPerformance = false;
    fireEvent.click(
      within(view.getByRole('region', { name: '자산 추이' })).getByRole('button', {
        name: '다시 시도',
      }),
    );
    expect(await view.findByRole('img', { name: /평가금액 750,000원/ })).toBeTruthy();
  });
});
