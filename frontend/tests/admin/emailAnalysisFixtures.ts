import type {
  AdminEmailAnalysisDetailResponse,
  AdminEmailAnalysisPageResponse,
} from '../../src/features/admin/emailAnalysisApi';

export const emailAnalysisDetail: AdminEmailAnalysisDetailResponse = {
  analysis: {
    id: 77,
    assetId: 1,
    assetName: '삼성전자',
    assetCode: '005930',
    market: 'KOSPI',
    category: 'STOCK',
    status: 'VERIFIED',
    asOf: '2026-10-27T22:00:00Z',
    analyzedAt: '2026-10-27T22:03:00Z',
    invalidatedAt: null,
  },
  model: 'test-model',
  analysisVersion: 'analysis-v1',
  evidenceVersion: 'evidence-v1',
  content: {
    direction: 'POSITIVE',
    impactLevel: 'MEDIUM',
    issueSummary: '검증용 실적 발표 소식',
    expectedReaction: '검증용 예상 반응',
    outlook: '검증용 종목 전망',
    reason: '새로 확인한 공식 자료를 근거로 판단했어요.',
    sources: [
      {
        title: '공식 실적 발표 자료 (테스트)',
        url: 'https://example.invalid/releases/20261027',
        publishedOn: '2026-10-27',
        occurredOn: '2026-10-27',
        excerpt: '테스트용 자료 요약',
        factQuote: 'This is a test quote.',
      },
    ],
  },
  verification: {
    checkedAt: '2026-10-27T22:04:00Z',
    sources: [
      {
        url: 'https://example.invalid/releases/20261027',
        companyMatched: true,
        datesMatched: true,
        numbersMatched: true,
        quoteMatched: true,
        passed: true,
      },
    ],
  },
  invalidationReason: null,
};
export function emailAnalysisPage(page = 0, totalPages = 1): AdminEmailAnalysisPageResponse {
  return {
    items: [{ ...emailAnalysisDetail.analysis, id: 77 + page }],
    page,
    size: 20,
    totalPages,
    totalElements: (totalPages - 1) * 20 + 1,
    hasNext: page + 1 < totalPages,
  };
}
