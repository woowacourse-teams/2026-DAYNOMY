import type { AssetCategory, PortfolioHoldingInput, StockMarket } from './types';

export const GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY = 'daynomy:guest-portfolio-holdings:v1';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isAssetCategory(value: unknown): value is AssetCategory {
  return value === 'STOCK' || value === 'ETF';
}

function isStockMarket(value: unknown): value is StockMarket {
  return value === 'KOSPI' || value === 'KOSDAQ';
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

export function loadGuestPortfolioHoldings(): PortfolioHoldingInput[] {
  try {
    const saved = sessionStorage.getItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY);
    if (!saved) return [];

    const parsed: unknown = JSON.parse(saved);
    if (
      !Array.isArray(parsed) ||
      !parsed.every(isPortfolioHolding) ||
      new Set(parsed.map((holding) => holding.assetId)).size !== parsed.length
    ) {
      clearInvalidStoredHoldings();
      return [];
    }
    return parsed;
  } catch {
    clearInvalidStoredHoldings();
    return [];
  }
}

export function saveGuestPortfolioHoldings(holdings: PortfolioHoldingInput[]) {
  try {
    sessionStorage.setItem(GUEST_PORTFOLIO_HOLDINGS_STORAGE_KEY, JSON.stringify(holdings));
  } catch {
    throw new Error(
      '현재 탭에 보유정보를 임시 보관하지 못했어요. 브라우저 저장 설정을 확인해 주세요.',
    );
  }
}
