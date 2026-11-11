import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  calculatePortfolio,
  getGuestPortfolioAnalysis,
  getPortfolioHistories,
  getSavedPortfolioAnalysis,
  isPortfolioAnalysisResponse,
  updateSavedPortfolioAnalysis,
} from '../../src/features/portfolio/api';
import { isStockMarket, searchStocks } from '../../src/features/stocks/api';
import { getDailyHistory, getRankings } from '../../src/features/league/api';
import { getContentPopularity } from '../../src/features/news/issue/popularity';
import { demoCalculation, demoHistories, demoHoldings } from '../../src/dev/portfolioDemoData';

function respond(body: unknown) {
  vi.stubGlobal(
    'fetch',
    vi.fn(
      async (input: RequestInfo | URL) =>
        new Response(
          JSON.stringify(
            String(input).endsWith('/csrf') ? { token: 'test', headerName: 'X-CSRF-TOKEN' } : body,
          ),
          { headers: { 'content-type': 'application/json' } },
        ),
    ),
  );
}

afterEach(() => vi.unstubAllGlobals());

const saved = {
  status: 'EMPTY',
  summaryStatus: null,
  lastCheckOutcome: null,
  analysisId: null,
  matchesCurrentPortfolio: false,
  lastSucceededAt: null,
  lastFailedAt: null,
  failureCode: null,
  changeReason: null,
  analysis: null,
};
const guest = {
  taskId: null,
  status: 'EMPTY',
  analysis: null,
  summaryStatus: null,
  failureCode: null,
  retryAllowed: false,
};
const daily = {
  weekStart: '2026-10-19',
  weekEnd: '2026-10-25',
  confirmed: false,
  asOfDate: null,
  eligibleFrom: null,
  weeklyReturnRate: null,
  days: [],
};

describe('외부 응답의 enum은 원래 문자열 타입을 검사한다', () => {
  it.each(['KOSPI', 'KOSDAQ', 'NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS'])(
    '정상 시장 %s를 유지한다',
    (market) => {
      expect(isStockMarket(market)).toBe(true);
    },
  );
  it.each([['KOSPI'], { toString: () => 'KOSPI' }, null, undefined, 0, 'UNKNOWN'])(
    '문자열이 아니거나 모르는 시장 %j를 거부한다',
    (market) => {
      expect(isStockMarket(market)).toBe(false);
    },
  );
  it.each([
    { status: ['EMPTY'] },
    { status: 0 },
    { status: 'UNKNOWN' },
    { status: null },
    { status: undefined },
    { summaryStatus: ['READY'] },
    { lastCheckOutcome: ['UPDATED'] },
    { matchesCurrentPortfolio: 'false' },
  ])('저장 분석 조회·갱신 모두 잘못된 응답 %j를 거부한다', async (patch) => {
    respond({ ...saved, ...patch });
    await expect(getSavedPortfolioAnalysis()).rejects.toThrow('응답 형식');
    await expect(updateSavedPortfolioAnalysis(false)).rejects.toThrow('응답 형식');
  });
  it('옵셔널 필드 누락과 nullable 값은 유지하고 알 수 없는 추가 필드는 허용한다', async () => {
    const { summaryStatus: _summaryStatus, ...legacy } = saved;
    respond({ ...legacy, futureField: 'new' });
    await expect(getSavedPortfolioAnalysis()).resolves.toEqual({ ...legacy, futureField: 'new' });
  });
  it.each([{ status: ['EMPTY'] }, { summaryStatus: ['READY'] }])(
    '비회원 작업 응답 %j를 거부한다',
    async (patch) => {
      respond({ ...guest, ...patch });
      await expect(getGuestPortfolioAnalysis()).rejects.toThrow('응답 형식');
    },
  );
  it('종목 검색의 중첩 시장 타입을 검증한다', async () => {
    respond({ stocks: [{ ...demoHoldings[0], market: ['KOSPI'] }] });
    await expect(searchStocks('삼성')).rejects.toThrow('응답 형식');
  });
  it('계산 상태와 거래 종류를 문자열로 변환해 통과시키지 않는다', async () => {
    respond({ ...demoCalculation, pricingStatus: ['PARTIAL'] });
    await expect(calculatePortfolio(demoHoldings)).rejects.toThrow('응답 형식');
    respond([
      { ...demoHistories[0], changeType: ['ADJUSTED'], transactionQuantity: null, unitPrice: null },
    ]);
    await expect(getPortfolioHistories('2026-10-01', '2026-10-31')).rejects.toThrow('응답 형식');
  });
  it('중첩 분석의 coverage와 필수 출처도 검증한다', () => {
    const analysis = {
      totalAssetCount: 1,
      analyzedAssetCount: 0,
      overallDirection: null,
      overallScore: null,
      positiveImpactScore: 0,
      negativeImpactScore: 0,
      analyzedAt: null,
      overallImpact: '분석 근거 부족',
      impacts: [],
      sources: [],
      coverage: 'INSUFFICIENT',
    };
    expect(isPortfolioAnalysisResponse(analysis)).toBe(true);
    expect(isPortfolioAnalysisResponse({ ...analysis, coverage: ['INSUFFICIENT'] })).toBe(false);
    expect(isPortfolioAnalysisResponse({ ...analysis, sources: null })).toBe(false);
  });
  it('리그 집계와 일별 상태의 배열을 거부한다', async () => {
    respond({
      ...daily,
      collectionStatus: ['COMPLETE'],
      leagueType: 'WEEKLY_RETURN',
      rankings: [],
      totalCount: 0,
    });
    await expect(getRankings('WEEKLY_RETURN')).rejects.toThrow('응답 형식');
    await expect(getDailyHistory('public-id')).rejects.toThrow('응답 형식');
    respond({
      ...daily,
      days: [
        {
          baseDate: '2026-10-19',
          dailyReturnRate: null,
          cumulativeReturnRate: null,
          reason: null,
          status: ['PENDING'],
        },
      ],
    });
    await expect(getDailyHistory('public-id')).rejects.toThrow('응답 형식');
  });
  it('인기순의 콘텐츠 종류를 문자열로 변환해 키를 만들지 않는다', async () => {
    respond({
      periodDays: 7,
      items: [
        { contentType: ['NEWS'], contentId: 1, score: 1, lastInteractedAt: '2026-11-03T00:00:00Z' },
      ],
    });
    await expect(getContentPopularity()).rejects.toThrow('응답 형식');
  });
});
