import { useEffect, useState } from 'react';
import { portfolioErrorType, trackPortfolioEvent } from '../analytics';
import { getPortfolioHistories, getPortfolioPerformance } from '../api';
import { todayInSeoul } from '../transactionForm';
import type {
  PortfolioHoldingInput,
  PortfolioHoldingHistory,
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
  revision = 0,
  enabled = true,
) {
  const [points, setPoints] = useState<PortfolioPerformancePoint[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [histories, setHistories] = useState<PortfolioHoldingHistory[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
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

    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      setPoints([]);
      setLoading(false);
      setError('');
      setHistories([]);
      setHistoryLoading(false);
      setHistoryError('');
      return;
    }

    const controller = new AbortController();
    const to = new Date(`${todayInSeoul()}T12:00:00`);
    const from = getPeriodStart(to, period);
    setLoading(true);
    setError('');
    setHistories([]);
    setHistoryLoading(true);
    setHistoryError('');

    getPortfolioHistories(formatDate(from), formatDate(to), controller.signal)
      .then((items) => {
        if (controller.signal.aborted) return;
        setHistories(
          items.filter((item) => item.changeType === 'BUY' || item.changeType === 'SELL'),
        );
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted) return;
        setHistoryError(
          caught instanceof Error ? caught.message : '거래 표시를 불러오지 못했어요.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setHistoryLoading(false);
      });

    getPortfolioPerformance(formatDate(from), formatDate(to), controller.signal)
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
    histories,
    historyLoading,
    historyError,
    retry: () => setRetryCount((count) => count + 1),
  };
}
