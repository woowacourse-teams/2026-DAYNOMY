import { useEffect, useRef, useState } from 'react';
import { getLatestStockPrice, searchStocks } from '../api';
import { trackPortfolioEvent } from '../analytics';
import {
  todayInSeoul,
  toTransactionRequest,
  validateTransaction,
  type TransactionFields,
} from '../transactionForm';
import type {
  PortfolioHoldingHistory,
  PortfolioTransactionRequest,
  PortfolioTransactionType,
  StockSearchItem,
} from '../types';

type Props = {
  stock?: StockSearchItem;
  type?: PortfolioTransactionType;
  transaction?: PortfolioHoldingHistory;
  onClose: () => void;
  onDelete?: () => void;
  onOpeningBalance?: () => void;
  onSave: (request: PortfolioTransactionRequest, stock?: StockSearchItem) => Promise<void>;
};
export function PortfolioTransactionEditor({
  stock,
  type = 'BUY',
  transaction,
  onClose,
  onDelete,
  onOpeningBalance,
  onSave,
}: Props) {
  const [selected, setSelected] = useState<StockSearchItem | PortfolioHoldingHistory | null>(
    transaction ?? stock ?? null,
  );
  const [transactionType, setTransactionType] = useState<PortfolioTransactionType>(
    transaction?.changeType === 'SELL' ? 'SELL' : type,
  );
  const [fields, setFields] = useState<TransactionFields>({
    tradeDate: transaction?.effectiveDate ?? todayInSeoul(),
    quantity: transaction?.transactionQuantity?.toString() ?? '',
    unitPrice: transaction?.unitPrice?.toString() ?? '',
    fee: String(transaction?.fee ?? 0),
    tax: String(transaction?.tax ?? 0),
  });
  const priceEdited = useRef(false);
  const [priceLoading, setPriceLoading] = useState(false);
  const [priceError, setPriceError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<StockSearchItem[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [searchRetry, setSearchRetry] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
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
      searchStocks(keyword.trim(), controller.signal)
        .then((items) => {
          if (!controller.signal.aborted) setResults(items);
        })
        .catch(() => {
          if (!controller.signal.aborted)
            setSearchError('종목을 검색하지 못했습니다. 다시 시도해 주세요.');
        })
        .finally(() => {
          if (!controller.signal.aborted) setSearchLoading(false);
        });
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [keyword, selected, searchRetry]);
  useEffect(() => {
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
  }, [selected, transaction]);
  const invalid = validateTransaction(fields);
  const gross = Number(fields.quantity) * Number(fields.unitPrice);
  const costs = Number(fields.fee) + Number(fields.tax);
  const amount = transactionType === 'BUY' ? gross + costs : gross - costs;
  const format = (value: number) =>
    `${value.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}원`;
  function change(key: keyof TransactionFields, value: string) {
    if (key === 'unitPrice') priceEdited.current = true;
    setFields((current) => ({ ...current, [key]: value }));
    setError('');
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (saving || !selected) return;
    if (invalid) {
      setError(invalid);
      return;
    }
    setSaving(true);
    setError('');
    try {
      await onSave(
        {
          ...toTransactionRequest(fields),
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
        <fieldset disabled={saving} className="portfolio-sheet-fields">
          {!selected ? (
            <>
              {onOpeningBalance && (
                <div className="portfolio-registration-choice">
                  <p>매수·매도한 날짜와 금액을 기록하세요.</p>
                  <button type="button" onClick={onOpeningBalance}>
                    거래일을 모르면 현재 보유분으로 등록
                  </button>
                </div>
              )}
              <label className="portfolio-ledger-field">
                종목 검색
                <input
                  autoFocus
                  data-autofocus
                  type="search"
                  placeholder="종목명 또는 종목코드"
                  value={keyword}
                  onChange={(event) => setKeyword(event.target.value)}
                />
              </label>
              <div className="portfolio-stock-results" aria-live="polite">
                {searchLoading ? (
                  <p role="status">종목을 검색하고 있어요.</p>
                ) : searchError ? (
                  <div role="alert">
                    <p>{searchError}</p>
                    <button type="button" onClick={() => setSearchRetry((value) => value + 1)}>
                      다시 검색
                    </button>
                  </div>
                ) : results.length ? (
                  <ul>
                    {results.map((item) => (
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
                          <span className="portfolio-monogram">{item.name.slice(0, 1)}</span>
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
                    {keyword.trim()
                      ? '검색 결과가 없습니다. 종목명이나 코드를 확인해 주세요.'
                      : '국내 주식과 ETF를 검색하세요. 과거에 매도한 종목도 기록할 수 있어요.'}
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
                      setFields((current) => ({ ...current, unitPrice: '' }));
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
                <div className="portfolio-trade-types" role="group" aria-label="거래 구분">
                  {(['BUY', 'SELL'] as const).map((value) => (
                    <button
                      key={value}
                      type="button"
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
                    autoFocus={!transaction}
                    data-autofocus={!transaction || undefined}
                    type="date"
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
                    type="text"
                    inputMode="numeric"
                    autoFocus={!!transaction}
                    data-autofocus={transaction ? true : undefined}
                    placeholder="0"
                    value={fields.quantity}
                    onChange={(event) => change('quantity', event.target.value)}
                  />
                </label>
                <label className="portfolio-ledger-field">
                  거래 단가 (원)
                  <input
                    type="text"
                    inputMode="decimal"
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
                    autoFocus={!transaction}
                    data-autofocus={!transaction || undefined}
                    type="date"
                    required
                    max={todayInSeoul()}
                    value={fields.tradeDate}
                    onChange={(event) => change('tradeDate', event.target.value)}
                  />
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
                    수수료 (원)
                    <input
                      type="text"
                      inputMode="decimal"
                      value={fields.fee}
                      onChange={(event) => change('fee', event.target.value)}
                    />
                  </label>
                  <label className="portfolio-ledger-field">
                    세금 (원)
                    <input
                      type="text"
                      inputMode="decimal"
                      value={fields.tax}
                      onChange={(event) => change('tax', event.target.value)}
                    />
                  </label>
                </div>
              </details>
              <div className="portfolio-trade-total">
                <span>{transactionType === 'BUY' ? '총 매수 지출액' : '총 매도 수령액'}</span>
                <strong>{invalid ? '—' : format(amount)}</strong>
                <small>입력한 수수료와 세금을 반영한 금액</small>
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
          <p className="portfolio-form-error" role="alert">
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
            <button type="submit" className="portfolio-primary-button" disabled={saving}>
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
