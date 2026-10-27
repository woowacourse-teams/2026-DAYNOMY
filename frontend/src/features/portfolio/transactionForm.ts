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

export type TransactionFields = Record<
  Exclude<keyof PortfolioTransactionUpdateRequest, 'exchangeRate'>,
  string
> & { exchangeRate?: string; tradeTime?: string };

export function getTransactionCostValidationError(
  fields: Pick<TransactionFields, 'fee' | 'tax'>,
  currency: 'KRW' | 'USD' = 'KRW',
): { field: 'fee' | 'tax'; message: string } | null {
  for (const key of ['fee', 'tax'] as const) {
    if (!/^\d{1,15}(\.\d{1,2})?$/.test(fields[key]) || Number(fields[key]) < 0) {
      return {
        field: key,
        message:
          currency === 'KRW'
            ? '수수료와 세금은 0원 이상, 소수점 둘째 자리까지 입력해 주세요.'
            : '수수료와 세금은 0 USD 이상, 소수점 둘째 자리까지 입력해 주세요.',
      };
    }
  }
  return null;
}
export function getTransactionValidationError(
  fields: TransactionFields,
  currency: 'KRW' | 'USD' = 'KRW',
): { field: keyof TransactionFields; message: string } | null {
  const date = new Date(`${fields.tradeDate}T00:00:00Z`);
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(fields.tradeDate) ||
    !Number.isFinite(date.getTime()) ||
    date.toISOString().slice(0, 10) !== fields.tradeDate ||
    fields.tradeDate > todayInSeoul()
  ) {
    return {
      field: 'tradeDate',
      message: '거래일은 오늘 또는 과거의 올바른 날짜로 입력해 주세요.',
    };
  }
  if (fields.tradeTime !== undefined && !/^([01]\d|2[0-3]):[0-5]\d$/.test(fields.tradeTime)) {
    return { field: 'tradeTime', message: '거래 시간을 올바르게 입력해 주세요.' };
  }
  if (
    !/^\d+$/.test(fields.quantity) ||
    !Number.isSafeInteger(Number(fields.quantity)) ||
    Number(fields.quantity) <= 0
  ) {
    return { field: 'quantity', message: '수량은 1주 이상의 정수로 입력해 주세요.' };
  }
  if (
    currency === 'USD' &&
    (!fields.exchangeRate ||
      !/^\d{1,10}(\.\d{1,6})?$/.test(fields.exchangeRate) ||
      Number(fields.exchangeRate) <= 0)
  ) {
    return { field: 'exchangeRate', message: '1 USD당 원화 환율을 0보다 크게 입력해 주세요.' };
  }
  if (
    !/^\d{1,15}(\.\d{1,4})?$/.test(fields.unitPrice) ||
    Number(fields.unitPrice) < (currency === 'USD' ? 0.0001 : 0.01)
  ) {
    return {
      field: 'unitPrice',
      message: `거래 단가는 ${currency === 'USD' ? '0.0001' : '0.01'} ${currency} 이상, 소수점 넷째 자리까지 입력해 주세요.`,
    };
  }
  return getTransactionCostValidationError(fields, currency);
}

export function validateTransaction(
  fields: TransactionFields,
  currency: 'KRW' | 'USD' = 'KRW',
): string | null {
  return getTransactionValidationError(fields, currency)?.message ?? null;
}

export function toTransactionRequest(fields: TransactionFields): PortfolioTransactionUpdateRequest {
  return {
    ...(fields.exchangeRate ? { exchangeRate: Number(fields.exchangeRate) } : {}),
    tradeDate: fields.tradeDate,
    quantity: Number(fields.quantity),
    unitPrice: Number(fields.unitPrice),
    fee: Number(fields.fee),
    tax: Number(fields.tax),
  };
}
