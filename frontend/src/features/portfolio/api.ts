import type {
  PortfolioAnalysisRequest,
  PortfolioAnalysisResponse,
  PortfolioAsset,
  PortfolioAssetImpactResponse,
  PortfolioImpactDirection,
  PortfolioImpactLevel,
} from './types';

const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const PORTFOLIO_ANALYSIS_CACHE_TIME = 5 * 60 * 1000;

type PortfolioAnalysisCacheEntry = {
  request: Promise<PortfolioAnalysisResponse>;
  expiresAt?: number;
};

const portfolioAnalysisRequests = new Map<string, PortfolioAnalysisCacheEntry>();

const IMPACT_DIRECTIONS = new Set<PortfolioImpactDirection>(['POSITIVE', 'NEGATIVE', 'NEUTRAL']);
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
    typeof impact.assetName === 'string' &&
    typeof impact.weight === 'number' &&
    typeof impact.direction === 'string' &&
    IMPACT_DIRECTIONS.has(impact.direction as PortfolioImpactDirection) &&
    typeof impact.impactLevel === 'string' &&
    IMPACT_LEVELS.has(impact.impactLevel as PortfolioImpactLevel) &&
    typeof impact.summary === 'string' &&
    typeof impact.reason === 'string' &&
    typeof impact.evidenceSentence === 'string' &&
    Number.isInteger(impact.rank)
  );
}

function isPortfolioAnalysisResponse(value: unknown): value is PortfolioAnalysisResponse {
  if (!value || typeof value !== 'object') return false;

  const response = value as Record<string, unknown>;
  return (
    Number.isInteger(response.totalAssetCount) &&
    Number.isInteger(response.analyzedAssetCount) &&
    Array.isArray(response.impacts) &&
    response.impacts.every(isPortfolioAssetImpact)
  );
}

function createRequestKey(newsId: string, assets: PortfolioAsset[]) {
  return JSON.stringify([newsId, assets]);
}

async function requestPortfolioAnalysis(
  newsId: string,
  assets: PortfolioAsset[],
): Promise<PortfolioAnalysisResponse> {
  const request: PortfolioAnalysisRequest = { assets };
  const response = await fetch(
    `${API_BASE_URL}/api/news/${encodeURIComponent(newsId)}/portfolio-analysis`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
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

export function getPortfolioAnalysis(
  newsId: string,
  assets: PortfolioAsset[],
): Promise<PortfolioAnalysisResponse> {
  const requestKey = createRequestKey(newsId, assets);
  const cachedEntry = portfolioAnalysisRequests.get(requestKey);
  const isFresh = cachedEntry && (!cachedEntry.expiresAt || Date.now() < cachedEntry.expiresAt);

  if (isFresh) return cachedEntry.request;

  if (cachedEntry) portfolioAnalysisRequests.delete(requestKey);

  const request = requestPortfolioAnalysis(newsId, assets);
  portfolioAnalysisRequests.set(requestKey, { request });

  void request.then(
    () => {
      const currentEntry = portfolioAnalysisRequests.get(requestKey);
      if (currentEntry?.request === request) {
        currentEntry.expiresAt = Date.now() + PORTFOLIO_ANALYSIS_CACHE_TIME;
      }
    },
    () => {
      if (portfolioAnalysisRequests.get(requestKey)?.request === request) {
        portfolioAnalysisRequests.delete(requestKey);
      }
    },
  );

  return request;
}

export function retryPortfolioAnalysis(
  newsId: string,
  assets: PortfolioAsset[],
): Promise<PortfolioAnalysisResponse> {
  portfolioAnalysisRequests.delete(createRequestKey(newsId, assets));
  return getPortfolioAnalysis(newsId, assets);
}
