import { useCallback, useEffect, useState } from 'react';
import { portfolioErrorType, trackPortfolioEvent } from '../analytics';
import {
  getSavedPortfolio,
  removeSavedPortfolioHolding,
  updateSavedPortfolioHolding,
  createPortfolioTransaction,
  updatePortfolioTransaction,
  deletePortfolioTransaction,
  updateSavedPortfolioHoldings,
  createPortfolioTransactions,
  createPortfolioHoldings,
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
  guestRealizedProfitLoss,
  replayGuestPortfolio,
  type GuestPortfolioLedger,
} from '../guestPortfolioLedger';
import type {
  PortfolioHoldingInput,
  PortfolioTransactionRequest,
  PortfolioTransactionUpdateRequest,
  PortfolioTransactionResponse,
} from '../types';
import type { StockSearchItem } from '../../stocks/types';

export function usePortfolioHoldings(isLoggedIn: boolean) {
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
      const ledger = loadGuestPortfolioLedger();
      setGuestLedger(ledger);
      setHoldings(loadGuestPortfolioHoldings());
      setTotalRealizedProfitLoss(ledger ? guestRealizedProfitLoss(ledger) : 0);
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

  const commitGuestLedger = useCallback((next: GuestPortfolioLedger) => {
    const nextHoldings = replayGuestPortfolio(next);
    saveGuestPortfolioLedger(next);
    setGuestLedger(next);
    setHoldings(nextHoldings);
    setTotalRealizedProfitLoss(guestRealizedProfitLoss(next));
    setRevision((value) => value + 1);
  }, []);

  const saveHolding = useCallback(
    async (nextHolding: PortfolioHoldingInput) => {
      setSaving(true);
      setError('');
      try {
        const exists = holdings.some((holding) => holding.assetId === nextHolding.assetId);
        if (!exists) throw new Error('정정할 보유자산이 없습니다. 매수 거래로 등록해 주세요.');
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
          const nextHoldings = holdings.map((holding) =>
            holding.assetId === nextHolding.assetId ? nextHolding : holding,
          );
          saveGuestPortfolioHoldings(nextHoldings);
          setHoldings(nextHoldings);
          setRevision((value) => value + 1);
          return;
        }

        const savedHolding = await updateSavedPortfolioHolding(nextHolding);
        setHoldings((current) =>
          current.map((holding) =>
            holding.assetId === savedHolding.assetId ? savedHolding : holding,
          ),
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
    saveHoldings: async (changes: PortfolioHoldingInput[]) => {
      if (!changes.length) return;
      setSaving(true);
      try {
        if (changes.some((item) => !holdings.some((holding) => holding.assetId === item.assetId)))
          throw new Error('보유자산이 변경됐어요. 편집창을 닫고 다시 열어 주세요.');
        if (isLoggedIn) {
          const saved = await updateSavedPortfolioHoldings(changes);
          setHoldings((current) =>
            current.map(
              (holding) => saved.find((item) => item.assetId === holding.assetId) ?? holding,
            ),
          );
          setRevision((value) => value + 1);
        } else {
          let next = guestLedger ?? { openingHoldings: holdings, histories: [] };
          for (const holding of changes)
            next = {
              ...next,
              histories: [...next.histories, guestHoldingHistory(next, holding, 'ADJUSTED')],
            };
          commitGuestLedger(next);
        }
      } finally {
        setSaving(false);
      }
    },
    addHoldings: async (items: PortfolioHoldingInput[]) => {
      setSaving(true);
      try {
        const ids = new Set(holdings.map((holding) => holding.assetId));
        for (const item of items) {
          if (ids.has(item.assetId)) throw new Error('이미 보유 중이거나 중복 입력한 종목이에요.');
          ids.add(item.assetId);
        }
        if (isLoggedIn) {
          const saved = await createPortfolioHoldings(items);
          setHoldings(saved.holdings);
          setTotalRealizedProfitLoss(saved.totalRealizedProfitLoss);
          setRevision((value) => value + 1);
        } else {
          let next = guestLedger ?? { openingHoldings: holdings, histories: [] };
          for (const item of items)
            next = {
              ...next,
              histories: [...next.histories, guestHoldingHistory(next, item, 'OPENING_BALANCE')],
            };
          commitGuestLedger(next);
        }
      } finally {
        setSaving(false);
      }
    },
    createTransactions: async (
      items: { stock: StockSearchItem; transaction: PortfolioTransactionRequest }[],
      csv: boolean,
    ) => {
      setSaving(true);
      try {
        if (isLoggedIn) {
          const response = await createPortfolioTransactions(
            items.map((item) => item.transaction),
            csv,
          );
          setHoldings(response.portfolio.holdings);
          setTotalRealizedProfitLoss(response.portfolio.totalRealizedProfitLoss);
          setRevision((value) => value + 1);
          return response.alreadyImported;
        }
        const ordered = [...items].sort((a, b) =>
          a.transaction.tradeDate.localeCompare(b.transaction.tradeDate),
        );
        const fingerprint = JSON.stringify(
          ordered.map((item) => {
            const { assetId, type, tradeDate, quantity, unitPrice, fee, tax, exchangeRate } =
              item.transaction;
            return [assetId, type, tradeDate, quantity, unitPrice, fee, tax, exchangeRate ?? 1];
          }),
        );
        let next = guestLedger ?? { openingHoldings: holdings, histories: [] };
        if (csv && next.importFingerprints?.includes(fingerprint)) return true;
        for (const { stock, transaction } of ordered) {
          if (
            next.histories.some(
              (history) =>
                history.assetId === stock.assetId &&
                history.changeType !== 'BUY' &&
                history.changeType !== 'SELL' &&
                history.effectiveDate > transaction.tradeDate,
            )
          )
            throw new Error('거래일은 해당 종목의 마지막 보유정보 정정일 이후여야 합니다.');
          next = {
            ...next,
            histories: [...next.histories, guestTransactionHistory(next, stock, transaction)],
          };
        }
        if (csv)
          next = { ...next, importFingerprints: [...(next.importFingerprints ?? []), fingerprint] };
        commitGuestLedger(next);
        return false;
      } finally {
        setSaving(false);
      }
    },
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
        tradeTime: existing.tradeTime ?? undefined,
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
