import { request, requestWithCsrf } from '../../api/client';
import { isStockMarket } from '../stocks/api';
import type { AssetCategory, StockMarket } from '../stocks/types';
import type { PortfolioImpactDirection, PortfolioImpactLevel } from '../portfolio/types';

export type EmailAnalysisStatus = 'PENDING' | 'VERIFIED' | 'REJECTED' | 'INVALIDATED';
export type AnalysisInvalidationResponse = {
  analysisId: number;
  invalidatedAt: string;
  cancelledDeliveries: number;
};
export type AdminEmailAnalysisItemResponse = {
  id: number;
  assetId: number;
  assetName: string;
  assetCode: string;
  market: StockMarket;
  category: AssetCategory;
  status: EmailAnalysisStatus;
  asOf: string;
  analyzedAt: string;
  invalidatedAt: string | null;
};
export type AdminEmailAnalysisPageResponse = {
  items: AdminEmailAnalysisItemResponse[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};
export type AdminEmailAnalysisDetailResponse = {
  analysis: AdminEmailAnalysisItemResponse;
  model: string;
  analysisVersion: string;
  evidenceVersion: string;
  content: {
    direction: PortfolioImpactDirection | null;
    impactLevel: PortfolioImpactLevel | null;
    issueSummary: string | null;
    expectedReaction: string | null;
    outlook: string | null;
    reason: string | null;
    sources: {
      title: string | null;
      url: string | null;
      publishedOn: string | null;
      occurredOn: string | null;
      excerpt: string | null;
      factQuote: string | null;
    }[];
  };
  verification: {
    checkedAt: string;
    sources: {
      url: string;
      companyMatched: boolean;
      datesMatched: boolean;
      numbersMatched: boolean;
      quoteMatched: boolean;
      passed: boolean;
    }[];
  } | null;
  invalidationReason: string | null;
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
function isInteger(value: unknown, minimum = 0): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= minimum;
}
function isTimestamp(value: unknown): value is string {
  return typeof value === 'string' && value.includes('T') && Number.isFinite(Date.parse(value));
}
function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}
export function isEmailAnalysisStatus(value: unknown): value is EmailAnalysisStatus {
  return (
    typeof value === 'string' && ['PENDING', 'VERIFIED', 'REJECTED', 'INVALIDATED'].includes(value)
  );
}
function isItem(value: unknown): value is AdminEmailAnalysisItemResponse {
  return (
    isRecord(value) &&
    isInteger(value.id, 1) &&
    isInteger(value.assetId, 1) &&
    typeof value.assetName === 'string' &&
    typeof value.assetCode === 'string' &&
    typeof value.market === 'string' &&
    isStockMarket(value.market) &&
    (value.category === 'STOCK' || value.category === 'ETF') &&
    isEmailAnalysisStatus(value.status) &&
    isTimestamp(value.asOf) &&
    isTimestamp(value.analyzedAt) &&
    (value.invalidatedAt === null || isTimestamp(value.invalidatedAt))
  );
}
function isPage(value: unknown): value is AdminEmailAnalysisPageResponse {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isItem) &&
    isInteger(value.page) &&
    isInteger(value.size, 1) &&
    value.size <= 100 &&
    value.items.length <= value.size &&
    isInteger(value.totalPages) &&
    isInteger(value.totalElements) &&
    typeof value.hasNext === 'boolean'
  );
}
function isDetail(value: unknown): value is AdminEmailAnalysisDetailResponse {
  if (
    !isRecord(value) ||
    !isItem(value.analysis) ||
    !['model', 'analysisVersion', 'evidenceVersion'].every(
      (key) => typeof value[key] === 'string',
    ) ||
    !isNullableString(value.invalidationReason) ||
    !isRecord(value.content)
  )
    return false;
  const content = value.content;
  if (
    !(
      content.direction === null ||
      (typeof content.direction === 'string' &&
        ['POSITIVE', 'NEGATIVE', 'NEUTRAL'].includes(content.direction))
    ) ||
    !(
      content.impactLevel === null ||
      (typeof content.impactLevel === 'string' &&
        ['HIGH', 'MEDIUM', 'LOW'].includes(content.impactLevel))
    ) ||
    !['issueSummary', 'expectedReaction', 'outlook', 'reason'].every((key) =>
      isNullableString(content[key]),
    ) ||
    !Array.isArray(content.sources) ||
    !content.sources.every(
      (source) =>
        isRecord(source) &&
        ['title', 'url', 'excerpt', 'factQuote'].every((key) => isNullableString(source[key])) &&
        ['publishedOn', 'occurredOn'].every(
          (key) =>
            source[key] === null ||
            (typeof source[key] === 'string' &&
              /^\d{4}-\d{2}-\d{2}$/.test(source[key]) &&
              Number.isFinite(Date.parse(source[key]))),
        ),
    )
  )
    return false;
  const verification = value.verification;
  return (
    verification === null ||
    (isRecord(verification) &&
      isTimestamp(verification.checkedAt) &&
      Array.isArray(verification.sources) &&
      verification.sources.every(
        (check) =>
          isRecord(check) &&
          typeof check.url === 'string' &&
          ['companyMatched', 'datesMatched', 'numbersMatched', 'quoteMatched', 'passed'].every(
            (key) => typeof check[key] === 'boolean',
          ),
      ))
  );
}

const PATH = '/api/admin/email-analyses';
export async function getAdminEmailAnalyses(
  filters: { page: number; status?: EmailAnalysisStatus; assetId?: number },
  signal?: AbortSignal,
) {
  if (
    !isInteger(filters.page) ||
    (filters.status !== undefined && !isEmailAnalysisStatus(filters.status)) ||
    (filters.assetId !== undefined && !isInteger(filters.assetId, 1))
  )
    throw new Error('분석 조회 조건을 확인해 주세요.');
  const query = new URLSearchParams({ page: String(filters.page), size: '20' });
  if (filters.status) query.set('status', filters.status);
  if (filters.assetId !== undefined) query.set('assetId', String(filters.assetId));
  const value = await request<unknown>(`${PATH}?${query}`, { signal });
  if (!isPage(value) || value.page !== filters.page)
    throw new Error('분석 목록 응답 형식이 올바르지 않습니다.');
  return value;
}
export async function getAdminEmailAnalysis(id: number, signal?: AbortSignal) {
  if (!isInteger(id, 1)) throw new Error('올바른 분석 ID가 필요합니다.');
  const value = await request<unknown>(`${PATH}/${id}`, { signal });
  if (!isDetail(value) || value.analysis.id !== id)
    throw new Error('분석 상세 응답 형식이 올바르지 않습니다.');
  return value;
}

export async function invalidateAdminEmailAnalysis(
  id: number,
  reason: string,
  signal?: AbortSignal,
): Promise<AnalysisInvalidationResponse> {
  const trimmed = reason.trim();
  if (!isInteger(id, 1) || trimmed.length < 1 || trimmed.length > 500)
    throw new Error('무효화 사유는 1~500자로 입력해 주세요.');
  const value = await requestWithCsrf<unknown>(`${PATH}/${id}/invalidate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason: trimmed }),
    signal,
  });
  if (
    !isRecord(value) ||
    value.analysisId !== id ||
    !isTimestamp(value.invalidatedAt) ||
    !isInteger(value.cancelledDeliveries)
  )
    throw new Error('무효화 응답 형식이 올바르지 않습니다.');
  return {
    analysisId: id,
    invalidatedAt: value.invalidatedAt,
    cancelledDeliveries: value.cancelledDeliveries,
  };
}

export const CORRECTION_REVIEW_PAGE_SIZE = 50;
export async function getAdminEmailCorrectionReviewIds(
  id: number,
  page: number,
  signal?: AbortSignal,
): Promise<string[]> {
  if (!isInteger(id, 1) || !isInteger(page)) throw new Error('분석 ID와 페이지를 확인해 주세요.');
  const query = new URLSearchParams({
    page: String(page),
    size: String(CORRECTION_REVIEW_PAGE_SIZE),
  });
  const value = await request<unknown>(`${PATH}/${id}/correction-review-deliveries?${query}`, {
    signal,
  });
  if (
    !Array.isArray(value) ||
    value.length > CORRECTION_REVIEW_PAGE_SIZE ||
    !value.every(
      (entry) =>
        typeof entry === 'string' &&
        /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(entry),
    ) ||
    new Set(value).size !== value.length
  )
    throw new Error('정정 검토 대상 응답 형식이 올바르지 않습니다.');
  return value;
}
