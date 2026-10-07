import { useEffect, useState } from 'react';
import { getPortfolioPerformance } from '../api';
import type {
  PortfolioHoldingInput,
  PortfolioPerformancePoint,
  PortfolioTrendPeriod,
} from '../types';

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function getPeriodStart(to: Date, period: PortfolioTrendPeriod) {
  const from = new Date(to);
  if (period === 'YTD') {
    from.setMonth(0, 1);
    return from;
  }

  const months = period === '1M' ? 1 : period === '6M' ? 6 : period === '1Y' ? 12 : 60;
  const day = from.getDate();
  from.setDate(1);
  from.setMonth(from.getMonth() - months);
  const lastDay = new Date(from.getFullYear(), from.getMonth() + 1, 0).getDate();
  from.setDate(Math.min(day, lastDay));
  return from;
}

export function usePortfolioPerformance(
  period: PortfolioTrendPeriod,
  holdings: PortfolioHoldingInput[],
) {
  const [points, setPoints] = useState<PortfolioPerformancePoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const holdingKey = holdings
    .map(({ assetId, quantity, averagePurchasePrice }) =>
      [assetId, quantity, averagePurchasePrice].join(':'),
    )
    .sort()
    .join('|');

  useEffect(() => {
    if (holdings.length === 0) {
      setPoints([]);
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    const to = new Date();
    const from = getPeriodStart(to, period);
    setLoading(true);
    setError('');

    getPortfolioPerformance(formatDate(from), formatDate(to), controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setPoints(
          [
            ...response.points.filter((point) => point.baseDate !== response.currentPoint.baseDate),
            response.currentPoint,
          ].sort((left, right) => left.baseDate.localeCompare(right.baseDate)),
        );
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        setPoints([]);
        setError(
          caughtError instanceof Error ? caughtError.message : '자산 추이를 불러오지 못했습니다.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [holdingKey, holdings.length, period, retryCount]);

  return { points, loading, error, retry: () => setRetryCount((count) => count + 1) };
}
