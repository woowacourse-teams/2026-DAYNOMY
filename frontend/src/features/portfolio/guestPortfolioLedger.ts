import type {
  PortfolioHoldingHistory,
  PortfolioHoldingInput,
  PortfolioTransactionRequest,
} from './types';
import type { StockSearchItem } from '../stocks/types';
import { todayInSeoul, validateTransaction } from './transactionForm';

export type GuestPortfolioHistory = PortfolioHoldingHistory &
  Pick<StockSearchItem, 'category' | 'market'>;
export type GuestPortfolioLedger = {
  openingHoldings: PortfolioHoldingInput[];
  histories: GuestPortfolioHistory[];
};

export function replayGuestPortfolio(ledger: GuestPortfolioLedger): PortfolioHoldingInput[] {
  const positions = new Map(ledger.openingHoldings.map((holding) => [holding.assetId, holding]));
  const ordered = [...ledger.histories].sort(
    (a, b) => a.effectiveDate.localeCompare(b.effectiveDate) || a.historyId - b.historyId,
  );
  for (const item of ordered) {
    const current = positions.get(item.assetId);
    const stock: StockSearchItem = {
      assetId: item.assetId,
      assetCode: item.assetCode,
      name: item.name,
      category: item.category,
      market: item.market,
    };
    if (item.changeType === 'REMOVED') {
      positions.delete(item.assetId);
    } else if (item.changeType === 'BUY') {
      const quantity = (current?.quantity ?? 0) + item.transactionQuantity!;
      if (!Number.isSafeInteger(quantity))
        throw new Error('보유 수량이 너무 큽니다. 수량을 확인해 주세요.');
      // 수수료·세금을 포함한 매입 원가를 원 단위 소수 둘째 자리까지 보관한다.
      const cents = (value: number) => BigInt(Math.round(value * 100));
      const cost =
        cents(current?.averagePurchasePrice ?? 0) * BigInt(current?.quantity ?? 0) +
        cents(item.unitPrice!) * BigInt(item.transactionQuantity!) +
        cents(item.fee) +
        cents(item.tax);
      const averagePurchasePrice =
        Number((cost + BigInt(Math.floor(quantity / 2))) / BigInt(quantity)) / 100;
      positions.set(item.assetId, { ...stock, quantity, averagePurchasePrice });
    } else if (item.changeType === 'SELL') {
      if (!current || current.quantity < item.transactionQuantity!) {
        throw new Error(
          '매도 수량이 해당 거래일의 보유 수량보다 많아요. 거래일과 수량을 확인해 주세요.',
        );
      }
      const quantity = current.quantity - item.transactionQuantity!;
      if (quantity) positions.set(item.assetId, { ...current, quantity });
      else positions.delete(item.assetId);
    } else {
      positions.set(item.assetId, {
        ...stock,
        quantity: item.quantity,
        averagePurchasePrice: item.averagePurchasePrice,
      });
    }
  }
  return [...positions.values()];
}

export function guestHoldingHistory(
  ledger: GuestPortfolioLedger,
  holding: PortfolioHoldingInput,
  changeType: 'ADJUSTED' | 'REMOVED',
): GuestPortfolioHistory {
  return {
    ...holding,
    historyId: nextGuestHistoryId(ledger),
    changeType,
    effectiveDate: todayInSeoul(),
    occurredAt: new Date().toISOString(),
    transactionQuantity: null,
    unitPrice: null,
    fee: 0,
    tax: 0,
  };
}

function nextGuestHistoryId(ledger: GuestPortfolioLedger) {
  return Math.max(0, ...ledger.histories.map((item) => item.historyId)) + 1;
}

export function guestTransactionHistory(
  ledger: GuestPortfolioLedger,
  stock: StockSearchItem,
  body: PortfolioTransactionRequest,
): GuestPortfolioHistory {
  const invalid = validateTransaction({
    tradeDate: body.tradeDate,
    quantity: String(body.quantity),
    unitPrice: String(body.unitPrice),
    fee: String(body.fee),
    tax: String(body.tax),
  });
  if (invalid) throw new Error(invalid);
  return {
    ...stock,
    historyId: nextGuestHistoryId(ledger),
    changeType: body.type,
    quantity: 0,
    averagePurchasePrice: 0,
    effectiveDate: body.tradeDate,
    transactionQuantity: body.quantity,
    unitPrice: body.unitPrice,
    fee: body.fee,
    tax: body.tax,
    occurredAt: new Date().toISOString(),
  };
}
