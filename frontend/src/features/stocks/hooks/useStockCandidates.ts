import { useEffect, useState } from 'react';
import { getKosdaqTopStocks } from '../api';
import { mockStockCandidates } from '../mock';
import type { StockCandidate } from '../types';

type StockCandidatesState = {
  stocks: StockCandidate[];
  baseDate: string | null;
  totalPages: number;
  totalElements: number;
  loading: boolean;
  error: string | null;
  isFallback: boolean;
};

type StockCandidatesCache = Pick<
  StockCandidatesState,
  'stocks' | 'baseDate' | 'totalPages' | 'totalElements' | 'isFallback'
>;

const stockCandidatesCache = new Map<string, StockCandidatesCache>();

function getCacheKey(page: number, size: number) {
  return `${page}:${size}`;
}

function getMockStockCandidatesCache(): StockCandidatesCache {
  return {
    stocks: mockStockCandidates.rankings,
    baseDate: mockStockCandidates.baseDate,
    totalPages: 1,
    totalElements: mockStockCandidates.rankings.length,
    isFallback: true,
  };
}

export function resolveStockCandidatesCache(
  response: Awaited<ReturnType<typeof getKosdaqTopStocks>>,
): StockCandidatesCache {
  if (response.rankings.length === 0) {
    return getMockStockCandidatesCache();
  }

  return {
    stocks: response.rankings,
    baseDate: response.baseDate,
    totalPages: response.totalPages,
    totalElements: response.totalElements,
    isFallback: false,
  };
}

export function useStockCandidates(page: number, size: number): StockCandidatesState {
  const cacheKey = getCacheKey(page, size);
  const cached = stockCandidatesCache.get(cacheKey);
  const [stocks, setStocks] = useState<StockCandidate[]>(() => cached?.stocks ?? []);
  const [baseDate, setBaseDate] = useState<string | null>(() => cached?.baseDate ?? null);
  const [totalPages, setTotalPages] = useState(() => cached?.totalPages ?? 1);
  const [totalElements, setTotalElements] = useState(() => cached?.totalElements ?? 0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isFallback, setIsFallback] = useState(() => cached?.isFallback ?? false);

  useEffect(() => {
    let ignore = false;
    const currentCache = stockCandidatesCache.get(cacheKey);

    async function loadStocks() {
      setLoading(currentCache === undefined);
      setError(null);

      if (currentCache) {
        setStocks(currentCache.stocks);
        setBaseDate(currentCache.baseDate);
        setTotalPages(currentCache.totalPages);
        setTotalElements(currentCache.totalElements);
        setIsFallback(currentCache.isFallback);
      }

      try {
        const response = await getKosdaqTopStocks(page, size);
        const nextCache = resolveStockCandidatesCache(response);

        if (!ignore) {
          stockCandidatesCache.set(cacheKey, nextCache);
          setStocks(nextCache.stocks);
          setBaseDate(nextCache.baseDate);
          setTotalPages(nextCache.totalPages);
          setTotalElements(nextCache.totalElements);
          setIsFallback(nextCache.isFallback);
        }
      } catch (caughtError) {
        console.error('Failed to load KOSDAQ top stocks.', caughtError);

        if (!ignore) {
          const nextCache = getMockStockCandidatesCache();

          stockCandidatesCache.set(cacheKey, nextCache);
          setStocks(nextCache.stocks);
          setBaseDate(nextCache.baseDate);
          setTotalPages(nextCache.totalPages);
          setTotalElements(nextCache.totalElements);
          setError(
            caughtError instanceof Error ? caughtError.message : '종목 목록을 불러오지 못했습니다.',
          );
          setIsFallback(nextCache.isFallback);
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    loadStocks();

    return () => {
      ignore = true;
    };
  }, [cacheKey, page, size]);

  return { stocks, baseDate, totalPages, totalElements, loading, error, isFallback };
}
