import type { PortfolioHoldingInput } from './types';
import type { AssetCategory } from '../stocks/types';
import { replayGuestPortfolio, type GuestPortfolioLedger } from './guestPortfolioLedger';
import { validateTransaction } from './transactionForm';
import { isStockMarket } from '../stocks/api';

export const GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY = 'daynomy:guest-portfolio-holdings:v1';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isAssetCategory(value: unknown): value is AssetCategory {
  return value === 'STOCK' || value === 'ETF';
}

function isPortfolioHolding(value: unknown): value is PortfolioHoldingInput {
  return (
    isRecord(value) &&
    typeof value.assetId === 'number' &&
    Number.isSafeInteger(value.assetId) &&
    value.assetId > 0 &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    isAssetCategory(value.category) &&
    isStockMarket(value.market) &&
    typeof value.quantity === 'number' &&
    Number.isSafeInteger(value.quantity) &&
    value.quantity > 0 &&
    typeof value.averagePurchasePrice === 'number' &&
    Number.isFinite(value.averagePurchasePrice) &&
    value.averagePurchasePrice > 0
  );
}

function clearInvalidStoredHoldings() {
  try {
    sessionStorage.removeItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY);
  } catch {
    // 저장소에 접근할 수 없어도 빈 포트폴리오로 계속 사용할 수 있다.
  }
}

function validHoldings(value: unknown): value is PortfolioHoldingInput[] {
  return (
    Array.isArray(value) &&
    value.every(isPortfolioHolding) &&
    new Set(value.map((holding) => holding.assetId)).size === value.length
  );
}

function isGuestLedger(value: unknown): value is GuestPortfolioLedger {
  if (!isRecord(value) || !validHoldings(value.openingHoldings) || !Array.isArray(value.histories))
    return false;
  return (
    new Set(value.histories.map((item) => item?.historyId)).size === value.histories.length &&
    value.histories.every((item: unknown) => {
      if (
        !isRecord(item) ||
        !isPortfolioHolding({ ...item, quantity: 1, averagePurchasePrice: 1 }) ||
        !Number.isSafeInteger(item.historyId) ||
        Number(item.historyId) <= 0 ||
        typeof item.effectiveDate !== 'string' ||
        typeof item.occurredAt !== 'string' ||
        !Number.isFinite(Date.parse(item.occurredAt))
      )
        return false;
      if (item.changeType === 'BUY' || item.changeType === 'SELL') {
        return (
          typeof item.transactionQuantity === 'number' &&
          typeof item.unitPrice === 'number' &&
          typeof item.fee === 'number' &&
          typeof item.tax === 'number' &&
          validateTransaction(
            {
              tradeDate: item.effectiveDate,
              quantity: String(item.transactionQuantity),
              unitPrice: String(item.unitPrice),
              exchangeRate: item.exchangeRate == null ? undefined : String(item.exchangeRate),
              fee: String(item.fee),
              tax: String(item.tax),
            },
            String(item.assetCode).endsWith('.US') ? 'USD' : 'KRW',
          ) === null
        );
      }
      return (
        (item.changeType === 'ADJUSTED' || item.changeType === 'REMOVED') &&
        item.transactionQuantity === null &&
        item.unitPrice === null &&
        item.fee === 0 &&
        item.tax === 0 &&
        validateTransaction({
          tradeDate: item.effectiveDate,
          quantity: '1',
          unitPrice: '1',
          fee: '0',
          tax: '0',
        }) === null &&
        isPortfolioHolding(item)
      );
    })
  );
}

function loadGuestPortfolio(): PortfolioHoldingInput[] | GuestPortfolioLedger {
  try {
    const saved = sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY);
    if (!saved) return [];
    const parsed: unknown = JSON.parse(saved);
    if (validHoldings(parsed)) return parsed;
    if (isGuestLedger(parsed)) {
      replayGuestPortfolio(parsed);
      return parsed;
    }
  } catch {
    // 손상된 임시 데이터는 기존 정책대로 제거한다.
  }
  clearInvalidStoredHoldings();
  return [];
}

export function loadGuestPortfolioHoldings(): PortfolioHoldingInput[] {
  const saved = loadGuestPortfolio();
  return Array.isArray(saved) ? saved : replayGuestPortfolio(saved);
}

export function loadGuestPortfolioLedger(): GuestPortfolioLedger | null {
  const saved = loadGuestPortfolio();
  return Array.isArray(saved) ? null : saved;
}

export function saveGuestPortfolioLedger(ledger: GuestPortfolioLedger) {
  replayGuestPortfolio(ledger);
  saveGuestPortfolioData(ledger);
}

function saveGuestPortfolioData(data: PortfolioHoldingInput[] | GuestPortfolioLedger) {
  try {
    sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, JSON.stringify(data));
  } catch {
    throw new Error(
      '현재 탭에 보유정보를 임시 보관하지 못했어요. 브라우저 저장 설정을 확인해 주세요.',
    );
  }
}

export function saveGuestPortfolioHoldings(holdings: PortfolioHoldingInput[]) {
  saveGuestPortfolioData(holdings);
}
