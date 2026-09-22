import { useEffect, useState } from 'react';
import { searchStocks } from '../api';
import type { PortfolioHoldingInput, StockSearchItem } from '../types';

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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (holding || keyword.trim().length < 1) {
      setResults([]);
      setLoading(false);
      return;
    }

    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError('');
      searchStocks(keyword.trim(), controller.signal)
        .then(setResults)
        .catch((caughtError: unknown) => {
          if (controller.signal.aborted) return;
          setError(
            caughtError instanceof Error ? caughtError.message : '종목을 검색하지 못했습니다.',
          );
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

  const quantityNumber = Number(quantity);
  const averagePriceNumber = Number(averagePrice);
  const canSave =
    selectedStock !== null &&
    Number.isSafeInteger(quantityNumber) &&
    quantityNumber > 0 &&
    Number.isFinite(averagePriceNumber) &&
    averagePriceNumber > 0;

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedStock || !canSave) return;

    onSave({
      ...selectedStock,
      quantity: quantityNumber,
      averagePurchasePrice: averagePriceNumber,
    });
  }

  return (
    <div className="portfolio-dialog-backdrop" onMouseDown={onClose}>
      <section
        className="portfolio-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="portfolio-dialog-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <form onSubmit={submit}>
          <div className="portfolio-dialog-title">
            <h2 id="portfolio-dialog-title">{holding ? '자산 수정' : '자산 추가'}</h2>
            <button
              type="button"
              className="portfolio-dialog-close"
              aria-label="닫기"
              onClick={onClose}
            >
              ×
            </button>
          </div>
          <p className="portfolio-dialog-note">
            국내 주식을 검색하고 보유 수량과 평균 매수가를 입력해 주세요.
          </p>

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
              <button type="button" onClick={() => setSelectedStock(null)}>
                다시 검색
              </button>
            </div>
          ) : (
            <label className="portfolio-field">
              종목 검색
              <input
                autoFocus
                type="search"
                value={keyword}
                placeholder="종목명 또는 종목코드"
                onChange={(event) => setKeyword(event.target.value)}
              />
            </label>
          )}

          {!holding && !selectedStock && keyword.trim() ? (
            <div className="portfolio-search-results" aria-live="polite">
              {loading ? <p>검색 중입니다.</p> : null}
              {error ? <p role="alert">{error}</p> : null}
              {!loading && !error && keyword.trim() && results.length === 0 ? (
                <p>검색된 종목이 없습니다.</p>
              ) : null}
              {results.length > 0 ? (
                <ul>
                  {results.map((stock) => {
                    const alreadySaved = savedAssetIds.includes(stock.assetId);
                    return (
                      <li key={stock.assetId}>
                        <button
                          type="button"
                          disabled={alreadySaved}
                          onClick={() => setSelectedStock(stock)}
                        >
                          <span>
                            <strong>{stock.name}</strong>
                            <small>
                              {stock.assetCode} · {stock.market}
                            </small>
                          </span>
                          <em>{alreadySaved ? '추가됨' : '선택'}</em>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              ) : null}
            </div>
          ) : null}

          <div className="portfolio-form-grid">
            <label className="portfolio-field">
              보유수량
              <input
                type="number"
                min="1"
                step="1"
                inputMode="numeric"
                placeholder="주"
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
              />
            </label>
            <label className="portfolio-field">
              평균 매수가
              <input
                type="number"
                min="1"
                step="1"
                inputMode="numeric"
                placeholder="원"
                value={averagePrice}
                onChange={(event) => setAveragePrice(event.target.value)}
              />
            </label>
          </div>

          <div className="portfolio-dialog-actions">
            <button type="button" className="portfolio-secondary-button" onClick={onClose}>
              취소
            </button>
            <button type="submit" className="portfolio-primary-button" disabled={!canSave}>
              {holding ? '수정하기' : '추가하기'}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
