import { useEffect, useState } from 'react';
import {
  addSavedPortfolioHolding,
  getSavedPortfolio,
  removeSavedPortfolioHolding,
  updateSavedPortfolioHolding,
} from '../api';
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

function loadLegacyHoldings() {
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

async function migrateLegacyHoldings(serverHoldings: PortfolioHoldingInput[]) {
  const legacyHoldings = loadLegacyHoldings();
  if (legacyHoldings.length === 0) return serverHoldings;
  const migrated = [...serverHoldings];
  const savedAssetIds = new Set(serverHoldings.map((holding) => holding.assetId));
  for (const holding of legacyHoldings) {
    if (savedAssetIds.has(holding.assetId)) continue;
    const saved = await addSavedPortfolioHolding(holding);
    migrated.push(saved);
    savedAssetIds.add(saved.assetId);
  }
  localStorage.removeItem(PORTFOLIO_STORAGE_KEY);
  return migrated;
}

function userMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

export function usePortfolioHoldings() {
  const [holdings, setHoldings] = useState<PortfolioHoldingInput[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getSavedPortfolio(controller.signal)
      .then(migrateLegacyHoldings)
      .then((savedHoldings) => {
        if (!controller.signal.aborted) setHoldings(savedHoldings);
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        setError(userMessage(caughtError, '포트폴리오를 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [reloadCount]);

  async function saveHolding(nextHolding: PortfolioHoldingInput) {
    const exists = holdings.some((holding) => holding.assetId === nextHolding.assetId);
    setSaving(true);
    setError('');
    try {
      const saved = exists
        ? await updateSavedPortfolioHolding(nextHolding)
        : await addSavedPortfolioHolding(nextHolding);
      setHoldings((current) =>
        exists
          ? current.map((holding) => (holding.assetId === saved.assetId ? saved : holding))
          : [...current, saved],
      );
    } catch (caughtError) {
      const message = userMessage(caughtError, '보유자산을 저장하지 못했습니다.');
      setError(message);
      throw new Error(message);
    } finally {
      setSaving(false);
    }
  }

  async function removeHolding(assetId: number) {
    setSaving(true);
    setError('');
    try {
      await removeSavedPortfolioHolding(assetId);
      setHoldings((current) => current.filter((holding) => holding.assetId !== assetId));
    } catch (caughtError) {
      setError(userMessage(caughtError, '보유자산을 삭제하지 못했습니다.'));
    } finally {
      setSaving(false);
    }
  }

  return {
    holdings,
    loading,
    saving,
    error,
    retry: () => setReloadCount((count) => count + 1),
    saveHolding,
    removeHolding,
  };
}
