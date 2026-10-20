import { request } from '../../api/client';
import type { StockPrice, StockSearchItem } from './types';

export function isStockMarket(value: unknown): value is import('./types').StockMarket {
  return ['KOSPI', 'KOSDAQ', 'NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS'].includes(
    String(value),
  );
}

export function isStock(value: unknown): value is StockSearchItem {
  if (typeof value !== 'object' || value === null) return false;
  const stock = value as Record<string, unknown>;
  return (
    Number.isSafeInteger(stock.assetId) &&
    Number(stock.assetId) > 0 &&
    typeof stock.assetCode === 'string' &&
    typeof stock.name === 'string' &&
    (stock.category === 'STOCK' || stock.category === 'ETF') &&
    isStockMarket(stock.market)
  );
}

export async function searchStocks(
  keyword: string,
  signal?: AbortSignal,
  country: 'KR' | 'US' = 'KR',
) {
  const query = new URLSearchParams({ q: keyword });
  if (country === 'US') query.set('country', country);
  const response = await request<unknown>(`/api/stocks?${query}`, { signal });
  if (
    typeof response !== 'object' ||
    response === null ||
    !('stocks' in response) ||
    !Array.isArray(response.stocks) ||
    !response.stocks.every(isStock)
  ) {
    throw new Error('종목 검색 응답 형식이 올바르지 않습니다.');
  }
  return response.stocks;
}

export async function getLatestStockPrice(
  assetId: number,
  signal?: AbortSignal,
): Promise<StockPrice> {
  const value = await request<unknown>(`/api/stocks/${assetId}/price`, { signal });
  if (typeof value !== 'object' || value === null)
    throw new Error('최근 종가 응답 형식이 올바르지 않습니다.');
  const price = value as Record<string, unknown>;
  if (
    !Number.isSafeInteger(price.assetId) ||
    Number(price.assetId) <= 0 ||
    typeof price.assetCode !== 'string' ||
    typeof price.name !== 'string' ||
    typeof price.baseDate !== 'string' ||
    typeof price.closePrice !== 'number' ||
    !Number.isFinite(price.closePrice)
  ) {
    throw new Error('최근 종가 응답 형식이 올바르지 않습니다.');
  }
  return price as StockPrice;
}

export async function getUsdKrwRate(
  date: string,
  signal?: AbortSignal,
): Promise<{ baseDate: string; rate: number }> {
  const value = await request<unknown>(
    `/api/exchange-rates/usd-krw?${new URLSearchParams({ date })}`,
    { signal },
  );
  if (
    typeof value !== 'object' ||
    value === null ||
    !('baseDate' in value) ||
    !('rate' in value) ||
    typeof value.baseDate !== 'string' ||
    typeof value.rate !== 'number' ||
    !Number.isFinite(value.rate) ||
    value.rate <= 0
  )
    throw new Error('환율 응답 형식이 올바르지 않습니다.');
  return { baseDate: value.baseDate, rate: value.rate };
}
