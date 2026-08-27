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

        if (!ignore) {
          stockCandidatesCache = {
            stocks: response.rankings,
            baseDate: response.baseDate,
            isFallback: false,
          };
          setStocks(response.rankings);
          setBaseDate(response.baseDate);
          setIsFallback(false);
        }
      } catch (caughtError) {
        console.error('Failed to load KOSDAQ top stocks.', caughtError);

        if (!ignore) {
          stockCandidatesCache = {
            stocks: mockStockCandidates.rankings,
            baseDate: mockStockCandidates.baseDate,
            isFallback: true,
          };
          setStocks(mockStockCandidates.rankings);
          setBaseDate(mockStockCandidates.baseDate);
          setError(
            caughtError instanceof Error ? caughtError.message : '종목 목록을 불러오지 못했습니다.',
          );
          setIsFallback(true);
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
