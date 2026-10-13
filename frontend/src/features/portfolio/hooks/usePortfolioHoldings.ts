import { useCallback, useEffect, useState } from 'react';
import { portfolioErrorType, trackPortfolioEvent } from '../analytics';
import {
  addSavedPortfolioHolding,
  getSavedPortfolio,
  removeSavedPortfolioHolding,
  updateSavedPortfolioHolding,
  createPortfolioTransaction,
  updatePortfolioTransaction,
  deletePortfolioTransaction,
} from '../api';
import {
  loadGuestPortfolioHoldings,
  saveGuestPortfolioHoldings,
} from '../portfolioHoldingsStorage';
import type {
  PortfolioHoldingInput,
  PortfolioTransactionRequest,
  PortfolioTransactionUpdateRequest,
  PortfolioTransactionResponse,
} from '../types';

export function usePortfolioHoldings(isLoggedIn = true) {
  const [holdings, setHoldings] = useState<PortfolioHoldingInput[]>([]);
  const [totalRealizedProfitLoss, setTotalRealizedProfitLoss] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (!isLoggedIn) {
      setHoldings(loadGuestPortfolioHoldings());
      setTotalRealizedProfitLoss(0);
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getSavedPortfolio(controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) {
          setHoldings(response.holdings);
          setTotalRealizedProfitLoss(response.totalRealizedProfitLoss);
        }
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        trackPortfolioEvent('portfolio_dashboard_load_failed', {
          stage: 'holdings',
          error_type: portfolioErrorType(caughtError),
        });
        setError(
          caughtError instanceof Error ? caughtError.message : '포트폴리오를 불러오지 못했습니다.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [isLoggedIn, retryCount]);

  const saveHolding = useCallback(
    async (nextHolding: PortfolioHoldingInput) => {
      setSaving(true);
      setError('');
      try {
        const exists = holdings.some((holding) => holding.assetId === nextHolding.assetId);
        if (!isLoggedIn) {
          const nextHoldings = exists
            ? holdings.map((holding) =>
                holding.assetId === nextHolding.assetId ? nextHolding : holding,
              )
            : [...holdings, nextHolding];
          saveGuestPortfolioHoldings(nextHoldings);
          setHoldings(nextHoldings);
          setRevision((value) => value + 1);
          return;
        }

        const savedHolding = exists
          ? await updateSavedPortfolioHolding(nextHolding)
          : await addSavedPortfolioHolding(nextHolding);
        setHoldings((current) =>
          exists
            ? current.map((holding) =>
                holding.assetId === savedHolding.assetId ? savedHolding : holding,
              )
            : [...current, savedHolding],
        );
        setRevision((value) => value + 1);
      } catch (caughtError) {
        setError(
          caughtError instanceof Error ? caughtError.message : '보유 자산을 저장하지 못했습니다.',
        );
        throw caughtError;
      } finally {
        setSaving(false);
      }
    },
    [holdings, isLoggedIn],
  );

  const removeHolding = useCallback(
    async (assetId: number) => {
      setSaving(true);
      setError('');
      try {
        if (!isLoggedIn) {
          const nextHoldings = holdings.filter((holding) => holding.assetId !== assetId);
          saveGuestPortfolioHoldings(nextHoldings);
          setHoldings(nextHoldings);
          setRevision((value) => value + 1);
          return;
        }

        await removeSavedPortfolioHolding(assetId);
        setHoldings((current) => current.filter((holding) => holding.assetId !== assetId));
        setRevision((value) => value + 1);
      } catch (caughtError) {
        setError(
          caughtError instanceof Error ? caughtError.message : '보유 자산을 삭제하지 못했습니다.',
        );
        throw caughtError;
      } finally {
        setSaving(false);
      }
    },
    [holdings, isLoggedIn],
  );

  async function transact(assetId: number, operation: () => Promise<PortfolioTransactionResponse>) {
    if (!isLoggedIn) throw new Error('로그인 후 거래를 기록할 수 있어요.');
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
    retry: () => setRetryCount((count) => count + 1),
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
