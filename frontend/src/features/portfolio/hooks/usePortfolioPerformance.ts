import { useEffect, useState } from 'react';
import { getStockPrices } from '../api';
import type { PortfolioHoldingInput, PortfolioPerformancePoint, StockPrice } from '../types';

export const PORTFOLIO_PERFORMANCE_STORAGE_KEY = 'daynomy:portfolio-performance:v1';
export const PORTFOLIO_PERFORMANCE_STATE_STORAGE_KEY = 'daynomy:portfolio-performance-state:v1';

type PortfolioPerformanceState = {
  version: 1;
  holdingKey: string;
  baseEvaluationAmount: number;
  baseReturnRate: number;
  lastCloseDate: string;
};

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function normalizePerformancePoint(value: unknown): PortfolioPerformancePoint | null {
  if (!value || typeof value !== 'object') return null;
  const point = value as Partial<PortfolioPerformancePoint>;
  if (
    typeof point.baseDate !== 'string' ||
    typeof point.totalPurchaseAmount !== 'number' ||
    typeof point.totalEvaluationAmount !== 'number' ||
    typeof point.totalProfitLoss !== 'number' ||
    typeof point.totalReturnRate !== 'number'
  ) {
    return null;
  }
  return {
    baseDate: point.baseDate,
    recordedAt:
      typeof point.recordedAt === 'string' ? point.recordedAt : `${point.baseDate}T15:30:00.000Z`,
    source: point.source === 'HOLDING_CHANGE' ? 'HOLDING_CHANGE' : 'CLOSE',
    totalPurchaseAmount: point.totalPurchaseAmount,
    totalEvaluationAmount: point.totalEvaluationAmount,
    totalProfitLoss: point.totalProfitLoss,
    totalReturnRate: point.totalReturnRate,
  };
}

function loadPoints() {
  try {
    const saved = localStorage.getItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY);
    if (!saved) return [];
    const parsed: unknown = JSON.parse(saved);
    return Array.isArray(parsed)
      ? parsed
          .flatMap((value) => {
            const point = normalizePerformancePoint(value);
            return point ? [point] : [];
          })
          .slice(-7)
      : [];
  } catch {
    return [];
  }
}

function loadPerformanceState(): PortfolioPerformanceState | null {
  try {
    const saved = localStorage.getItem(PORTFOLIO_PERFORMANCE_STATE_STORAGE_KEY);
    if (!saved) return null;
    const parsed = JSON.parse(saved) as Partial<PortfolioPerformanceState>;
    if (
      parsed.version !== 1 ||
      typeof parsed.holdingKey !== 'string' ||
      typeof parsed.baseEvaluationAmount !== 'number' ||
      parsed.baseEvaluationAmount < 0 ||
      typeof parsed.baseReturnRate !== 'number' ||
      typeof parsed.lastCloseDate !== 'string'
    ) {
      return null;
    }
    return {
      version: 1,
      holdingKey: parsed.holdingKey,
      baseEvaluationAmount: parsed.baseEvaluationAmount,
      baseReturnRate: parsed.baseReturnRate,
      lastCloseDate: parsed.lastCloseDate,
    };
  } catch {
    return null;
  }
}

function createHoldingKey(holdings: PortfolioHoldingInput[]) {
  return JSON.stringify(
    [...holdings]
      .sort((first, second) => first.assetId - second.assetId)
      .map(({ assetId, quantity, averagePurchasePrice }) => [
        assetId,
        quantity,
        averagePurchasePrice,
      ]),
  );
}

function savePerformance(points: PortfolioPerformancePoint[], state: PortfolioPerformanceState) {
  localStorage.setItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY, JSON.stringify(points));
  localStorage.setItem(PORTFOLIO_PERFORMANCE_STATE_STORAGE_KEY, JSON.stringify(state));
}

function calculatePoints(holdings: PortfolioHoldingInput[], prices: StockPrice[]) {
  const pricesByDate = new Map<string, Map<number, number>>();
  prices.forEach((price) => {
    const pricesByAsset = pricesByDate.get(price.baseDate) ?? new Map<number, number>();
    pricesByAsset.set(price.assetId, price.closePrice);
    pricesByDate.set(price.baseDate, pricesByAsset);
  });

  return [...pricesByDate.keys()].sort().flatMap<PortfolioPerformancePoint>((baseDate) => {
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
        recordedAt: `${baseDate}T15:30:00.000Z`,
        source: 'CLOSE',
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
      const performanceState = loadPerformanceState();
      if (performanceState && performanceState.holdingKey !== '[]') {
        setPoints((currentPoints) => {
          const previousPoint = currentPoints.at(-1);
          if (!previousPoint) return currentPoints;
          const recordedAt = new Date().toISOString();
          const holdingChangePoint: PortfolioPerformancePoint = {
            ...previousPoint,
            baseDate: formatDate(new Date()),
            recordedAt,
            source: 'HOLDING_CHANGE',
            totalPurchaseAmount: 0,
            totalEvaluationAmount: 0,
            totalProfitLoss: 0,
          };
          const nextPoints = [...currentPoints, holdingChangePoint].slice(-7);
          savePerformance(nextPoints, {
            ...performanceState,
            holdingKey: '[]',
            baseEvaluationAmount: 0,
            baseReturnRate: holdingChangePoint.totalReturnRate,
          });
          return nextPoints;
        });
      }
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    const to = new Date();
    const from = new Date(to);
    from.setDate(to.getDate() - 14);
    const holdingKey = createHoldingKey(holdings);
    const performanceState = loadPerformanceState();
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
        const calculatedPoints = calculatePoints(holdings, prices);
        const latestCalculatedPoint = calculatedPoints.at(-1);
        if (!latestCalculatedPoint) return;

        setPoints((currentPoints) => {
          if (performanceState?.holdingKey === holdingKey) {
            const closePoints = calculatedPoints.filter(
              (point) => point.baseDate > performanceState.lastCloseDate,
            );
            if (closePoints.length === 0) return currentPoints;

            const nextPoints = [...currentPoints, ...closePoints].slice(-7);
            const latestClosePoint = closePoints.at(-1);
            savePerformance(nextPoints, {
              ...performanceState,
              baseEvaluationAmount:
                latestClosePoint?.totalEvaluationAmount ?? performanceState.baseEvaluationAmount,
              baseReturnRate: latestClosePoint?.totalReturnRate ?? performanceState.baseReturnRate,
              lastCloseDate: latestClosePoint?.baseDate ?? performanceState.lastCloseDate,
            });
            return nextPoints;
          }

          const recordedAt = new Date().toISOString();
          const holdingChangePoint: PortfolioPerformancePoint = {
            ...latestCalculatedPoint,
            baseDate: formatDate(new Date()),
            recordedAt,
            source: 'HOLDING_CHANGE',
          };
          const nextPoints = [...(performanceState ? currentPoints : []), holdingChangePoint].slice(
            -7,
          );
          savePerformance(nextPoints, {
            version: 1,
            holdingKey,
            baseEvaluationAmount: latestCalculatedPoint.totalEvaluationAmount,
            baseReturnRate: latestCalculatedPoint.totalReturnRate,
            lastCloseDate: latestCalculatedPoint.baseDate,
          });
          return nextPoints;
        });
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
