import { ApiError, request, requestWithCsrf } from '../../api/client';
import type {
  PortfolioHoldingHistory,
  PortfolioTransactionRequest,
  PortfolioTransactionUpdateRequest,
  PortfolioTransactionResponse,
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
  PortfolioPerformancePoint,
  PortfolioPerformanceResponse,
  SavedPortfolioResponse,
  StockMarket,
  StockPrice,
  StockSearchItem,
} from './types';

const IMPACT_DIRECTIONS = new Set<PortfolioImpactDirection>(['POSITIVE', 'NEGATIVE', 'NEUTRAL']);
const IMPACT_LEVELS = new Set<PortfolioImpactLevel>(['HIGH', 'MEDIUM', 'LOW']);
type PortfolioPerformanceApiPoint = Omit<PortfolioPerformancePoint, 'recordedAt' | 'source'>;
type PortfolioPerformanceApiResponse = Omit<
  PortfolioPerformanceResponse,
  'points' | 'currentPoint'
> & {
  points: PortfolioPerformanceApiPoint[];
  currentPoint: PortfolioPerformanceApiPoint | null;
};

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
  if (!isStock(value) || typeof record.priceAvailable !== 'boolean') return false;
  return (
    hasNumber(value, 'quantity') &&
    hasNumber(value, 'averagePurchasePrice') &&
    hasNumber(value, 'purchaseAmount') &&
    (record.priceAvailable
      ? typeof record.baseDate === 'string' &&
        ['closePrice', 'evaluationAmount', 'profitLoss', 'returnRate', 'weight'].every((key) =>
          hasNumber(value, key),
        )
      : record.baseDate === null &&
        ['closePrice', 'evaluationAmount', 'profitLoss', 'returnRate', 'weight'].every(
          (key) => record[key] === null,
        ))
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

function isSavedPortfolioHolding(value: unknown): value is PortfolioHoldingInput {
  if (!isRecord(value)) return false;
  const quantity = value.quantity;
  const averagePurchasePrice = value.averagePurchasePrice;
  return (
    isStock(value) &&
    typeof quantity === 'number' &&
    Number.isSafeInteger(quantity) &&
    quantity > 0 &&
    typeof averagePurchasePrice === 'number' &&
    Number.isFinite(averagePurchasePrice) &&
    averagePurchasePrice > 0
  );
}

function isPerformancePoint(
  value: unknown,
  current = false,
): value is PortfolioPerformanceApiPoint {
  return (
    isRecord(value) &&
    typeof value.baseDate === 'string' &&
    (!current || value.priceBaseDate === null || typeof value.priceBaseDate === 'string') &&
    hasNumber(value, 'totalPurchaseAmount') &&
    hasNumber(value, 'totalEvaluationAmount') &&
    hasNumber(value, 'totalProfitLoss') &&
    hasNumber(value, 'totalReturnRate')
  );
}

function isPortfolioCalculation(value: unknown): value is PortfolioCalculation {
  return (
    isRecord(value) &&
    (value.baseDate === null || typeof value.baseDate === 'string') &&
    ['COMPLETE', 'MIXED_DATES', 'PARTIAL'].includes(String(value.pricingStatus)) &&
    Number.isInteger(value.pricedHoldingCount) &&
    Number.isInteger(value.unpricedHoldingCount) &&
    hasNumber(value, 'totalPurchaseAmount') &&
    hasNumber(value, 'totalEvaluationAmount') &&
    hasNullableNumber(value, 'dailyProfitLoss') &&
    hasNullableNumber(value, 'dailyReturnRate') &&
    hasNullableNumber(value, 'totalProfitLoss') &&
    hasNullableNumber(value, 'totalReturnRate') &&
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

export function isPortfolioAnalysisResponse(value: unknown): value is PortfolioAnalysisResponse {
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

export async function getSavedPortfolio(signal?: AbortSignal) {
  const response = await request<unknown>('/api/portfolio', { signal });

  if (
    !isRecord(response) ||
    !Array.isArray(response.holdings) ||
    !response.holdings.every(isSavedPortfolioHolding) ||
    !hasNumber(response, 'totalRealizedProfitLoss')
  ) {
    throw new Error('포트폴리오 응답 형식이 올바르지 않습니다.');
  }

  return response as SavedPortfolioResponse;
}

export async function addSavedPortfolioHolding(holding: PortfolioHoldingInput) {
  const response = await requestWithCsrf<unknown>('/api/portfolio/holdings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      assetId: holding.assetId,
      quantity: holding.quantity,
      averagePurchasePrice: holding.averagePurchasePrice,
    }),
  });

  if (!isSavedPortfolioHolding(response)) {
    throw new Error('보유 자산 추가 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

export async function updateSavedPortfolioHolding(holding: PortfolioHoldingInput) {
  const response = await requestWithCsrf<unknown>(`/api/portfolio/holdings/${holding.assetId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      quantity: holding.quantity,
      averagePurchasePrice: holding.averagePurchasePrice,
    }),
  });

  if (!isSavedPortfolioHolding(response)) {
    throw new Error('보유 자산 수정 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

export function removeSavedPortfolioHolding(assetId: number) {
  return requestWithCsrf<void>(`/api/portfolio/holdings/${assetId}`, { method: 'DELETE' });
}

export async function getPortfolioPerformance(from: string, to: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ from, to });
  const response = await request<unknown>(`/api/portfolio/performance?${query.toString()}`, {
    signal,
  });

  if (
    !isRecord(response) ||
    (response.status !== 'READY' && response.status !== 'INSUFFICIENT_DATA') ||
    (response.reason !== null && response.reason !== 'SNAPSHOT_DATA_INSUFFICIENT') ||
    (response.baseDate !== null && typeof response.baseDate !== 'string') ||
    (response.previousBaseDate !== null && typeof response.previousBaseDate !== 'string') ||
    !Array.isArray(response.points) ||
    !response.points.every((point) => isPerformancePoint(point)) ||
    (response.currentPoint !== null && !isPerformancePoint(response.currentPoint, true))
  ) {
    throw new Error('자산 추이 응답 형식이 올바르지 않습니다.');
  }

  const normalizePoint = (
    point: PortfolioPerformanceApiPoint,
    current: boolean,
  ): PortfolioPerformancePoint => ({
    ...point,
    recordedAt: `${point.baseDate}T15:30:00.000Z`,
    source: 'CLOSE',
    priceBaseDate: current ? point.priceBaseDate : undefined,
  });

  const performance = response as unknown as PortfolioPerformanceApiResponse;
  return {
    ...performance,
    points: performance.points.map((point) => normalizePoint(point, false)),
    currentPoint: performance.currentPoint ? normalizePoint(performance.currentPoint, true) : null,
  };
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

const HISTORY_TYPES = new Set([
  'ADDED',
  'UPDATED',
  'REMOVED',
  'OPENING_BALANCE',
  'ADJUSTED',
  'BUY',
  'SELL',
]);
function isHistory(value: unknown): value is PortfolioHoldingHistory {
  if (!isRecord(value)) return false;
  const transaction = value.changeType === 'BUY' || value.changeType === 'SELL';
  return (
    Number.isSafeInteger(value.historyId) &&
    Number.isSafeInteger(value.assetId) &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    HISTORY_TYPES.has(String(value.changeType)) &&
    hasNumber(value, 'quantity') &&
    hasNumber(value, 'averagePurchasePrice') &&
    typeof value.effectiveDate === 'string' &&
    typeof value.occurredAt === 'string' &&
    hasNumber(value, 'fee') &&
    hasNumber(value, 'tax') &&
    (transaction
      ? Number.isSafeInteger(value.transactionQuantity) &&
        Number(value.transactionQuantity) > 0 &&
        hasNumber(value, 'unitPrice')
      : hasNullableNumber(value, 'transactionQuantity') && hasNullableNumber(value, 'unitPrice'))
  );
}

export async function getPortfolioHistories(from: string, to: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ from, to });
  let response: unknown;
  try {
    response = await request<unknown>(`/api/portfolio/histories?${query}`, { signal });
  } catch (error) {
    if (error instanceof ApiError && error.code === 'PORTFOLIO_NOT_FOUND') return [];
    throw error;
  }
  if (!Array.isArray(response) || !response.every(isHistory)) {
    throw new Error('거래 내역 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

async function mutateTransaction(
  path: string,
  method: 'POST' | 'PATCH' | 'DELETE',
  body?: PortfolioTransactionRequest | PortfolioTransactionUpdateRequest,
) {
  const response = await requestWithCsrf<unknown>(path, {
    method,
    ...(body
      ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
      : {}),
  });
  if (
    !isRecord(response) ||
    !Number.isSafeInteger(response.transactionId) ||
    (response.holding !== null && !isSavedPortfolioHolding(response.holding)) ||
    !hasNumber(response, 'totalRealizedProfitLoss')
  ) {
    throw new Error(
      '거래 처리 결과를 확인하지 못했습니다. 내역을 새로 확인한 뒤 다시 시도해 주세요.',
    );
  }
  return response as PortfolioTransactionResponse;
}
export function createPortfolioTransaction(body: PortfolioTransactionRequest) {
  return mutateTransaction('/api/portfolio/transactions', 'POST', body);
}
export function updatePortfolioTransaction(id: number, body: PortfolioTransactionUpdateRequest) {
  return mutateTransaction(`/api/portfolio/transactions/${id}`, 'PATCH', body);
}
export function deletePortfolioTransaction(id: number) {
  return mutateTransaction(`/api/portfolio/transactions/${id}`, 'DELETE');
}
