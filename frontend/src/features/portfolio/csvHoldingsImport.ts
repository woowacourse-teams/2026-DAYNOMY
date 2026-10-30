import { parseDelimited } from './bulkEditorModel';
import { stockCurrency, type StockSearchItem } from '../stocks/types';

export const CSV_HOLDING_FIELDS = {
  assetCode: '종목코드',
  name: '종목명',
  quantity: '보유 수량',
  unitPrice: '평균 매입가',
  purchaseAmount: '총 매입금액',
  currency: '통화',
  market: '시장',
} as const;
export type CsvHoldingField = keyof typeof CSV_HOLDING_FIELDS;
export type CsvColumnMapping = Record<CsvHoldingField, number>;
export type CsvHoldingTable = {
  text: string;
  rows: { cells: string[]; line: number }[];
  headerIndex: number;
  delimiter: string;
};
export type CsvHoldingRow = {
  line: number;
  assetCode: string;
  name: string;
  quantity: string;
  unitPrice: string;
  purchaseAmount: string;
  currency: string;
  market: string;
};
const aliases: Record<CsvHoldingField, string[]> = {
  assetCode: [
    'assetcode',
    'ticker',
    'symbol',
    'stockcode',
    'code',
    '종목코드',
    '종목번호',
    '단축코드',
    '증권코드',
    '티커',
  ],
  name: [
    'name',
    'stock',
    'stockname',
    'security',
    'assetname',
    '종목',
    '종목명',
    '주식명',
    '상품명',
    '한글종목명',
  ],
  quantity: [
    'quantity',
    'qty',
    'shares',
    'units',
    'count',
    'holdingquantity',
    '개수',
    '수량',
    '보유수량',
    '잔고수량',
    '보유량',
    '주식수',
    '보유주식수',
    '보유주수',
    '주수',
  ],
  unitPrice: [
    'unitprice',
    'averageprice',
    'averagecost',
    'averagepurchaseprice',
    'nativeaveragepurchaseprice',
    'avgprice',
    'avgcost',
    '평단',
    '평단가',
    '평균단가',
    '평균매입가',
    '평균매수가',
    '평균매입단가',
    '평균매수단가',
    '매입단가',
    '매수단가',
    '취득단가',
    '매입평균단가',
    '평균매입가격',
    '평균매수가격',
  ],
  purchaseAmount: [
    'purchaseamount',
    'totalcost',
    'costbasis',
    'bookcost',
    '매입금액',
    '매수금액',
    '총매입금액',
    '취득금액',
    '매입원금',
  ],
  currency: ['currency', 'ccy', '통화', '거래통화', '통화코드', '기준통화'],
  market: ['market', 'exchange', '시장', '거래소', '상장시장', '거래시장', '국가'],
};
const normalizeHeader = (value: string) =>
  value
    .normalize('NFKC')
    .toLowerCase()
    .replace(/\([^)]*\)|\[[^\]]*\]/g, '')
    .replace(/[\s_\-./]/g, '');
export function guessCsvColumns(headers: string[]): CsvColumnMapping {
  return Object.fromEntries(
    Object.entries(aliases).map(([field, names]) => {
      const matches = headers.flatMap((header, index) =>
        names.includes(normalizeHeader(header)) ? [index] : [],
      );
      return [field, matches.length === 1 ? matches[0] : -1];
    }),
  ) as CsvColumnMapping;
}
export function csvTableColumns(table: CsvHoldingTable, headerIndex: number) {
  const width = table.rows[Math.max(0, headerIndex)]?.cells.length ?? 0;
  return Array.from({ length: width }, (_, index) =>
    headerIndex < 0 ? `${index + 1}열` : table.rows[headerIndex].cells[index] || `${index + 1}열`,
  );
}
export function inspectHoldingCsv(text: string, selectedDelimiter?: string): CsvHoldingTable {
  if (text.length > 500_000) throw new Error('500KB 이하의 CSV 파일을 선택해 주세요.');
  let best: (CsvHoldingTable & { score: number }) | undefined;
  let parseError: unknown;
  for (const delimiter of selectedDelimiter ? [selectedDelimiter] : [',', '\t', ';', '|']) {
    try {
      const rows = parseDelimited(text, delimiter);
      if (!rows.length) continue;
      rows.slice(0, 20).forEach((row, index) => {
        if (row.cells.length < 2) return;
        const recognized = Object.values(guessCsvColumns(row.cells)).filter(
          (value) => value >= 0,
        ).length;
        const score = recognized * 100 + Math.min(row.cells.length, 30);
        if (!best || score > best.score)
          best = { text, rows, delimiter, headerIndex: index, score };
      });
    } catch (caught) {
      parseError = caught;
    }
  }
  if (!best)
    throw parseError instanceof Error
      ? parseError
      : new Error('열을 구분할 수 없어요. 쉼표나 탭으로 구분한 CSV를 선택해 주세요.');
  return best;
}
export function csvMappingError(mapping: CsvColumnMapping) {
  if (mapping.assetCode < 0 && mapping.name < 0) return '종목코드 또는 종목명 열을 연결해 주세요.';
  if (mapping.quantity < 0) return '보유 수량 열을 연결해 주세요.';
  if (mapping.unitPrice < 0 && mapping.purchaseAmount < 0)
    return '평균 매입가 또는 총 매입금액 열을 연결해 주세요.';
  const selected = Object.values(mapping).filter((index) => index >= 0);
  if (new Set(selected).size !== selected.length)
    return '하나의 CSV 열은 한 항목에만 연결해 주세요.';
  return null;
}
export function normalizeCsvNumber(value: string) {
  let text = value.normalize('NFKC').trim();
  if (/^\(.*\)$/.test(text)) text = `-${text.slice(1, -1)}`;
  text = text
    .replace(/^(?:US\$|USD|KRW|₩|￦|\$)\s*/i, '')
    .replace(/\s*(?:USD|KRW|원|달러|주|개|shares?|units?)$/i, '')
    .trim()
    .replace(/^\+/, '');
  if (/^-?\d{1,3}(?:,\d{3})+(?:\.\d+)?$/.test(text)) text = text.replace(/,/g, '');
  if (/^-?\d{1,3}(?:\s\d{3})+(?:\.\d+)?$/.test(text)) text = text.replace(/\s/g, '');
  if (/^\d+(?:\.\d+)?[eE][+-]?\d+$/.test(text) && Number.isFinite(Number(text)))
    text = Number(text).toLocaleString('en-US', { useGrouping: false, maximumFractionDigits: 8 });
  return text;
}
export function normalizeCsvCode(value: string) {
  let text = value.normalize('NFKC').trim().toUpperCase().replace(/^'/, '');
  if (/^="[^"]+"$/.test(text)) text = text.slice(2, -1);
  text = text.replace(/\.(KS|KQ)$/, '').replace(/^A(?=\d{6}$)/, '');
  if (
    /^\d+(?:\.0+|(?:\.\d+)?E[+-]?\d+)$/.test(text) &&
    Number.isSafeInteger(Number(text)) &&
    Number(text) <= 999999
  )
    text = String(Number(text));
  if (/^\d{1,6}$/.test(text)) return text.padStart(6, '0');
  return text;
}
export function csvCurrency(value: string) {
  const normalized = value.normalize('NFKC').trim().toUpperCase();
  if (['KRW', '원', '원화', '₩', '￦', '한국원', 'WON'].includes(normalized)) return 'KRW';
  if (['USD', '$', 'US$', '달러', '미국달러', '미달러', '미화', 'DOLLAR'].includes(normalized))
    return 'USD';
  return normalized;
}
function moneyCurrency(value: string) {
  if (/(?:USD|US\$|\$|달러)/i.test(value)) return 'USD';
  if (/(?:KRW|₩|￦|원)/i.test(value)) return 'KRW';
  return '';
}
export function mapHoldingCsv(
  table: CsvHoldingTable,
  headerIndex: number,
  mapping: CsvColumnMapping,
) {
  const invalid = csvMappingError(mapping);
  if (invalid) throw new Error(invalid);
  const columns = csvTableColumns(table, headerIndex);
  const sideColumn = columns.findIndex((column) =>
    ['type', '매매', '매매구분', '거래구분'].includes(normalizeHeader(column)),
  );
  const dated = columns.some((column) =>
    ['tradedate', '거래일', '거래일자', '체결일', '매매일', '매매일자'].includes(
      normalizeHeader(column),
    ),
  );
  if (
    dated &&
    sideColumn >= 0 &&
    table.rows
      .slice(headerIndex + 1)
      .some((row) => /^(BUY|SELL|매수|매도)$/i.test(row.cells[sideColumn] ?? ''))
  )
    throw new Error(
      '매수·매도 날짜가 있는 거래내역 파일입니다. 보유현황으로 등록하지 말고 거래내역 유형과 예제 양식을 사용해 주세요.',
    );
  let skipped = 0;
  const rows: CsvHoldingRow[] = [];
  for (const row of table.rows.slice(headerIndex + 1)) {
    const get = (field: CsvHoldingField) => row.cells[mapping[field]] ?? '';
    if (/^(합계|총계|소계|total|subtotal)$/i.test((get('assetCode') || get('name')).trim())) {
      skipped++;
      continue;
    }
    if (row.cells.length > columns.length && row.cells.slice(columns.length).some(Boolean))
      throw new Error(`${row.line}행: 열 개수가 달라요. 파일의 구분자와 헤더 행을 확인해 주세요.`);
    const rawPrice = mapping.unitPrice >= 0 ? get('unitPrice') : get('purchaseAmount');
    const priceHeader =
      columns[mapping.unitPrice >= 0 ? mapping.unitPrice : mapping.purchaseAmount];
    const declared = csvCurrency(get('currency'));
    const inferred =
      moneyCurrency(rawPrice) ||
      moneyCurrency(priceHeader?.match(/\([^)]*\)|\[[^\]]*\]/)?.[0] ?? '');
    if (declared && inferred && declared !== inferred)
      throw new Error(
        `${row.line}행: 통화 열과 가격의 통화가 달라요. 열 연결과 파일 값을 확인해 주세요.`,
      );
    rows.push({
      line: row.line,
      assetCode: normalizeCsvCode(
        get('assetCode') ||
          get('name').match(/[([]\s*(\d{6}|[A-Z]{1,5}(?:[.-][A-Z]{1,2})?)\s*[)\]]/)?.[1] ||
          get('name').match(/(?:^|\s)(\d{6})(?:\s|$)/)?.[1] ||
          '',
      ),
      name: get('name').trim(),
      quantity: normalizeCsvNumber(get('quantity')).replace(/\.0+$/, ''),
      unitPrice: normalizeCsvNumber(get('unitPrice')),
      purchaseAmount: normalizeCsvNumber(get('purchaseAmount')),
      currency: declared || inferred,
      market: get('market').normalize('NFKC').trim().toUpperCase(),
    });
  }
  if (!rows.length || rows.length > 200)
    throw new Error('한 번에 1~200개 보유 종목을 가져올 수 있어요.');
  return { rows, skipped };
}
const normalizeName = (name: string) =>
  name
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[\s\-.,()]/g, '');
const marketAliases: Record<string, string> = {
  코스피: 'KOSPI',
  코스닥: 'KOSDAQ',
  나스닥: 'NASDAQ',
  뉴욕: 'NYSE',
  한국: 'KR',
  국내: 'KR',
  미국: 'US',
  KOREA: 'KR',
  USA: 'US',
  KRX: 'KR',
};
export function matchCsvStock(row: CsvHoldingRow, candidates: StockSearchItem[]) {
  const market = marketAliases[row.market] ?? row.market.replace(/\s+/g, '_');
  const matches = [...new Map(candidates.map((stock) => [stock.assetId, stock])).values()].filter(
    (stock) => {
      const identity = row.assetCode
        ? normalizeCsvCode(stock.assetCode).replace(/\.US$/, '') ===
          row.assetCode.replace(/\.US$/, '')
        : normalizeName(stock.name) === normalizeName(row.name) ||
          normalizeCsvCode(stock.assetCode).replace(/\.US$/, '') ===
            normalizeCsvCode(row.name).replace(/\.US$/, '');
      const country = stockCurrency(stock) === 'USD' ? 'US' : 'KR';
      return (
        identity &&
        (!market || market === stock.market || market === country) &&
        (!row.currency || row.currency === stockCurrency(stock))
      );
    },
  );
  return matches.length === 1 ? matches[0] : undefined;
}
export function csvAveragePrice(row: CsvHoldingRow, currency: 'KRW' | 'USD') {
  if (row.unitPrice || !row.purchaseAmount) return row.unitPrice;
  if (
    !/^\d{1,30}(?:\.\d{1,8})?$/.test(row.purchaseAmount) ||
    !/^\d{1,16}$/.test(row.quantity) ||
    Number(row.quantity) <= 0
  )
    return '';
  const [integer, fraction = ''] = row.purchaseAmount.split('.');
  const scale = currency === 'USD' ? 4 : 2;
  const numerator = BigInt(integer + fraction) * 10n ** BigInt(scale);
  const denominator = BigInt(row.quantity) * 10n ** BigInt(fraction.length);
  const rounded = ((numerator + denominator / 2n) / denominator)
    .toString()
    .padStart(scale + 1, '0');
  return `${rounded.slice(0, -scale)}.${rounded.slice(-scale)}`.replace(
    /(\.\d*?[1-9])0+$|\.0+$/,
    '$1',
  );
}
export async function readCsvText(file: File) {
  if (typeof file.arrayBuffer !== 'function') return file.text();
  const bytes = new Uint8Array(await file.arrayBuffer());
  if (bytes[0] === 0xff && bytes[1] === 0xfe)
    return new TextDecoder('utf-16le', { fatal: true }).decode(bytes);
  if (bytes[0] === 0xfe && bytes[1] === 0xff)
    return new TextDecoder('utf-16be', { fatal: true }).decode(bytes);
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    return new TextDecoder('euc-kr', { fatal: true }).decode(bytes);
  }
}
