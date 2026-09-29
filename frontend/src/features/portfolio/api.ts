import { getApiUrl, request, requestWithCsrf } from '../../api/client';
import type {
  MarketAllocation,
  PortfolioAnalysisRequest,
  PortfolioAnalysisResponse,
  PortfolioAsset,
  PortfolioAssetImpactResponse,
  PortfolioCalculation,
  PortfolioHoldingInput,
  PortfolioHoldingResult,
  PortfolioImpactDirection,
  PortfolioImpactLevel,
  StockMarket,
  StockSearchItem,
} from './types';

const PORTFOLIO_ANALYSIS_CACHE_TIME = 5 * 60 * 1000;
export const PORTFOLIO_ANALYSIS_STORAGE_KEY = 'daynomy:portfolio-analysis:v1';

type PortfolioAnalysisCacheEntry = {
  request: Promise<PortfolioAnalysisResponse>;
  expiresAt?: number;
  cleanupTimer?: ReturnType<typeof setTimeout>;
};

const portfolioAnalysisRequests = new Map<string, PortfolioAnalysisCacheEntry>();
const IMPACT_DIRECTIONS = new Set<PortfolioImpactDirection>(['POSITIVE', 'NEGATIVE', 'NEUTRAL']);
const IMPACT_LEVELS = new Set<PortfolioImpactLevel>(['HIGH', 'MEDIUM', 'LOW']);

type PortfolioErrorResponse = {
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isMarket(value: unknown): value is StockMarket {
  return value === 'KOSPI' || value === 'KOSDAQ';
}

function isStock(value: unknown): value is StockSearchItem {
  return (
    isRecord(value) &&
    typeof value.assetId === 'number' &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    isMarket(value.market)
  );
}

function hasNumber(value: Record<string, unknown>, key: string) {
  return typeof value[key] === 'number' && Number.isFinite(value[key]);
}

function isHoldingResult(value: unknown): value is PortfolioHoldingResult {
  if (!isRecord(value)) return false;
  const record = value;

  return (
    isStock(value) &&
    typeof record.baseDate === 'string' &&
    hasNumber(record, 'quantity') &&
    hasNumber(record, 'averagePurchasePrice') &&
    hasNumber(record, 'closePrice') &&
    hasNumber(record, 'purchaseAmount') &&
    hasNumber(record, 'evaluationAmount') &&
    hasNumber(record, 'profitLoss') &&
    hasNumber(record, 'returnRate') &&
    hasNumber(record, 'weight')
  );
}

function isMarketAllocation(value: unknown): value is MarketAllocation {
  return (
    isRecord(value) &&
    isMarket(value.market) &&
    hasNumber(value, 'evaluationAmount') &&
    hasNumber(value, 'weight')
  );
}

function isPortfolioCalculation(value: unknown): value is PortfolioCalculation {
  return (
    isRecord(value) &&
    typeof value.baseDate === 'string' &&
    hasNumber(value, 'totalPurchaseAmount') &&
    hasNumber(value, 'totalEvaluationAmount') &&
    hasNumber(value, 'totalProfitLoss') &&
    hasNumber(value, 'totalReturnRate') &&
    Array.isArray(value.holdings) &&
    value.holdings.every(isHoldingResult) &&
    Array.isArray(value.marketAllocations) &&
    value.marketAllocations.every(isMarketAllocation)
  );
}

function isPortfolioAssetImpact(value: unknown): value is PortfolioAssetImpactResponse {
  if (!isRecord(value)) return false;

  return (
    typeof value.assetName === 'string' &&
    hasNumber(value, 'weight') &&
    typeof value.direction === 'string' &&
    IMPACT_DIRECTIONS.has(value.direction as PortfolioImpactDirection) &&
    typeof value.impactLevel === 'string' &&
    IMPACT_LEVELS.has(value.impactLevel as PortfolioImpactLevel) &&
    typeof value.summary === 'string' &&
    typeof value.reason === 'string' &&
    typeof value.evidenceSentence === 'string' &&
    Number.isInteger(value.rank)
  );
}

function isPortfolioAnalysisResponse(value: unknown): value is PortfolioAnalysisResponse {
  return (
    isRecord(value) &&
    Number.isInteger(value.totalAssetCount) &&
    Number.isInteger(value.analyzedAssetCount) &&
    Array.isArray(value.impacts) &&
    value.impacts.every(isPortfolioAssetImpact)
  );
}

function normalizePortfolioAssets(assets: PortfolioAsset[]) {
  return assets
    .map((asset) => ({
      assetName: asset.assetName.trim().toLocaleLowerCase(),
      weight: Number(asset.weight.toFixed(2)),
    }))
    .sort(
      (left, right) => left.assetName.localeCompare(right.assetName) || left.weight - right.weight,
    );
}

export function createPortfolioSnapshotKey(assets: PortfolioAsset[]) {
  return JSON.stringify(normalizePortfolioAssets(assets));
}

function createPortfolioAnalysisRequestKey(newsId: string, assets: PortfolioAsset[]) {
  return JSON.stringify([newsId, normalizePortfolioAssets(assets)]);
}

function savePortfolioAnalysis(requestKey: string, analysis: PortfolioAnalysisResponse) {
  try {
    const saved = localStorage.getItem(PORTFOLIO_ANALYSIS_STORAGE_KEY);
    const parsed: unknown = saved ? JSON.parse(saved) : {};
    const storedAnalyses = isRecord(parsed) ? parsed : {};

    localStorage.setItem(
      PORTFOLIO_ANALYSIS_STORAGE_KEY,
      JSON.stringify({ ...storedAnalyses, [requestKey]: analysis }),
    );
  } catch {
    // 브라우저 저장소를 사용할 수 없어도 완료된 분석 결과는 반환한다.
  }
}

function loadPortfolioAnalysis(requestKey: string) {
  try {
    const saved = localStorage.getItem(PORTFOLIO_ANALYSIS_STORAGE_KEY);
    if (!saved) return null;

    const parsed: unknown = JSON.parse(saved);
    if (!isRecord(parsed)) return null;

    const analysis = parsed[requestKey];
    return isPortfolioAnalysisResponse(analysis) ? analysis : null;
  } catch {
    return null;
  }
}

function deleteStoredPortfolioAnalysis(requestKey: string) {
  try {
    const saved = localStorage.getItem(PORTFOLIO_ANALYSIS_STORAGE_KEY);
    if (!saved) return;

    const parsed: unknown = JSON.parse(saved);
    if (!isRecord(parsed) || !(requestKey in parsed)) return;

    const { [requestKey]: _deleted, ...remainingAnalyses } = parsed;
    localStorage.setItem(PORTFOLIO_ANALYSIS_STORAGE_KEY, JSON.stringify(remainingAnalyses));
  } catch {
    // 브라우저 저장소를 사용할 수 없어도 재분석 요청은 계속한다.
  }
}

function deletePortfolioAnalysisCacheEntry(requestKey: string) {
  const cachedEntry = portfolioAnalysisRequests.get(requestKey);
  if (cachedEntry?.cleanupTimer) clearTimeout(cachedEntry.cleanupTimer);
  portfolioAnalysisRequests.delete(requestKey);
}

async function requestPortfolioAnalysis(
  newsId: string,
  assets: PortfolioAsset[],
): Promise<PortfolioAnalysisResponse> {
  const analysisRequest: PortfolioAnalysisRequest = { assets };
  const response = await fetch(
    getApiUrl(`/api/news/${encodeURIComponent(newsId)}/portfolio-analysis`),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(analysisRequest),
    },
  );

  if (!response.ok) {
    const contentType = response.headers.get('content-type') ?? '';
    const error = contentType.includes('application/json')
      ? ((await response.json()) as PortfolioErrorResponse)
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

export async function searchStocks(keyword: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ q: keyword });
  const response = await request<unknown>(`/api/stocks?${query.toString()}`, { signal });

  if (!isRecord(response) || !Array.isArray(response.stocks) || !response.stocks.every(isStock)) {
    throw new Error('종목 검색 응답 형식이 올바르지 않습니다.');
  }

  return response.stocks;
}

export async function calculatePortfolio(holdings: PortfolioHoldingInput[], signal?: AbortSignal) {
  const response = await requestWithCsrf<unknown>('/api/portfolio/calculate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      holdings: holdings.map(({ assetId, quantity, averagePurchasePrice }) => ({
        assetId,
        quantity,
        averagePurchasePrice,
      })),
    }),
    signal,
  });

  if (!isPortfolioCalculation(response)) {
    throw new Error('포트폴리오 계산 응답 형식이 올바르지 않습니다.');
  }

  return response;
}

export function getPortfolioAnalysis(
  newsId: string,
  assets: PortfolioAsset[],
): Promise<PortfolioAnalysisResponse> {
  const requestKey = createPortfolioAnalysisRequestKey(newsId, assets);
  const cachedEntry = portfolioAnalysisRequests.get(requestKey);
  const isFresh = cachedEntry && (!cachedEntry.expiresAt || Date.now() < cachedEntry.expiresAt);

  if (isFresh) return cachedEntry.request;
  if (cachedEntry) deletePortfolioAnalysisCacheEntry(requestKey);

  const storedAnalysis = loadPortfolioAnalysis(requestKey);
  if (storedAnalysis) return Promise.resolve(storedAnalysis);

  const analysisRequest = requestPortfolioAnalysis(newsId, assets);
  portfolioAnalysisRequests.set(requestKey, { request: analysisRequest });

  void analysisRequest.then(
    (analysis) => {
      savePortfolioAnalysis(requestKey, analysis);
      const currentEntry = portfolioAnalysisRequests.get(requestKey);
      if (currentEntry?.request === analysisRequest) {
        currentEntry.expiresAt = Date.now() + PORTFOLIO_ANALYSIS_CACHE_TIME;
        currentEntry.cleanupTimer = setTimeout(() => {
          if (portfolioAnalysisRequests.get(requestKey)?.request === analysisRequest) {
            portfolioAnalysisRequests.delete(requestKey);
          }
        }, PORTFOLIO_ANALYSIS_CACHE_TIME);
      }
    },
    () => {
      if (portfolioAnalysisRequests.get(requestKey)?.request === analysisRequest) {
        deletePortfolioAnalysisCacheEntry(requestKey);
      }
    },
  );

  return analysisRequest;
}

export function retryPortfolioAnalysis(
  newsId: string,
  assets: PortfolioAsset[],
): Promise<PortfolioAnalysisResponse> {
  const requestKey = createPortfolioAnalysisRequestKey(newsId, assets);
  deletePortfolioAnalysisCacheEntry(requestKey);
  deleteStoredPortfolioAnalysis(requestKey);
  return getPortfolioAnalysis(newsId, assets);
}
