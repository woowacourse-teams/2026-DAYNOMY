import { isStockMarket } from '../stocks/api';
import { stockCurrency, type StockMarket, type StockSearchItem } from '../stocks/types';
import type { PortfolioHoldingInput, PortfolioTransactionRequest } from './types';
import {
  getTransactionValidationError,
  todayInSeoul,
  toTransactionRequest,
  type TransactionFields,
} from './transactionForm';

export type HoldingDraft = { quantity: string; averagePurchasePrice: string };
export const holdingDraft = (holding: PortfolioHoldingInput): HoldingDraft => ({
  quantity: String(holding.quantity),
  averagePurchasePrice: String(nativeAverage(holding) ?? ''),
});
export function nativeAverage(holding: PortfolioHoldingInput) {
  return stockCurrency(holding) === 'KRW'
    ? holding.averagePurchasePrice
    : (holding.nativeAveragePurchasePrice ?? null);
}
export function holdingError(draft: HoldingDraft, currency: 'KRW' | 'USD' = 'KRW') {
  if (
    !/^\d+$/.test(draft.quantity) ||
    !Number.isSafeInteger(Number(draft.quantity)) ||
    Number(draft.quantity) < 1
  )
    return { field: 'quantity' as const, message: '수량은 1주 이상의 정수로 입력해 주세요.' };
  if (
    !(currency === 'USD' ? /^\d{1,15}(\.\d{1,4})?$/ : /^\d{1,15}(\.\d{1,2})?$/).test(
      draft.averagePurchasePrice,
    ) ||
    Number(draft.averagePurchasePrice) < (currency === 'USD' ? 0.0001 : 0.01)
  )
    return {
      field: 'averagePurchasePrice' as const,
      message:
        currency === 'USD'
          ? '평균 매입가는 0.0001 USD 이상, 소수점 넷째 자리까지 입력해 주세요.'
          : '평균 매입가는 0.01원 이상, 소수점 둘째 자리까지 입력해 주세요.',
    };
  return null;
}

export function parseDelimited(
  text: string,
  delimiter = ',',
  preserveEmptyRows = false,
): { cells: string[]; line: number }[] {
  const rows: { cells: string[]; line: number }[] = [];
  let cells: string[] = [],
    value = '',
    quoted = false,
    closed = false,
    line = 1,
    start = 1;
  const source = text.replace(/^\uFEFF/, '');
  const pushCell = () => {
    cells.push(value.trim());
    value = '';
    closed = false;
  };
  const pushRow = () => {
    pushCell();
    if (preserveEmptyRows || cells.some(Boolean)) rows.push({ cells, line: start });
    cells = [];
    start = line + 1;
  };
  for (let index = 0; index < source.length; index++) {
    const char = source[index];
    if (quoted) {
      if (char === '"') {
        if (source[index + 1] === '"') {
          value += '"';
          index++;
        } else {
          quoted = false;
          closed = true;
        }
      } else {
        value += char;
        if (char === '\n') line++;
      }
    } else if (char === '"' && !value && !closed) quoted = true;
    else if (char === delimiter) pushCell();
    else if (char === '\n' || char === '\r') {
      pushRow();
      if (char === '\r' && source[index + 1] === '\n') index++;
      line++;
    } else {
      if (closed && char.trim()) throw new Error(`${line}행: 따옴표 뒤에는 구분자만 올 수 있어요.`);
      if (char === '"') throw new Error(`${line}행: 따옴표 형식을 확인해 주세요.`);
      value += char;
    }
  }
  if (quoted) throw new Error(`${start}행: 닫히지 않은 따옴표가 있어요.`);
  if (value || cells.length || closed) pushRow();
  return rows;
}

export const CSV_HEADERS = [
  'assetCode',
  'market',
  'type',
  'tradeDate',
  'quantity',
  'unitPrice',
  'fee',
  'tax',
  'currency',
  'exchangeRate',
] as const;
export type CsvTrade = {
  line: number;
  assetCode: string;
  market: StockMarket;
  currency: 'KRW' | 'USD';
  type: 'BUY' | 'SELL';
  fields: TransactionFields;
};
export function parseTransactionCsv(text: string): CsvTrade[] {
  if (text.length > 500_000) throw new Error('CSV는 500KB 이하로 업로드해 주세요.');
  const [header, ...rows] = parseDelimited(text);
  if (
    !header ||
    header.cells.length !== CSV_HEADERS.length ||
    new Set(header.cells).size !== CSV_HEADERS.length ||
    CSV_HEADERS.some((key) => !header.cells.includes(key))
  )
    throw new Error('CSV 열 이름이 맞지 않아요. 예제 파일의 첫 행을 그대로 사용해 주세요.');
  if (!rows.length || rows.length > 200)
    throw new Error('한 번에 1~200건의 거래를 가져올 수 있어요.');
  return rows.map(({ cells, line }) => {
    if (cells.length !== header.cells.length) throw new Error(`${line}행: 열 개수가 맞지 않아요.`);
    const get = (key: (typeof CSV_HEADERS)[number]) => cells[header.cells.indexOf(key)];
    const market = get('market'),
      type = get('type'),
      currency = get('currency');
    if (!get('assetCode') || !isStockMarket(market))
      throw new Error(`${line}행: 종목 코드와 시장을 확인해 주세요.`);
    if (type !== 'BUY' && type !== 'SELL')
      throw new Error(`${line}행: 매매 구분은 BUY 또는 SELL로 입력해 주세요.`);
    const expected = market === 'KOSPI' || market === 'KOSDAQ' ? 'KRW' : 'USD';
    if (currency !== expected)
      throw new Error(`${line}행: ${market} 시장의 통화는 ${expected}예요.`);
    const fields: TransactionFields = {
      tradeDate: get('tradeDate'),
      quantity: get('quantity'),
      unitPrice: get('unitPrice'),
      fee: get('fee'),
      tax: get('tax'),
      exchangeRate: get('exchangeRate') || undefined,
    };
    const invalid = getTransactionValidationError(fields, currency);
    if (invalid) throw new Error(`${line}행: ${invalid.message}`);
    if (currency === 'KRW' && fields.exchangeRate && Number(fields.exchangeRate) !== 1)
      throw new Error(`${line}행: 원화 거래의 환율은 비워두거나 1로 입력해 주세요.`);
    return { line, assetCode: get('assetCode'), market, currency, type, fields };
  });
}

export function csvTransaction(row: CsvTrade, stock: StockSearchItem): PortfolioTransactionRequest {
  return { ...toTransactionRequest(row.fields), assetId: stock.assetId, type: row.type };
}

// A balance is a current position, not a buy transaction. Only its cost is converted to KRW.
export function balanceHolding(
  stock: StockSearchItem,
  fields: Pick<TransactionFields, 'quantity' | 'unitPrice' | 'exchangeRate'>,
): PortfolioHoldingInput {
  const invalid = getTransactionValidationError(
    { ...fields, tradeDate: todayInSeoul(), fee: '0', tax: '0' },
    stockCurrency(stock),
  );
  if (invalid) throw new Error(invalid.message.replace('거래 단가', '평균 매입가'));
  const units = (value: string, scale: number) => {
    const [integer, fraction = ''] = value.split('.');
    return BigInt(integer) * 10n ** BigInt(scale) + BigInt(fraction.padEnd(scale, '0'));
  };
  const cents =
    (units(fields.unitPrice, 4) *
      units(stockCurrency(stock) === 'USD' ? fields.exchangeRate! : '1', 6) +
      50_000_000n) /
    100_000_000n;
  const holding = {
    ...stock,
    quantity: Number(fields.quantity),
    averagePurchasePrice: Number(cents) / 100,
    ...(stockCurrency(stock) === 'USD'
      ? { nativeAveragePurchasePrice: Number(fields.unitPrice) }
      : {}),
  };
  if (
    holdingError({
      quantity: String(holding.quantity),
      averagePurchasePrice: String(holding.averagePurchasePrice),
    })
  )
    throw new Error('원화 환산 평균 매입가는 0.01원 이상, 정수 15자리 이내로 입력해 주세요.');
  return holding;
}
