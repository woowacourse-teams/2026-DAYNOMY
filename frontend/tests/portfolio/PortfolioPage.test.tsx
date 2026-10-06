/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PortfolioPage } from '../../src/features/portfolio/PortfolioPage';
import { toPortfolioAnalysisAssets } from '../../src/features/portfolio/portfolioAnalysisAssets';
import { PORTFOLIO_STORAGE_KEY } from '../../src/features/portfolio/hooks/usePortfolioHoldings';
import {
  PORTFOLIO_PERFORMANCE_STATE_STORAGE_KEY,
  PORTFOLIO_PERFORMANCE_STORAGE_KEY,
} from '../../src/features/portfolio/hooks/usePortfolioPerformance';

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
  baseDate: '2026-09-21',
  totalPurchaseAmount: 700000,
  totalEvaluationAmount: 750000,
  dailyProfitLoss: 10000,
  dailyReturnRate: 1.35,
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

function portfolioApiResponse(input: RequestInfo | URL, _init?: RequestInit) {
  const url = String(input);
  if (url.endsWith('/api/auth/csrf')) {
    return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
  }
  if (url.includes('/api/stocks/prices?')) {
    const assetIds = new URL(url, 'http://localhost').searchParams.getAll('assetIds').map(Number);
    return jsonResponse({
      prices: assetIds.flatMap((assetId) => {
        const asset = [stock, secondStock, kosdaqStock, etf].find(
          (candidate) => candidate.assetId === assetId,
        );
        const initialPrice = assetId === etf.assetId ? 32000 : 70000;
        return [24, 25, 26, 27, 28, 29, 30].map((day, index) => ({
          ...asset,
          baseDate: `2026-09-${day}`,
          closePrice: initialPrice + index * 1000,
        }));
      }),
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
        return jsonResponse({
          ...calculation,
          holdings: calculation.holdings.map((holding) => ({
            ...holding,
            quantity: requestedHolding?.quantity ?? holding.quantity,
            averagePurchasePrice:
              requestedHolding?.averagePurchasePrice ?? holding.averagePurchasePrice,
          })),
        });
      }
      const portfolioResponse = portfolioApiResponse(input, init);
      if (portfolioResponse) return portfolioResponse;
      return jsonResponse({}, 404);
    }),
  );
}

beforeEach(() => {
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

  it('저장된 자산이 없으면 추가 안내를 표시한다', async () => {
    const view = render(<PortfolioPage />);
    expect(await view.findByRole('heading', { name: '첫 자산을 추가해 보세요' })).toBeTruthy();
    expect(view.getByRole('button', { name: /자산 추가/ })).toBeTruthy();
  });

  it('보유 자산의 소식 버튼을 비활성화한다', async () => {
    localStorage.setItem(
      PORTFOLIO_STORAGE_KEY,
      JSON.stringify([{ ...stock, quantity: 10, averagePurchasePrice: 70000 }]),
    );
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
    expect(view.getByRole('heading', { name: '수익률 추적' })).toBeTruthy();
    expect(await view.findByRole('img', { name: /현재 \+8.57%/ })).toBeTruthy();
    expect(view.queryByText('샘플 데이터')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '자산 분석 접기' }));
    expect(view.queryByRole('heading', { name: '자산 구성' })).toBeNull();
    expect(view.queryByRole('heading', { name: '수익률 추적' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '자산 분석 펼치기' }));
    expect(view.getByRole('heading', { name: '자산 구성' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '수익률 추적' })).toBeTruthy();
    expect(view.queryByRole('heading', { name: '포트폴리오 요약' })).toBeNull();
    expect(view.getByRole('heading', { name: '포트폴리오 AI 분석' })).toBeTruthy();
    expect(view.getByRole('button', { name: '분석하기' })).toBeTruthy();
    expect(view.getAllByText('삼성전자').length).toBeGreaterThan(0);
    expect(localStorage.getItem(PORTFOLIO_STORAGE_KEY)).toContain('005930');
  });

  it('새 종가로 현재 포트폴리오 수익률을 다시 계산한다', async () => {
    localStorage.setItem(
      PORTFOLIO_STORAGE_KEY,
      JSON.stringify([{ ...stock, quantity: 10, averagePurchasePrice: 70000 }]),
    );
    localStorage.setItem(
      PORTFOLIO_PERFORMANCE_STORAGE_KEY,
      JSON.stringify([
        {
          baseDate: '2026-09-29',
          totalPurchaseAmount: 700000,
          totalEvaluationAmount: 750000,
          totalProfitLoss: 50000,
          totalReturnRate: 5,
        },
      ]),
    );
    localStorage.setItem(
      PORTFOLIO_PERFORMANCE_STATE_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        holdingKey: '[[1,10,70000]]',
        baseEvaluationAmount: 750000,
        baseReturnRate: 5,
        lastCloseDate: '2026-09-29',
      }),
    );
    mockPortfolioApi();

    const view = render(<PortfolioPage />);

    expect(await view.findByRole('img', { name: /현재 \+8.57%/ })).toBeTruthy();
    expect(view.getByText('포트폴리오 누적 수익률')).toBeTruthy();
  });

  it('민감한 금액을 한 번에 숨기고 다시 표시한다', async () => {
    localStorage.setItem(
      PORTFOLIO_STORAGE_KEY,
      JSON.stringify([{ ...stock, quantity: 10, averagePurchasePrice: 70000 }]),
    );
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
    localStorage.setItem(
      PORTFOLIO_STORAGE_KEY,
      JSON.stringify([
        { ...stock, quantity: 10, averagePurchasePrice: 70000 },
        { ...etf, quantity: 30, averagePurchasePrice: 30000 },
      ]),
    );
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
    expect(localStorage.getItem(PORTFOLIO_STORAGE_KEY)).toContain('"category":"ETF"');
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

  it('로컬에 저장된 보유자산을 수정한다', async () => {
    localStorage.setItem(
      PORTFOLIO_STORAGE_KEY,
      JSON.stringify([{ ...stock, quantity: 10, averagePurchasePrice: 70000 }]),
    );
    localStorage.setItem(
      PORTFOLIO_PERFORMANCE_STORAGE_KEY,
      JSON.stringify([
        {
          baseDate: '2026-09-29',
          totalPurchaseAmount: 700000,
          totalEvaluationAmount: 750000,
          totalProfitLoss: 50000,
          totalReturnRate: 5,
        },
      ]),
    );
    localStorage.setItem(
      PORTFOLIO_PERFORMANCE_STATE_STORAGE_KEY,
      JSON.stringify({
        version: 1,
        holdingKey: '[[1,10,70000]]',
        baseEvaluationAmount: 750000,
        baseReturnRate: 5,
        lastCloseDate: '2026-09-29',
      }),
    );
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    await view.findAllByText('750,000원');
    await view.findByRole('img', { name: /현재 \+8.57%/ });
    fireEvent.click(view.getByRole('button', { name: '수정' }));
    const dialog = view.getByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('보유수량'), { target: { value: '12' } });
    fireEvent.change(within(dialog).getByLabelText('평균 매수가'), {
      target: { value: '60,000' },
    });
    fireEvent.click(within(dialog).getByRole('button', { name: '수정하기' }));

    await waitFor(() => expect(view.queryByRole('dialog')).toBeNull());
    expect(await view.findByRole('img', { name: /현재 \+26.67%/ })).toBeTruthy();
    expect(localStorage.getItem(PORTFOLIO_STORAGE_KEY)).toContain('"quantity":12');
    await waitFor(() => {
      const state = JSON.parse(
        localStorage.getItem(PORTFOLIO_PERFORMANCE_STATE_STORAGE_KEY) ?? '{}',
      ) as { holdingKey?: string; baseReturnRate?: number };
      expect(state.holdingKey).toBe('[[1,12,60000]]');
      expect(state.baseReturnRate).toBeCloseTo(26.6667);
    });
    const storedPoints = JSON.parse(
      localStorage.getItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY) ?? '[]',
    ) as Array<{
      baseDate: string;
      source: 'CLOSE' | 'HOLDING_CHANGE';
      totalReturnRate: number;
    }>;
    expect(storedPoints.at(-1)?.totalReturnRate).toBeCloseTo(26.6667);
    expect(storedPoints.at(-1)?.source).toBe('HOLDING_CHANGE');

    fireEvent.click(view.getByRole('button', { name: '수정' }));
    const secondDialog = view.getByRole('dialog');
    fireEvent.change(within(secondDialog).getByLabelText('보유수량'), {
      target: { value: '14' },
    });
    fireEvent.change(within(secondDialog).getByLabelText('평균 매수가'), {
      target: { value: '80,000' },
    });
    fireEvent.click(within(secondDialog).getByRole('button', { name: '수정하기' }));

    expect(await view.findByRole('img', { name: /현재 −5.00%/ })).toBeTruthy();
    await waitFor(() => {
      const points = JSON.parse(
        localStorage.getItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY) ?? '[]',
      ) as Array<{
        baseDate: string;
        source: 'CLOSE' | 'HOLDING_CHANGE';
        totalReturnRate: number;
      }>;
      const holdingChanges = points.filter((point) => point.source === 'HOLDING_CHANGE');
      expect(holdingChanges).toHaveLength(2);
      expect(new Set(holdingChanges.map((point) => point.baseDate)).size).toBe(1);
      expect(holdingChanges.map((point) => point.totalReturnRate)).toEqual([
        expect.closeTo(26.6667, 3),
        -5,
      ]);
    });
    expect(
      vi.mocked(fetch).mock.calls.some(([input]) => String(input).includes('/api/users/me/')),
    ).toBe(false);
  });

  it('로컬 저장 자산을 복원해 계산하고 삭제한다', async () => {
    localStorage.setItem(
      PORTFOLIO_STORAGE_KEY,
      JSON.stringify([
        {
          assetId: stock.assetId,
          assetCode: stock.assetCode,
          name: stock.name,
          market: stock.market,
          quantity: 10,
          averagePurchasePrice: 70000,
        },
      ]),
    );
    mockPortfolioApi();
    const view = render(<PortfolioPage />);

    expect((await view.findAllByText('750,000원')).length).toBeGreaterThan(0);
    fireEvent.click(view.getByRole('button', { name: '삭제' }));
    await waitFor(() =>
      expect(view.getByRole('heading', { name: '첫 자산을 추가해 보세요' })).toBeTruthy(),
    );
    expect(localStorage.getItem(PORTFOLIO_STORAGE_KEY)).toBe('[]');
  });
});
