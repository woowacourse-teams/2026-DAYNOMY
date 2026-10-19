import { useEffect, useState } from 'react';
import { portfolioErrorType, trackPortfolioEvent } from '../analytics';
import { getPortfolioPerformance } from '../api';
import { todayInSeoul } from '../transactionForm';
import { getTrendPeriodStart } from '../trendDates';
import type {
  PortfolioHoldingInput,
  PortfolioPerformancePoint,
  PortfolioTrendPeriod,
} from '../types';

export function usePortfolioPerformance(
  period: PortfolioTrendPeriod,
  holdings: PortfolioHoldingInput[],
  revision = 0,
  enabled = true,
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
    if (!enabled) return;

    function refreshWhenVisible() {
      if (document.visibilityState === 'visible') setRetryCount((count) => count + 1);
    }

    // A visible dashboard must pick up a new Seoul date and newly saved snapshots.
    let lastDate = todayInSeoul();
    let elapsedMinutes = 0;
    const timer = window.setInterval(() => {
      if (document.visibilityState !== 'visible') return;
      elapsedMinutes += 1;
      const date = todayInSeoul();
      if (date !== lastDate || elapsedMinutes >= 5) {
        lastDate = date;
        elapsedMinutes = 0;
        refreshWhenVisible();
      }
    }, 60_000);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    window.addEventListener('online', refreshWhenVisible);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
      window.removeEventListener('online', refreshWhenVisible);
    };
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      setPoints([]);
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    const to = todayInSeoul();
    const from = getTrendPeriodStart(to, period);
    setLoading(true);
    setError('');

    getPortfolioPerformance(from, to, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setPoints(
          [
            ...response.points.filter(
              (point) => point.baseDate !== response.currentPoint?.baseDate,
            ),
            ...(response.currentPoint ? [response.currentPoint] : []),
          ].sort((left, right) => left.baseDate.localeCompare(right.baseDate)),
        );
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        trackPortfolioEvent('portfolio_dashboard_load_failed', {
          stage: 'chart',
          error_type: portfolioErrorType(caughtError),
        });
        setPoints([]);
        setError(
          caughtError instanceof Error ? caughtError.message : '자산 추이를 불러오지 못했습니다.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [enabled, holdingKey, holdings.length, period, retryCount, revision]);

  return {
    points,
    loading,
    error,
    retry: () => setRetryCount((count) => count + 1),
  };
}
