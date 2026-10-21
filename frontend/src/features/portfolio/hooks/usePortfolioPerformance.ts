import { useEffect, useState } from 'react';
import { portfolioErrorType, trackPortfolioEvent } from '../analytics';
import { getPortfolioPerformance, preparePortfolioPerformance } from '../api';
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
  const [notice, setNotice] = useState('');
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
    setPoints([]);
  }, [enabled, holdingKey, period, revision]);

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      setError('');
      setNotice('');
      return;
    }

    const controller = new AbortController();
    let pollTimer: number | undefined;
    const to = todayInSeoul();
    const from = getTrendPeriodStart(to, period);
    setLoading(true);
    setError('');
    setNotice('');

    async function loadSaved() {
      try {
        const response = await getPortfolioPerformance(from, to, controller.signal);
        if (controller.signal.aborted) return;
        setPoints(
          [
            ...response.points.filter(
              (point) => point.baseDate !== response.currentPoint?.baseDate,
            ),
            ...(response.currentPoint ? [response.currentPoint] : []),
          ].sort((left, right) => left.baseDate.localeCompare(right.baseDate)),
        );
        setError('');
      } catch (caughtError) {
        if (controller.signal.aborted) return;
        trackPortfolioEvent('portfolio_dashboard_load_failed', {
          stage: 'chart',
          error_type: portfolioErrorType(caughtError),
        });
        setError(
          caughtError instanceof Error ? caughtError.message : '자산 추이를 불러오지 못했습니다.',
        );
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }

    const savedRequest = loadSaved();
    async function prepare(retry = false, wasProcessing = false) {
      try {
        const status = await preparePortfolioPerformance(from, to, retry, controller.signal);
        if (controller.signal.aborted) return;
        if (status === 'PROCESSING') {
          setNotice('과거 종가를 추가로 수집하고 있어요. 준비된 데이터부터 표시합니다.');
          pollTimer = window.setTimeout(() => void prepare(false, true), 2_000);
          return;
        }
        setNotice(
          status === 'FAILED'
            ? '일부 과거 종가를 가져오지 못했어요. 준비된 데이터만 표시합니다. 새로고침으로 다시 시도할 수 있어요.'
            : '',
        );
        if (wasProcessing) {
          // Do not let the initial response overwrite freshly collected snapshots.
          await savedRequest;
          if (!controller.signal.aborted) await loadSaved();
        }
      } catch {
        if (!controller.signal.aborted)
          setNotice('과거 종가 수집 상태를 확인하지 못했어요. 준비된 데이터만 표시합니다.');
      }
    }

    void prepare(retryCount > 0);
    return () => {
      controller.abort();
      window.clearTimeout(pollTimer);
    };
  }, [enabled, holdingKey, holdings.length, period, retryCount, revision]);

  return {
    points,
    loading,
    error,
    notice,
    retry: () => setRetryCount((count) => count + 1),
  };
}
