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
import {
  loadGuestPortfolioHoldings,
  saveGuestPortfolioHoldings,
  loadGuestPortfolioLedger,
  saveGuestPortfolioLedger,
} from '../portfolioHoldingsStorage';
import {
  guestHoldingHistory,
  guestTransactionHistory,
  replayGuestPortfolio,
  type GuestPortfolioLedger,
} from '../guestPortfolioLedger';
import type {
  StockSearchItem,
  PortfolioHoldingInput,
  PortfolioTransactionRequest,
  PortfolioTransactionUpdateRequest,
  PortfolioTransactionResponse,
} from '../types';

export function usePortfolioHoldings(isLoggedIn = true) {
  const [guestLedger, setGuestLedger] = useState<GuestPortfolioLedger | null>(null);
  const [holdings, setHoldings] = useState<PortfolioHoldingInput[]>([]);
  const [totalRealizedProfitLoss, setTotalRealizedProfitLoss] = useState(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    if (!isLoggedIn) {
      setGuestLedger(loadGuestPortfolioLedger());
      setHoldings(loadGuestPortfolioHoldings());
      setTotalRealizedProfitLoss(0);
      setLoading(false);
      setError('');
      return;
    }

    setGuestLedger(null);
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
        setError(
          caughtError instanceof Error ? caughtError.message : '포트폴리오를 불러오지 못했습니다.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [isLoggedIn, retryCount]);

  const commitGuestLedger = useCallback((next: GuestPortfolioLedger) => {
    const nextHoldings = replayGuestPortfolio(next);
    saveGuestPortfolioLedger(next);
    setGuestLedger(next);
    setHoldings(nextHoldings);
    setRevision((value) => value + 1);
  }, []);

  const saveHolding = useCallback(
    async (nextHolding: PortfolioHoldingInput) => {
      setSaving(true);
      setError('');
      try {
        const exists = holdings.some((holding) => holding.assetId === nextHolding.assetId);
        if (!isLoggedIn) {
          if (guestLedger) {
            commitGuestLedger({
              ...guestLedger,
              histories: [
                ...guestLedger.histories,
                guestHoldingHistory(guestLedger, nextHolding, 'ADJUSTED'),
              ],
            });
            return;
          }
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
    [holdings, isLoggedIn, guestLedger, commitGuestLedger],
  );

  const removeHolding = useCallback(
    async (assetId: number) => {
      setSaving(true);
      setError('');
      try {
        if (!isLoggedIn) {
          if (guestLedger) {
            const holding = holdings.find((item) => item.assetId === assetId);
            if (holding)
              commitGuestLedger({
                ...guestLedger,
                histories: [
                  ...guestLedger.histories,
                  guestHoldingHistory(guestLedger, holding, 'REMOVED'),
                ],
              });
            return;
          }
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
    [holdings, isLoggedIn, guestLedger, commitGuestLedger],
  );

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
    retry: () => setRetryCount((count) => count + 1),
    saveHolding,
    removeHolding,
    guestHistories: guestLedger?.histories ?? [],
    createTransaction: async (body: PortfolioTransactionRequest, stock?: StockSearchItem) => {
      if (isLoggedIn) return transact(body.assetId, () => createPortfolioTransaction(body));
      const selected = stock ?? holdings.find((holding) => holding.assetId === body.assetId);
      if (!selected || selected.assetId !== body.assetId)
        throw new Error('거래할 종목을 다시 선택해 주세요.');
      const ledger = guestLedger ?? { openingHoldings: holdings, histories: [] };
      commitGuestLedger({
        ...ledger,
        histories: [...ledger.histories, guestTransactionHistory(ledger, selected, body)],
      });
    },
    updateTransaction: async (
      id: number,
      assetId: number,
      body: PortfolioTransactionUpdateRequest,
    ) => {
      if (isLoggedIn) return transact(assetId, () => updatePortfolioTransaction(id, body));
      const existing = guestLedger?.histories.find(
        (item) => item.historyId === id && item.assetId === assetId,
      );
      if (
        !guestLedger ||
        !existing ||
        (existing.changeType !== 'BUY' && existing.changeType !== 'SELL')
      )
        throw new Error('수정할 거래를 찾지 못했어요.');
      const updated = guestTransactionHistory(guestLedger, existing, {
        ...body,
        assetId,
        type: existing.changeType,
      });
      commitGuestLedger({
        ...guestLedger,
        histories: guestLedger.histories.map((item) =>
          item.historyId === id
            ? { ...updated, historyId: id, occurredAt: existing.occurredAt }
            : item,
        ),
      });
    },
    deleteTransaction: async (id: number, assetId: number) => {
      if (isLoggedIn) return transact(assetId, () => deletePortfolioTransaction(id));
      const existing = guestLedger?.histories.find(
        (item) => item.historyId === id && item.assetId === assetId,
      );
      if (
        !guestLedger ||
        !existing ||
        (existing.changeType !== 'BUY' && existing.changeType !== 'SELL')
      )
        throw new Error('삭제할 거래를 찾지 못했어요.');
      commitGuestLedger({
        ...guestLedger,
        histories: guestLedger.histories.filter((item) => item.historyId !== id),
      });
    },
  };
}
