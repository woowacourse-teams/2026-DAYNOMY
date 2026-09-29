import { useEffect, useState } from 'react';
import { getLatestStockPrice, searchStocks } from '../api';
import type { PortfolioHoldingInput, StockMarket, StockSearchItem } from '../types';

type MarketFilter = 'ALL' | StockMarket;

const MARKET_FILTERS: Array<{ value: MarketFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'KOSPI', label: 'KOSPI' },
  { value: 'KOSDAQ', label: 'KOSDAQ' },
];

function normalizePriceInput(value: string) {
  return value.replace(/\D/g, '').replace(/^0+(?=\d)/, '');
}

function formatPriceInput(value: string) {
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

type PortfolioEditorProps = {
  holding?: PortfolioHoldingInput;
  savedAssetIds: number[];
  onClose: () => void;
  onSave: (holding: PortfolioHoldingInput) => void;
};

export function PortfolioEditor({ holding, savedAssetIds, onClose, onSave }: PortfolioEditorProps) {
  const [keyword, setKeyword] = useState('');
  const [selectedStock, setSelectedStock] = useState<StockSearchItem | null>(holding ?? null);
  const [quantity, setQuantity] = useState(holding ? String(holding.quantity) : '');
  const [averagePrice, setAveragePrice] = useState(
    holding ? String(holding.averagePurchasePrice) : '',
  );
  const [results, setResults] = useState<StockSearchItem[]>([]);
  const [marketFilter, setMarketFilter] = useState<MarketFilter>('ALL');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [priceLoading, setPriceLoading] = useState(false);
  const [priceError, setPriceError] = useState('');

  useEffect(() => {
    const trimmedKeyword = keyword.trim();

    if (holding || trimmedKeyword.length < 1) {
      setResults([]);
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');
    const timer = window.setTimeout(() => {
      searchStocks(trimmedKeyword, controller.signal)
        .then(setResults)
        .catch(() => {
          if (controller.signal.aborted) return;
          setError('네트워크 상태를 확인하고 잠시 후 다시 검색해 주세요.');
          setResults([]);
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 250);

    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [holding, keyword]);

  useEffect(() => {
    if (holding || !selectedStock) {
      setPriceLoading(false);
      setPriceError('');
      return;
    }

    const controller = new AbortController();
    setPriceLoading(true);
    setPriceError('');
    getLatestStockPrice(selectedStock.assetId, controller.signal)
      .then((price) => {
        if (controller.signal.aborted) return;
        setAveragePrice((currentPrice) => currentPrice || String(Math.round(price.closePrice)));
      })
      .catch(() => {
        if (controller.signal.aborted) return;
        setPriceError('최근 종가를 불러오지 못했습니다. 가격을 직접 입력해 주세요.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setPriceLoading(false);
      });

    return () => controller.abort();
  }, [holding, selectedStock]);

  const quantityNumber = Number(quantity);
  const averagePriceNumber = Number(averagePrice);
  const canAdjustAveragePrice = Number.isFinite(averagePriceNumber) && averagePriceNumber > 0;
  const canSave =
    selectedStock !== null &&
    Number.isSafeInteger(quantityNumber) &&
    quantityNumber > 0 &&
    Number.isFinite(averagePriceNumber) &&
    averagePriceNumber > 0;
  const filteredResults = results.filter(
    (stock) => marketFilter === 'ALL' || stock.market === marketFilter,
  );

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedStock || !canSave) return;

    onSave({
      ...selectedStock,
      quantity: quantityNumber,
      averagePurchasePrice: averagePriceNumber,
    });
  }

  function adjustAveragePrice(amount: number) {
    if (!canAdjustAveragePrice) return;
    const nextPrice = Math.max(1, averagePriceNumber + amount);
    setAveragePrice(String(nextPrice));
  }

  function resetSelectedStock() {
    setSelectedStock(null);
    setAveragePrice('');
    setPriceError('');
  }

  const isDetailsStep = holding !== undefined || selectedStock !== null;

  return (
    <div className="portfolio-dialog-backdrop" onMouseDown={onClose}>
      <section
        className="portfolio-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="portfolio-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <form className={isDetailsStep ? 'is-details-step' : 'is-search-step'} onSubmit={submit}>
          <div className="portfolio-dialog-title">
            <div>
              {!holding && selectedStock ? (
                <button
                  type="button"
                  className="portfolio-dialog-back"
                  aria-label="종목 검색으로 돌아가기"
                  onClick={resetSelectedStock}
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="m15 18-6-6 6-6" />
                  </svg>
                </button>
              ) : null}
              <div>
                <h2 id="portfolio-dialog-title">{holding ? '자산 수정' : '자산 추가'}</h2>
                <p>
                  {isDetailsStep
                    ? '보유 정보를 입력해 주세요.'
                    : '포트폴리오에 담을 국내 주식을 찾아보세요.'}
                </p>
              </div>
            </div>
            <button
              type="button"
              className="portfolio-dialog-close"
              aria-label="닫기"
              onClick={onClose}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m6 6 12 12M18 6 6 18" />
              </svg>
            </button>
          </div>

          {holding ? (
            <div className="portfolio-selected-stock">
              <span className="portfolio-monogram">{holding.name.slice(0, 1)}</span>
              <div>
                <strong>{holding.name}</strong>
                <small>
                  {holding.assetCode} · {holding.market}
                </small>
              </div>
            </div>
          ) : selectedStock ? (
            <div className="portfolio-selected-stock">
              <span className="portfolio-monogram">{selectedStock.name.slice(0, 1)}</span>
              <div>
                <strong>{selectedStock.name}</strong>
                <small>
                  {selectedStock.assetCode} · {selectedStock.market}
                </small>
              </div>
              <button type="button" onClick={resetSelectedStock}>
                다시 검색
              </button>
            </div>
          ) : (
            <div className="portfolio-search-step">
              <label className="portfolio-search-field">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="11" cy="11" r="6.5" />
                  <path d="m16 16 4 4" />
                </svg>
                <span className="sr-only">종목 검색</span>
                <input
                  autoFocus
                  type="search"
                  value={keyword}
                  placeholder="종목명 또는 종목코드를 검색하세요"
                  onChange={(event) => setKeyword(event.target.value)}
                />
              </label>

              <div className="portfolio-market-tabs" role="group" aria-label="시장 필터">
                {MARKET_FILTERS.map((filter) => (
                  <button
                    key={filter.value}
                    type="button"
                    aria-pressed={marketFilter === filter.value}
                    onClick={() => setMarketFilter(filter.value)}
                  >
                    {filter.label}
                  </button>
                ))}
              </div>
            </div>
          )}

          {!holding && !selectedStock ? (
            <div className="portfolio-search-results" aria-live="polite">
              {!keyword.trim() ? (
                <div className="portfolio-search-empty">
                  <span>종목 검색</span>
                  <strong>보유 중인 종목을 검색해 보세요</strong>
                  <p>국내 KOSPI · KOSDAQ 종목을 추가할 수 있습니다.</p>
                </div>
              ) : loading ? (
                <div className="portfolio-search-status" role="status">
                  <strong>종목을 검색하고 있어요</strong>
                  <p>잠시만 기다려 주세요.</p>
                </div>
              ) : error ? (
                <div className="portfolio-search-status error" role="alert">
                  <strong>검색 결과를 불러오지 못했어요</strong>
                  <p>{error}</p>
                </div>
              ) : filteredResults.length === 0 ? (
                <div className="portfolio-search-status">
                  <strong>검색된 종목이 없어요</strong>
                  <p>
                    {results.length > 0
                      ? `${marketFilter} 시장에 일치하는 종목이 없습니다.`
                      : '종목명이나 종목코드를 다시 확인해 주세요.'}
                  </p>
                </div>
              ) : (
                <ul>
                  {filteredResults.map((stock) => {
                    const alreadySaved = savedAssetIds.includes(stock.assetId);
                    return (
                      <li key={stock.assetId}>
                        <button
                          type="button"
                          disabled={alreadySaved}
                          onClick={() => setSelectedStock(stock)}
                        >
                          <span className="portfolio-search-monogram" aria-hidden="true">
                            {stock.name.slice(0, 1)}
                          </span>
                          <span className="portfolio-search-copy">
                            <strong>{stock.name}</strong>
                            <small>
                              {stock.assetCode} <span>{stock.market}</span>
                            </small>
                          </span>
                          <em>
                            {alreadySaved ? '추가됨' : '선택'}
                            {!alreadySaved ? (
                              <svg viewBox="0 0 24 24" aria-hidden="true">
                                <path d="m9 18 6-6-6-6" />
                              </svg>
                            ) : null}
                          </em>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
            </div>
          ) : null}

          <div className="portfolio-form-grid" hidden={!isDetailsStep}>
            <div className="portfolio-field">
              <label htmlFor="portfolio-quantity-input">보유수량</label>
              <div className="portfolio-input-with-unit">
                <input
                  id="portfolio-quantity-input"
                  type="number"
                  min="1"
                  step="1"
                  inputMode="numeric"
                  placeholder="0"
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                />
                <span>주</span>
              </div>
            </div>
            <div className="portfolio-field">
              <label id="average-price-label" htmlFor="average-price-input">
                평균 매수가
              </label>
              <div className="portfolio-price-control" role="group" aria-label="평균 매수가 조절">
                <button
                  type="button"
                  aria-label="평균 매수가 1,000원 내리기"
                  disabled={!canAdjustAveragePrice}
                  onClick={() => adjustAveragePrice(-1000)}
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M6 12h12" />
                  </svg>
                </button>
                <div className="portfolio-input-with-unit">
                  <input
                    id="average-price-input"
                    type="text"
                    inputMode="numeric"
                    pattern="[0-9,]*"
                    placeholder="0"
                    value={formatPriceInput(averagePrice)}
                    onChange={(event) => setAveragePrice(normalizePriceInput(event.target.value))}
                  />
                  <span>원</span>
                </div>
                <button
                  type="button"
                  aria-label="평균 매수가 1,000원 올리기"
                  disabled={!canAdjustAveragePrice}
                  onClick={() => adjustAveragePrice(1000)}
                >
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 6v12M6 12h12" />
                  </svg>
                </button>
              </div>
              {priceLoading ? (
                <small className="portfolio-price-message" role="status">
                  최근 종가를 불러오는 중입니다.
                </small>
              ) : null}
              {priceError ? (
                <small className="portfolio-price-message error" role="alert">
                  {priceError}
                </small>
              ) : null}
            </div>
          </div>

          {isDetailsStep ? (
            <div className="portfolio-dialog-actions">
              <button type="button" className="portfolio-secondary-button" onClick={onClose}>
                취소
              </button>
              <button type="submit" className="portfolio-primary-button" disabled={!canSave}>
                {holding ? '수정하기' : '추가하기'}
              </button>
            </div>
          ) : null}
        </form>
      </section>
    </div>
  );
}
