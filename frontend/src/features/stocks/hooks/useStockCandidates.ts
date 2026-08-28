import { useEffect, useState } from 'react';
import { getKosdaqTopStocks } from '../api';
import { mockStockCandidates } from '../mock';
import type { StockCandidate } from '../types';

type StockCandidatesState = {
  stocks: StockCandidate[];
  baseDate: string | null;
  loading: boolean;
  error: string | null;
  isFallback: boolean;
};

type StockCandidatesCache = Pick<StockCandidatesState, 'stocks' | 'baseDate' | 'isFallback'>;

let stockCandidatesCache: StockCandidatesCache | null = null;

function getMockStockCandidatesCache(): StockCandidatesCache {
  return {
    stocks: mockStockCandidates.rankings,
    baseDate: mockStockCandidates.baseDate,
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
    isFallback: false,
  };
}

export function useStockCandidates(): StockCandidatesState {
  const [stocks, setStocks] = useState<StockCandidate[]>(() => stockCandidatesCache?.stocks ?? []);
  const [baseDate, setBaseDate] = useState<string | null>(
    () => stockCandidatesCache?.baseDate ?? null,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isFallback, setIsFallback] = useState(() => stockCandidatesCache?.isFallback ?? false);

  useEffect(() => {
    let ignore = false;

    async function loadStocks() {
      setLoading(stockCandidatesCache === null);
      setError(null);

      try {
        const response = await getKosdaqTopStocks();
        const nextCache = resolveStockCandidatesCache(response);

        if (!ignore) {
          stockCandidatesCache = nextCache;
          setStocks(nextCache.stocks);
          setBaseDate(nextCache.baseDate);
          setIsFallback(nextCache.isFallback);
        }
      } catch (caughtError) {
        console.error('Failed to load KOSDAQ top stocks.', caughtError);

        if (!ignore) {
          const nextCache = getMockStockCandidatesCache();

          stockCandidatesCache = nextCache;
          setStocks(nextCache.stocks);
          setBaseDate(nextCache.baseDate);
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
  }, []);

  return { stocks, baseDate, loading, error, isFallback };
}
