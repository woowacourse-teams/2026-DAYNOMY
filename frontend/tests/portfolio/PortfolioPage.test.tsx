/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { PortfolioPage } from '../../src/features/portfolio/PortfolioPage';
import { toPortfolioAnalysisAssets } from '../../src/features/portfolio/portfolioAnalysisAssets';
import type {
  PortfolioHoldingInput,
  PortfolioPerformancePoint,
} from '../../src/features/portfolio/types';

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
  if (url.endsWith('/api/auth/csrf')) {
    return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
  }
  if (url.endsWith('/api/users/me/portfolio') && method === 'GET') {
    return jsonResponse({ holdings: savedHoldings });
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
      (sum, holding) => sum + holding.quantity * (holding.assetId === etf.assetId ? 30000 : 76000),
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
      savedPerformancePoints.length > 0 ? savedPerformancePoints : defaultPoints
    ).filter((point) => point.baseDate >= from && point.baseDate <= to);
    return jsonResponse({
      status: points.length >= 2 ? 'READY' : 'INSUFFICIENT_DATA',
      reason: points.length >= 2 ? null : 'SNAPSHOT_DATA_INSUFFICIENT',
      baseDate: points.at(-1)?.baseDate ?? null,
      previousBaseDate: points.at(-2)?.baseDate ?? null,
      points,
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
      if (url.endsWith('/api/assets/1/contents'))
        return jsonResponse({
          assetId: 1,
          assetCode: '005930',
          assetName: '삼성전자',
          contents: [
            {
              id: 10,
              assetId: 1,
              sourceType: 'YOUTUBE',
              title: '삼성전자 분석 영상',
              url: 'https://youtube.com/watch?v=abc',
              imageUrl: 'https://i.ytimg.com/vi/abc/hqdefault.jpg',
              createdAt: null,
            },
            {
              id: 11,
              assetId: 1,
              sourceType: 'YOUTUBE',
              title: '삼성전자 두 번째 분석 영상',
              url: 'https://youtube.com/watch?v=def',
              imageUrl: 'https://i.ytimg.com/vi/def/hqdefault.jpg',
              createdAt: null,
            },
            {
              id: 12,
              assetId: 1,
              sourceType: 'INTERNAL_NEWS',
              title: '삼성전자 관련 이슈',
              url: '/news/12',
              imageUrl: null,
              createdAt: null,
            },
            ...Array.from({ length: 8 }, (_, index) => ({
              id: 20 + index,
              assetId: 1,
              sourceType: 'YOUTUBE',
              title: `삼성전자 추가 콘텐츠 ${index + 1}`,
              url: `https://youtube.com/watch?v=extra-${index + 1}`,
              imageUrl: null,
              createdAt: null,
            })),
          ],
        });
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
  cleanup();
  localStorage.clear();
  window.history.replaceState(null, '', '/');
  vi.unstubAllGlobals();
});

describe('포트폴리오 화면', () => {
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
    expect(view.getByRole('button', { name: /자산 추가/ })).toBeTruthy();
    expect(view.getAllByText('표시할 자산이 없습니다').length).toBeGreaterThanOrEqual(3);
    expect(view.queryByText('표시할 자산이 없어요')).toBeNull();
    const summary = within(view.getByLabelText('자산 요약'));
    expect(summary.getByText('보유 자산').parentElement?.textContent).toContain('0개');
    expect(
      summary.getByText('전체 자산 평가금액').parentElement?.parentElement?.textContent,
    ).toContain('0원');
    expect(view.queryByRole('combobox', { name: '자산 추이 기간' })).toBeNull();

    fireEvent.click(view.getByRole('button', { name: '자산 추이 크게 보기' }));
    const dialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(within(dialog).getByText('표시할 자산이 없습니다')).toBeTruthy();
    expect(within(dialog).getByRole('combobox', { name: '자산 추이 기간' })).toBeTruthy();
    expect(within(dialog).getByLabelText('자산 추이 범례')).toBeTruthy();
  });

  it('보유 자산의 소식 버튼을 비활성화한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();

    const view = render(<PortfolioPage />);
    await view.findAllByText('750,000원');
    const relatedContentsButton = view.getByRole('button', { name: '소식' });
    expect(relatedContentsButton).toHaveProperty('disabled', true);
    fireEvent.click(relatedContentsButton);

    expect(view.queryByRole('complementary', { name: '소식' })).toBeNull();
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) => String(input).endsWith('/api/assets/1/contents')),
    ).toBe(false);
  });

  it('종목을 검색해 추가하고 계산 결과를 표시한다', async () => {
    mockPortfolioApi();
    const view = render(<PortfolioPage />);
    fireEvent.click(await view.findByRole('button', { name: /자산 추가/ }));

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '삼성' } });
    const result = await within(dialog).findByRole('button', { name: /삼성전자/ });
    fireEvent.click(result);
    const averagePriceInput = within(dialog).getByLabelText('평균 매수가') as HTMLInputElement;
    await waitFor(() => expect(averagePriceInput.value).toBe('75,000'));
    fireEvent.click(within(dialog).getByRole('button', { name: '평균 매수가 1,000원 올리기' }));
    expect(averagePriceInput.value).toBe('76,000');
    fireEvent.click(within(dialog).getByRole('button', { name: '평균 매수가 1,000원 내리기' }));
    expect(averagePriceInput.value).toBe('75,000');
    fireEvent.change(within(dialog).getByLabelText('보유수량'), { target: { value: '10' } });
    fireEvent.change(averagePriceInput, { target: { value: '70,000' } });
    expect(averagePriceInput.value).toBe('70,000');
    fireEvent.click(within(dialog).getByRole('button', { name: '추가하기' }));

    expect((await view.findAllByText('750,000원')).length).toBeGreaterThan(0);
    expect(view.getByText('+10,000원')).toBeTruthy();
    expect(view.queryByText('주식 100.00% · ETF 0.00%')).toBeNull();
    expect(view.getByRole('heading', { name: '자산 구성' })).toBeTruthy();
    expect(view.getByRole('img', { name: '주식 100.00%, ETF 0.00%' })).toBeTruthy();
    expect(view.queryByText('시장 구성')).toBeNull();
    expect(view.queryByText('KOSPI')).toBeNull();
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
      (view.getByRole('combobox', { name: '자산 추이 기간' }) as HTMLSelectElement).value,
    ).toBe('1M');
    expect(view.queryByText('08.10')).toBeNull();
    expect(view.queryByText(/최근 1개월.*거래일 종가 기준/)).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '자산 추이 크게 보기' }));
    const trendDialog = view.getByRole('dialog', { name: '자산 추이 상세' });
    expect(within(trendDialog).queryByText(/최근 1개월.*거래일 종가 기준/)).toBeNull();
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
        });
      }
      return originalImplementation?.(input, init) ?? jsonResponse({}, 404);
    });

    const view = render(<PortfolioPage />);

    const singlePointChart = await view.findByRole('img', {
      name: /평가금액 750,000원, 매입원금 700,000원/,
    });
    expect(singlePointChart.getAttribute('viewBox')).toBe('0 0 720 100');
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

  it('잘못된 서버 추이 응답은 오류로 표시하고 재시도하면 실제 계산 결과를 보여준다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();
    const mockedFetch = vi.mocked(fetch);
    const originalImplementation = mockedFetch.getMockImplementation();
    let performanceRequests = 0;
    mockedFetch.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).includes('/api/users/me/portfolio/performance?')) {
        performanceRequests += 1;
        if (performanceRequests === 1) {
          return jsonResponse({
            status: 'READY',
            reason: null,
            baseDate: calculation.baseDate,
            previousBaseDate: null,
            points: [{ ...calculation, totalEvaluationAmount: '750000' }],
          });
        }
      }
      return originalImplementation?.(input, init) ?? jsonResponse({}, 404);
    });

    const view = render(<PortfolioPage />);
    const error = await view.findByRole('alert');
    expect(error.textContent).toContain('자산 추이 응답 형식이 올바르지 않습니다.');
    expect(view.queryByRole('img', { name: /포트폴리오 자산 추이/ })).toBeNull();
    fireEvent.click(within(error).getByRole('button', { name: '다시 시도' }));

    expect(
      await view.findByRole('img', {
        name: /평가금액 750,000원, 매입원금 700,000원/,
      }),
    ).toBeTruthy();
    expect(performanceRequests).toBe(2);
  });

  it('5년 기간을 선택하면 서버의 5년 자산 추이를 표시한다', async () => {
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
    fireEvent.click(await view.findByRole('button', { name: /자산 추가/ }));

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '주식' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /삼성전자/ }));
    fireEvent.click(within(dialog).getByRole('button', { name: '다시 검색' }));

    await act(async () => {
      resolveFirstPrice(
        jsonResponse({
          ...stock,
          baseDate: '2026-09-21',
          closePrice: 75000,
        }),
      );
      await firstPriceResponse;
    });

    const averagePriceInput = within(dialog).getByLabelText('평균 매수가') as HTMLInputElement;
    expect(averagePriceInput.value).toBe('');

    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '두산' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /두산/ }));
    await waitFor(() => expect(averagePriceInput.value).toBe('80,000'));
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
    fireEvent.click(await view.findByRole('button', { name: /자산 추가/ }));

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
    fireEvent.click(await view.findByRole('button', { name: /자산 추가/ }));
    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: 'KODEX' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /KODEX 200/ }));
    await waitFor(() =>
      expect((within(dialog).getByLabelText('평균 매수가') as HTMLInputElement).value).toBe(
        '35,000',
      ),
    );
    fireEvent.change(within(dialog).getByLabelText('보유수량'), { target: { value: '3' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '추가하기' }));

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
    fireEvent.click(await view.findByRole('button', { name: /자산 추가/ }));

    const dialog = view.getByRole('dialog');
    const searchInput = within(dialog).getByRole('searchbox');
    fireEvent.change(searchInput, { target: { value: '없는종목' } });

    const error = await within(dialog).findByRole('alert');
    expect(within(error).getByText('검색 결과를 불러오지 못했어요')).toBeTruthy();
    expect(
      within(error).getByText('네트워크 상태를 확인하고 잠시 후 다시 검색해 주세요.'),
    ).toBeTruthy();
    expect(within(dialog).queryByText('Failed to fetch')).toBeNull();
    expect(within(dialog).queryByText('보유 중인 종목을 검색해 보세요')).toBeNull();

    fireEvent.change(searchInput, { target: { value: '' } });
    await waitFor(() => expect(within(dialog).queryByRole('alert')).toBeNull());
    expect(within(dialog).getByText('보유 중인 주식과 ETF를 검색해 보세요')).toBeTruthy();
  });

  it('최근 종가를 불러오는 동안 평균 매수가 조절을 막는다', async () => {
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
    fireEvent.click(await view.findByRole('button', { name: /자산 추가/ }));

    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByRole('searchbox'), { target: { value: '삼성' } });
    fireEvent.click(await within(dialog).findByRole('button', { name: /삼성전자/ }));

    const averagePriceInput = within(dialog).getByLabelText('평균 매수가') as HTMLInputElement;
    const increaseButton = within(dialog).getByRole('button', {
      name: '평균 매수가 1,000원 올리기',
    }) as HTMLButtonElement;
    expect(increaseButton.disabled).toBe(true);
    fireEvent.click(increaseButton);
    expect(averagePriceInput.value).toBe('');

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

    await waitFor(() => expect(averagePriceInput.value).toBe('75,000'));
    expect(increaseButton.disabled).toBe(false);
    fireEvent.click(increaseButton);
    expect(averagePriceInput.value).toBe('76,000');
  });

  it('백엔드에 저장된 보유자산을 수정하고 자산 추이를 다시 조회한다', async () => {
    savedHoldings = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    await view.findAllByText('750,000원');
    await view.findByRole('img', { name: /평가금액 750,000원, 매입원금 700,000원/ });
    fireEvent.click(view.getByRole('button', { name: '수정' }));
    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('보유수량'), { target: { value: '12' } });
    fireEvent.change(within(dialog).getByLabelText('평균 매수가'), {
      target: { value: '60,000' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '수정하기' }));

    await waitFor(() => expect(view.queryByRole('dialog')).toBeNull());
    expect(
      await view.findByRole('img', {
        name: /평가금액 900,000원, 매입원금 720,000원/,
      }),
    ).toBeTruthy();
    expect(savedHoldings[0]).toEqual(expect.objectContaining({ quantity: 12 }));

    fireEvent.click(view.getByRole('button', { name: '수정' }));
    const secondDialog = view.getByRole('dialog');
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
    fireEvent.click(view.getByRole('button', { name: '삭제' }));
    await waitFor(() =>
      expect(view.getAllByText('표시할 자산이 없습니다').length).toBeGreaterThanOrEqual(3),
    );
    expect(savedHoldings).toEqual([]);
  });
});
