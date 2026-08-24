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

export function useStockCandidates(): StockCandidatesState {
  const [stocks, setStocks] = useState<StockCandidate[]>([]);
  const [baseDate, setBaseDate] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isFallback, setIsFallback] = useState(false);

  useEffect(() => {
    let ignore = false;

    async function loadStocks() {
      setLoading(true);
      setError(null);
      setIsFallback(false);

      try {
        const response = await getKosdaqTopStocks();

        if (!ignore) {
          setStocks(response.rankings);
          setBaseDate(response.baseDate);
          setIsFallback(false);
        }
      } catch (caughtError) {
        console.error('Failed to load KOSDAQ top stocks.', caughtError);

        if (!ignore) {
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
