import { useEffect, useId, useRef, useState } from 'react';
import { tourStock } from './portfolioTourExample';
import { getLatestStockPrice, searchStocks, getUsdKrwRate } from '../../stocks/api';
import { trackPortfolioEvent } from '../analytics';
import {
  todayInSeoul,
  toTransactionRequest,
  getTransactionValidationError,
  type TransactionFields,
} from '../transactionForm';
import type {
  PortfolioHoldingHistory,
  PortfolioTransactionRequest,
  PortfolioTransactionType,
} from '../types';
import { stockCurrency } from '../../stocks/types';
import type { AssetCategory, StockSearchItem } from '../../stocks/types';

type Props = {
  demoStep?: number;
  stock?: StockSearchItem;
  type?: PortfolioTransactionType;
  transaction?: PortfolioHoldingHistory;
  onClose: () => void;
  onDelete?: () => void;
  onSave: (request: PortfolioTransactionRequest, stock?: StockSearchItem) => Promise<void>;
};
export function PortfolioTransactionEditor({
  demoStep,
  stock,
  type = 'BUY',
  transaction,
  onClose,
  onDelete,
  onSave,
}: Props) {
  const formRef = useRef<HTMLFormElement>(null);
  const errorId = useId();
  const [selected, setSelected] = useState<StockSearchItem | PortfolioHoldingHistory | null>(
    demoStep !== undefined ? (demoStep >= 6 ? tourStock : null) : (transaction ?? stock ?? null),
  );
  const [transactionType, setTransactionType] = useState<PortfolioTransactionType>(
    transaction?.changeType === 'SELL' ? 'SELL' : type,
  );
  const [fields, setFields] = useState<TransactionFields>({
    tradeDate: transaction?.effectiveDate ?? todayInSeoul(),
    quantity: demoStep !== undefined ? '10' : (transaction?.transactionQuantity?.toString() ?? ''),
    unitPrice: demoStep !== undefined ? '70000' : (transaction?.unitPrice?.toString() ?? ''),
    exchangeRate: transaction?.exchangeRate?.toString(),
    fee: String(transaction?.fee ?? 0),
    tax: String(transaction?.tax ?? 0),
  });
  const currency = selected ? stockCurrency(selected) : 'KRW';
  const [country, setCountry] = useState<'KR' | 'US'>('KR');
  const [priceBaseDate, setPriceBaseDate] = useState('');
  const [rateBaseDate, setRateBaseDate] = useState('');
  const [rateLoading, setRateLoading] = useState(false);
  const [rateError, setRateError] = useState('');
  const rateEdited = useRef(false);
  const priceEdited = useRef(false);
  const [priceLoading, setPriceLoading] = useState(false);
  const [priceError, setPriceError] = useState('');
  const [keyword, setKeyword] = useState(demoStep === 2 ? '삼성전자' : '');
  const [results, setResults] = useState<StockSearchItem[]>(demoStep === 2 ? [tourStock] : []);
  const [categoryFilter, setCategoryFilter] = useState<'ALL' | AssetCategory>('ALL');
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [searchRetry, setSearchRetry] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    if (demoStep !== undefined) return;
    if (selected || !keyword.trim()) {
      setResults([]);
      setSearchLoading(false);
      setSearchError('');
      return;
    }
    const controller = new AbortController();
    setResults([]);
    setSearchLoading(true);
    setSearchError('');
    const timer = window.setTimeout(() => {
      (country === 'US'
        ? searchStocks(keyword.trim(), controller.signal, country)
        : searchStocks(keyword.trim(), controller.signal)
      )
        .then((items) => {
          if (!controller.signal.aborted) setResults(items);
        })
        .catch(() => {
          if (!controller.signal.aborted) setSearchError('검색 결과를 불러오지 못했어요.');
        })
        .finally(() => {
          if (!controller.signal.aborted) setSearchLoading(false);
        });
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [keyword, selected, searchRetry, demoStep, country]);
  useEffect(() => {
    if (demoStep !== undefined) return;
    if (transaction || !selected) {
      setPriceLoading(false);
      setPriceError('');
      return;
    }
    const controller = new AbortController();
    setPriceLoading(true);
    setPriceError('');
    getLatestStockPrice(selected.assetId, controller.signal)
      .then((price) => {
        if (controller.signal.aborted) return;
        setPriceBaseDate(price.baseDate);
        if (price.closePrice <= 0) throw new Error('최근 종가가 없습니다.');
        setFields((current) =>
          controller.signal.aborted || priceEdited.current || current.unitPrice
            ? current
            : { ...current, unitPrice: String(price.closePrice) },
        );
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setPriceError('최근 종가를 불러오지 못했습니다. 거래 단가를 직접 입력해 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setPriceLoading(false);
      });
    return () => controller.abort();
  }, [selected, transaction, demoStep]);
  useEffect(() => {
    if (demoStep !== undefined || currency !== 'USD' || !selected) return;
    if (transaction && fields.tradeDate === transaction.effectiveDate) {
      setRateLoading(false);
      setRateError('');
      setRateBaseDate('');
      if (!rateEdited.current && transaction.exchangeRate)
        setFields((current) => ({ ...current, exchangeRate: String(transaction.exchangeRate) }));
      return;
    }
    const controller = new AbortController();
    setRateLoading(true);
    setRateError('');
    setRateBaseDate('');
    getUsdKrwRate(fields.tradeDate, controller.signal)
      .then((quote) => {
        if (controller.signal.aborted) return;
        setRateBaseDate(quote.baseDate);
        if (!rateEdited.current)
          setFields((current) => ({ ...current, exchangeRate: String(quote.rate) }));
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setRateError('환율을 가져오지 못했어요. 실제 적용 환율을 직접 입력해 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setRateLoading(false);
      });
    return () => controller.abort();
  }, [selected, currency, fields.tradeDate, transaction, demoStep]);
  const invalid = getTransactionValidationError(fields, currency);
  const invalidField = error === invalid?.message ? invalid?.field : undefined;
  const filteredResults = results.filter(
    (item) => categoryFilter === 'ALL' || item.category === categoryFilter,
  );
  const gross = Number(fields.quantity) * Number(fields.unitPrice);
  const costs = Number(fields.fee) + Number(fields.tax);
  const amount = transactionType === 'BUY' ? gross + costs : gross - costs;
  const format = (value: number) =>
    `${value.toLocaleString('ko-KR', { maximumFractionDigits: 4 })}${currency === 'USD' ? ' USD' : '원'}`;
  function change(key: keyof TransactionFields, value: string) {
    if (key === 'unitPrice') priceEdited.current = true;
    if (key === 'exchangeRate') rateEdited.current = true;
    if (key === 'tradeDate' && currency === 'USD') {
      rateEdited.current = false;
      setFields((current) => ({ ...current, exchangeRate: '' }));
    }
    setFields((current) => ({ ...current, [key]: value }));
    setError('');
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (demoStep !== undefined || saving || !selected) return;
    if (invalid) {
      setError(invalid.message);
      window.requestAnimationFrame(() => {
        const input = formRef.current?.elements.namedItem(invalid.field);
        if (input instanceof HTMLElement) {
          input.closest('details')?.setAttribute('open', '');
          input.focus();
        }
      });
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onSave(
        {
          ...toTransactionRequest(
            currency === 'USD' ? fields : { ...fields, exchangeRate: undefined },
          ),
          assetId: selected.assetId,
          type: transactionType,
        },
        'category' in selected ? selected : undefined,
      );
    } catch (caught) {
      setError(
        caught instanceof Error
          ? caught.message
          : '거래를 저장하지 못했습니다. 다시 시도해 주세요.',
      );
    } finally {
      setSaving(false);
    }
  }
  return (
    <>
      <form
        ref={formRef}
        onSubmit={submit}
        noValidate
        className={transaction ? 'portfolio-edit-form' : undefined}
      >
        <header className="portfolio-sheet-heading">
          <div>
            {!transaction && <span className="portfolio-eyebrow">내 포트폴리오</span>}
            <h2 id="transaction-title">{transaction ? '거래 수정' : '거래 기록'}</h2>
            {!transaction && <p>매수와 매도를 기록하고 자산의 흐름을 확인하세요.</p>}
          </div>
          <button
            type="button"
            className="portfolio-icon-button"
            aria-label="닫기"
            disabled={saving}
            onClick={onClose}
          >
            ×
          </button>
        </header>
        <fieldset
          data-portfolio-tour="trade-fields"
          disabled={saving}
          className="portfolio-sheet-fields"
        >
          {!selected ? (
            <>
              <label className="portfolio-ledger-field">
                종목 검색
                <input
                  readOnly={demoStep !== undefined}
                  autoFocus
                  data-autofocus
                  type="search"
                  data-portfolio-tour="search"
                  placeholder="종목명 또는 종목코드"
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                />
              </label>
              <div className="portfolio-stock-filters">
                <div className="portfolio-trade-types" role="group" aria-label="종목 국가">
                  {(['KR', 'US'] as const).map((value) => (
                    <button
                      type="button"
                      key={value}
                      aria-pressed={country === value}
                      onClick={() => {
                        setCountry(value);
                        setResults([]);
                        setSearchError('');
                      }}
                    >
                      {value === 'KR' ? '국내' : '미국'}
                    </button>
                  ))}
                </div>
                <div className="portfolio-market-tabs" role="group" aria-label="자산 유형 필터">
                  {(['ALL', 'STOCK', 'ETF'] as const).map((category) => (
                    <button
                      key={category}
                      type="button"
                      aria-pressed={categoryFilter === category}
                      onClick={() => setCategoryFilter(category)}
                    >
                      {category === 'ALL' ? '전체' : category === 'STOCK' ? '주식' : 'ETF'}
                    </button>
                  ))}
                </div>
              </div>
              <div
                data-portfolio-tour="search-results"
                className="portfolio-stock-results"
                aria-live="polite"
                aria-busy={searchLoading}
              >
                {searchLoading ? (
                  <div className="portfolio-stock-search-loading" role="status">
                    <span
                      className="portfolio-analysis-spinner portfolio-stock-search-spinner"
                      aria-hidden="true"
                    />
                    <span className="sr-only">종목 검색 중</span>
                  </div>
                ) : searchError ? (
                  <div className="portfolio-stock-search-error" role="alert">
                    <p>{searchError}</p>
                    <button
                      type="button"
                      className="portfolio-stock-search-retry"
                      onClick={() => setSearchRetry((value) => value + 1)}
                    >
                      다시 시도
                    </button>
                  </div>
                ) : filteredResults.length ? (
                  <ul aria-label="검색된 종목">
                    {filteredResults.map((item) => (
                      <li key={item.assetId}>
                        <button
                          type="button"
                          onClick={() => {
                            trackPortfolioEvent('portfolio_stock_selected', {
                              category: item.category,
                              market: item.market,
                              mode: 'transaction_create',
                            });
                            setSelected(item);
                          }}
                        >
                          <span className="portfolio-monogram" aria-hidden="true">
                            {item.name.slice(0, 1)}
                          </span>
                          <span>
                            <strong>{item.name}</strong>
                            <small>
                              {item.assetCode} · {item.market} ·{' '}
                              {item.category === 'ETF' ? 'ETF' : '주식'}
                            </small>
                          </span>
                          <span aria-hidden="true">＋</span>
                        </button>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p>
                    {keyword.trim() ? (
                      '검색 결과가 없습니다. 종목명이나 코드를 확인해 주세요.'
                    ) : (
                      <>
                        국내·미국 주식과 ETF를 검색하세요. <br />
                        과거에 매도한 종목도 기록할 수 있어요.
                      </>
                    )}
                  </p>
                )}
              </div>
            </>
          ) : (
            <>
              <div className="portfolio-trade-stock">
                <span className="portfolio-monogram">{selected.name.slice(0, 1)}</span>
                <div>
                  <strong>{selected.name}</strong>
                  <small>{selected.assetCode}</small>
                </div>
                {!transaction && (
                  <button
                    type="button"
                    onClick={() => {
                      setSelected(null);
                      priceEdited.current = false;
                      setFields((current) => ({ ...current, unitPrice: '', exchangeRate: '' }));
                      rateEdited.current = false;
                      setRateBaseDate('');
                      setRateError('');
                      setPriceBaseDate('');
                    }}
                  >
                    종목 변경
                  </button>
                )}
              </div>
              {transaction ? (
                <span className={`portfolio-edit-type type-${transactionType.toLowerCase()}`}>
                  {transactionType === 'BUY' ? '매수' : '매도'} 거래
                </span>
              ) : (
                <div
                  data-portfolio-tour="trade-type"
                  className="portfolio-trade-types"
                  role="group"
                  aria-label="거래 구분"
                >
                  {(['BUY', 'SELL'] as const).map((value) => (
                    <button
                      key={value}
                      type="button"
                      data-tour-buy={value === 'BUY' ? '' : undefined}
                      aria-pressed={transactionType === value}
                      onClick={() => setTransactionType(value)}
                    >
                      {value === 'BUY' ? '매수' : '매도'}
                    </button>
                  ))}
                </div>
              )}
              {!transaction && (
                <label className="portfolio-ledger-field">
                  거래일
                  <input
                    readOnly={demoStep !== undefined}
                    autoFocus={!transaction}
                    data-autofocus={!transaction || undefined}
                    type="date"
                    name="tradeDate"
                    data-portfolio-tour="trade-date"
                    aria-invalid={invalidField === 'tradeDate' || undefined}
                    aria-describedby={invalidField === 'tradeDate' ? errorId : undefined}
                    required
                    max={todayInSeoul()}
                    value={fields.tradeDate}
                    onChange={(event) => change('tradeDate', event.target.value)}
                  />
                </label>
              )}
              <div className={transaction ? 'portfolio-edit-values' : 'portfolio-two-fields'}>
                <label className="portfolio-ledger-field">
                  수량 (주)
                  <input
                    readOnly={demoStep !== undefined}
                    type="text"
                    inputMode="numeric"
                    name="quantity"
                    data-portfolio-tour="trade-quantity"
                    aria-invalid={invalidField === 'quantity' || undefined}
                    aria-describedby={invalidField === 'quantity' ? errorId : undefined}
                    autoFocus={!!transaction}
                    data-autofocus={transaction ? true : undefined}
                    placeholder="0"
                    value={fields.quantity}
                    onChange={(event) => change('quantity', event.target.value)}
                  />
                </label>
                <label className="portfolio-ledger-field">
                  거래 단가 ({currency === 'USD' ? 'USD' : '원'})
                  <input
                    readOnly={demoStep !== undefined}
                    type="text"
                    inputMode="decimal"
                    name="unitPrice"
                    data-portfolio-tour="trade-price"
                    aria-invalid={invalidField === 'unitPrice' || undefined}
                    aria-describedby={invalidField === 'unitPrice' ? errorId : undefined}
                    placeholder="0"
                    value={fields.unitPrice}
                    aria-busy={priceLoading}
                    onChange={(event) => change('unitPrice', event.target.value)}
                  />
                </label>
              </div>
              {transaction && (
                <label className="portfolio-ledger-field">
                  거래일
                  <input
                    readOnly={demoStep !== undefined}
                    autoFocus={!transaction}
                    data-autofocus={!transaction || undefined}
                    type="date"
                    name="tradeDate"
                    data-portfolio-tour="trade-date"
                    aria-invalid={invalidField === 'tradeDate' || undefined}
                    aria-describedby={invalidField === 'tradeDate' ? errorId : undefined}
                    required
                    max={todayInSeoul()}
                    value={fields.tradeDate}
                    onChange={(event) => change('tradeDate', event.target.value)}
                  />
                </label>
              )}
              {priceBaseDate && (
                <p className="portfolio-field-note">
                  {priceBaseDate} 최근 종가 · {currency} · 과거 거래일에도 참고 단가로 유지됩니다.
                  실제 체결가와 다르면 수정해 주세요.
                </p>
              )}
              {currency === 'USD' && (
                <label className="portfolio-ledger-field">
                  적용 환율 (1 USD당 원)
                  <input
                    type="text"
                    name="exchangeRate"
                    inputMode="decimal"
                    value={fields.exchangeRate ?? ''}
                    aria-busy={rateLoading}
                    aria-invalid={invalidField === 'exchangeRate' || undefined}
                    aria-describedby={invalidField === 'exchangeRate' ? errorId : undefined}
                    onChange={(event) => change('exchangeRate', event.target.value)}
                  />
                  <small>
                    {rateLoading
                      ? '거래일 참고 환율을 불러오고 있어요.'
                      : rateError ||
                        (rateBaseDate
                          ? `${rateBaseDate} 참고 환율 · 증권사의 실제 적용 환율로 수정할 수 있어요.`
                          : '저장할 거래의 실제 적용 환율을 입력해 주세요.')}
                  </small>
                </label>
              )}
              {(priceLoading || priceError) && (
                <p className="portfolio-field-note" role="status">
                  {priceLoading ? '최근 종가를 불러오고 있어요.' : priceError}
                </p>
              )}
              <details
                className="portfolio-cost-fields"
                open={
                  transaction && (transaction.fee > 0 || transaction.tax > 0) ? true : undefined
                }
              >
                <summary>
                  수수료·세금 <span>선택 입력</span>
                </summary>
                <div className="portfolio-two-fields">
                  <label className="portfolio-ledger-field">
                    수수료 ({currency === 'USD' ? 'USD' : '원'})
                    <input
                      readOnly={demoStep !== undefined}
                      type="text"
                      inputMode="decimal"
                      name="fee"
                      aria-invalid={invalidField === 'fee' || undefined}
                      aria-describedby={invalidField === 'fee' ? errorId : undefined}
                      value={fields.fee}
                      onChange={(event) => change('fee', event.target.value)}
                    />
                  </label>
                  <label className="portfolio-ledger-field">
                    세금 ({currency === 'USD' ? 'USD' : '원'})
                    <input
                      readOnly={demoStep !== undefined}
                      type="text"
                      inputMode="decimal"
                      name="tax"
                      aria-invalid={invalidField === 'tax' || undefined}
                      aria-describedby={invalidField === 'tax' ? errorId : undefined}
                      value={fields.tax}
                      onChange={(event) => change('tax', event.target.value)}
                    />
                  </label>
                </div>
              </details>
              <div className="portfolio-trade-total">
                <span>{transactionType === 'BUY' ? '총 매수 지출액' : '총 매도 수령액'}</span>
                <strong>
                  {invalid
                    ? '—'
                    : currency === 'USD'
                      ? `${(amount * Number(fields.exchangeRate)).toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`
                      : format(amount)}
                </strong>
                <small>입력한 수수료와 세금을 반영한 금액</small>
                {currency === 'USD' && <small>달러 합계: {invalid ? '—' : format(amount)}</small>}
              </div>
              <p className="portfolio-field-note">
                {transaction
                  ? '수정하면 이후 보유 수량과 손익, 자산 추이가 다시 계산됩니다.'
                  : transactionType === 'SELL'
                    ? '매도 가능 수량은 해당 거래일의 보유 기록을 기준으로 확인합니다.'
                    : '거래일을 기준으로 보유 수량과 평균 매입가가 계산됩니다.'}
              </p>
            </>
          )}
        </fieldset>
        {error && (
          <p className="portfolio-form-error" role="alert" id={errorId}>
            {error}
          </p>
        )}
        {selected && (
          <footer className="portfolio-sheet-footer">
            {transaction ? (
              onDelete && (
                <button
                  type="button"
                  className="portfolio-edit-delete"
                  disabled={saving}
                  onClick={onDelete}
                >
                  거래 삭제
                </button>
              )
            ) : (
              <button
                type="button"
                className="portfolio-secondary-button"
                disabled={saving}
                onClick={onClose}
              >
                취소
              </button>
            )}
            <button
              data-portfolio-tour="trade-submit"
              type="submit"
              className="portfolio-primary-button"
              disabled={saving}
            >
              {saving
                ? '저장 중…'
                : transaction
                  ? '수정하기'
                  : `${transactionType === 'BUY' ? '매수' : '매도'} 기록하기`}
            </button>
          </footer>
        )}
      </form>
    </>
  );
}
