import { useEffect, useState } from 'react';
import type { PortfolioHoldingInput, StockMarket } from '../types';

export const PORTFOLIO_STORAGE_KEY = 'daynomy:portfolio-holdings:v1';

function isMarket(value: unknown): value is StockMarket {
  return value === 'KOSPI' || value === 'KOSDAQ';
}

function isHolding(value: unknown): value is PortfolioHoldingInput {
  if (!value || typeof value !== 'object') return false;
  const holding = value as Partial<PortfolioHoldingInput>;

  return (
    typeof holding.assetId === 'number' &&
    typeof holding.assetCode === 'string' &&
    typeof holding.name === 'string' &&
    isMarket(holding.market) &&
    typeof holding.quantity === 'number' &&
    Number.isFinite(holding.quantity) &&
    holding.quantity > 0 &&
    typeof holding.averagePurchasePrice === 'number' &&
    Number.isFinite(holding.averagePurchasePrice) &&
    holding.averagePurchasePrice > 0
  );
}

function loadHoldings() {
  try {
    const saved = localStorage.getItem(PORTFOLIO_STORAGE_KEY);
    if (!saved) return [];
    const parsed: unknown = JSON.parse(saved);
    if (!Array.isArray(parsed)) return [];

    const seen = new Set<number>();
    return parsed.filter((holding): holding is PortfolioHoldingInput => {
      if (!isHolding(holding) || seen.has(holding.assetId)) return false;
      seen.add(holding.assetId);
      return true;
    });
  } catch {
    return [];
  }
}

export function usePortfolioHoldings() {
  const [holdings, setHoldings] = useState<PortfolioHoldingInput[]>(loadHoldings);

  useEffect(() => {
    localStorage.setItem(PORTFOLIO_STORAGE_KEY, JSON.stringify(holdings));
  }, [holdings]);

  function saveHolding(nextHolding: PortfolioHoldingInput) {
    setHoldings((current) => {
      const exists = current.some((holding) => holding.assetId === nextHolding.assetId);
      return exists
        ? current.map((holding) =>
            holding.assetId === nextHolding.assetId ? nextHolding : holding,
          )
        : [...current, nextHolding];
    });
  }

  function removeHolding(assetId: number) {
    setHoldings((current) => current.filter((holding) => holding.assetId !== assetId));
  }

  return { holdings, saveHolding, removeHolding };
}
