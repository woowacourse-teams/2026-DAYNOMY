import { useEffect, useState } from 'react';
import type {
  AssetCategory,
  PortfolioHoldingHistory,
  PortfolioHoldingInput,
  StockMarket,
} from '../types';

export const PORTFOLIO_STORAGE_KEY = 'daynomy:portfolio-holdings:v1';
export const PORTFOLIO_HISTORY_STORAGE_KEY = 'daynomy:portfolio-holding-history:v1';

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
    Number.isSafeInteger(holding.quantity) &&
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

function loadHistories() {
  try {
    const saved = localStorage.getItem(PORTFOLIO_HISTORY_STORAGE_KEY);
    if (!saved) return [];
    const parsed: unknown = JSON.parse(saved);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((value): value is PortfolioHoldingHistory => {
      if (!value || typeof value !== 'object') return false;
      const history = value as Partial<PortfolioHoldingHistory>;
      return (
        (history.changeType === 'ADDED' ||
          history.changeType === 'UPDATED' ||
          history.changeType === 'REMOVED') &&
        typeof history.occurredAt === 'string' &&
        !Number.isNaN(Date.parse(history.occurredAt)) &&
        (history.previousHolding === null || normalizeHolding(history.previousHolding) !== null) &&
        (history.holding === null || normalizeHolding(history.holding) !== null)
      );
    });
  } catch {
    return [];
  }
}

export function usePortfolioHoldings() {
  const [holdings, setHoldings] = useState<PortfolioHoldingInput[]>(loadHoldings);
  const [histories, setHistories] = useState<PortfolioHoldingHistory[]>(loadHistories);

  useEffect(() => {
    localStorage.setItem(PORTFOLIO_STORAGE_KEY, JSON.stringify(holdings));
  }, [holdings]);

  useEffect(() => {
    localStorage.setItem(PORTFOLIO_HISTORY_STORAGE_KEY, JSON.stringify(histories));
  }, [histories]);

  async function saveHolding(nextHolding: PortfolioHoldingInput) {
    const previousHolding = holdings.find((holding) => holding.assetId === nextHolding.assetId);
    setHoldings(
      previousHolding
        ? holdings.map((holding) =>
            holding.assetId === nextHolding.assetId ? nextHolding : holding,
          )
        : [...holdings, nextHolding],
    );
    setHistories((current) => [
      ...current,
      {
        changeType: previousHolding ? 'UPDATED' : 'ADDED',
        occurredAt: new Date().toISOString(),
        previousHolding: previousHolding ?? null,
        holding: nextHolding,
      },
    ]);
  }

  function removeHolding(assetId: number) {
    const previousHolding = holdings.find((holding) => holding.assetId === assetId);
    if (!previousHolding) return;
    setHoldings((current) => current.filter((holding) => holding.assetId !== assetId));
    setHistories((current) => [
      ...current,
      {
        changeType: 'REMOVED',
        occurredAt: new Date().toISOString(),
        previousHolding,
        holding: null,
      },
    ]);
  }

  return {
    holdings,
    histories,
    loading: false,
    saving: false,
    error: '',
    retry: () => undefined,
    saveHolding,
    removeHolding,
  };
}
