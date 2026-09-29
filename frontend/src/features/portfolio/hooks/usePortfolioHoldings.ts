import { useEffect, useState } from 'react';
import type { AssetCategory, PortfolioHoldingInput, StockMarket } from '../types';

export const PORTFOLIO_STORAGE_KEY = 'daynomy:portfolio-holdings:v1';

function isMarket(value: unknown): value is StockMarket {
  return value === 'KOSPI' || value === 'KOSDAQ';
}

function isCategory(value: unknown): value is AssetCategory {
  return value === 'STOCK' || value === 'ETF';
}

function normalizeHolding(value: unknown): PortfolioHoldingInput | null {
  if (!value || typeof value !== 'object') return null;
  const holding = value as Partial<PortfolioHoldingInput>;
  const category = holding.category ?? 'STOCK';

  if (
    typeof holding.assetId === 'number' &&
    typeof holding.assetCode === 'string' &&
    typeof holding.name === 'string' &&
    isCategory(category) &&
    isMarket(holding.market) &&
    typeof holding.quantity === 'number' &&
    Number.isFinite(holding.quantity) &&
    holding.quantity > 0 &&
    typeof holding.averagePurchasePrice === 'number' &&
    Number.isFinite(holding.averagePurchasePrice) &&
    holding.averagePurchasePrice > 0
  ) {
    return {
      assetId: holding.assetId,
      assetCode: holding.assetCode,
      name: holding.name,
      category,
      market: holding.market,
      quantity: holding.quantity,
      averagePurchasePrice: holding.averagePurchasePrice,
    };
  }

  return null;
}

function loadHoldings() {
  try {
    const saved = localStorage.getItem(PORTFOLIO_STORAGE_KEY);
    if (!saved) return [];
    const parsed: unknown = JSON.parse(saved);
    if (!Array.isArray(parsed)) return [];

    const seen = new Set<number>();
    return parsed.reduce<PortfolioHoldingInput[]>((holdings, value) => {
      const holding = normalizeHolding(value);
      if (!holding || seen.has(holding.assetId)) return holdings;
      seen.add(holding.assetId);
      holdings.push(holding);
      return holdings;
    }, []);
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
