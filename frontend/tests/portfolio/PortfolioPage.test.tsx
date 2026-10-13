/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import { trackPortfolioEvent } from '../../src/features/portfolio/analytics';
import { PortfolioPage } from '../../src/features/portfolio/PortfolioPage';
import { toPortfolioAnalysisAssets } from '../../src/features/portfolio/portfolioAnalysisAssets';
import {
  GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY,
  loadGuestPortfolioHoldings,
  loadGuestPortfolioLedger,
} from '../../src/features/portfolio/portfolioHoldingsStorage';
import type {
  PortfolioHoldingInput,
  PortfolioPerformancePoint,
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
const secondStock = {
  assetId: 2,
  assetCode: '000150',
  name: '두산',
  category: 'STOCK',
  market: 'KOSPI',
} as const;
const kosdaqStock = {
  assetId: 3,
  assetCode: '247540',
  name: '에코프로비엠',
  category: 'STOCK',
  market: 'KOSDAQ',
} as const;
const etf = {
  assetId: 4,
  assetCode: '069500',
  name: 'KODEX 200',
  category: 'ETF',
  market: 'KOSPI',
} as const;
const calculation = {
  pricingStatus: 'COMPLETE',
  pricedHoldingCount: 1,
  unpricedHoldingCount: 0,
  baseDate: '2026-10-06',
  totalPurchaseAmount: 700000,
  totalEvaluationAmount: 750000,
  dailyProfitLoss: 10000,
  dailyReturnRate: 1.35,
  totalProfitLoss: 50000,
  totalReturnRate: 7.142857,
  holdings: [
    {
      ...stock,
      priceAvailable: true,
      baseDate: '2026-10-06',
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

let savedHoldings: PortfolioHoldingInput[] = [];
let savedPerformancePoints: PortfolioPerformancePoint[] = [];
const originalShowModal = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close() {
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
});

afterAll(() => {
  HTMLDialogElement.prototype.showModal = originalShowModal;
  HTMLDialogElement.prototype.close = originalClose;
});

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function portfolioApiResponse(input: RequestInfo | URL, init?: RequestInit) {
  const url = String(input);
  const method = init?.method ?? 'GET';
  const contentsMatch = url.match(/\/api\/assets\/(\d+)\/contents$/);
  if (contentsMatch) {
    const asset = [stock, secondStock, kosdaqStock, etf].find(
      (candidate) => candidate.assetId === Number(contentsMatch[1]),
    );
    if (!asset) return jsonResponse({}, 404);
    return jsonResponse({
      assetId: asset.assetId,
      assetCode: asset.assetCode,
      assetName: asset.name,
      contents: [],
    });
  }
  if (url.includes('/api/portfolio/histories?')) return jsonResponse([]);
  if (url.endsWith('/api/auth/csrf')) {
    return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
  }
  if (url.endsWith('/api/users/me/portfolio/analysis') && method === 'GET') {
    return jsonResponse({
      status: 'EMPTY',
      lastCheckOutcome: null,
      analysisId: null,
      matchesCurrentPortfolio: false,
      lastSucceededAt: null,
      lastFailedAt: null,
      failureCode: null,
      changeReason: null,
      analysis: null,
    });
  }
  if (url.endsWith('/api/users/me/portfolio') && method === 'GET') {
    return jsonResponse({ holdings: savedHoldings, totalRealizedProfitLoss: 0 });
  }
  if (url.endsWith('/api/portfolio/transactions') && method === 'POST') {
    const body = JSON.parse(String(init?.body)) as PortfolioTransactionRequest;
    const asset = [stock, secondStock, kosdaqStock, etf].find(
      (item) => item.assetId === body.assetId,
    );
    if (!asset) return jsonResponse({}, 404);
    const holding = { ...asset, quantity: body.quantity, averagePurchasePrice: body.unitPrice };
    savedHoldings = [...savedHoldings, holding];
    return jsonResponse({ transactionId: 1, holding, totalRealizedProfitLoss: 0 }, 201);
  }
  if (url.endsWith('/api/users/me/portfolio/holdings') && method === 'POST') {
    const body = JSON.parse(String(init?.body)) as {
      assetId: number;
      quantity: number;
      averagePurchasePrice: number;
    };
    const asset = [stock, secondStock, kosdaqStock, etf].find(
      (candidate) => candidate.assetId === body.assetId,
    );
    if (!asset) return jsonResponse({}, 404);
    const holding = {
      ...asset,
      quantity: body.quantity,
      averagePurchasePrice: body.averagePurchasePrice,
    };
    savedHoldings = [...savedHoldings, holding];
    return jsonResponse(holding, 201);
  }
  const holdingMatch = url.match(/\/api\/users\/me\/portfolio\/holdings\/(\d+)$/);
  if (holdingMatch && method === 'PATCH') {
    const assetId = Number(holdingMatch[1]);
    const body = JSON.parse(String(init?.body)) as {
      quantity: number;
      averagePurchasePrice: number;
    };
    const previous = savedHoldings.find((holding) => holding.assetId === assetId);
    if (!previous) return jsonResponse({}, 404);
    const holding = { ...previous, ...body };
    savedHoldings = savedHoldings.map((item) => (item.assetId === assetId ? holding : item));
    return jsonResponse(holding);
  }
  if (holdingMatch && method === 'DELETE') {
    const assetId = Number(holdingMatch[1]);
    savedHoldings = savedHoldings.filter((holding) => holding.assetId !== assetId);
    return new Response(null, { status: 204 });
  }
  if (url.includes('/api/users/me/portfolio/performance?')) {
    const requestUrl = new URL(url, 'http://localhost');
    const from = requestUrl.searchParams.get('from') ?? '';
    const to = requestUrl.searchParams.get('to') ?? '';
    const totalPurchaseAmount = savedHoldings.reduce(
      (sum, holding) => sum + holding.quantity * holding.averagePurchasePrice,
      0,
    );
    const totalEvaluationAmount = savedHoldings.reduce(
      (sum, holding) => sum + holding.quantity * (holding.assetId === etf.assetId ? 30000 : 75000),
      0,
    );
    const totalProfitLoss = totalEvaluationAmount - totalPurchaseAmount;
    const defaultPoints = [24, 25, 26, 27, 28, 29, 30].map((day, index) => ({
      baseDate: `2026-09-${day}`,
      totalPurchaseAmount,
      totalEvaluationAmount: totalEvaluationAmount - (6 - index) * 10000,
      totalProfitLoss: totalProfitLoss - (6 - index) * 10000,
      totalReturnRate:
        totalPurchaseAmount === 0
          ? 0
          : ((totalProfitLoss - (6 - index) * 10000) / totalPurchaseAmount) * 100,
      dailyProfitLoss: index === 0 ? null : 10000,
      dailyReturnRate: index === 0 ? null : 1.35,
    }));
    const points = (
      savedPerformancePoints.length > 0
        ? savedPerformancePoints
        : savedHoldings.length
          ? defaultPoints
          : []
    ).filter((point) => point.baseDate >= from && point.baseDate <= to);
    return jsonResponse({
      status: points.length >= 2 ? 'READY' : 'INSUFFICIENT_DATA',
      reason: points.length >= 2 ? null : 'SNAPSHOT_DATA_INSUFFICIENT',
      baseDate: points.at(-1)?.baseDate ?? null,
      previousBaseDate: points.at(-2)?.baseDate ?? null,
      points,
      currentPoint: savedHoldings.length
        ? {
            baseDate: '2026-10-06',
            priceBaseDate: '2026-10-06',
            totalPurchaseAmount,
            totalEvaluationAmount,
            totalProfitLoss,
            totalReturnRate:
              totalPurchaseAmount === 0 ? 0 : (totalProfitLoss / totalPurchaseAmount) * 100,
            dailyProfitLoss: 10000,
            dailyReturnRate: 1.35,
          }
        : null,
    });
  }
  return null;
}

function mockPortfolioApi() {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
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
      if (url.endsWith('/api/portfolio/calculate')) {
        const request = JSON.parse(String(init?.body)) as {
          holdings: Array<{ assetId: number; quantity: number; averagePurchasePrice: number }>;
        };
        const requestedHolding = request.holdings[0];
        const quantity = requestedHolding.quantity;
        const averagePurchasePrice = requestedHolding.averagePurchasePrice;
        const totalPurchaseAmount = quantity * averagePurchasePrice;
        const totalEvaluationAmount = quantity * calculation.holdings[0].closePrice;
        const totalProfitLoss = totalEvaluationAmount - totalPurchaseAmount;
        const totalReturnRate = (totalProfitLoss / totalPurchaseAmount) * 100;
        return jsonResponse({
          ...calculation,
          totalPurchaseAmount,
          totalEvaluationAmount,
          totalProfitLoss,
          totalReturnRate,
          holdings: calculation.holdings.map((holding) => ({
            ...holding,
            quantity,
            averagePurchasePrice,
            purchaseAmount: totalPurchaseAmount,
            evaluationAmount: totalEvaluationAmount,
            profitLoss: totalProfitLoss,
            returnRate: totalReturnRate,
          })),
          marketAllocations: [
            { market: 'KOSPI', evaluationAmount: totalEvaluationAmount, weight: 100 },
          ],
        });
      }
      const portfolioResponse = portfolioApiResponse(input, init);
      if (portfolioResponse) return portfolioResponse;
      return jsonResponse({}, 404);
    }),
  );
}

beforeEach(() => {
  vi.mocked(trackPortfolioEvent).mockClear();
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-10-06T06:00:00Z'));
  savedHoldings = [];
  savedPerformancePoints = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      return portfolioApiResponse(input, init) ?? jsonResponse({}, 404);
    }),
  );
});

afterEach(() => {
  vi.useRealTimers();
  cleanup();
  vi.restoreAllMocks();
  localStorage.clear();
  sessionStorage.clear();
  window.history.replaceState(null, '', '/');
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('포트폴리오 화면', () => {
  it('StrictMode에서도 진입은 한 번 수집하고 실제 계산 완료 전에는 loaded를 보내지 않는다', async () => {
    let finishHoldings!: () => void;
    let finishCalculation!: () => void;
    const holdingsReady = new Promise<void>((resolve) => {
      finishHoldings = resolve;
    });
    const calculationReady = new Promise<void>((resolve) => {
      finishCalculation = resolve;
    });
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/api/users/me/portfolio')) {
          await holdingsReady;
          return jsonResponse({
            holdings: [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }],
            totalRealizedProfitLoss: 0,
          });
        }
        if (url.endsWith('/api/portfolio/calculate')) {
          await calculationReady;
          return jsonResponse(calculation);
        }
        if (url.includes('/performance?')) return jsonResponse({ points: [], currentPoint: null });
        if (url.endsWith('/api/auth/csrf'))
          return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
        return jsonResponse({});
      }),
    );
    const view = render(
      <StrictMode>
        <PortfolioPage />
      </StrictMode>,
    );
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_dashboard_viewed'),
    ).toHaveLength(1);
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_dashboard_loaded'),
    ).toHaveLength(0);
    await act(async () => finishHoldings());
    await waitFor(() =>
      expect(
        vi
          .mocked(fetch)
          .mock.calls.some(([input]) => String(input).endsWith('/api/portfolio/calculate')),
      ).toBe(true),
    );
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_dashboard_loaded'),
    ).toHaveLength(0);
    await act(async () => finishCalculation());
    await view.findAllByText('750,000원');
    expect(trackPortfolioEvent).toHaveBeenCalledWith('portfolio_dashboard_loaded', {
      is_logged_in: true,
      has_holdings: true,
      pricing_status: 'COMPLETE',
    });
    fireEvent.click(view.getByRole('button', { name: '금액 숨기기' }));
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_dashboard_loaded'),
    ).toHaveLength(1);
  });

  it('이탈 후 취소된 조회 실패를 대시보드 실패로 집계하지 않는다', async () => {
    let rejectHoldings!: (error: Error) => void;
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).endsWith('/api/users/me/portfolio')) {
          return new Promise<Response>((_resolve, reject) => {
            rejectHoldings = reject;
          });
        }
        return jsonResponse({ points: [], currentPoint: null });
      }),
    );
    const view = render(<PortfolioPage />);
    view.unmount();
    await act(async () => rejectHoldings(new DOMException('Aborted', 'AbortError')));
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_dashboard_load_failed'),
    ).toHaveLength(0);
  });

  it('비회원 포트폴리오를 세션에서 복원하고 회원 전용 API를 호출하지 않는다', async () => {
    const guestHolding = { ...stock, quantity: 10, averagePurchasePrice: 70000 };
    sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, JSON.stringify([guestHolding]));
    mockPortfolioApi();

    const view = render(<PortfolioPage isLoggedIn={false} />);

    expect((await view.findAllByText('750,000원')).length).toBeGreaterThan(0);
    expect(view.getByRole('button', { name: '삼성전자 보유 상세' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '자산 구성' })).toBeTruthy();
    expect(view.getByRole('button', { name: '1회 분석 체험하기' })).toBeTruthy();
    expect(view.getByText('비회원 자산은 이 탭에만 임시 저장돼요.')).toBeTruthy();
    const trend = view.getByRole('region', { name: '자산 추이' });
    expect(within(trend).getByText('잠금')).toBeTruthy();
    expect(within(trend).getByText('로그인 후 자산 추이를 확인할 수 있어요')).toBeTruthy();
    expect(within(trend).getByRole('link', { name: '로그인하기' }).getAttribute('href')).toBe(
      '/login?returnTo=%2F',
    );
    expect(within(trend).queryByRole('img', { name: /포트폴리오 자산 추이/ })).toBeNull();
    expect(view.queryByRole('combobox', { name: '자산 추이 기간' })).toBeNull();
    expect(view.queryByRole('button', { name: '자산 추이 크게 보기' })).toBeNull();
    expect(view.queryByRole('button', { name: '자산 추이 새로고침' })).toBeNull();
    fireEvent(document, new Event('visibilitychange'));
    fireEvent.click(view.getByRole('button', { name: '거래 내역' }));
    const history = within(view.getByRole('region', { name: '거래 내역' }));
    expect(history.getByText('이 기간에 표시할 내역이 없어요')).toBeTruthy();
    expect(history.getByRole('button', { name: '거래 기록' })).toBeTruthy();
    const summary = within(view.getByLabelText('자산 요약'));
    expect(summary.getByText('누적 실현손익')).toBeTruthy();
    expect(summary.getByText('로그인 후 확인할 수 있어요')).toBeTruthy();
    expect(summary.getByText('현재 보유 수량 기준')).toBeTruthy();
    expect(summary.getByText('매도로 확정된 손익')).toBeTruthy();
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) => String(input).includes('/api/users/me/portfolio')),
    ).toBe(false);
  });

  it('비회원도 상세에서 매수·매도를 기록하고 새로 진입하면 임시 거래 내역을 복원한다', async () => {
    sessionStorage.setItem(
      GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY,
      JSON.stringify([{ ...stock, quantity: 10, averagePurchasePrice: 70000 }]),
    );
    mockPortfolioApi();
    const view = render(<PortfolioPage isLoggedIn={false} />);
    await view.findAllByText('750,000원');
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = within(view.getByRole('dialog', { name: '삼성전자 보유 상세' }));
    fireEvent.click(detail.getByRole('button', { name: '매수' }));
    let editor = within(view.getByRole('dialog', { name: '거래 기록' }));
    fireEvent.change(editor.getByLabelText('수량 (주)'), { target: { value: '2' } });
    fireEvent.change(editor.getByLabelText('거래 단가 (원)'), { target: { value: '76000' } });
    fireEvent.click(editor.getByRole('button', { name: '매수 기록하기' }));
    await waitFor(() => expect(view.queryByRole('dialog', { name: '거래 기록' })).toBeNull());
    expect(detail.getByText('12주')).toBeTruthy();
    expect(detail.getAllByText('152,000원').length).toBeGreaterThan(0);
    fireEvent.click(detail.getByRole('button', { name: '매도' }));
    editor = within(view.getByRole('dialog', { name: '거래 기록' }));
    fireEvent.change(editor.getByLabelText('수량 (주)'), { target: { value: '3' } });
    fireEvent.change(editor.getByLabelText('거래 단가 (원)'), { target: { value: '78000' } });
    fireEvent.click(editor.getByRole('button', { name: '매도 기록하기' }));
    await waitFor(() => expect(view.queryByRole('dialog', { name: '거래 기록' })).toBeNull());
    expect(detail.getByText('9주')).toBeTruthy();
    view.unmount();
    const restored = render(<PortfolioPage isLoggedIn={false} />);
    await restored.findByRole('button', { name: '삼성전자 보유 상세' });
    fireEvent.click(restored.getByRole('button', { name: '거래 내역' }));
    const history = within(restored.getByRole('region', { name: '거래 내역' }));
    expect((await history.findAllByText('152,000원')).length).toBeGreaterThan(0);
    expect(history.getAllByText('234,000원').length).toBeGreaterThan(0);
    fireEvent.change(history.getByLabelText('내역 유형'), { target: { value: 'SELL' } });
    expect(history.queryByText('152,000원')).toBeNull();
    expect(history.getAllByText('234,000원').length).toBeGreaterThan(0);
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) =>
          /users\/me\/portfolio|portfolio\/(histories|transactions)/.test(String(input)),
        ),
    ).toBe(false);
  });

  it('비회원이 추가한 자산을 브라우저 세션에 저장한다', async () => {
    mockPortfolioApi();
    const view = render(<PortfolioPage isLoggedIn={false} />);
    fireEvent.click(await view.findByRole('button', { name: '자산 추가' }));

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '삼성' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /삼성전자/ }));
    fireEvent.change(within(dialog).getByLabelText('수량 (주)'), { target: { value: '10' } });
    fireEvent.change(within(dialog).getByLabelText('거래 단가 (원)'), {
      target: { value: '70000' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '매수 기록하기' }));

    await waitFor(() => {
      expect(loadGuestPortfolioHoldings()).toEqual([
        { ...stock, quantity: 10, averagePurchasePrice: 70000 },
      ]);
      expect(loadGuestPortfolioLedger()?.histories).toEqual([
        expect.objectContaining({ changeType: 'BUY', transactionQuantity: 10, unitPrice: 70000 }),
      ]);
    });
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) => String(input).includes('/api/users/me/portfolio')),
    ).toBe(false);
    expect(trackPortfolioEvent).toHaveBeenCalledWith('portfolio_editor_opened', {
      mode: 'transaction_create',
      entry_point: 'header',
      is_logged_in: false,
    });
    expect(trackPortfolioEvent).toHaveBeenCalledWith('portfolio_stock_selected', {
      category: 'STOCK',
      market: 'KOSPI',
      mode: 'transaction_create',
    });
    expect(trackPortfolioEvent).toHaveBeenCalledWith('portfolio_save_succeeded', {
      operation: 'transaction_create',
      transaction_type: 'BUY',
      storage: 'temporary',
    });
    const sent = JSON.stringify(vi.mocked(trackPortfolioEvent).mock.calls);
    expect(sent).not.toMatch(/삼성|005930|quantity|averagePurchasePrice|70000/);
  });

  it('비회원은 상세 패널에서 거래 API 없이 보유자산을 제거한다', async () => {
    const guestHolding = { ...stock, quantity: 10, averagePurchasePrice: 70000 };
    sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, JSON.stringify([guestHolding]));
    mockPortfolioApi();

    const view = render(<PortfolioPage isLoggedIn={false} />);
    await view.findAllByText('750,000원');
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    expect(within(detail).getByRole('button', { name: '매도' })).toBeTruthy();
    expect(within(detail).getByRole('button', { name: '매수' })).toBeTruthy();
    expect(within(detail).getByRole('heading', { name: '거래 내역' })).toBeTruthy();
    expect(within(detail).getByText('이 기간에 표시할 내역이 없어요')).toBeTruthy();
    fireEvent.click(within(detail).getByText('보유정보 편집'));
    fireEvent.click(within(detail).getByRole('button', { name: '보유자산 제거' }));
    fireEvent.click(view.getByRole('button', { name: '보유자산 제거' }));

    await waitFor(() => {
      expect(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)).toBe('[]');
    });
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) =>
          /\/api\/portfolio\/(holdings|histories|transactions|performance)/.test(String(input)),
        ),
    ).toBe(false);
  });

  it('비회원 보유정보를 정정하고 다시 진입해도 서버 저장 없이 복원한다', async () => {
    const guestHolding = { ...stock, quantity: 10, averagePurchasePrice: 70000 };
    sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, JSON.stringify([guestHolding]));
    mockPortfolioApi();
    const view = render(<PortfolioPage isLoggedIn={false} />);
    await view.findAllByText('750,000원');
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    fireEvent.click(within(detail).getByText('보유정보 편집'));
    fireEvent.click(within(detail).getByRole('button', { name: '보유정보 정정' }));
    const editor = view.getByRole('dialog', { name: '보유정보 정정' });
    expect(within(editor).queryByText(/거래 기록을 이용하세요/)).toBeNull();
    fireEvent.change(within(editor).getByLabelText('보유수량'), { target: { value: '12' } });
    fireEvent.change(within(editor).getByLabelText('평균 매수가'), { target: { value: '65,000' } });
    fireEvent.click(within(editor).getByRole('button', { name: '수정하기' }));
    await waitFor(() => {
      expect(JSON.parse(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)!)).toEqual([
        { ...guestHolding, quantity: 12, averagePurchasePrice: 65000 },
      ]);
    });
    view.unmount();
    const restored = render(<PortfolioPage isLoggedIn={false} />);
    expect(await restored.findByText('12주')).toBeTruthy();
    expect(restored.getByText('65,000원')).toBeTruthy();
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) =>
          /\/api\/portfolio(?:$|\/(?:holdings|histories|transactions|performance))/.test(
            String(input),
          ),
        ),
    ).toBe(false);
  });

  it('브라우저 임시 저장에 실패하면 기존 보유정보와 수정 입력을 유지하고 재시도한다', async () => {
    const guestHolding = { ...stock, quantity: 10, averagePurchasePrice: 70000 };
    const original = JSON.stringify([guestHolding]);
    sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, original);
    mockPortfolioApi();
    const view = render(<PortfolioPage isLoggedIn={false} />);
    await view.findAllByText('750,000원');
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    fireEvent.click(within(detail).getByText('보유정보 편집'));
    fireEvent.click(within(detail).getByRole('button', { name: '보유정보 정정' }));
    const editor = view.getByRole('dialog', { name: '보유정보 정정' });
    fireEvent.change(within(editor).getByLabelText('보유수량'), { target: { value: '12' } });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementationOnce(() => {
      throw new DOMException('Storage full', 'QuotaExceededError');
    });
    fireEvent.click(within(editor).getByRole('button', { name: '수정하기' }));
    expect(await within(editor).findByRole('alert')).toHaveProperty(
      'textContent',
      '현재 탭에 보유정보를 임시 보관하지 못했어요. 브라우저 저장 설정을 확인해 주세요.',
    );
    expect(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)).toBe(original);
    expect((within(editor).getByLabelText('보유수량') as HTMLInputElement).value).toBe('12');
    expect(trackPortfolioEvent).toHaveBeenCalledWith('portfolio_save_failed', {
      operation: 'holding_update',
      storage: 'temporary',
      error_type: 'network_or_unknown',
    });
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_save_succeeded'),
    ).toHaveLength(0);
    fireEvent.click(within(editor).getByRole('button', { name: '수정하기' }));
    await waitFor(() => {
      expect(JSON.parse(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)!)).toEqual([
        { ...guestHolding, quantity: 12 },
      ]);
    });
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_save_submitted'),
    ).toHaveLength(2);
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_save_succeeded'),
    ).toHaveLength(1);
  });

  it.each([
    ['잘못된 JSON', '{broken'],
    [
      '유효하지 않은 수량',
      JSON.stringify([{ ...stock, quantity: 0, averagePurchasePrice: 70000 }]),
    ],
    [
      '유효하지 않은 종목 ID',
      JSON.stringify([{ ...stock, assetId: 0, quantity: 10, averagePurchasePrice: 70000 }]),
    ],
    [
      '중복 종목',
      JSON.stringify(Array(2).fill({ ...stock, quantity: 10, averagePurchasePrice: 70000 })),
    ],
  ])(
    '%s 임시 데이터는 제거하고 비회원이 다시 입력할 수 있게 한다',
    async (_description, stored) => {
      sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, stored);
      mockPortfolioApi();
      const view = render(<PortfolioPage isLoggedIn={false} />);
      expect(await view.findByRole('button', { name: '첫 자산 추가하기' })).toBeTruthy();
      expect(sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY)).toBeNull();
      expect(
        vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes('/api/portfolio')),
      ).toBe(false);
    },
  );

  it('반올림된 비중이 0인 자산을 분석 대상에서 제외한다', () => {
    const zeroWeightHolding = {
      ...calculation.holdings[0],
      assetId: 2,
      assetCode: '000150',
      name: '두산',
      evaluationAmount: 1,
      weight: 0,
    };

    expect(
      toPortfolioAnalysisAssets([{ ...calculation.holdings[0], weight: 99.99 }, zeroWeightHolding]),
    ).toEqual([{ assetName: '삼성전자', weight: 100 }]);
    expect(toPortfolioAnalysisAssets([zeroWeightHolding])).toEqual([]);
  });

  it('저장된 자산이 없어도 빈 대시보드를 표시한다', async () => {
    const view = render(<PortfolioPage />);
    expect(await view.findByRole('button', { name: '자산 분석 접기' })).toBeTruthy();
    expect(view.queryByRole('heading', { name: '첫 자산을 추가해 보세요' })).toBeNull();
    expect(view.getByRole('button', { name: '자산 추가' })).toBeTruthy();
    expect(view.getByRole('button', { name: '보유 자산 0' })).toBeTruthy();
    expect(view.queryByText('표시할 자산이 없어요')).toBeNull();
    const summary = within(view.getByLabelText('자산 요약'));
    expect(summary.getByText('현재 보유 수량 기준')).toBeTruthy();
    expect(summary.getByText('매도로 확정된 손익')).toBeTruthy();
    expect(view.getByRole('button', { name: '보유 자산 0' })).toBeTruthy();
    expect(
      summary.getByText('전체 자산 평가금액').parentElement?.parentElement?.textContent,
    ).toContain('0원');
    expect(view.getByRole('combobox', { name: '자산 추이 기간' })).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '자산 추이 크게 보기' }));
    const dialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(within(dialog).getByText('선택한 기간에 표시할 추이가 없어요')).toBeTruthy();
    expect(within(dialog).getByRole('combobox', { name: '자산 추이 기간' })).toBeTruthy();
    expect(within(dialog).getByLabelText('자산 추이 범례')).toBeTruthy();
  });

  it('종목을 누르면 보유 상세 패널에서 종목 정보로 이동할 수 있다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();

    const view = render(<PortfolioPage />);
    await view.findAllByText('750,000원');
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_holding_detail_opened'),
    ).toHaveLength(1);
    expect(
      within(detail)
        .getByRole('link', { name: /종목 정보 보기/ })
        .getAttribute('href'),
    ).toBe('/stocks/1');
    expect(view.queryByText('관리')).toBeNull();
  });

  it('소식은 본문 옆에 표시하고 접고 펼쳐도 다시 조회하지 않으며 보유 상세와 분리한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();

    const view = render(<PortfolioPage />);
    await view.findAllByText('750,000원');
    const newsPanel = view.getByRole('complementary', { name: '보유 자산 소식' });
    const newsTab = view.getByRole('button', { name: /^소식$/ });
    expect(newsTab.getAttribute('aria-expanded')).toBe('true');
    expect(newsTab.getAttribute('aria-controls')).toBe(newsPanel.id);
    expect(view.queryByRole('dialog', { name: '보유 자산 소식' })).toBeNull();
    await within(newsPanel).findByText('보유 자산에 등록된 소식이 없습니다.');
    expect(
      vi
        .mocked(fetch)
        .mock.calls.filter(([input]) => String(input).endsWith('/api/assets/1/contents')),
    ).toHaveLength(1);
    fireEvent.click(within(newsPanel).getByRole('button', { name: '소식 접기' }));
    expect(view.queryByRole('complementary', { name: '보유 자산 소식' })).toBeNull();
    expect(newsTab.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(newsTab);
    fireEvent.click(newsTab);
    expect(view.getByRole('complementary', { name: '보유 자산 소식' })).toBe(newsPanel);
    expect(newsTab.getAttribute('aria-expanded')).toBe('true');
    expect(
      vi
        .mocked(fetch)
        .mock.calls.filter(([input]) => String(input).endsWith('/api/assets/1/contents')),
    ).toHaveLength(1);
    fireEvent.click(newsTab);
    vi.mocked(fetch).mockClear();
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    const detail = view.getByRole('dialog', { name: '삼성전자 보유 상세' });
    expect(within(detail).queryByRole('complementary', { name: '소식' })).toBeNull();
    expect(within(detail).getByRole('link', { name: /종목 정보 보기/ })).toBeTruthy();
    expect(within(detail).getByRole('heading', { name: '거래 내역' })).toBeTruthy();
    await within(detail).findByText('이 기간에 표시할 내역이 없어요');
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) => String(input).endsWith('/api/assets/1/contents')),
    ).toBe(false);
  });

  it('비회원 소식은 배경을 잠그지 않고 버튼으로 접고 펼친다', async () => {
    sessionStorage.setItem(
      'daynomy:guest-portfolio-holdings:v1',
      JSON.stringify([{ ...stock, quantity: 10, averagePurchasePrice: 70000 }]),
    );
    mockPortfolioApi();
    const view = render(<PortfolioPage isLoggedIn={false} />);
    await view.findAllByText('750,000원');
    const tab = view.getByRole('button', { name: /^소식$/ });
    await within(view.getByRole('complementary', { name: '보유 자산 소식' })).findByText(
      '보유 자산에 등록된 소식이 없습니다.',
    );
    expect(document.body.style.overflow).not.toBe('hidden');
    fireEvent.click(view.getByRole('button', { name: '자산 분석 접기' }));
    expect(view.getByRole('complementary', { name: '보유 자산 소식' })).toBeTruthy();
    tab.focus();
    fireEvent.click(tab);
    expect(view.queryByRole('complementary', { name: '보유 자산 소식' })).toBeNull();
    expect(tab.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(tab);
    fireEvent.click(tab);
    expect(view.getByRole('complementary', { name: '보유 자산 소식' })).toBeTruthy();
    expect(tab.getAttribute('aria-expanded')).toBe('true');
  });

  it('종목을 검색해 추가하고 계산 결과를 표시한다', async () => {
    mockPortfolioApi();
    const view = render(<PortfolioPage />);
    await waitFor(() =>
      expect(view.getByRole('button', { name: '자산 추가' })).toHaveProperty('disabled', false),
    );
    fireEvent.click(view.getByRole('button', { name: '자산 추가' }));
    expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
    expect(view.queryByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' })).toBeNull();

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '삼성' } });
    const result = await within(dialog).findByRole('button', { name: /삼성전자/ });
    fireEvent.click(result);
    const averagePriceInput = within(dialog).getByLabelText('거래 단가 (원)') as HTMLInputElement;
    await waitFor(() => expect(averagePriceInput.value).toBe('75000'));
    fireEvent.change(averagePriceInput, { target: { value: '76000' } });
    expect(averagePriceInput.value).toBe('76000');
    fireEvent.change(within(dialog).getByLabelText('수량 (주)'), { target: { value: '10' } });
    fireEvent.change(averagePriceInput, { target: { value: '70000' } });
    expect(averagePriceInput.value).toBe('70000');
    fireEvent.click(within(dialog).getByRole('button', { name: '매수 기록하기' }));

    expect((await view.findAllByText('750,000원')).length).toBeGreaterThan(0);
    expect(view.getByText('+10,000원')).toBeTruthy();
    expect(view.queryByText('주식 100.00% · ETF 0.00%')).toBeNull();
    expect(view.getByRole('heading', { name: '자산 구성' })).toBeTruthy();
    expect(view.getByRole('img', { name: '주식 100.00%, ETF 0.00%' })).toBeTruthy();
    expect(view.queryByText('시장 구성')).toBeNull();
    expect(view.getByText('KOSPI · 100.00%')).toBeTruthy();
    expect(view.queryByText('KOSDAQ')).toBeNull();
    expect(view.getByRole('heading', { name: '자산 추이' })).toBeTruthy();
    expect(
      await view.findByRole('img', {
        name: /평가금액 750,000원, 매입원금 700,000원/,
      }),
    ).toBeTruthy();
    expect(
      (view.getByRole('combobox', { name: '자산 추이 기간' }) as HTMLSelectElement).value,
    ).toBe('YTD');
    expect(view.queryByText('현재 보유 수량 · 거래일 종가 기준')).toBeNull();
    expect(view.queryByText(/1월 1일부터.*거래일 종가 기준/)).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '자산 추이 크게 보기' }));
    const trendDialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(within(trendDialog).queryByText('현재 보유 수량 · 거래일 종가 기준')).toBeNull();
    expect(within(trendDialog).queryByText(/1월 1일부터.*거래일 종가 기준/)).toBeNull();
    fireEvent.click(within(trendDialog).getByRole('button', { name: '자산 추이 상세 닫기' }));
    expect(view.queryByText('샘플 데이터')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '자산 분석 접기' }));
    expect(view.queryByRole('heading', { name: '자산 구성' })).toBeNull();
    expect(view.queryByRole('heading', { name: '자산 추이' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '자산 분석 펼치기' }));
    expect(view.getByRole('heading', { name: '자산 구성' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '자산 추이' })).toBeTruthy();
    expect(view.queryByRole('heading', { name: '포트폴리오 요약' })).toBeNull();
    expect(view.getByRole('heading', { name: '포트폴리오 AI 분석' })).toBeTruthy();
    expect(view.getByRole('button', { name: '분석하기' })).toBeTruthy();
    expect(view.getAllByText('삼성전자').length).toBeGreaterThan(0);
    expect(savedHoldings).toContainEqual(expect.objectContaining({ assetCode: '005930' }));
  });

  it('새 종가로 현재 포트폴리오 수익률을 다시 계산한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    savedPerformancePoints = [
      {
        baseDate: '2026-08-10',
        dailyProfitLoss: null,
        dailyReturnRate: null,
        totalPurchaseAmount: 700000,
        totalEvaluationAmount: 720000,
        totalProfitLoss: 20000,
        totalReturnRate: 2.86,
      },
      {
        baseDate: '2026-09-29',
        dailyProfitLoss: 10000,
        dailyReturnRate: 1.35,
        totalPurchaseAmount: 700000,
        totalEvaluationAmount: 750000,
        totalProfitLoss: 50000,
        totalReturnRate: 5,
      },
    ];
    mockPortfolioApi();

    const view = render(<PortfolioPage />);

    expect(
      await view.findByRole('img', {
        name: /평가금액 750,000원, 매입원금 700,000원/,
      }),
    ).toBeTruthy();
    expect(view.getByText('08.10')).toBeTruthy();
    fireEvent.change(view.getByRole('combobox', { name: '자산 추이 기간' }), {
      target: { value: '1M' },
    });
    expect(
      ((await view.findByRole('combobox', { name: '자산 추이 기간' })) as HTMLSelectElement).value,
    ).toBe('1M');
    expect(view.queryByText('08.10')).toBeNull();
    expect(view.queryByText(/최근 1개월.*거래일 종가 기준/)).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '자산 추이 크게 보기' }));
    const trendDialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(within(trendDialog).queryByText(/최근 1개월.*거래일 종가 기준/)).toBeNull();
  });

  it('날짜별 매입금액을 기준으로 상승·하락·동일 구간을 나누고 교차 지점에서 색을 연결한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    const points = [
      [700000, 650000],
      [720000, 800000],
      [750000, 700000],
      [800000, 800000],
      [800000, 800000],
    ].map(([totalPurchaseAmount, totalEvaluationAmount], index) => ({
      baseDate: `2026-10-0${index + 1}`,
      totalPurchaseAmount,
      totalEvaluationAmount,
      totalProfitLoss: totalEvaluationAmount - totalPurchaseAmount,
      totalReturnRate: ((totalEvaluationAmount - totalPurchaseAmount) / totalPurchaseAmount) * 100,
      dailyProfitLoss: null,
      dailyReturnRate: null,
    }));
    mockPortfolioApi();
    const mockedFetch = vi.mocked(fetch);
    const originalImplementation = mockedFetch.getMockImplementation();
    mockedFetch.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).includes('/api/users/me/portfolio/performance?')) {
        return jsonResponse({
          status: 'READY',
          reason: null,
          baseDate: points.at(-1)!.baseDate,
          previousBaseDate: points.at(-2)!.baseDate,
          points,
          currentPoint: null,
        });
      }
      return originalImplementation?.(input, init) ?? jsonResponse({}, 404);
    });
    const view = render(<PortfolioPage />);
    const chart = await view.findByRole('img', { name: /평가금액 800,000원, 매입원금 800,000원/ });
    const lines = [
      ...chart.querySelectorAll<SVGPolylineElement>('.portfolio-return-line.evaluation'),
    ];
    expect(lines.map((line) => line.style.stroke)).toEqual([
      'var(--portfolio-trend-loss)',
      'var(--portfolio-trend-gain)',
      'var(--portfolio-trend-gain)',
      'var(--portfolio-trend-loss)',
      'var(--portfolio-trend-loss)',
      'var(--portfolio-trend-neutral)',
    ]);
    const coordinates = (line: Element) =>
      line
        .getAttribute('points')!
        .split(' ')
        .map((pair) => pair.split(',').map(Number));
    expect(coordinates(lines[0])[1]).toEqual(coordinates(lines[1])[0]);
    expect(coordinates(lines[2])[1]).toEqual(coordinates(lines[3])[0]);
    expect(coordinates(lines[0])[1][0]).toBeCloseTo(120.5);
    expect(coordinates(lines[2])[1][0]).toBeCloseTo(320.5);
    const purchaseLine = chart.querySelector('.principal')!;
    const purchaseCoordinates = coordinates(purchaseLine);
    expect(coordinates(lines[0])[1][1]).toBeCloseTo(
      purchaseCoordinates[0][1] +
        (purchaseCoordinates[1][1] - purchaseCoordinates[0][1]) * (5 / 13),
    );
    expect(chart.querySelectorAll('.portfolio-return-area.is-gain')).toHaveLength(2);
    expect(chart.querySelectorAll('.portfolio-return-area.is-loss')).toHaveLength(3);
    expect(chart.querySelector('.portfolio-return-area.is-neutral')).toBeNull();

    const wrapper = view.getByLabelText(
      '자산 추이 그래프. 좌우 방향키로 날짜를 이동할 수 있습니다.',
    );
    for (let index = 0; index < 3; index++) fireEvent.keyDown(wrapper, { key: 'ArrowLeft' });
    expect(
      chart.querySelector<SVGCircleElement>('.portfolio-return-point.evaluation')!.style.fill,
    ).toBe('var(--portfolio-trend-gain)');
    fireEvent.keyDown(wrapper, { key: 'ArrowLeft' });
    expect(
      chart.querySelector<SVGCircleElement>('.portfolio-return-point.evaluation')!.style.fill,
    ).toBe('var(--portfolio-trend-loss)');

    fireEvent.click(view.getByRole('button', { name: '자산 추이 크게 보기' }));
    const expanded = within(view.getByRole('dialog', { name: '자산 추이 상세' })).getByRole('img', {
      name: /포트폴리오 자산 추이/,
    });
    expect(
      [...expanded.querySelectorAll<SVGPolylineElement>('.portfolio-return-line.evaluation')].map(
        (line) => line.style.stroke,
      ),
    ).toEqual(lines.map((line) => line.style.stroke));
    expect(
      expanded.querySelector<SVGCircleElement>('.portfolio-return-point.evaluation')!.style.fill,
    ).toBe('var(--portfolio-trend-neutral)');
  });

  it('기간별 종가 기록이 없어도 현재 계산 결과로 자산 추이를 표시한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();
    const mockedFetch = vi.mocked(fetch);
    const originalImplementation = mockedFetch.getMockImplementation();
    mockedFetch.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).includes('/api/users/me/portfolio/performance?')) {
        return jsonResponse({
          status: 'INSUFFICIENT_DATA',
          reason: 'SNAPSHOT_DATA_INSUFFICIENT',
          baseDate: null,
          previousBaseDate: null,
          points: [],
          currentPoint: { ...calculation, priceBaseDate: calculation.baseDate },
        });
      }
      return originalImplementation?.(input, init) ?? jsonResponse({}, 404);
    });

    const view = render(<PortfolioPage />);

    const singlePointChart = await view.findByRole('img', {
      name: /평가금액 750,000원, 매입원금 700,000원/,
    });
    expect(singlePointChart.getAttribute('viewBox')).toBe('0 0 720 190');
    expect(view.container.querySelectorAll('.portfolio-return-line')).toHaveLength(2);
    expect(
      view.container.querySelector('.portfolio-return-dashboard.is-compact.has-single-point'),
    ).toBeTruthy();
    expect(
      view.container
        .querySelector('.portfolio-return-single-point .portfolio-return-point.evaluation')
        ?.getAttribute('cx'),
    ).toBe('58');
    expect(view.container.querySelector('.portfolio-return-area')).toBeNull();
    expect(view.queryByText('표시할 추이가 없어요')).toBeNull();
  });

  it.each(['points', 'currentPoint'] as const)(
    '잘못된 서버 추이 응답(%s)은 오류로 표시하고 재시도하면 실제 계산 결과를 보여준다',
    async (invalidField) => {
      savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
      mockPortfolioApi();
      const mockedFetch = vi.mocked(fetch);
      const originalImplementation = mockedFetch.getMockImplementation();
      let performanceRequests = 0;
      let invalidResponse = true;
      mockedFetch.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).includes('/api/users/me/portfolio/performance?')) {
          performanceRequests += 1;
          if (invalidResponse) {
            return jsonResponse({
              status: 'READY',
              reason: null,
              baseDate: calculation.baseDate,
              previousBaseDate: null,
              points:
                invalidField === 'points'
                  ? [{ ...calculation, totalEvaluationAmount: '750000' }]
                  : [],
              currentPoint:
                invalidField === 'currentPoint' ? { ...calculation, priceBaseDate: 123 } : null,
            });
          }
        }
        return originalImplementation?.(input, init) ?? jsonResponse({}, 404);
      });

      const view = render(<PortfolioPage />);
      const error = await view.findByRole('alert');
      expect(error.textContent).toContain('자산 추이 응답 형식이 올바르지 않습니다.');
      expect(view.queryByRole('img', { name: /포트폴리오 자산 추이/ })).toBeNull();
      const requestsBeforeRetry = performanceRequests;
      invalidResponse = false;
      fireEvent.click(within(error).getByRole('button', { name: '다시 시도' }));

      expect(
        await view.findByRole('img', {
          name: /평가금액 750,000원, 매입원금 700,000원/,
        }),
      ).toBeTruthy();
      expect(performanceRequests).toBe(requestsBeforeRetry + 1);
    },
  );

  it('5년 기간을 선택하면 서버의 5년 자산 추이를 표시한다', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-06T12:00:00'));
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    savedPerformancePoints = [
      {
        baseDate: '2021-10-07',
        dailyProfitLoss: null,
        dailyReturnRate: null,
        totalPurchaseAmount: 500000,
        totalEvaluationAmount: 520000,
        totalProfitLoss: 20000,
        totalReturnRate: 4,
      },
      {
        baseDate: '2026-09-30',
        dailyProfitLoss: 10000,
        dailyReturnRate: 1.35,
        totalPurchaseAmount: 700000,
        totalEvaluationAmount: 750000,
        totalProfitLoss: 50000,
        totalReturnRate: 7.142857,
      },
    ];
    mockPortfolioApi();

    const view = render(<PortfolioPage />);

    await view.findByRole('img', {
      name: /평가금액 750,000원, 매입원금 700,000원/,
    });
    fireEvent.change(view.getByRole('combobox', { name: '자산 추이 기간' }), {
      target: { value: '5Y' },
    });
    await waitFor(() => expect(view.getByText('2021.10')).toBeTruthy());
    expect(view.getByText('2026.10')).toBeTruthy();
  });

  it('자산 추이 그래프를 상세 화면으로 확장하고 닫는다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    await view.findByRole('img', {
      name: /평가금액 750,000원, 매입원금 700,000원/,
    });
    await waitFor(() =>
      expect(view.container.querySelectorAll('.portfolio-return-line').length).toBeGreaterThan(0),
    );
    fireEvent.change(view.getByRole('combobox', { name: '자산 추이 기간' }), {
      target: { value: '5Y' },
    });
    const expandButton = view.getByRole('button', { name: '자산 추이 크게 보기' });
    expandButton.focus();
    fireEvent.click(expandButton);

    const dialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(dialog).toHaveProperty('open', true);
    expect(
      (
        within(dialog).getByRole('combobox', {
          name: '자산 추이 기간',
        }) as HTMLSelectElement
      ).value,
    ).toBe('5Y');
    expect(within(dialog).queryByText('기간 변동')).toBeNull();
    expect(within(dialog).queryByText('최고 · 최저')).toBeNull();
    expect(within(dialog).queryByText('자산 변경 이력')).toBeNull();
    expect(within(dialog).queryByText('자산 구성')).toBeNull();
    await waitFor(() =>
      expect(within(dialog).getByRole('status').textContent).toContain('매입원금'),
    );
    expect(within(dialog).getByRole('status').textContent).toContain('평가금액');

    fireEvent.keyDown(
      within(dialog).getByLabelText('자산 추이 그래프. 좌우 방향키로 날짜를 이동할 수 있습니다.'),
      { key: 'ArrowLeft' },
    );
    expect(within(dialog).getByRole('status').textContent).toContain('2026년 9월 30일');

    fireEvent.click(within(dialog).getByRole('button', { name: '자산 추이 상세 닫기' }));
    expect(view.queryByRole('dialog', { name: '자산 추이 상세' })).toBeNull();
    expect(document.activeElement).toBe(expandButton);

    fireEvent.click(expandButton);
    const escapeDialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    fireEvent(escapeDialog, new Event('cancel', { cancelable: true }));
    expect(view.queryByRole('dialog', { name: '자산 추이 상세' })).toBeNull();
    expect(document.activeElement).toBe(expandButton);

    fireEvent.click(expandButton);
    const backdropDialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    fireEvent.click(backdropDialog);
    expect(view.queryByRole('dialog', { name: '자산 추이 상세' })).toBeNull();
    expect(document.activeElement).toBe(expandButton);
  });

  it('민감한 금액을 한 번에 숨기고 다시 표시한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    await view.findAllByText('750,000원');
    fireEvent.click(view.getByRole('button', { name: '금액 숨기기' }));

    expect(view.queryByText('750,000원')).toBeNull();
    expect(view.getAllByText('••••••원').length).toBeGreaterThan(2);
    fireEvent.click(view.getByRole('button', { name: '금액 보기' }));
    expect(view.getAllByText('750,000원').length).toBeGreaterThan(0);
  });

  it('보유 자산을 유형으로 필터링하고 선택한 기준으로 정렬한다', async () => {
    savedHoldings = [
      { ...stock, quantity: 10, averagePurchasePrice: 70000 },
      { ...etf, quantity: 30, averagePurchasePrice: 30000 },
    ];
    const mixedCalculation = {
      ...calculation,
      totalPurchaseAmount: 1600000,
      totalEvaluationAmount: 1650000,
      holdings: [
        calculation.holdings[0],
        {
          ...etf,
          priceAvailable: true,
          baseDate: '2026-09-21',
          quantity: 30,
          averagePurchasePrice: 30000,
          closePrice: 30000,
          purchaseAmount: 900000,
          evaluationAmount: 900000,
          profitLoss: 0,
          returnRate: 0,
          weight: 54.55,
        },
      ],
      marketAllocations: [{ market: 'KOSPI', evaluationAmount: 1650000, weight: 100 }],
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
        }
        if (url.endsWith('/api/portfolio/calculate')) return jsonResponse(mixedCalculation);
        const portfolioResponse = portfolioApiResponse(input, init);
        if (portfolioResponse) return portfolioResponse;
        return jsonResponse({}, 404);
      }),
    );
    const view = render(<PortfolioPage />);
    const table = await view.findByRole('region', { name: /보유 자산 표/ });

    fireEvent.change(view.getByRole('combobox', { name: '보유 자산 정렬' }), {
      target: { value: 'EVALUATION' },
    });
    let rows = within(table).getAllByRole('row');
    expect(within(rows[1]).getByText('KODEX 200')).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '주식' }));
    rows = within(table).getAllByRole('row');
    expect(within(rows[1]).getByText('삼성전자')).toBeTruthy();
    expect(within(table).queryByText('KODEX 200')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '주식' }));
    fireEvent.change(view.getByRole('combobox', { name: '보유 자산 정렬' }), {
      target: { value: 'EVALUATION' },
    });
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_filter_changed'),
    ).toEqual([['portfolio_filter_changed', { category: 'STOCK' }]]);
    expect(
      vi
        .mocked(trackPortfolioEvent)
        .mock.calls.filter(([name]) => name === 'portfolio_sort_changed'),
    ).toEqual([['portfolio_sort_changed', { sort: 'EVALUATION' }]]);
  });

  it('종목을 다시 선택하면 이전 종목의 늦은 종가 응답을 무시한다', async () => {
    let resolveFirstPrice!: (response: Response) => void;
    const firstPriceResponse = new Promise<Response>((resolve) => {
      resolveFirstPrice = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.includes('/api/stocks?')) return jsonResponse({ stocks: [stock, secondStock] });
        if (url.endsWith('/api/stocks/1/price')) return firstPriceResponse;
        if (url.endsWith('/api/stocks/2/price'))
          return jsonResponse({
            ...secondStock,
            baseDate: '2026-09-21',
            closePrice: 80000,
          });
        const portfolioResponse = portfolioApiResponse(input, init);
        if (portfolioResponse) return portfolioResponse;
        return jsonResponse({}, 404);
      }),
    );

    const view = render(<PortfolioPage />);
    await waitFor(() =>
      expect(view.getByRole('button', { name: '자산 추가' })).toHaveProperty('disabled', false),
    );
    fireEvent.click(view.getByRole('button', { name: '자산 추가' }));
    expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
    expect(view.queryByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' })).toBeNull();

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '주식' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /삼성전자/ }));
    fireEvent.click(within(dialog).getByRole('button', { name: '종목 변경' }));

    expect(within(dialog).queryByLabelText('거래 단가 (원)')).toBeNull();
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '두산' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /두산/ }));
    const unitPriceInput = within(dialog).getByLabelText('거래 단가 (원)') as HTMLInputElement;
    await waitFor(() => expect(unitPriceInput.value).toBe('80000'));
    await act(async () => {
      resolveFirstPrice(jsonResponse({ ...stock, baseDate: '2026-09-21', closePrice: 75000 }));
      await firstPriceResponse;
    });
    expect(unitPriceInput.value).toBe('80000');
  });

  it('검색 결과를 주식과 ETF로 필터링한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).includes('/api/stocks?')) {
          return jsonResponse({ stocks: [stock, kosdaqStock, etf] });
        }
        const portfolioResponse = portfolioApiResponse(input, init);
        if (portfolioResponse) return portfolioResponse;
        return jsonResponse({}, 404);
      }),
    );

    const view = render(<PortfolioPage />);
    await waitFor(() =>
      expect(view.getByRole('button', { name: '자산 추가' })).toHaveProperty('disabled', false),
    );
    fireEvent.click(view.getByRole('button', { name: '자산 추가' }));
    expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
    expect(view.queryByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' })).toBeNull();

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '주식' } });
    await within(dialog).findByRole('button', { name: /삼성전자/ });

    fireEvent.click(within(dialog).getByRole('button', { name: 'ETF' }));
    expect(within(dialog).queryByRole('button', { name: /삼성전자/ })).toBeNull();
    expect(within(dialog).getByRole('button', { name: /KODEX 200/ })).toBeTruthy();

    fireEvent.click(within(dialog).getByRole('button', { name: '주식' }));
    expect(within(dialog).getByRole('button', { name: /삼성전자/ })).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: /에코프로비엠/ })).toBeTruthy();
    expect(within(dialog).queryByRole('button', { name: /KODEX 200/ })).toBeNull();
  });

  it('ETF를 검색해 포트폴리오에 추가한다', async () => {
    const etfCalculation = {
      ...calculation,
      holdings: [{ ...calculation.holdings[0], ...etf }],
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.includes('/api/stocks?')) return jsonResponse({ stocks: [etf] });
        if (url.endsWith('/api/stocks/4/price'))
          return jsonResponse({ ...etf, baseDate: '2026-09-21', closePrice: 35000 });
        if (url.endsWith('/api/auth/csrf'))
          return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
        if (url.endsWith('/api/portfolio/calculate')) return jsonResponse(etfCalculation);
        const portfolioResponse = portfolioApiResponse(input, init);
        if (portfolioResponse) return portfolioResponse;
        return jsonResponse({}, 404);
      }),
    );

    const view = render(<PortfolioPage />);
    await waitFor(() =>
      expect(view.getByRole('button', { name: '자산 추가' })).toHaveProperty('disabled', false),
    );
    fireEvent.click(view.getByRole('button', { name: '자산 추가' }));
    expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
    expect(view.queryByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' })).toBeNull();
    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: 'KODEX' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /KODEX 200/ }));
    await waitFor(() =>
      expect((within(dialog).getByLabelText('거래 단가 (원)') as HTMLInputElement).value).toBe(
        '35000',
      ),
    );
    fireEvent.change(within(dialog).getByLabelText('수량 (주)'), { target: { value: '3' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '매수 기록하기' }));

    expect((await view.findAllByText('KODEX 200')).length).toBeGreaterThan(0);
    expect(savedHoldings).toContainEqual(expect.objectContaining({ category: 'ETF' }));
  });

  it('검색 실패를 사용자용 문구로 표시하고 검색어를 지우면 오류를 초기화한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        if (String(input).includes('/api/stocks?')) throw new TypeError('Failed to fetch');
        return portfolioApiResponse(input, init) ?? jsonResponse({}, 404);
      }),
    );

    const view = render(<PortfolioPage />);
    await waitFor(() =>
      expect(view.getByRole('button', { name: '자산 추가' })).toHaveProperty('disabled', false),
    );
    fireEvent.click(view.getByRole('button', { name: '자산 추가' }));
    expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
    expect(view.queryByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' })).toBeNull();

    const dialog = view.getByRole('dialog');
    const searchInput = within(dialog).getByRole('searchbox');
    fireEvent.change(searchInput, { target: { value: '없는종목' } });

    const error = await within(dialog).findByRole('alert');
    expect(within(error).getByText('종목을 검색하지 못했습니다. 다시 시도해 주세요.')).toBeTruthy();
    expect(within(error).getByRole('button', { name: '다시 검색' })).toBeTruthy();
    expect(within(dialog).queryByText('Failed to fetch')).toBeNull();
    expect(within(dialog).queryByText('보유 중인 종목을 검색해 보세요')).toBeNull();

    fireEvent.change(searchInput, { target: { value: '' } });
    await waitFor(() => expect(within(dialog).queryByRole('alert')).toBeNull());
    expect(
      within(dialog).getByText(
        '국내 주식과 ETF를 검색하세요. 과거에 매도한 종목도 기록할 수 있어요.',
      ),
    ).toBeTruthy();
  });

  it('최근 종가를 불러오는 동안 직접 입력한 거래 단가를 유지한다', async () => {
    let resolvePrice!: (response: Response) => void;
    const priceResponse = new Promise<Response>((resolve) => {
      resolvePrice = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.includes('/api/stocks?')) return jsonResponse({ stocks: [stock] });
        if (url.endsWith('/api/stocks/1/price')) return priceResponse;
        const portfolioResponse = portfolioApiResponse(input, init);
        if (portfolioResponse) return portfolioResponse;
        return jsonResponse({}, 404);
      }),
    );

    const view = render(<PortfolioPage />);
    await waitFor(() =>
      expect(view.getByRole('button', { name: '자산 추가' })).toHaveProperty('disabled', false),
    );
    fireEvent.click(view.getByRole('button', { name: '자산 추가' }));
    expect(view.getAllByRole('dialog', { hidden: true })).toHaveLength(1);
    expect(view.queryByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' })).toBeNull();

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '삼성' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /삼성전자/ }));

    const averagePriceInput = within(dialog).getByLabelText('거래 단가 (원)') as HTMLInputElement;
    expect(averagePriceInput.getAttribute('aria-busy')).toBe('true');
    fireEvent.change(averagePriceInput, { target: { value: '76000' } });
    expect(averagePriceInput.value).toBe('76000');

    await act(async () => {
      resolvePrice(
        jsonResponse({
          ...stock,
          baseDate: '2026-09-21',
          closePrice: 75000,
        }),
      );
      await priceResponse;
    });

    await waitFor(() => expect(averagePriceInput.getAttribute('aria-busy')).toBe('false'));
    expect(averagePriceInput.value).toBe('76000');
  });

  it('백엔드에 저장된 보유자산을 수정하고 자산 추이를 다시 조회한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    await view.findAllByText('750,000원');
    await view.findByRole('img', { name: /평가금액 750,000원, 매입원금 700,000원/ });
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    fireEvent.click(view.getByText('보유정보 편집'));
    fireEvent.click(view.getByRole('button', { name: '보유정보 정정' }));
    const dialog = view.getByRole('dialog', { name: '보유정보 정정' });
    fireEvent.change(within(dialog).getByLabelText('보유수량'), { target: { value: '12' } });
    fireEvent.change(within(dialog).getByLabelText('평균 매수가'), {
      target: { value: '60,000' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '수정하기' }));

    await waitFor(() => expect(view.queryByRole('dialog', { name: '보유정보 정정' })).toBeNull());
    expect(
      await view.findByRole('img', {
        name: /평가금액 900,000원, 매입원금 720,000원/,
      }),
    ).toBeTruthy();
    expect(savedHoldings[0]).toEqual(expect.objectContaining({ quantity: 12 }));

    fireEvent.click(view.getByRole('button', { name: '보유정보 정정' }));
    const secondDialog = view.getByRole('dialog', { name: '보유정보 정정' });
    fireEvent.change(within(secondDialog).getByLabelText('보유수량'), {
      target: { value: '14' },
    });
    fireEvent.change(within(secondDialog).getByLabelText('평균 매수가'), {
      target: { value: '80,000' },
    });
    fireEvent.click(within(secondDialog).getByRole('button', { name: '수정하기' }));

    expect(
      await view.findByRole('img', {
        name: /평가금액 1,050,000원, 매입원금 1,120,000원/,
      }),
    ).toBeTruthy();
    expect(savedHoldings[0]).toEqual(
      expect.objectContaining({ quantity: 14, averagePurchasePrice: 80000 }),
    );
    expect(
      vi
        .mocked(fetch)
        .mock.calls.filter(
          ([input, init]) =>
            String(input).endsWith('/api/users/me/portfolio/holdings/1') &&
            init?.method === 'PATCH',
        ),
    ).toHaveLength(2);
  });

  it('백엔드 저장 자산을 조회해 계산하고 삭제한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    expect((await view.findAllByText('750,000원')).length).toBeGreaterThan(0);
    fireEvent.click(view.getByRole('button', { name: '삼성전자 보유 상세' }));
    fireEvent.click(view.getByText('보유정보 편집'));
    fireEvent.click(view.getByRole('button', { name: '보유자산 제거' }));
    fireEvent.click(
      within(view.getByRole('dialog', { name: '보유자산 제거' })).getByRole('button', {
        name: '보유자산 제거',
      }),
    );
    await waitFor(() => expect(view.getByRole('button', { name: '보유 자산 0' })).toBeTruthy());
    expect(savedHoldings).toEqual([]);
  });
});
