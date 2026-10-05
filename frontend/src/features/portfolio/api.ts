import { request, requestWithCsrf } from '../../api/client';
import type {
  AssetCategory,
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
  PortfolioAnalysisSource,
  StockMarket,
  StockPrice,
  StockSearchItem,
} from './types';

const IMPACT_DIRECTIONS = new Set<PortfolioImpactDirection>(['POSITIVE', 'NEGATIVE', 'NEUTRAL']);
const IMPACT_LEVELS = new Set<PortfolioImpactLevel>(['HIGH', 'MEDIUM', 'LOW']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isMarket(value: unknown): value is StockMarket {
  return value === 'KOSPI' || value === 'KOSDAQ';
}

function isAssetCategory(value: unknown): value is AssetCategory {
  return value === 'STOCK' || value === 'ETF';
}

function isStock(value: unknown): value is StockSearchItem {
  return (
    isRecord(value) &&
    typeof value.assetId === 'number' &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    isAssetCategory(value.category) &&
    isMarket(value.market)
  );
}

function isStockPrice(value: unknown): value is StockPrice {
  return (
    isRecord(value) &&
    typeof value.assetId === 'number' &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    typeof value.baseDate === 'string' &&
    hasNumber(value, 'closePrice')
  );
}

function hasNumber(value: Record<string, unknown>, key: string) {
  return typeof value[key] === 'number' && Number.isFinite(value[key]);
}

function hasNullableNumber(value: Record<string, unknown>, key: string) {
  return value[key] === null || hasNumber(value, key);
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
    hasNullableNumber(value, 'dailyProfitLoss') &&
    hasNullableNumber(value, 'dailyReturnRate') &&
    hasNumber(value, 'totalProfitLoss') &&
    hasNumber(value, 'totalReturnRate') &&
    Array.isArray(value.holdings) &&
    value.holdings.every(isHoldingResult) &&
    Array.isArray(value.marketAllocations) &&
    value.marketAllocations.every(isMarketAllocation)
  );
}

function isPortfolioAnalysisSource(value: unknown): value is PortfolioAnalysisSource {
  return isRecord(value) && typeof value.title === 'string' && typeof value.url === 'string';
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
    typeof value.issueSummary === 'string' &&
    typeof value.expectedReaction === 'string' &&
    typeof value.outlook === 'string' &&
    typeof value.reason === 'string' &&
    typeof value.evidenceSentence === 'string' &&
    Array.isArray(value.sources) &&
    value.sources.every(isPortfolioAnalysisSource) &&
    Number.isInteger(value.rank)
  );
}

function isPortfolioAnalysisResponse(value: unknown): value is PortfolioAnalysisResponse {
  return (
    isRecord(value) &&
    Number.isInteger(value.totalAssetCount) &&
    Number.isInteger(value.analyzedAssetCount) &&
    typeof value.overallDirection === 'string' &&
    IMPACT_DIRECTIONS.has(value.overallDirection as PortfolioImpactDirection) &&
    hasNumber(value, 'overallScore') &&
    hasNumber(value, 'positiveImpactScore') &&
    hasNumber(value, 'negativeImpactScore') &&
    (value.analyzedAt === null || typeof value.analyzedAt === 'string') &&
    typeof value.overallImpact === 'string' &&
    Array.isArray(value.impacts) &&
    value.impacts.every(isPortfolioAssetImpact) &&
    Array.isArray(value.sources) &&
    value.sources.every(isPortfolioAnalysisSource)
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

export async function searchStocks(keyword: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ q: keyword });
  const response = await request<unknown>(`/api/stocks?${query.toString()}`, { signal });

  if (!isRecord(response) || !Array.isArray(response.stocks) || !response.stocks.every(isStock)) {
    throw new Error('종목 검색 응답 형식이 올바르지 않습니다.');
  }

  return response.stocks;
}

export async function getLatestStockPrice(assetId: number, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/stocks/${assetId}/price`, { signal });

  if (!isStockPrice(response)) {
    throw new Error('최근 종가 응답 형식이 올바르지 않습니다.');
  }

  return response;
}

export async function getStockPrices(
  assetIds: number[],
  from: string,
  to: string,
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({ from, to });
  assetIds.forEach((assetId) => query.append('assetIds', String(assetId)));
  const response = await request<unknown>(`/api/stocks/prices?${query.toString()}`, { signal });

  if (
    !isRecord(response) ||
    !Array.isArray(response.prices) ||
    !response.prices.every(isStockPrice)
  ) {
    throw new Error('기간별 종가 응답 형식이 올바르지 않습니다.');
  }

  return response.prices;
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

export async function analyzePortfolio(assets: PortfolioAsset[], signal?: AbortSignal) {
  const analysisRequest: PortfolioAnalysisRequest = { assets };
  const response = await request<unknown>('/api/portfolio/analysis', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(analysisRequest),
    signal,
  });

  if (!isPortfolioAnalysisResponse(response)) {
    throw new Error('포트폴리오 분석 API 응답 형식이 올바르지 않습니다.');
  }

  return response;
}
