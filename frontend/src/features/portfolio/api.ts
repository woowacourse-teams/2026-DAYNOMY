import { ApiError, request, requestWithCsrf } from '../../api/client';
import { isStock, isStockMarket } from '../stocks/api';
import type {
  PortfolioHoldingHistory,
  PortfolioTransactionRequest,
  PortfolioTransactionUpdateRequest,
  PortfolioTransactionResponse,
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
  SavedPortfolioAnalysisResponse,
} from './types';

const IMPACT_DIRECTIONS = new Set<PortfolioImpactDirection>(['POSITIVE', 'NEGATIVE', 'NEUTRAL']);
const IMPACT_LEVELS = new Set<PortfolioImpactLevel>(['HIGH', 'MEDIUM', 'LOW']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
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
    isStockMarket(value.market) &&
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

function isPerformancePoint(value: unknown): value is PortfolioPerformancePoint {
  return (
    isRecord(value) &&
    typeof value.baseDate === 'string' &&
    hasNumber(value, 'totalPurchaseAmount') &&
    hasNumber(value, 'totalEvaluationAmount') &&
    hasNumber(value, 'totalProfitLoss') &&
    hasNumber(value, 'totalReturnRate') &&
    hasNullableNumber(value, 'dailyProfitLoss') &&
    hasNullableNumber(value, 'dailyReturnRate')
  );
}

function isCurrentPerformancePoint(
  value: unknown,
): value is NonNullable<PortfolioPerformanceResponse['currentPoint']> {
  return (
    isPerformancePoint(value) &&
    'priceBaseDate' in value &&
    (value.priceBaseDate === null || typeof value.priceBaseDate === 'string')
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
  return (
    isRecord(value) &&
    typeof value.title === 'string' &&
    typeof value.url === 'string' &&
    /^https?:\/\//i.test(value.url) &&
    ['publishedOn', 'occurredOn', 'factQuote'].every(
      (key) => value[key] == null || typeof value[key] === 'string',
    ) &&
    (value.excerpt === undefined || typeof value.excerpt === 'string')
  );
}

function isPortfolioAssetImpact(value: unknown): value is PortfolioAssetImpactResponse {
  if (!isRecord(value)) return false;

  return (
    typeof value.assetName === 'string' &&
    hasNumber(value, 'weight') &&
    (value.direction === null ||
      (typeof value.direction === 'string' &&
        IMPACT_DIRECTIONS.has(value.direction as PortfolioImpactDirection))) &&
    (value.impactLevel === null ||
      (typeof value.impactLevel === 'string' &&
        IMPACT_LEVELS.has(value.impactLevel as PortfolioImpactLevel))) &&
    (value.direction === null) === (value.impactLevel === null) &&
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
    (value.overallDirection === null ||
      (typeof value.overallDirection === 'string' &&
        IMPACT_DIRECTIONS.has(value.overallDirection as PortfolioImpactDirection))) &&
    hasNullableNumber(value, 'overallScore') &&
    (value.coverage === undefined ||
      ['COMPLETE', 'PARTIAL', 'INSUFFICIENT'].includes(String(value.coverage))) &&
    (value.analyzedWeight === undefined || hasNumber(value, 'analyzedWeight')) &&
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
  const response = await request<unknown>('/api/users/me/portfolio', { signal });

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

export async function updateSavedPortfolioHolding(holding: PortfolioHoldingInput) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/holdings/${holding.assetId}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        quantity: holding.quantity,
        averagePurchasePrice: holding.averagePurchasePrice,
      }),
    },
  );

  if (!isSavedPortfolioHolding(response)) {
    throw new Error('보유 자산 수정 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

export function removeSavedPortfolioHolding(assetId: number) {
  return requestWithCsrf<void>(`/api/users/me/portfolio/holdings/${assetId}`, { method: 'DELETE' });
}

export async function getPortfolioPerformance(from: string, to: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ from, to });
  const response = await request<unknown>(
    `/api/users/me/portfolio/performance?${query.toString()}`,
    {
      signal,
      cache: 'no-store',
    },
  );

  if (
    !isRecord(response) ||
    (response.status !== 'READY' && response.status !== 'INSUFFICIENT_DATA') ||
    (response.reason !== null && response.reason !== 'SNAPSHOT_DATA_INSUFFICIENT') ||
    (response.baseDate !== null && typeof response.baseDate !== 'string') ||
    (response.previousBaseDate !== null && typeof response.previousBaseDate !== 'string') ||
    !Array.isArray(response.points) ||
    !response.points.every(isPerformancePoint) ||
    (response.currentPoint !== null && !isCurrentPerformancePoint(response.currentPoint))
  ) {
    throw new Error('자산 추이 응답 형식이 올바르지 않습니다.');
  }

  return response as PortfolioPerformanceResponse;
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
    (!String(value.assetCode).endsWith('.US') ||
      (value.currency === 'USD' &&
        hasNumber(value, 'exchangeRate') &&
        Number(value.exchangeRate) > 0)) &&
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

function isSavedPortfolioAnalysis(value: unknown): value is SavedPortfolioAnalysisResponse {
  return (
    isRecord(value) &&
    ['EMPTY', 'READY', 'PORTFOLIO_CHANGED', 'PROCESSING', 'RECHECK_REQUIRED', 'FAILED'].includes(
      String(value.status),
    ) &&
    (value.lastCheckOutcome === null ||
      ['UPDATED', 'UNCHANGED', 'UNCONFIRMED'].includes(String(value.lastCheckOutcome))) &&
    (value.analysisId === null || Number.isSafeInteger(value.analysisId)) &&
    typeof value.matchesCurrentPortfolio === 'boolean' &&
    ['lastSucceededAt', 'lastFailedAt', 'failureCode', 'changeReason'].every(
      (key) => value[key] === null || typeof value[key] === 'string',
    ) &&
    (value.analysis === null || isPortfolioAnalysisResponse(value.analysis))
  );
}

export async function getSavedPortfolioAnalysis(signal?: AbortSignal) {
  const response = await request<unknown>('/api/users/me/portfolio/analysis', { signal });
  if (!isSavedPortfolioAnalysis(response))
    throw new Error('저장된 분석 응답 형식이 올바르지 않아요.');
  return response;
}

export async function updateSavedPortfolioAnalysis(refresh: boolean, signal?: AbortSignal) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/analysis${refresh ? '/refresh' : ''}`,
    { method: 'POST', signal },
  );
  if (!isSavedPortfolioAnalysis(response))
    throw new Error('분석 갱신 응답 형식이 올바르지 않아요.');
  return response;
}
