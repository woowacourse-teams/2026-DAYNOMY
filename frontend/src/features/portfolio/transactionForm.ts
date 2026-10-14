import type { PortfolioTransactionUpdateRequest } from './types';

export function todayInSeoul() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  return ['year', 'month', 'day']
    .map((key) => parts.find((part) => part.type === key)?.value)
    .join('-');
}

export type TransactionFields = Record<keyof PortfolioTransactionUpdateRequest, string>;
export function validateTransaction(fields: TransactionFields): string | null {
  const date = new Date(`${fields.tradeDate}T00:00:00Z`);
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(fields.tradeDate) ||
    !Number.isFinite(date.getTime()) ||
    date.toISOString().slice(0, 10) !== fields.tradeDate ||
    fields.tradeDate > todayInSeoul()
  ) {
    return '거래일은 오늘 또는 과거의 올바른 날짜로 입력해 주세요.';
  }
  if (
    !/^\d+$/.test(fields.quantity) ||
    !Number.isSafeInteger(Number(fields.quantity)) ||
    Number(fields.quantity) <= 0
  ) {
    return '수량은 1주 이상의 정수로 입력해 주세요.';
  }
  for (const key of ['unitPrice', 'fee', 'tax'] as const) {
    const minimum = key === 'unitPrice' ? 0.01 : 0;
    if (!/^\d{1,15}(\.\d{1,2})?$/.test(fields[key]) || Number(fields[key]) < minimum) {
      return key === 'unitPrice'
        ? '거래 단가는 0.01원 이상, 소수점 둘째 자리까지 입력해 주세요.'
        : '수수료와 세금은 0원 이상, 소수점 둘째 자리까지 입력해 주세요.';
    }
  }
  return null;
}

export function toTransactionRequest(fields: TransactionFields): PortfolioTransactionUpdateRequest {
  return {
    tradeDate: fields.tradeDate,
    quantity: Number(fields.quantity),
    unitPrice: Number(fields.unitPrice),
    fee: Number(fields.fee),
    tax: Number(fields.tax),
  };
}
