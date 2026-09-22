import { request, requestWithCsrf } from '../../api/client';
import type {
  MarketAllocation,
  PortfolioCalculation,
  PortfolioHoldingInput,
  PortfolioHoldingResult,
  StockMarket,
  StockSearchItem,
} from './types';

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
