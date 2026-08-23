import type {
  PortfolioAnalysisResponse,
  PortfolioAssetCategory,
  PortfolioAssetImpactResponse,
  PortfolioImpactDirection,
  PortfolioImpactLevel,
} from './types';

const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const portfolioAnalysisRequests = new Map<string, Promise<PortfolioAnalysisResponse>>();

const PORTFOLIO_ASSET_CATEGORIES = new Set<PortfolioAssetCategory>([
  'GOLD',
  'STOCK',
  'BOND',
  'REAL_ESTATE',
  'FOREIGN_EXCHANGE',
  'VIRTUAL_ASSET',
  'DEPOSIT_SAVINGS',
  'ETF',
  'PENSION',
  'MOCK',
]);
const IMPACT_DIRECTIONS = new Set<PortfolioImpactDirection>(['POSITIVE', 'NEGATIVE']);
const IMPACT_LEVELS = new Set<PortfolioImpactLevel>(['HIGH', 'MEDIUM', 'LOW']);

type ErrorResponse = {
  code?: unknown;
  message?: unknown;
};

export class PortfolioApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, code?: string, message = '포트폴리오 분석을 불러오지 못했습니다.') {
    super(message);
    this.name = 'PortfolioApiError';
    this.status = status;
    this.code = code;
  }
}

function isPortfolioAssetImpact(value: unknown): value is PortfolioAssetImpactResponse {
  if (!value || typeof value !== 'object') return false;

  const impact = value as Record<string, unknown>;
  return (
    typeof impact.bookmarkId === 'number' &&
    typeof impact.assetId === 'number' &&
    typeof impact.name === 'string' &&
    typeof impact.category === 'string' &&
    PORTFOLIO_ASSET_CATEGORIES.has(impact.category as PortfolioAssetCategory) &&
    typeof impact.assetCode === 'string' &&
    typeof impact.direction === 'string' &&
    IMPACT_DIRECTIONS.has(impact.direction as PortfolioImpactDirection) &&
    typeof impact.impactLevel === 'string' &&
    IMPACT_LEVELS.has(impact.impactLevel as PortfolioImpactLevel) &&
    typeof impact.expectedReaction === 'string' &&
    typeof impact.reason === 'string' &&
    Number.isInteger(impact.sortOrder)
  );
}

function isPortfolioAnalysisResponse(value: unknown): value is PortfolioAnalysisResponse {
  if (!value || typeof value !== 'object') return false;

  const response = value as Record<string, unknown>;
  return Array.isArray(response.impacts) && response.impacts.every(isPortfolioAssetImpact);
}

async function requestPortfolioAnalysis(newsId: string): Promise<PortfolioAnalysisResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/news/${encodeURIComponent(newsId)}/portfolio-analysis`,
    { credentials: 'include' },
  );

  if (!response.ok) {
    const contentType = response.headers.get('content-type') ?? '';
    const error = contentType.includes('application/json')
      ? ((await response.json()) as ErrorResponse)
      : undefined;
    throw new PortfolioApiError(
      response.status,
      typeof error?.code === 'string' ? error.code : undefined,
      typeof error?.message === 'string' ? error.message : undefined,
    );
  }

  const data = (await response.json()) as unknown;
  if (!isPortfolioAnalysisResponse(data)) {
    throw new Error('포트폴리오 분석 API 응답 형식이 올바르지 않습니다.');
  }

  return data;
}

export function getPortfolioAnalysis(newsId: string): Promise<PortfolioAnalysisResponse> {
  const cachedRequest = portfolioAnalysisRequests.get(newsId);
  if (cachedRequest) return cachedRequest;

  const request = requestPortfolioAnalysis(newsId).catch((error: unknown) => {
    portfolioAnalysisRequests.delete(newsId);
    throw error;
  });
  portfolioAnalysisRequests.set(newsId, request);

  return request;
}

export function retryPortfolioAnalysis(newsId: string): Promise<PortfolioAnalysisResponse> {
  portfolioAnalysisRequests.delete(newsId);
  return getPortfolioAnalysis(newsId);
}
