import { useEffect, useState } from 'react';
import { getStockPrices } from '../api';
import type { PortfolioHoldingInput, PortfolioPerformancePoint, StockPrice } from '../types';

export const PORTFOLIO_PERFORMANCE_STORAGE_KEY = 'daynomy:portfolio-performance:v1';

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function isPerformancePoint(value: unknown): value is PortfolioPerformancePoint {
  if (!value || typeof value !== 'object') return false;
  const point = value as Partial<PortfolioPerformancePoint>;
  return (
    typeof point.baseDate === 'string' &&
    typeof point.totalPurchaseAmount === 'number' &&
    typeof point.totalEvaluationAmount === 'number' &&
    typeof point.totalProfitLoss === 'number' &&
    typeof point.totalReturnRate === 'number'
  );
}

function loadPoints() {
  try {
    const saved = localStorage.getItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY);
    if (!saved) return [];
    const parsed: unknown = JSON.parse(saved);
    return Array.isArray(parsed) ? parsed.filter(isPerformancePoint).slice(-7) : [];
  } catch {
    return [];
  }
}

function calculatePoints(holdings: PortfolioHoldingInput[], prices: StockPrice[]) {
  const pricesByDate = new Map<string, Map<number, number>>();
  prices.forEach((price) => {
    const pricesByAsset = pricesByDate.get(price.baseDate) ?? new Map<number, number>();
    pricesByAsset.set(price.assetId, price.closePrice);
    pricesByDate.set(price.baseDate, pricesByAsset);
  });

  return [...pricesByDate.keys()]
    .sort()
    .slice(-7)
    .flatMap<PortfolioPerformancePoint>((baseDate) => {
      const datePrices = pricesByDate.get(baseDate);
      if (!datePrices) return [];

      let totalPurchaseAmount = 0;
      let totalEvaluationAmount = 0;
      for (const holding of holdings) {
        const closePrice = datePrices.get(holding.assetId);
        if (closePrice === undefined) return [];
        totalPurchaseAmount += holding.averagePurchasePrice * holding.quantity;
        totalEvaluationAmount += closePrice * holding.quantity;
      }
      const totalProfitLoss = totalEvaluationAmount - totalPurchaseAmount;
      return [
        {
          baseDate,
          totalPurchaseAmount,
          totalEvaluationAmount,
          totalProfitLoss,
          totalReturnRate:
            totalPurchaseAmount === 0 ? 0 : (totalProfitLoss / totalPurchaseAmount) * 100,
        },
      ];
    });
}

export function usePortfolioPerformance(holdings: PortfolioHoldingInput[]) {
  const [points, setPoints] = useState<PortfolioPerformancePoint[]>(loadPoints);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (holdings.length === 0) {
      setPoints([]);
      localStorage.removeItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY);
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    const to = new Date();
    const from = new Date(to);
    from.setDate(to.getDate() - 14);
    setLoading(true);
    setError('');
    getStockPrices(
      holdings.map((holding) => holding.assetId),
      formatDate(from),
      formatDate(to),
      controller.signal,
    )
      .then((prices) => {
        if (controller.signal.aborted) return;
        const nextPoints = calculatePoints(holdings, prices);
        setPoints(nextPoints);
        localStorage.setItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY, JSON.stringify(nextPoints));
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        setError(
          caughtError instanceof Error ? caughtError.message : '수익률을 불러오지 못했습니다.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [holdings, retryCount]);

  return { points, loading, error, retry: () => setRetryCount((count) => count + 1) };
}
