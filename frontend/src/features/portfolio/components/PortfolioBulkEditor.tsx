import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { getLatestStockPrice, getUsdKrwRate, searchStocks } from '../../stocks/api';
import { stockCurrency, type StockSearchItem } from '../../stocks/types';
import type {
  PortfolioHoldingInput,
  PortfolioHoldingResult,
  PortfolioTransactionRequest,
} from '../types';
import {
  holdingDraft,
  nativeAverage,
  balanceHolding,
  holdingError,
  parseTransactionCsv,
  csvTransaction,
  CSV_HEADERS,
  type HoldingDraft,
} from '../bulkEditorModel';
import {
  getTransactionValidationError,
  todayInSeoul,
  type TransactionFields,
} from '../transactionForm';
import { PortfolioDialog } from './PortfolioDialog';
import { holdingReturn } from '../holdingDisplay';
import { useBulkCellSelection, type BulkCell } from '../hooks/useBulkCellSelection';
import { PortfolioBulkStockSearch } from './PortfolioBulkStockSearch';
import { PortfolioCsvColumnMapping } from './PortfolioCsvColumnMapping';
import {
  inspectHoldingCsv,
  guessCsvColumns,
  csvMappingError,
  mapHoldingCsv,
  matchCsvStock,
  csvAveragePrice,
  readCsvText,
  type CsvHoldingTable,
  type CsvColumnMapping,
  type CsvHoldingRow,
} from '../csvHoldingsImport';
import '../portfolioBulkEditor.css';

export type BulkEditorMode = 'holdings' | 'add' | 'csv';
type TradeDraft = TransactionFields & {
  id: number;
  stock?: StockSearchItem;
  type: 'BUY' | 'SELL';
  sourceLine?: number;
  csvRow?: CsvHoldingRow;
  csvPriceEdited?: boolean;
};
export type BulkTransaction = { stock: StockSearchItem; transaction: PortfolioTransactionRequest };
const newTrade = (id: number): TradeDraft => ({
  id,
  type: 'BUY',
  tradeDate: todayInSeoul(),
  quantity: '',
  unitPrice: '',
  fee: '0',
  tax: '0',
  exchangeRate: '',
});
const label = { quantity: '수량', averagePurchasePrice: '평균 매입가' } as const;

export function PortfolioBulkEditor({
  mode,
  holdings,
  prices,
  isLoggedIn,
  amountsHidden,
  onClose,
  onSaveHoldings,
  onAddHoldings,
  onSaveTransactions,
}: {
  mode: BulkEditorMode;
  holdings: PortfolioHoldingInput[];
  prices: PortfolioHoldingResult[];
  isLoggedIn: boolean;
  amountsHidden: boolean;
  onClose: () => void;
  onSaveHoldings: (changes: PortfolioHoldingInput[]) => Promise<void>;
  onAddHoldings: (items: PortfolioHoldingInput[]) => Promise<void>;
  onSaveTransactions: (items: BulkTransaction[], csv: boolean) => Promise<boolean>;
}) {
  const [csvKind, setCsvKind] = useState<'balances' | 'transactions'>('balances');
  const [pendingCsvKind, setPendingCsvKind] = useState<typeof csvKind | null>(null);
  const [csvTable, setCsvTable] = useState<CsvHoldingTable | null>(null);
  const [csvHeader, setCsvHeader] = useState(0);
  const [csvMapping, setCsvMapping] = useState<CsvColumnMapping>(() => guessCsvColumns([]));
  const [csvSkipped, setCsvSkipped] = useState(0);
  const isBalance = mode === 'add' || (mode === 'csv' && csvKind === 'balances');
  const csvTransactions = mode === 'csv' && csvKind === 'transactions';
  const initial = useRef(holdings.map(holdingDraft));
  const [history, setHistory] = useState<HoldingDraft[][]>([initial.current]);
  const [cursor, setCursor] = useState(0);
  const drafts = history[cursor];
  const [tradeState, setTradeState] = useState<{
    past: TradeDraft[][];
    present: TradeDraft[];
    future: TradeDraft[][];
  }>(() => ({ past: [], present: mode === 'add' ? [newTrade(1)] : [], future: [] }));
  const trades = tradeState.present;
  function setTrades(next: TradeDraft[] | ((current: TradeDraft[]) => TradeDraft[])) {
    setTradeState((current) => ({
      past: [...current.past, current.present].slice(-100),
      present: typeof next === 'function' ? next(current.present) : next,
      future: [],
    }));
  }
  const [quotes, setQuotes] = useState(new Map<number, number>());
  const quoteRequests = useRef(new Set<AbortController>());
  useEffect(() => () => quoteRequests.current.forEach((request) => request.abort()), []);
  function unavailableStocks(tradeId: number) {
    const blocked = new Map<number, string>();
    trades.forEach((trade) => {
      if (trade.id !== tradeId && trade.stock) blocked.set(trade.stock.assetId, '추가됨');
    });
    holdings.forEach((holding) => blocked.set(holding.assetId, '보유 중'));
    return blocked;
  }
  const [referenceRates, setReferenceRates] = useState(new Map<number, string>());
  const rateRequests = useRef(new Map<number, AbortController>());
  const [rateStates, setRateStates] = useState(
    new Map<number, { loading: boolean; error: string }>(),
  );
  useEffect(() => () => rateRequests.current.forEach((request) => request.abort()), []);
  function loadExchangeRate(tradeId: number, stock: StockSearchItem, tradeDate: string) {
    rateRequests.current.get(tradeId)?.abort();
    if (stockCurrency(stock) !== 'USD') {
      setRateStates((current) => {
        const next = new Map(current);
        next.delete(tradeId);
        return next;
      });
      return;
    }
    const request = new AbortController();
    rateRequests.current.set(tradeId, request);
    setRateStates((current) => new Map(current).set(tradeId, { loading: true, error: '' }));
    getUsdKrwRate(tradeDate, request.signal)
      .then((quote) => {
        if (request.signal.aborted) return;
        setReferenceRates((current) => new Map(current).set(stock.assetId, String(quote.rate)));
        setTradeState((current) => ({
          ...current,
          present: current.present.map((row) =>
            row.id === tradeId &&
            row.stock?.assetId === stock.assetId &&
            row.tradeDate === tradeDate &&
            !row.exchangeRate
              ? { ...row, exchangeRate: String(quote.rate) }
              : row,
          ),
        }));
        setRateStates((current) => new Map(current).set(tradeId, { loading: false, error: '' }));
      })
      .catch(() => {
        if (!request.signal.aborted)
          setRateStates((current) =>
            new Map(current).set(tradeId, {
              loading: false,
              error: '평가 기준정보를 불러오지 못했어요. 종목을 다시 선택해 주세요.',
            }),
          );
      })
      .finally(() => {
        if (rateRequests.current.get(tradeId) === request) rateRequests.current.delete(tradeId);
      });
  }
  function selectStock(tradeId: number, stock: StockSearchItem) {
    const trade = trades.find((trade) => trade.id === tradeId);
    if (!trade || (isBalance && unavailableStocks(tradeId).has(stock.assetId))) return;
    updateTrade(tradeId, {
      stock,
      exchangeRate: stockCurrency(stock) === 'KRW' ? '1' : '',
      ...(trade.csvRow && !trade.csvPriceEdited && !trade.csvRow.unitPrice
        ? { unitPrice: csvAveragePrice(trade.csvRow, stockCurrency(stock)) }
        : {}),
    });
    loadExchangeRate(tradeId, stock, trade.tradeDate);
    const request = new AbortController();
    quoteRequests.current.add(request);
    getLatestStockPrice(stock.assetId, request.signal)
      .then((price) => {
        if (!request.signal.aborted)
          setQuotes((current) => new Map(current).set(stock.assetId, price.closePrice));
      })
      .catch(() => {})
      .finally(() => quoteRequests.current.delete(request));
  }
  const nextId = useRef(2);
  const [busy, setBusy] = useState(false);
  const saving = useRef(false);
  const [reading, setReading] = useState(false);
  const [error, setError] = useState('');
  const [fileName, setFileName] = useState('');
  const [discarding, setDiscarding] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const fileRequest = useRef<AbortController | null>(null);
  const csvInputRef = useRef<HTMLInputElement>(null);
  const tableRef = useRef<HTMLDivElement>(null);
  const cells = useBulkCellSelection({
    tableRef,
    rowKey: `${mode}:${csvKind}:${holdings.map((row) => row.assetId).join(',')}:${trades.map((row) => row.id).join(',')}`,
    disabled: busy || reading,
    onPaste: pasteCells,
    onError: setError,
  });
  useEffect(() => () => fileRequest.current?.abort(), []);
  const changed =
    mode === 'holdings'
      ? holdings.filter(
          (_holding, index) =>
            drafts[index].quantity !== initial.current[index].quantity ||
            drafts[index].averagePurchasePrice !== initial.current[index].averagePurchasePrice,
        )
      : [];
  const invalidHoldings = drafts.map((draft, index) =>
    !draft.averagePurchasePrice &&
    !initial.current[index].averagePurchasePrice &&
    draft.quantity === initial.current[index].quantity
      ? null
      : holdingError(draft, stockCurrency(holdings[index])),
  );
  const priceByAsset = new Map(prices.map((price) => [price.assetId, price]));
  const evaluations =
    mode === 'holdings'
      ? holdings.map((holding, index) => {
          const price = priceByAsset.get(holding.assetId);
          return price?.closePrice == null || invalidHoldings[index]
            ? null
            : price.closePrice * Number(drafts[index].quantity);
        })
      : [];
  const balanceFields = (trade: TradeDraft) => ({
    ...trade,
    exchangeRate:
      trade.stock && stockCurrency(trade.stock) === 'USD'
        ? (referenceRates.get(trade.stock.assetId) ?? trade.exchangeRate)
        : '1',
  });
  const tradeErrors = trades.map((trade) => {
    if (!trade.stock) return '종목을 검색해서 선택해 주세요.';
    if (isBalance && unavailableStocks(trade.id).has(trade.stock.assetId))
      return '이미 입력된 종목이에요. 다른 종목을 선택해 주세요.';
    if (isBalance) {
      if (
        trade.csvRow?.currency &&
        trade.csvRow.currency !== stockCurrency(trade.stock) &&
        !trade.csvPriceEdited
      )
        return `파일 가격의 통화는 ${trade.csvRow.currency}입니다. ${stockCurrency(trade.stock)} 기준 평단가로 확인해 수정해 주세요.`;
      if (stockCurrency(trade.stock) === 'USD' && !balanceFields(trade).exchangeRate)
        return rateStates.get(trade.id)?.loading
          ? '평가 기준정보를 불러오는 중입니다.'
          : '종목을 다시 선택해 평가 기준정보를 조회해 주세요.';
      try {
        balanceHolding(trade.stock, balanceFields(trade));
        return null;
      } catch (caught) {
        return caught instanceof Error ? caught.message : '입력값을 확인해 주세요.';
      }
    }
    return getTransactionValidationError(trade, stockCurrency(trade.stock))?.message ?? null;
  });
  const dirty =
    mode === 'holdings'
      ? changed.length > 0
      : trades.some((trade) => trade.stock || trade.quantity || trade.unitPrice);
  const valid =
    mode === 'holdings'
      ? changed.length > 0 && !invalidHoldings.some(Boolean)
      : trades.length > 0 && !tradeErrors.some(Boolean);
  const title =
    mode === 'holdings'
      ? '포트폴리오 일괄편집'
      : mode === 'add'
        ? '여러 종목 일괄 추가'
        : 'CSV 파일 업로드';
  function close() {
    if (busy) return;
    if (reading) {
      fileRequest.current?.abort();
      setReading(false);
      onClose();
      return;
    }
    if (dirty) setDiscarding(true);
    else onClose();
  }
  function change(next: HoldingDraft[]) {
    setHistory([...history.slice(0, cursor + 1), next].slice(-100));
    setCursor(Math.min(cursor + 1, 99));
    setError('');
  }
  function updateTrade(id: number, values: Partial<TradeDraft>) {
    setTrades((current) => current.map((row) => (row.id === id ? { ...row, ...values } : row)));
    setError('');
  }
  function pasteCells(start: BulkCell, values: string[][]) {
    const rowCount =
      mode === 'holdings' ? drafts.length : (mode === 'add' ? holdings.length : 0) + trades.length;
    const width = values[0].length;
    if (start.row + values.length > rowCount)
      throw new Error('현재 표의 행 수 안에서 붙여넣어 주세요. 필요한 행은 먼저 추가해 주세요.');
    if (csvTransactions) {
      if (
        start.column !== 3 ||
        width !== 1 ||
        values.some((_row, offset) => {
          const stock = trades[start.row + offset].stock;
          return !stock || stockCurrency(stock) !== 'USD';
        })
      )
        throw new Error('CSV 미리보기에서는 미국 거래의 환율만 수정할 수 있습니다.');
      const affected = trades.slice(start.row, start.row + values.length);
      affected.forEach((trade) => {
        rateRequests.current.get(trade.id)?.abort();
        rateRequests.current.delete(trade.id);
      });
      setRateStates((current) => {
        const next = new Map(current);
        affected.forEach((trade) => next.delete(trade.id));
        return next;
      });
      setTrades((current) =>
        current.map((trade, index) =>
          index >= start.row && index < start.row + values.length
            ? { ...trade, exchangeRate: values[index - start.row][0].replace(/,/g, '') }
            : trade,
        ),
      );
    } else {
      if (
        start.column < 3 ||
        start.column + width > 5 ||
        (mode === 'add' && start.row < holdings.length)
      )
        throw new Error('수정 가능한 수량·평균 매입가 칸에만 붙여넣을 수 있습니다.');
      if (mode === 'holdings') {
        const next = drafts.map((draft) => ({ ...draft }));
        values.forEach((row, offset) =>
          row.forEach((value, column) => {
            const field = start.column + column === 3 ? 'quantity' : 'averagePurchasePrice';
            next[start.row + offset][field] = value.replace(/,/g, '');
          }),
        );
        change(next);
      } else {
        setTrades((current) => {
          const next = current.map((trade) => ({ ...trade }));
          values.forEach((row, offset) =>
            row.forEach((value, column) => {
              const field = start.column + column === 3 ? 'quantity' : 'unitPrice';
              const target = next[start.row - (mode === 'add' ? holdings.length : 0) + offset];
              target[field] = value.replace(/,/g, '');
              if (field === 'unitPrice') target.csvPriceEdited = true;
            }),
          );
          return next;
        });
      }
    }
    setError('');
  }
  function keydown(event: KeyboardEvent<HTMLInputElement>, row: number, column: number) {
    if (event.key === 'ArrowUp' || event.key === 'ArrowDown' || event.key === 'Enter') {
      const next =
        row + (event.key === 'ArrowUp' || (event.key === 'Enter' && event.shiftKey) ? -1 : 1);
      const input = tableRef.current?.querySelector<HTMLInputElement>(
        `[data-row="${next}"][data-col="${column}"]`,
      );
      if (input) {
        event.preventDefault();
        input.focus();
        input.select();
      }
    }
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'd' && row > 0) {
      event.preventDefault();
      const field = column === 0 ? 'quantity' : 'averagePurchasePrice';
      change(
        drafts.map((item, index) =>
          index === row ? { ...item, [field]: drafts[row - 1][field] } : item,
        ),
      );
    }
  }
  async function readFile(file: File | undefined) {
    if (!file) return;
    fileRequest.current?.abort();
    const controller = new AbortController();
    fileRequest.current = controller;
    setReading(true);
    setError('');
    setTradeState({ past: [], present: [], future: [] });
    setCsvTable(null);
    setCsvSkipped(0);
    setFileName(file.name);
    try {
      if (!file.name.toLowerCase().endsWith('.csv') || file.size > 500_000)
        throw new Error('500KB 이하의 CSV 파일을 선택해 주세요.');
      const text = await readCsvText(file);
      if (controller.signal.aborted) return;
      if (csvKind === 'balances') {
        const table = inspectHoldingCsv(text);
        const mapping = guessCsvColumns(table.rows[table.headerIndex].cells);
        setCsvTable(table);
        setCsvHeader(table.headerIndex);
        setCsvMapping(mapping);
        setCsvSkipped(0);
        if (!csvMappingError(mapping))
          await previewCsvHoldings({ table, header: table.headerIndex, mapping, controller });
        return;
      }
      const rows = parseTransactionCsv(text);
      const stocks = new Map<string, StockSearchItem>();
      const unique = [
        ...new Map(rows.map((row) => [`${row.market}:${row.assetCode}`, row])).values(),
      ];
      for (let offset = 0; offset < unique.length; offset += 8) {
        if (controller.signal.aborted) return;
        await Promise.all(
          unique.slice(offset, offset + 8).map(async (row) => {
            const results = await searchStocks(
              row.assetCode,
              controller.signal,
              row.currency === 'USD' ? 'US' : 'KR',
            );
            const matches = results.filter(
              (item) => item.assetCode === row.assetCode && item.market === row.market,
            );
            if (matches.length !== 1)
              throw new Error(
                `${row.line}행: 종목 코드와 시장에 맞는 종목을 찾을 수 없어요. 국내 코드는 앞자리 0까지 입력해 주세요.`,
              );
            stocks.set(`${row.market}:${row.assetCode}`, matches[0]);
          }),
        );
      }
      const next: TradeDraft[] = rows.map((row) => ({
        ...row.fields,
        id: nextId.current++,
        stock: stocks.get(`${row.market}:${row.assetCode}`)!,
        type: row.type,
        sourceLine: row.line,
      }));
      if (!controller.signal.aborted) setTrades(next);
    } catch (caught) {
      if (!controller.signal.aborted)
        setError(caught instanceof Error ? caught.message : 'CSV 파일을 읽지 못했어요.');
    } finally {
      if (!controller.signal.aborted) setReading(false);
    }
  }
  function changeCsvKind(kind: typeof csvKind) {
    fileRequest.current?.abort();
    setCsvKind(kind);
    setPendingCsvKind(null);
    setCsvTable(null);
    setFileName('');
    setCsvSkipped(0);
    setError('');
    setTradeState({ past: [], present: [], future: [] });
  }
  function requestCsvKind(kind: typeof csvKind) {
    if (kind === csvKind) return;
    if (fileName) setPendingCsvKind(kind);
    else changeCsvKind(kind);
  }
  async function previewCsvHoldings(source: {
    table: CsvHoldingTable;
    header: number;
    mapping: CsvColumnMapping;
    controller?: AbortController;
  }) {
    if (busy) return;
    if (!source.controller) fileRequest.current?.abort();
    const controller = source.controller ?? new AbortController();
    fileRequest.current = controller;
    setReading(true);
    setError('');
    try {
      const imported = mapHoldingCsv(source.table, source.header, source.mapping);
      const searches = new Map<string, Promise<StockSearchItem[]>>();
      const next: TradeDraft[] = [];
      for (let offset = 0; offset < imported.rows.length; offset += 8) {
        if (controller.signal.aborted) return;
        const batch = await Promise.all(
          imported.rows.slice(offset, offset + 8).map(async (row) => {
            const query = row.assetCode || row.name;
            if (query && !searches.has(query))
              searches.set(
                query,
                Promise.all([
                  searchStocks(query, controller.signal),
                  searchStocks(query, controller.signal, 'US'),
                ])
                  .then((results) => results.flat())
                  .catch(() => []),
              );
            const stock = matchCsvStock(row, query ? await searches.get(query)! : []);
            return {
              ...newTrade(nextId.current++),
              stock,
              sourceLine: row.line,
              csvRow: row,
              quantity: row.quantity,
              unitPrice: csvAveragePrice(
                row,
                stock ? stockCurrency(stock) : row.currency === 'USD' ? 'USD' : 'KRW',
              ),
              exchangeRate: stock && stockCurrency(stock) === 'KRW' ? '1' : '',
            };
          }),
        );
        next.push(...batch);
      }
      if (controller.signal.aborted) return;
      const usdRows = next.filter((row) => row.stock && stockCurrency(row.stock) === 'USD');
      if (usdRows.length) {
        try {
          const quote = await getUsdKrwRate(todayInSeoul(), controller.signal);
          if (controller.signal.aborted) return;
          usdRows.forEach((row) => {
            row.exchangeRate = String(quote.rate);
          });
          setReferenceRates((current) => {
            const rates = new Map(current);
            usdRows.forEach((row) => rates.set(row.stock!.assetId, String(quote.rate)));
            return rates;
          });
        } catch {
          if (controller.signal.aborted) return;
        }
      }
      const unique = [
        ...new Map(
          next.flatMap((row) => (row.stock ? [[row.stock.assetId, row.stock] as const] : [])),
        ).values(),
      ];
      const fetchedQuotes = new Map<number, number>();
      for (let offset = 0; offset < unique.length; offset += 8) {
        if (controller.signal.aborted) return;
        await Promise.all(
          unique.slice(offset, offset + 8).map(async (stock) => {
            try {
              const quote = await getLatestStockPrice(stock.assetId, controller.signal);
              fetchedQuotes.set(stock.assetId, quote.closePrice);
            } catch {
              /* A missing quote does not change the imported cost or prevent position registration. */
            }
          }),
        );
      }
      if (!controller.signal.aborted) {
        setQuotes((current) => new Map([...current, ...fetchedQuotes]));
        setTradeState({ past: [], present: next, future: [] });
        setCsvSkipped(imported.skipped);
      }
    } catch (caught) {
      if (!controller.signal.aborted) {
        setTradeState({ past: [], present: [], future: [] });
        setError(caught instanceof Error ? caught.message : '보유현황을 확인하지 못했어요.');
      }
    } finally {
      if (!controller.signal.aborted) setReading(false);
    }
  }
  function updateCsvColumns(table: CsvHoldingTable, header: number, mapping: CsvColumnMapping) {
    fileRequest.current?.abort();
    setCsvTable(table);
    setCsvHeader(header);
    setCsvMapping(mapping);
    setCsvSkipped(0);
    setError('');
    if (csvMappingError(mapping)) {
      setTradeState({ past: [], present: [], future: [] });
    } else {
      void previewCsvHoldings({ table, header, mapping });
    }
  }
  function template() {
    const content =
      '\uFEFF' +
      (csvKind === 'balances'
        ? '종목코드,종목명,보유수량,평균매입가\r\n005930,삼성전자,10,70000\r\nAAPL,애플,5,200\r\n'
        : CSV_HEADERS.join(',') +
          '\r\n' +
          `005930,KOSPI,BUY,${todayInSeoul()},1,70000,0,0,KRW,\r\n`);
    const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = csvKind === 'balances' ? 'daynomy-holdings.csv' : 'daynomy-transactions.csv';
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  async function save() {
    if (!valid || saving.current || reading) return;
    saving.current = true;
    setBusy(true);
    setError('');
    try {
      if (mode === 'holdings') {
        const changes: PortfolioHoldingInput[] = [];
        let referenceRate: number | undefined;
        for (const [index, holding] of holdings.entries()) {
          if (!changed.includes(holding)) continue;
          const draft = drafts[index];
          if (stockCurrency(holding) === 'KRW') {
            changes.push({
              ...holding,
              quantity: Number(draft.quantity),
              averagePurchasePrice: Number(draft.averagePurchasePrice),
            });
          } else if (draft.averagePurchasePrice === initial.current[index].averagePurchasePrice) {
            changes.push({ ...holding, quantity: Number(draft.quantity) });
          } else {
            const originalNative = nativeAverage(holding);
            const rate =
              originalNative == null
                ? (referenceRate ??= (await getUsdKrwRate(todayInSeoul())).rate)
                : holding.averagePurchasePrice / originalNative;
            changes.push(
              balanceHolding(holding, {
                quantity: draft.quantity,
                unitPrice: draft.averagePurchasePrice,
                exchangeRate: rate.toFixed(6),
              }),
            );
          }
        }
        await onSaveHoldings(changes);
      } else if (isBalance) {
        await onAddHoldings(
          trades.map((trade) => balanceHolding(trade.stock!, balanceFields(trade))),
        );
      } else
        await onSaveTransactions(
          trades.map((trade) => ({
            stock: trade.stock!,
            transaction: csvTransaction(
              {
                ...trade,
                line: trade.sourceLine ?? 0,
                assetCode: trade.stock!.assetCode,
                market: trade.stock!.market,
                currency: stockCurrency(trade.stock!),
                fields: trade,
              },
              trade.stock!,
            ),
          })),
          mode === 'csv',
        );
      onClose();
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : '저장하지 못했어요. 입력값을 확인하고 다시 시도해 주세요.',
      );
    } finally {
      saving.current = false;
      setBusy(false);
    }
  }

  const undoAvailable = mode === 'holdings' ? cursor > 0 : tradeState.past.length > 0;
  const redoAvailable =
    mode === 'holdings' ? cursor < history.length - 1 : tradeState.future.length > 0;
  function undo() {
    if (mode === 'holdings') setCursor((value) => Math.max(0, value - 1));
    else
      setTradeState((current) =>
        current.past.length
          ? {
              past: current.past.slice(0, -1),
              present: current.past.at(-1)!,
              future: [current.present, ...current.future],
            }
          : current,
      );
  }
  function redo() {
    if (mode === 'holdings') setCursor((value) => Math.min(history.length - 1, value + 1));
    else
      setTradeState((current) =>
        current.future.length
          ? {
              past: [...current.past, current.present],
              present: current.future[0],
              future: current.future.slice(1),
            }
          : current,
      );
  }
  function tradeKeydown(
    event: KeyboardEvent<HTMLInputElement>,
    row: number,
    field: keyof TransactionFields,
  ) {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'd' && row > 0) {
      event.preventDefault();
      updateTrade(trades[row].id, {
        [field]: trades[row - 1][field],
        ...(field === 'unitPrice' ? { csvPriceEdited: true } : {}),
      });
    } else if (['ArrowUp', 'ArrowDown', 'Enter'].includes(event.key)) {
      const next =
        row + (event.key === 'ArrowUp' || (event.key === 'Enter' && event.shiftKey) ? -1 : 1);
      const input = tableRef.current?.querySelector<HTMLInputElement>(
        `[data-trade-row="${next}"][data-field="${field}"]`,
      );
      if (input) {
        event.preventDefault();
        input.focus();
        if (input.type !== 'date') input.select();
      }
    }
  }
  function revealField(input: HTMLInputElement) {
    window.requestAnimationFrame(() => {
      const scroll = tableRef.current;
      const stock = scroll?.querySelector('thead th:nth-child(2)');
      if (!scroll || !stock || !input.isConnected) return;
      const left = stock.getBoundingClientRect().right + 1;
      const right = scroll.getBoundingClientRect().right;
      const cell = input.getBoundingClientRect();
      if (cell.left < left) scroll.scrollLeft -= left - cell.left;
      else if (cell.right > right) scroll.scrollLeft += cell.right - right;
    });
  }
  function tradeField(
    trade: TradeDraft,
    index: number,
    field: 'quantity' | 'unitPrice' | 'tradeDate' | 'fee' | 'tax' | 'exchangeRate',
  ) {
    const editable =
      field === 'exchangeRate'
        ? Boolean(trade.stock && stockCurrency(trade.stock) === 'USD')
        : isBalance;
    const rateState = field === 'exchangeRate' ? rateStates.get(trade.id) : undefined;
    const invalid = trade.stock
      ? getTransactionValidationError(
          isBalance ? { ...trade, tradeDate: todayInSeoul(), fee: '0', tax: '0' } : trade,
          stockCurrency(trade.stock),
        )
      : null;
    return (
      <td
        key={field}
        className={editable ? `is-editable${invalid?.field === field ? ' is-invalid' : ''}` : ''}
      >
        {editable ? (
          <input
            aria-label={`${index + 1}행 ${{ tradeDate: '거래일', quantity: '수량', unitPrice: isBalance ? '평균 매입가' : '거래 단가', fee: '수수료', tax: '세금', exchangeRate: '적용 환율' }[field]}`}
            data-trade-row={index}
            data-field={field}
            onFocus={(event) => revealField(event.currentTarget)}
            aria-invalid={invalid?.field === field}
            title={invalid?.field === field ? invalid.message : rateState?.error || undefined}
            aria-busy={rateState?.loading || undefined}
            type={field === 'tradeDate' ? 'date' : 'text'}
            max={field === 'tradeDate' ? todayInSeoul() : undefined}
            inputMode={
              field === 'tradeDate' ? undefined : field === 'quantity' ? 'numeric' : 'decimal'
            }
            placeholder={
              field === 'exchangeRate'
                ? rateState?.loading
                  ? '불러오는 중…'
                  : '환율 입력'
                : field === 'quantity' || field === 'unitPrice'
                  ? '입력'
                  : '0'
            }
            disabled={busy || reading}
            value={trade[field] ?? ''}
            onChange={(event) => {
              const value = event.target.value.replace(/,/g, '');
              if (field === 'tradeDate' && trade.stock && stockCurrency(trade.stock) === 'USD') {
                updateTrade(trade.id, { tradeDate: value, exchangeRate: '' });
                loadExchangeRate(trade.id, trade.stock, value);
              } else {
                if (field === 'exchangeRate') {
                  rateRequests.current.get(trade.id)?.abort();
                  rateRequests.current.delete(trade.id);
                  setRateStates((current) =>
                    new Map(current).set(trade.id, { loading: false, error: '' }),
                  );
                }
                updateTrade(trade.id, {
                  [field]: value,
                  ...(field === 'unitPrice' ? { csvPriceEdited: true } : {}),
                });
              }
            }}
            onKeyDown={(event) => tradeKeydown(event, index, field)}
          />
        ) : field === 'exchangeRate' ? (
          trade.stock && stockCurrency(trade.stock) === 'KRW' ? (
            '1'
          ) : (
            trade.exchangeRate || '—'
          )
        ) : (
          trade[field]
        )}
      </td>
    );
  }
  function nativeMoney(value: number | null | undefined, stock?: StockSearchItem) {
    if (value == null || !Number.isFinite(value)) return '—';
    if (amountsHidden) return '••••••';
    return `${stock && stockCurrency(stock) === 'USD' ? '$' : '₩'} ${value.toLocaleString('ko-KR', { maximumFractionDigits: 4 })}`;
  }
  function profitDisplay(value: number | null, cost: number | null, stock?: StockSearchItem) {
    const rate = value == null || cost == null || cost <= 0 ? null : (value / cost) * 100;
    return (
      <span className="portfolio-bulk-profit-inline">
        <span>{nativeMoney(value, stock)}</span>
        {!amountsHidden && rate != null && <small>{holdingReturn(rate)}</small>}
      </span>
    );
  }
  const invalidTradeCount = tradeErrors.filter(Boolean).length;
  const saveLabel = busy
    ? '저장 중…'
    : mode === 'holdings'
      ? '변경사항 저장'
      : `${trades.length}건 ${mode === 'csv' ? '가져오기' : '등록'}`;
  const summary =
    mode === 'holdings'
      ? changed.length
        ? `${changed.length}개 종목 변경됨`
        : '변경 없음'
      : mode === 'csv' && reading
        ? '파일 확인 중…'
        : mode === 'csv' && !trades.length
          ? csvTable
            ? '열 연결 확인'
            : '파일 선택'
          : invalidTradeCount
            ? `${invalidTradeCount}개 행 확인 필요`
            : `${trades.length}건 준비됨`;
  return (
    <PortfolioDialog
      titleId="portfolio-bulk-title"
      className={`portfolio-bulk-sheet${expanded ? ' is-expanded' : ''}`}
      busy={busy}
      onClose={close}
    >
      <section
        className="portfolio-bulk-editor"
        onKeyDown={(event) => {
          if (busy || csvTransactions || !(event.metaKey || event.ctrlKey)) return;
          if (event.key.toLowerCase() === 'z') {
            event.preventDefault();
            if (event.shiftKey) redo();
            else undo();
          }
        }}
      >
        <header className="portfolio-bulk-heading">
          <span className="portfolio-bulk-symbol" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <rect x="4" y="4" width="16" height="16" />
              <path d="M4 10h16M10 4v16M4 15h16" />
            </svg>
          </span>
          <div className="portfolio-bulk-heading-copy">
            <h2 id="portfolio-bulk-title" aria-label={title}>
              {mode === 'csv'
                ? csvTransactions
                  ? 'CSV 거래내역 가져오기'
                  : '엑셀 보유종목 가져오기'
                : '포트폴리오 일괄편집'}
            </h2>
            <p>
              드래그로 셀 범위 선택 <span>·</span> <kbd>⌘C</kbd> / <kbd>⌘V</kbd> 복붙 <span>·</span>{' '}
              <kbd>⌘D</kbd> 채우기
            </p>
          </div>
          {mode === 'add' && (
            <button
              type="button"
              className="portfolio-bulk-add-asset"
              disabled={busy || trades.length >= 200}
              onClick={() => setTrades((current) => [...current, newTrade(nextId.current++)])}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
              자산 추가
            </button>
          )}
          {mode === 'csv' && (
            <button
              type="button"
              className="portfolio-bulk-add-asset portfolio-bulk-csv-template"
              onClick={template}
              disabled={busy || reading}
            >
              예제 다운로드
            </button>
          )}
          <span className={`portfolio-bulk-change${dirty ? ' has-changes' : ''}`} role="status">
            {summary}
          </span>
          <button
            type="button"
            className="portfolio-icon-button portfolio-bulk-expand"
            aria-label={expanded ? '편집창 축소' : '편집창 확대'}
            disabled={busy}
            onClick={() => setExpanded(!expanded)}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5" />
            </svg>
          </button>
          <button
            type="button"
            className="portfolio-icon-button"
            aria-label="일괄 편집 닫기"
            disabled={busy}
            onClick={close}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="m6 6 12 12M18 6 6 18" />
            </svg>
          </button>
        </header>
        <div className="portfolio-bulk-toolbar">
          {mode !== 'csv' ? (
            <div className="portfolio-bulk-history">
              <button
                type="button"
                className="portfolio-secondary-button"
                disabled={!undoAvailable || busy}
                onClick={undo}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m8 4-5 5 5 5M3 9h10a7 7 0 0 1 7 7v4" />
                </svg>
                되돌리기
              </button>
              <button
                type="button"
                className="portfolio-secondary-button"
                disabled={!redoAvailable || busy}
                onClick={redo}
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m16 4 5 5-5 5m5-5H11a7 7 0 0 0-7 7v4" />
                </svg>
                다시 실행
              </button>
              <span>
                <kbd>⌘C</kbd> / <kbd>⌘V</kbd> · <kbd>⌘D</kbd> · <kbd>⌘Z</kbd>
              </span>
            </div>
          ) : (
            <span className="portfolio-bulk-file-name">{fileName || 'CSV 파일을 선택하세요'}</span>
          )}
          <div className="portfolio-bulk-legend">
            <span>
              <i />
              수정 가능
            </span>
            <span>
              <i />
              읽기 전용
            </span>
          </div>
          <div className="portfolio-bulk-toolbar-actions">
            <button
              type="button"
              className="portfolio-secondary-button"
              disabled={busy}
              onClick={close}
            >
              취소
            </button>
            <button
              type="button"
              className="portfolio-primary-button"
              disabled={!valid || busy || reading}
              title={
                mode !== 'holdings' && invalidTradeCount
                  ? `${tradeErrors.findIndex(Boolean) + 1}행: ${tradeErrors.find(Boolean)}`
                  : undefined
              }
              onClick={() => void save()}
            >
              {saveLabel}
            </button>
          </div>
        </div>
        {mode === 'csv' && (
          <div className="portfolio-bulk-upload">
            <div className="portfolio-csv-kind" role="group" aria-label="가져올 데이터">
              <button
                type="button"
                aria-pressed={csvKind === 'balances'}
                disabled={busy || reading}
                onClick={() => requestCsvKind('balances')}
              >
                보유현황
              </button>
              <button
                type="button"
                aria-pressed={csvKind === 'transactions'}
                disabled={busy || reading}
                onClick={() => requestCsvKind('transactions')}
              >
                거래내역
              </button>
            </div>
            <button
              type="button"
              className="portfolio-bulk-csv-select"
              disabled={busy || reading}
              onClick={() => csvInputRef.current?.click()}
            >
              {reading ? '파일 확인 중…' : 'CSV 파일 선택'}
            </button>
            <input
              ref={csvInputRef}
              id="portfolio-csv-file"
              type="file"
              hidden
              aria-label="CSV 파일 선택"
              accept=".csv,text/csv"
              disabled={busy || reading}
              onChange={(event) => {
                void readFile(event.target.files?.[0]);
                event.target.value = '';
              }}
            />
            <span>CSV · 최대 200건 · 500KB</span>
          </div>
        )}
        {mode === 'csv' && csvTable && csvKind === 'balances' && (
          <PortfolioCsvColumnMapping
            table={csvTable}
            headerIndex={csvHeader}
            mapping={csvMapping}
            disabled={busy || reading}
            previewReady={trades.length > 0}
            onChange={(header, mapping) => updateCsvColumns(csvTable, header, mapping)}
            onDelimiter={(delimiter) => {
              try {
                const table = inspectHoldingCsv(csvTable.text, delimiter);
                updateCsvColumns(
                  table,
                  table.headerIndex,
                  guessCsvColumns(table.rows[table.headerIndex].cells),
                );
              } catch (caught) {
                setError(caught instanceof Error ? caught.message : '구분자를 확인해 주세요.');
              }
            }}
          />
        )}
        {mode === 'csv' && csvSkipped > 0 && (
          <p className="portfolio-csv-note">합계·소계 {csvSkipped}개 행은 제외했습니다.</p>
        )}
        {error && (
          <p className="portfolio-bulk-error" role="alert">
            {error}
          </p>
        )}
        <div
          ref={tableRef}
          {...cells.handlers}
          aria-describedby={cells.descriptionId}
          className="portfolio-bulk-table-scroll"
          role="region"
          aria-label="일괄 편집 표, 가로 스크롤 가능"
          tabIndex={0}
        >
          {mode === 'holdings' ? (
            <table className="portfolio-bulk-table">
              <caption className="sr-only">
                보유 수량과 종목 통화 기준 평균 매입가를 수정할 수 있습니다. 미국 종목은 USD, 국내
                종목은 KRW입니다.
              </caption>
              <thead>
                <tr>
                  <th scope="col">#</th>
                  <th scope="col">종목</th>
                  <th scope="col">유형</th>
                  <th scope="col">통화</th>
                  <th scope="col" className="is-editable">
                    보유 수량
                  </th>
                  <th scope="col" className="is-editable">
                    평균 매입가
                  </th>
                  <th scope="col">
                    현재가 <small>거래 통화</small>
                  </th>
                  <th scope="col">
                    평가금액 <small>계산</small>
                  </th>
                  <th scope="col">
                    평가손익 <small>계산</small>
                  </th>
                </tr>
              </thead>
              <tbody>
                {holdings.map((holding, index) => {
                  const price = priceByAsset.get(holding.assetId);
                  const invalid = invalidHoldings[index];
                  const evaluation = evaluations[index];
                  const profit =
                    evaluation == null || !drafts[index].averagePurchasePrice
                      ? null
                      : evaluation -
                        Number(drafts[index].quantity) * Number(drafts[index].averagePurchasePrice);
                  return (
                    <tr key={holding.assetId}>
                      <td>{index + 1}</td>
                      <th scope="row" data-copy-value={`${holding.assetCode} ${holding.name}`}>
                        <span className="portfolio-bulk-stock-identity">
                          <i aria-hidden="true">{holding.name.slice(0, 1)}</i>
                          <strong>{holding.assetCode}</strong>
                          <small>{holding.name}</small>
                        </span>
                      </th>
                      <td>{holding.category === 'ETF' ? 'ETF' : '주식'}</td>
                      <td>
                        <span className="portfolio-bulk-badge">{stockCurrency(holding)}</span>
                      </td>
                      {(['quantity', 'averagePurchasePrice'] as const).map((field, column) => (
                        <td
                          key={field}
                          className={`is-editable${drafts[index][field] !== initial.current[index][field] ? ' is-changed' : ''}`}
                        >
                          <input
                            data-row={index}
                            data-col={column}
                            onFocus={(event) => revealField(event.currentTarget)}
                            aria-label={`${holding.name} ${label[field]}`}
                            aria-invalid={invalid?.field === field}
                            aria-describedby={
                              invalid?.field === field ? `bulk-holding-error-${index}` : undefined
                            }
                            inputMode={column === 0 ? 'numeric' : 'decimal'}
                            placeholder={
                              field === 'averagePurchasePrice' ? '평단가 입력' : undefined
                            }
                            value={drafts[index][field]}
                            disabled={busy}
                            onChange={(event) =>
                              change(
                                drafts.map((item, row) =>
                                  row === index
                                    ? { ...item, [field]: event.target.value.replace(/,/g, '') }
                                    : item,
                                ),
                              )
                            }
                            onKeyDown={(event) => keydown(event, index, column)}
                          />
                          {invalid?.field === field && (
                            <small
                              id={`bulk-holding-error-${index}`}
                              className="portfolio-bulk-cell-error"
                            >
                              {invalid.message}
                            </small>
                          )}
                        </td>
                      ))}
                      <td data-copy-value={nativeMoney(price?.closePrice, holding)}>
                        {nativeMoney(price?.closePrice, holding)}
                        <small>{price?.baseDate ?? '시세 미확인'}</small>
                      </td>
                      <td>{nativeMoney(evaluation, holding)}</td>
                      <td
                        className={
                          profit == null
                            ? ''
                            : profit > 0
                              ? 'portfolio-gain'
                              : profit < 0
                                ? 'portfolio-loss'
                                : ''
                        }
                      >
                        {profitDisplay(
                          profit,
                          drafts[index].averagePurchasePrice
                            ? Number(drafts[index].averagePurchasePrice) *
                                Number(drafts[index].quantity)
                            : null,
                          holding,
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <table
              className={`portfolio-bulk-table portfolio-bulk-trades${isBalance ? ' is-balance' : ''}`}
            >
              <caption className="sr-only">
                {isBalance
                  ? '현재 보유 종목과 수량·평균 매입가. 새 종목은 매수 거래 없이 현재 보유잔고로 등록합니다. 기존 보유정보는 읽기 전용입니다.'
                  : 'CSV 매수·매도 거래 미리보기. 해외 거래의 적용 환율을 수정할 수 있습니다.'}
              </caption>
              <colgroup>
                <col className="bulk-col-index" />
                <col className="bulk-col-stock" />
                <col className="bulk-col-category" />
                <col className="bulk-col-currency" />
                {csvTransactions && <col className="bulk-col-rate" />}
                <col className="bulk-col-quantity" />
                <col className="bulk-col-price" />
                <col className="bulk-col-price" />
                <col className="bulk-col-value" />
                <col className="bulk-col-profit" />
                {csvTransactions && (
                  <>
                    <col className="bulk-col-side" />
                    <col className="bulk-col-date" />
                    <col className="bulk-col-cost" />
                    <col className="bulk-col-cost" />
                  </>
                )}
              </colgroup>
              <thead>
                <tr>
                  <th scope="col">{mode === 'csv' ? '행' : '#'}</th>
                  <th scope="col" className="is-editable">
                    종목
                  </th>
                  <th scope="col">유형</th>
                  <th scope="col">통화</th>
                  {csvTransactions && (
                    <th scope="col" className="is-editable">
                      환율 <small>원/USD</small>
                    </th>
                  )}
                  <th scope="col" className="is-editable">
                    수량
                  </th>
                  <th scope="col" className="is-editable">
                    {isBalance ? '평균 매입가' : '거래 단가'}
                  </th>
                  <th scope="col">현재가</th>
                  <th scope="col">
                    평가금액 <small>계산</small>
                  </th>
                  <th scope="col">
                    평가손익 <small>계산</small>
                  </th>
                  {csvTransactions && (
                    <>
                      <th scope="col">매매</th>
                      <th scope="col">거래일</th>
                      <th scope="col">수수료</th>
                      <th scope="col">세금</th>
                    </>
                  )}
                </tr>
              </thead>
              <tbody>
                {mode === 'add' &&
                  holdings.map((holding, index) => {
                    const price = priceByAsset.get(holding.assetId);
                    const average = nativeAverage(holding);
                    const profit =
                      price?.closePrice == null || average == null
                        ? null
                        : (price.closePrice - average) * holding.quantity;
                    return (
                      <tr key={`holding-${holding.assetId}`} className="is-existing">
                        <td>{index + 1}</td>
                        <th scope="row" data-copy-value={`${holding.assetCode} ${holding.name}`}>
                          <span className="portfolio-bulk-stock-identity">
                            <i aria-hidden="true">{holding.name.slice(0, 1)}</i>
                            <strong>{holding.assetCode}</strong>
                            <small>{holding.name}</small>
                          </span>
                        </th>
                        <td>
                          <span className="portfolio-bulk-badge">
                            {holding.category === 'ETF' ? 'ETF' : '주식'}
                          </span>
                        </td>
                        <td>
                          <span className="portfolio-bulk-badge">{stockCurrency(holding)}</span>
                        </td>
                        <td>{holding.quantity.toLocaleString('ko-KR')}</td>
                        <td>{nativeMoney(nativeAverage(holding), holding)}</td>
                        <td>{nativeMoney(price?.closePrice, holding)}</td>
                        <td>
                          {nativeMoney(
                            price?.closePrice == null ? null : price.closePrice * holding.quantity,
                            holding,
                          )}
                        </td>
                        <td
                          className={
                            (profit ?? 0) > 0
                              ? 'portfolio-gain'
                              : (profit ?? 0) < 0
                                ? 'portfolio-loss'
                                : ''
                          }
                        >
                          {profitDisplay(
                            profit,
                            average == null ? null : average * holding.quantity,
                            holding,
                          )}
                        </td>
                      </tr>
                    );
                  })}
                {trades.map((trade, index) => {
                  const quote = trade.stock
                    ? (quotes.get(trade.stock.assetId) ??
                      priceByAsset.get(trade.stock.assetId)?.closePrice)
                    : null;
                  const preview =
                    trade.stock && !tradeErrors[index] && trade.type === 'BUY' && quote != null
                      ? quote * Number(trade.quantity)
                      : null;
                  const profit =
                    preview == null
                      ? null
                      : preview -
                        (Number(trade.unitPrice) * Number(trade.quantity) +
                          Number(trade.fee) +
                          Number(trade.tax));
                  return (
                    <tr key={trade.id} className={isBalance ? 'is-new' : ''}>
                      <td className={isBalance ? 'portfolio-bulk-row-index' : ''}>
                        <span className="portfolio-bulk-row-number">
                          {trade.sourceLine ?? (mode === 'add' ? holdings.length : 0) + index + 1}
                        </span>
                        {isBalance && (
                          <button
                            type="button"
                            className="portfolio-bulk-remove-row"
                            aria-label={`${index + 1}행 삭제`}
                            disabled={busy || reading}
                            onClick={() =>
                              setTrades((current) => current.filter((item) => item.id !== trade.id))
                            }
                          >
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                              <path d="m7 7 10 10M17 7 7 17" />
                            </svg>
                          </button>
                        )}
                      </td>
                      <th
                        scope="row"
                        data-copy-value={
                          trade.stock
                            ? `${trade.stock.assetCode} ${trade.stock.name}`
                            : trade.csvRow?.assetCode || trade.csvRow?.name || ''
                        }
                        className={isBalance ? 'is-editable portfolio-bulk-new-stock' : ''}
                      >
                        {isBalance ? (
                          <PortfolioBulkStockSearch
                            index={index}
                            value={trade.stock}
                            initialQuery={trade.csvRow?.assetCode || trade.csvRow?.name}
                            disabled={busy || reading}
                            unavailable={unavailableStocks(trade.id)}
                            onSelect={(stock) => selectStock(trade.id, stock)}
                          />
                        ) : (
                          <span className="portfolio-bulk-stock-identity">
                            <i aria-hidden="true">{trade.stock?.name.slice(0, 1)}</i>
                            <strong>{trade.stock?.assetCode}</strong>
                            <small>{trade.stock?.name}</small>
                          </span>
                        )}
                        {mode === 'csv' && isBalance && tradeErrors[index] && (
                          <small className="portfolio-bulk-cell-error">
                            {trade.sourceLine}행: {tradeErrors[index]}
                          </small>
                        )}
                      </th>
                      <td>
                        <span className="portfolio-bulk-badge">
                          {trade.stock ? (trade.stock.category === 'ETF' ? 'ETF' : '주식') : '—'}
                        </span>
                      </td>
                      <td>
                        <span className="portfolio-bulk-badge">
                          {trade.stock ? stockCurrency(trade.stock) : '—'}
                        </span>
                      </td>
                      {csvTransactions && tradeField(trade, index, 'exchangeRate')}
                      {tradeField(trade, index, 'quantity')}
                      {tradeField(trade, index, 'unitPrice')}
                      <td>{nativeMoney(quote, trade.stock)}</td>
                      <td>{nativeMoney(preview, trade.stock)}</td>
                      <td
                        className={
                          profit != null && profit > 0
                            ? 'portfolio-gain'
                            : profit != null && profit < 0
                              ? 'portfolio-loss'
                              : ''
                        }
                      >
                        {profitDisplay(
                          profit,
                          Number(trade.unitPrice) * Number(trade.quantity) +
                            Number(trade.fee) +
                            Number(trade.tax),
                          trade.stock,
                        )}
                      </td>
                      {csvTransactions && (
                        <>
                          <td>{trade.type === 'BUY' ? '매수' : '매도'}</td>
                          {tradeField(trade, index, 'tradeDate')}
                          {tradeField(trade, index, 'fee')}
                          {tradeField(trade, index, 'tax')}
                        </>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          {mode === 'holdings' && !holdings.length && (
            <div className="portfolio-bulk-empty">보유 종목이 없습니다.</div>
          )}
          {mode === 'csv' && !trades.length && (
            <div className="portfolio-bulk-empty">
              {csvTable
                ? reading
                  ? '보유 종목을 자동으로 연결하고 있어요.'
                  : '자동 연결되지 않은 필수 항목을 선택해 주세요.'
                : csvTransactions
                  ? '예제 양식의 CSV 파일을 선택하면 거래를 미리 볼 수 있습니다.'
                  : 'CSV 파일을 선택하면 보유 종목을 미리 볼 수 있습니다.'}
            </div>
          )}
          {mode === 'add' && (
            <button
              type="button"
              className="portfolio-bulk-add-row"
              disabled={busy || trades.length >= 200}
              onClick={() => setTrades((current) => [...current, newTrade(nextId.current++)])}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
              새 자산 추가
            </button>
          )}
        </div>
        <p id={cells.descriptionId} className="sr-only" role="status">
          {cells.description}
        </p>
        <p className="sr-only" role="status">
          {mode !== 'holdings' && tradeErrors.some(Boolean)
            ? `${tradeErrors.findIndex(Boolean) + 1}행: ${tradeErrors.find(Boolean)}`
            : ''}
          {!isLoggedIn && ' 비회원 데이터는 현재 탭에만 임시 보관됩니다.'}
        </p>
        {pendingCsvKind && (
          <div className="portfolio-bulk-discard" role="alert">
            <p>가져오기 유형을 바꾸면 현재 파일과 미리보기가 초기화됩니다.</p>
            <button type="button" onClick={() => setPendingCsvKind(null)}>
              계속 편집
            </button>
            <button type="button" onClick={() => changeCsvKind(pendingCsvKind)}>
              유형 변경
            </button>
          </div>
        )}
        {discarding && (
          <div className="portfolio-bulk-discard" role="alert">
            <p>저장하지 않은 변경사항이 있어요. 편집을 종료할까요?</p>
            <button type="button" onClick={() => setDiscarding(false)}>
              계속 편집
            </button>
            <button type="button" onClick={onClose}>
              변경 취소하고 닫기
            </button>
          </div>
        )}
      </section>
    </PortfolioDialog>
  );
}
