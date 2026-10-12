import { useCallback, useEffect, useState } from 'react';
import {
  addSavedPortfolioHolding,
  getSavedPortfolio,
  removeSavedPortfolioHolding,
  updateSavedPortfolioHolding,
} from '../api';
import {
  loadGuestPortfolioHoldings,
  saveGuestPortfolioHoldings,
} from '../portfolioHoldingsStorage';
import type { PortfolioHoldingInput } from '../types';

export function usePortfolioHoldings(isLoggedIn = true) {
  const [holdings, setHoldings] = useState<PortfolioHoldingInput[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    if (!isLoggedIn) {
      setHoldings(loadGuestPortfolioHoldings());
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');

    getSavedPortfolio(controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) setHoldings(response.holdings);
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
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
          return;
        }

        await removeSavedPortfolioHolding(assetId);
        setHoldings((current) => current.filter((holding) => holding.assetId !== assetId));
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

  return {
    holdings,
    loading,
    saving,
    error,
    retry: () => setRetryCount((count) => count + 1),
    saveHolding,
    removeHolding,
  };
}
