import { useEffect, useState } from 'react';
import type {
  PortfolioCalculation,
  PortfolioHoldingInput,
  PortfolioPerformancePoint,
} from '../types';

export const PORTFOLIO_PERFORMANCE_STORAGE_KEY = 'daynomy:portfolio-performance:v1';

function isPerformancePoint(value: unknown): value is PortfolioPerformancePoint {
  if (!value || typeof value !== 'object') return false;
  const point = value as Partial<PortfolioPerformancePoint>;
  return (
    typeof point.occurredAt === 'string' &&
    !Number.isNaN(Date.parse(point.occurredAt)) &&
    typeof point.holdingsKey === 'string' &&
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
    return Array.isArray(parsed) ? parsed.filter(isPerformancePoint).slice(-20) : [];
  } catch {
    return [];
  }
}

function holdingsKey(holdings: PortfolioHoldingInput[]) {
  return JSON.stringify(
    holdings
      .map(({ assetId, quantity, averagePurchasePrice }) => ({
        assetId,
        quantity,
        averagePurchasePrice,
      }))
      .toSorted((left, right) => left.assetId - right.assetId),
  );
}

export function usePortfolioPerformance(
  calculation: PortfolioCalculation | null,
  holdings: PortfolioHoldingInput[],
) {
  const [points, setPoints] = useState<PortfolioPerformancePoint[]>(loadPoints);

  useEffect(() => {
    localStorage.setItem(PORTFOLIO_PERFORMANCE_STORAGE_KEY, JSON.stringify(points));
  }, [points]);

  useEffect(() => {
    if (!calculation || holdings.length === 0) return;
    const nextHoldingsKey = holdingsKey(holdings);
    if (holdingsKey(calculation.holdings) !== nextHoldingsKey) return;
    setPoints((current) => {
      if (current.at(-1)?.holdingsKey === nextHoldingsKey) return current;
      return [
        ...current,
        {
          occurredAt: new Date().toISOString(),
          holdingsKey: nextHoldingsKey,
          baseDate: calculation.baseDate,
          totalPurchaseAmount: calculation.totalPurchaseAmount,
          totalEvaluationAmount: calculation.totalEvaluationAmount,
          totalProfitLoss: calculation.totalProfitLoss,
          totalReturnRate: calculation.totalReturnRate,
        },
      ].slice(-20);
    });
  }, [calculation, holdings]);

  return { points: points.slice(-7), loading: false, error: '', retry: () => undefined };
}
