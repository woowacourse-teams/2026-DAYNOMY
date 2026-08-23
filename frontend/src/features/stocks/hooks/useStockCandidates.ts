import { useEffect, useState } from 'react';
import { getKosdaqTopStocks } from '../api';
import { mockStockCandidates } from '../mock';
import type { StockCandidate } from '../types';

type StockCandidatesState = {
  stocks: StockCandidate[];
  baseDate: string | null;
  loading: boolean;
  error: string | null;
};

export function useStockCandidates(): StockCandidatesState {
  const [stocks, setStocks] = useState<StockCandidate[]>([]);
  const [baseDate, setBaseDate] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    async function loadStocks() {
      setLoading(true);
      setError(null);

      try {
        const response = await getKosdaqTopStocks();

        if (!ignore) {
          setStocks(response.rankings);
          setBaseDate(response.baseDate);
        }
      } catch {
        if (!ignore) {
          setStocks(mockStockCandidates.rankings);
          setBaseDate(mockStockCandidates.baseDate);
          setError(null);
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

  return { stocks, baseDate, loading, error };
}
