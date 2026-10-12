import { useCallback, useEffect, useState } from 'react';
import {
  addSavedPortfolioHolding,
  getSavedPortfolio,
  removeSavedPortfolioHolding,
  updateSavedPortfolioHolding,
  createPortfolioTransaction,
  updatePortfolioTransaction,
  deletePortfolioTransaction,
} from '../api';
import type {
  PortfolioHoldingInput,
  PortfolioTransactionRequest,
  PortfolioTransactionUpdateRequest,
  PortfolioTransactionResponse,
} from '../types';

export function usePortfolioHoldings() {
  const [holdings, setHoldings] = useState<PortfolioHoldingInput[]>([]);
  const [totalRealizedProfitLoss, setTotalRealizedProfitLoss] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [revision, setRevision] = useState(0);
  const retry = useCallback(() => setRetryCount((count) => count + 1), []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getSavedPortfolio(controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setHoldings(response.holdings);
        setTotalRealizedProfitLoss(response.totalRealizedProfitLoss);
      })
      .catch((caughtError: unknown) => {
        if (!controller.signal.aborted)
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : '포트폴리오를 불러오지 못했습니다.',
          );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [retryCount]);

  async function saveHolding(next: PortfolioHoldingInput) {
    setSaving(true);
    try {
      const exists = holdings.some((holding) => holding.assetId === next.assetId);
      const saved = exists
        ? await updateSavedPortfolioHolding(next)
        : await addSavedPortfolioHolding(next);
      setHoldings((current) =>
        exists
          ? current.map((holding) => (holding.assetId === saved.assetId ? saved : holding))
          : [...current, saved],
      );
      setRevision((value) => value + 1);
    } finally {
      setSaving(false);
    }
  }
  async function removeHolding(assetId: number) {
    setSaving(true);
    try {
      await removeSavedPortfolioHolding(assetId);
      setHoldings((current) => current.filter((holding) => holding.assetId !== assetId));
      setRevision((value) => value + 1);
    } finally {
      setSaving(false);
    }
  }
  async function transact(assetId: number, operation: () => Promise<PortfolioTransactionResponse>) {
    setSaving(true);
    try {
      const response = await operation();
      setHoldings((current) => {
        if (!response.holding) return current.filter((holding) => holding.assetId !== assetId);
        const saved = response.holding;
        return current.some((holding) => holding.assetId === assetId)
          ? current.map((holding) => (holding.assetId === assetId ? saved : holding))
          : [...current, saved];
      });
      setTotalRealizedProfitLoss(response.totalRealizedProfitLoss);
      setRevision((value) => value + 1);
    } finally {
      setSaving(false);
    }
  }
  return {
    holdings,
    totalRealizedProfitLoss,
    revision,
    loading,
    saving,
    error,
    retry,
    saveHolding,
    removeHolding,
    createTransaction: (body: PortfolioTransactionRequest) =>
      transact(body.assetId, () => createPortfolioTransaction(body)),
    updateTransaction: (id: number, assetId: number, body: PortfolioTransactionUpdateRequest) =>
      transact(assetId, () => updatePortfolioTransaction(id, body)),
    deleteTransaction: (id: number, assetId: number) =>
      transact(assetId, () => deletePortfolioTransaction(id)),
  };
}
