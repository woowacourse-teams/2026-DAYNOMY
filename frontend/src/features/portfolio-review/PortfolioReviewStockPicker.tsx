import { useEffect, useRef, useState } from 'react';
import { MagnifyingGlassIcon } from '@phosphor-icons/react/dist/csr/MagnifyingGlass';
import { searchStocks } from '../stocks/api';
import type { StockSearchItem } from '../stocks/types';
import type { SimulationDraft } from './simulationAllocation';

export function PortfolioReviewStockPicker({
  value,
  onSelect,
  draft,
  disabled,
}: {
  value: StockSearchItem | null;
  onSelect: (stock: StockSearchItem | null) => void;
  draft: SimulationDraft[];
  disabled: boolean;
}) {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('ALL');
  const [results, setResults] = useState<StockSearchItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const searchInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!value) {
      setQuery('');
      searchInput.current?.focus();
    }
  }, [value]);

  useEffect(() => {
    setResults([]);
    setError('');
    if (value || disabled || !query.trim()) {
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    const timer = window.setTimeout(() => {
      const search = Promise.all([
        searchStocks(query.trim(), controller.signal),
        searchStocks(query.trim(), controller.signal, 'US'),
      ]).then((items) => [...new Map(items.flat().map((item) => [item.assetId, item])).values()]);
      search
        .then((items) => {
          if (!controller.signal.aborted) setResults(items);
        })
        .catch(() => {
          if (!controller.signal.aborted) setError('종목 검색에 실패했어요. 다시 시도해 주세요.');
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, value, disabled, attempt]);

  const matches = results.filter((stock) => category === 'ALL' || stock.category === category);
  const unavailable = (stock: StockSearchItem) =>
    draft.some(
      (asset) =>
        asset.assetId === stock.assetId ||
        asset.assetName.trim().toLowerCase() === stock.name.trim().toLowerCase(),
    );

  return (
    <div className="portfolio-review-stock-picker">
      {value ? (
        <div className="portfolio-review-selected-stock">
          <span className="portfolio-review-stock-identity">
            <strong>{value.name}</strong>
            <small>
              {value.assetCode} · {value.market} · {value.category === 'ETF' ? 'ETF' : '주식'}
            </small>
          </span>
          <button type="button" disabled={disabled} onClick={() => onSelect(null)}>
            다른 종목 선택
          </button>
        </div>
      ) : (
        <>
          <div className="portfolio-review-stock-query">
            <label>
              종목 검색
              <span className="portfolio-review-search-input">
                <MagnifyingGlassIcon size={18} aria-hidden="true" />
                <input
                  ref={searchInput}
                  type="search"
                  maxLength={100}
                  disabled={disabled}
                  placeholder="종목명 또는 코드 검색"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') event.preventDefault();
                  }}
                />
              </span>
            </label>
            <label>
              종류
              <select
                disabled={disabled}
                value={category}
                onChange={(event) => setCategory(event.target.value)}
              >
                <option value="ALL">전체</option>
                <option value="STOCK">주식</option>
                <option value="ETF">ETF</option>
              </select>
            </label>
          </div>
          {loading ? (
            <p role="status">종목 검색 중…</p>
          ) : error ? (
            <p role="alert">
              {error}{' '}
              <button type="button" disabled={disabled} onClick={() => setAttempt(attempt + 1)}>
                검색 재시도
              </button>
            </p>
          ) : matches.length ? (
            <ul className="portfolio-review-stock-results" aria-label="검색된 종목">
              {matches.map((stock) => (
                <li key={stock.assetId}>
                  <button
                    type="button"
                    disabled={disabled || unavailable(stock)}
                    onClick={() => onSelect(stock)}
                  >
                    <span className="portfolio-review-stock-identity">
                      <strong>{stock.name}</strong>
                      <small>
                        {stock.assetCode} · {stock.market} ·{' '}
                        {stock.category === 'ETF' ? 'ETF' : '주식'}
                      </small>
                    </span>
                    <span className="portfolio-review-stock-action">
                      {unavailable(stock) ? '이미 포함됨' : '선택'}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          ) : query.trim() ? (
            <p role="status">검색 결과가 없어요. 다른 검색어나 종류를 선택해 주세요.</p>
          ) : (
            <p className="portfolio-simulation-help">
              국내·미국 주식과 ETF를 종목명이나 코드로 찾아보세요.
            </p>
          )}
        </>
      )}
    </div>
  );
}
