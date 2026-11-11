import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  analyzePortfolio,
  isPortfolioAnalysisResponse,
  getSavedPortfolioAnalysis,
  updateSavedPortfolioAnalysis,
} from '../../src/features/portfolio/api';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const analysisResponse = {
  totalAssetCount: 2,
  analyzedAssetCount: 2,
  overallDirection: 'POSITIVE',
  overallScore: 61,
  positiveImpactScore: 70,
  negativeImpactScore: 9,
  analyzedAt: '2026-10-05T08:00:00Z',
  overallImpact: '반도체 관련 이슈가 포트폴리오에 영향을 줄 수 있어요.',
  impacts: [
    {
      assetName: '삼성전자',
      weight: 60,
      direction: 'POSITIVE',
      impactLevel: 'HIGH',
      issueSummary: '반도체 수요가 증가했어요.',
      expectedReaction: '긍정적인 반응이 예상돼요.',
      outlook: '수요 흐름을 확인해야 해요.',
      reason: '실적 개선 가능성이 있어요.',
      evidenceSentence: '반도체 수요가 전년보다 증가했어요.',
      sources: [{ title: '반도체 산업 동향', url: 'https://example.com/semiconductor' }],
      rank: 1,
    },
    {
      assetName: 'SK하이닉스',
      weight: 40,
      direction: 'NEUTRAL',
      impactLevel: 'LOW',
      issueSummary: '직접적인 관련 이슈가 확인되지 않았어요.',
      expectedReaction: '중립적인 반응이 예상돼요.',
      outlook: '추가 흐름을 지켜봐야 해요.',
      reason: '직접적인 근거가 부족해요.',
      evidenceSentence: '직접적인 관련 이슈가 확인되지 않았어요.',
      sources: [],
      rank: 2,
    },
  ],
  sources: [{ title: '반도체 산업 동향', url: 'https://example.com/semiconductor' }],
};

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('포트폴리오 대시보드 분석 API', () => {
  const price = {
    status: 'AVAILABLE',
    currency: 'USD',
    priceType: 'CLOSE',
    baseDate: '2026-11-10',
    asOf: null,
    price: 180,
    comparisonDate: '2026-10-20',
    comparisonPrice: 200,
    changeRate: -10,
    observationCount: 15,
  };
  it('새 가격·검색 필드를 검증하고 구형 분석의 필드 누락은 허용한다', () => {
    expect(isPortfolioAnalysisResponse(analysisResponse)).toBe(true);
    expect(
      isPortfolioAnalysisResponse({
        ...analysisResponse,
        personalContext: null,
        priceContexts: { Apple: price },
      }),
    ).toBe(true);
  });
  it.each([
    { status: 'UNKNOWN' },
    { status: 1 },
    { price: '180' },
    { price: null },
    { baseDate: null },
    { baseDate: '2026-02-30' },
    { asOf: 10 },
    { observationCount: null },
    { observationCount: 1.5 },
    { comparisonPrice: null },
    { changeRate: '0' },
    { currency: 'EUR' },
    { priceType: null },
    { comparisonDate: '2026-11-11' },
    { priceType: 'DELAYED', asOf: null },
  ])('잘못된 가격 맥락을 정상 응답으로 통과시키지 않는다: %j', (patch) => {
    expect(
      isPortfolioAnalysisResponse({
        ...analysisResponse,
        priceContexts: { Apple: { ...price, ...patch } },
      }),
    ).toBe(false);
  });
  it.each([null, [], 1, { Apple: {} }])(
    '잘못된 가격 맥락 컨테이너를 거부한다: %j',
    (priceContexts) => {
      expect(isPortfolioAnalysisResponse({ ...analysisResponse, priceContexts })).toBe(false);
    },
  );
  it.each(['UNKNOWN', 1, {}])(
    '알 수 없는 반대 근거 검색 상태를 거부한다: %j',
    (riskSearchStatus) => {
      expect(
        isPortfolioAnalysisResponse({
          ...analysisResponse,
          impacts: analysisResponse.impacts.map((impact) => ({ ...impact, riskSearchStatus })),
        }),
      ).toBe(false);
    },
  );
  it('개인 손익의 정상·누락·null·잘못된 타입을 구분한다', () => {
    const personalContext = {
      baseDate: '2026-11-10',
      purchaseAmount: 300000,
      evaluationAmount: 240000,
      profitLoss: -60000,
      returnRate: -20,
      holdings: [
        {
          assetName: 'Apple',
          baseDate: '2026-11-10',
          quantity: 1,
          averagePurchasePrice: 300000,
          profitLoss: -60000,
          returnRate: -20,
        },
      ],
    };
    expect(isPortfolioAnalysisResponse({ ...analysisResponse, personalContext })).toBe(true);
    for (const invalid of [
      {},
      1,
      { ...personalContext, returnRate: '-20' },
      { ...personalContext, holdings: null },
      { ...personalContext, baseDate: undefined },
    ])
      expect(isPortfolioAnalysisResponse({ ...analysisResponse, personalContext: invalid })).toBe(
        false,
      );
  });
  it('전체 보유 자산과 비중으로 포트폴리오 분석을 요청한다', async () => {
    const fetchMock = vi.fn(async () => jsonResponse(analysisResponse));
    vi.stubGlobal('fetch', fetchMock);
    const assets = [
      { assetName: '삼성전자', weight: 60 },
      { assetName: 'SK하이닉스', weight: 40 },
    ];

    await expect(analyzePortfolio(assets)).resolves.toEqual(analysisResponse);
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/portfolio/analysis'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ assets }),
      signal: undefined,
      credentials: 'include',
    });
  });

  it('자산별 출처가 누락된 잘못된 응답을 거부한다', async () => {
    const invalidResponse = {
      ...analysisResponse,
      impacts: analysisResponse.impacts.map(({ sources: _sources, ...impact }) => impact),
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(invalidResponse)),
    );

    await expect(analyzePortfolio([{ assetName: '삼성전자', weight: 100 }])).rejects.toThrow(
      '포트폴리오 분석 API 응답 형식이 올바르지 않습니다.',
    );
  });
});

describe('회원 저장 분석 API', () => {
  const empty = {
    status: 'EMPTY',
    lastCheckOutcome: null,
    analysisId: null,
    matchesCurrentPortfolio: false,
    lastSucceededAt: null,
    lastFailedAt: null,
    failureCode: null,
    changeReason: null,
    analysis: null,
  };

  it('오늘 남은 횟수와 한국 시간 자정 초기화 시각을 보존한다', async () => {
    const response = { ...empty, remainingRefreshCount: 0, refreshResetAt: '2026-10-29T15:00:00Z' };
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(response)),
    );
    await expect(getSavedPortfolioAnalysis()).resolves.toEqual(response);
  });

  it.each([
    { remainingRefreshCount: -1, refreshResetAt: '2026-10-29T15:00:00Z' },
    { remainingRefreshCount: 4, refreshResetAt: '2026-10-29T15:00:00Z' },
    { remainingRefreshCount: 1.5, refreshResetAt: '2026-10-29T15:00:00Z' },
    { remainingRefreshCount: 0, refreshResetAt: 'invalid' },
    { remainingRefreshCount: 0 },
  ])('잘못된 확인 한도 응답을 거부한다: %j', async (limit) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ ...empty, ...limit })),
    );
    await expect(getSavedPortfolioAnalysis()).rejects.toThrow('저장된 분석 응답 형식');
  });

  it.each([false, true])(
    '회원 분석은 비동기 API에 CSRF를 포함하고 자산을 보내지 않는다 (갱신: %s)',
    async (refresh) => {
      const fetchMock = vi.fn(async (url: RequestInfo | URL) =>
        String(url).endsWith('/csrf')
          ? jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'test-token' })
          : jsonResponse({ ...empty, status: 'PROCESSING' }, 202),
      );
      vi.stubGlobal('fetch', fetchMock);
      await expect(updateSavedPortfolioAnalysis(refresh)).resolves.toMatchObject({
        status: 'PROCESSING',
      });
      expect(fetchMock).toHaveBeenLastCalledWith(
        expect.stringMatching(
          new RegExp(`/api/users/me/portfolio/analysis/tasks${refresh ? '/refresh' : ''}$`),
        ),
        expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
      );
      const init = vi.mocked(fetch).mock.calls.at(-1)?.[1];
      expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-token');
      expect(init?.body).toBeUndefined();
    },
  );

  it.each(['UNKNOWN', null, undefined])(
    '알 수 없는 상태 %s를 정상 결과로 표시하지 않는다',
    async (status) => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => jsonResponse({ ...empty, status })),
      );
      await expect(getSavedPortfolioAnalysis()).rejects.toThrow('저장된 분석 응답 형식');
    },
  );

  it('부분 분석에서 null 방향과 점수를 그대로 보존한다', async () => {
    const response = {
      ...empty,
      status: 'READY',
      analysisId: 1,
      analysis: {
        ...analysisResponse,
        overallDirection: null,
        overallScore: null,
        coverage: 'PARTIAL',
        analyzedWeight: 60,
        impacts: [
          analysisResponse.impacts[0],
          { ...analysisResponse.impacts[1], direction: null, impactLevel: null },
        ],
      },
    };
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(response)),
    );
    await expect(getSavedPortfolioAnalysis()).resolves.toEqual(response);
  });
});
