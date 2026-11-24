import { ApiError } from '../../api/client';
import type { PortfolioTransactionRequest, PortfolioTransactionResponse } from './types';

type PendingTransaction = { key: string; request: PortfolioTransactionRequest };
type SendTransaction = (
  request: PortfolioTransactionRequest,
  key: string,
) => Promise<PortfolioTransactionResponse>;

function fingerprint(request: PortfolioTransactionRequest) {
  return JSON.stringify([
    request.assetId,
    request.type,
    request.tradeDate,
    request.quantity,
    request.unitPrice,
    request.fee,
    request.tax,
    request.exchangeRate ?? null,
    request.tradeTime ?? null,
  ]);
}

function isPending(value: unknown): value is PendingTransaction {
  if (typeof value !== 'object' || value === null) return false;
  const { key, request } = value as Partial<PendingTransaction>;
  return (
    typeof key === 'string' &&
    /^[A-Za-z0-9._:-]{1,128}$/.test(key) &&
    typeof request === 'object' &&
    request !== null &&
    Number.isSafeInteger(request.assetId) &&
    request.assetId > 0 &&
    (request.type === 'BUY' || request.type === 'SELL') &&
    typeof request.tradeDate === 'string' &&
    /^\d{4}-\d{2}-\d{2}$/.test(request.tradeDate) &&
    Number.isSafeInteger(request.quantity) &&
    request.quantity > 0 &&
    [request.unitPrice, request.fee, request.tax].every(
      (number) => typeof number === 'number' && Number.isFinite(number) && number >= 0,
    ) &&
    (request.exchangeRate === undefined ||
      (Number.isFinite(request.exchangeRate) && request.exchangeRate > 0)) &&
    (request.tradeTime === undefined ||
      (typeof request.tradeTime === 'string' && /^\d{2}:\d{2}(:\d{2})?$/.test(request.tradeTime)))
  );
}

/** One unresolved trade per member and tab. Never infer a new intent after an ambiguous failure. */
export function createTransactionSubmission(memberId?: number | null) {
  const storageKey = memberId != null ? `daynomy:pending-transaction:v1:${memberId}` : undefined;
  let pending: PendingTransaction | null = null;
  let unreadable = false;
  let inFlight: Promise<PortfolioTransactionResponse> | null = null;
  try {
    const saved = storageKey ? sessionStorage.getItem(storageKey) : null;
    if (saved) {
      const value: unknown = JSON.parse(saved);
      if (!isPending(value)) throw new Error('Invalid pending transaction');
      pending = value;
    }
  } catch {
    unreadable = true;
  }

  function persist(value: PendingTransaction | null) {
    if (storageKey) {
      try {
        if (value) sessionStorage.setItem(storageKey, JSON.stringify(value));
        else sessionStorage.removeItem(storageKey);
      } catch {
        throw new Error(
          '거래 확인 정보를 보관하지 못했어요. 브라우저 저장소 설정을 확인해 주세요.',
        );
      }
    }
    pending = value;
  }

  return {
    getPending: () => pending,
    submit(request: PortfolioTransactionRequest, send: SendTransaction) {
      if (unreadable)
        return Promise.reject(
          new Error('이전 거래 확인 정보를 읽지 못했어요. 거래 내역을 확인한 뒤 문의해 주세요.'),
        );
      if (pending && fingerprint(pending.request) !== fingerprint(request))
        return Promise.reject(
          new Error(
            '아직 결과를 확인하지 못한 거래가 있어요. 먼저 이전 거래의 결과를 확인해 주세요.',
          ),
        );
      if (inFlight) return inFlight;
      try {
        if (!pending) persist({ key: crypto.randomUUID(), request: { ...request } });
      } catch (error) {
        return Promise.reject(error);
      }
      const intent = pending!;
      inFlight = (async () => {
        try {
          const response = await send(intent.request, intent.key);
          persist(null);
          return response;
        } catch (error) {
          // Only definitive validation/business rejection allows an edited, new submission.
          // Network errors, malformed success responses, auth, busy and conflicts keep the key.
          if (
            error instanceof ApiError &&
            error.status >= 400 &&
            error.status < 500 &&
            ![401, 403, 408, 409, 429].includes(error.status)
          )
            persist(null);
          throw error;
        } finally {
          inFlight = null;
        }
      })();
      return inFlight;
    },
  };
}
