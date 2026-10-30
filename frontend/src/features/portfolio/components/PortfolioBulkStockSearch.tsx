import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { searchStocks } from '../../stocks/api';
import type { StockSearchItem } from '../../stocks/types';

export function PortfolioBulkStockSearch({
  index,
  value,
  disabled,
  unavailable,
  onSelect,
  initialQuery = '',
}: {
  index: number;
  value?: StockSearchItem;
  disabled: boolean;
  unavailable: Map<number, string>;
  onSelect: (stock: StockSearchItem) => void;
  initialQuery?: string;
}) {
  const [query, setQuery] = useState('');
  const [open, setOpen] = useState(false);
  const [results, setResults] = useState<StockSearchItem[]>([]);
  const [active, setActive] = useState(-1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const ref = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const [position, setPosition] = useState({ top: 0, left: 0, width: 340 });
  const resultId = `bulk-stock-results-${index}`;
  function locate() {
    const rect = trigger.current?.getBoundingClientRect();
    if (!rect) return;
    const width = Math.min(340, window.innerWidth - 24);
    setPosition({
      top: Math.max(8, Math.min(rect.bottom + 5, window.innerHeight - 330)),
      left: Math.max(12, Math.min(rect.left, window.innerWidth - width - 12)),
      width,
    });
  }
  useEffect(() => {
    if (!open) return;
    input.current?.focus();
    window.addEventListener('resize', locate);
    window.addEventListener('scroll', locate, true);
    return () => {
      window.removeEventListener('resize', locate);
      window.removeEventListener('scroll', locate, true);
    };
  }, [open]);
  useEffect(() => {
    setResults([]);
    setActive(-1);
    setError('');
    if (!open || !query.trim()) {
      setLoading(false);
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    const timer = window.setTimeout(() => {
      Promise.all([
        searchStocks(query.trim(), controller.signal),
        searchStocks(query.trim(), controller.signal, 'US'),
      ])
        .then((items) => {
          if (!controller.signal.aborted)
            setResults([...new Map(items.flat().map((stock) => [stock.assetId, stock])).values()]);
        })
        .catch((caught: unknown) => {
          if (!controller.signal.aborted)
            setError(caught instanceof Error ? caught.message : '종목을 찾지 못했어요.');
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [query, open]);
  function close() {
    setOpen(false);
    trigger.current?.focus();
  }
  function select(stock: StockSearchItem) {
    if (disabled || unavailable.has(stock.assetId)) return;
    onSelect(stock);
    close();
  }
  function navigate(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      close();
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const eligible = results.flatMap((stock, i) => (unavailable.has(stock.assetId) ? [] : [i]));
      const current = eligible.indexOf(active);
      const next =
        event.key === 'ArrowDown'
          ? (current + 1) % eligible.length
          : (current <= 0 ? eligible.length : current) - 1;
      const target = eligible[next] ?? -1;
      setActive(target);
      if (target >= 0)
        document.getElementById(`${resultId}-${target}`)?.scrollIntoView({ block: 'nearest' });
    } else if (event.key === 'Enter') {
      event.preventDefault();
      if (active >= 0 && results[active]) select(results[active]);
    }
  }
  return (
    <div
      ref={ref}
      className="portfolio-bulk-stock-search"
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node)) setOpen(false);
      }}
    >
      <button
        ref={trigger}
        type="button"
        className={`portfolio-bulk-stock-trigger${value ? ' has-value' : ''}`}
        aria-label={`${index + 1}행 종목 검색`}
        disabled={disabled}
        aria-expanded={open}
        aria-controls={open ? `${resultId}-panel` : undefined}
        onClick={() => {
          locate();
          setQuery(initialQuery);
          setOpen(true);
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="10.5" cy="10.5" r="6.5" />
          <path d="m16 16 4 4" />
        </svg>
        <span>{value?.name ?? (initialQuery || '종목명 또는 코드 검색')}</span>
        {value && <small>{value.assetCode}</small>}
      </button>
      {open && (
        <div
          id={`${resultId}-panel`}
          className="portfolio-bulk-search-results"
          style={position}
          aria-label={`${index + 1}행 종목 선택`}
        >
          <div className="portfolio-bulk-search-input">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="10.5" cy="10.5" r="6.5" />
              <path d="m16 16 4 4" />
            </svg>
            <input
              ref={input}
              role="combobox"
              aria-label={`${index + 1}행 종목명 또는 코드 검색`}
              aria-autocomplete="list"
              aria-expanded={open}
              aria-controls={resultId}
              aria-activedescendant={active >= 0 ? `${resultId}-${active}` : undefined}
              placeholder="종목명 또는 종목코드"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={navigate}
            />
            {query && (
              <button
                type="button"
                aria-label="검색어 지우기"
                onClick={() => {
                  setQuery('');
                  input.current?.focus();
                }}
              >
                ×
              </button>
            )}
          </div>
          <div
            className="portfolio-bulk-search-list"
            role="listbox"
            id={resultId}
            aria-label="검색된 종목"
            aria-busy={loading}
          >
            {!loading &&
              !error &&
              results.map((stock, itemIndex) => (
                <button
                  type="button"
                  role="option"
                  id={`${resultId}-${itemIndex}`}
                  key={stock.assetId}
                  tabIndex={-1}
                  aria-selected={active === itemIndex}
                  disabled={unavailable.has(stock.assetId)}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => select(stock)}
                >
                  <i aria-hidden="true">{stock.name.slice(0, 1)}</i>
                  <span>
                    <strong>{stock.name}</strong>
                    <small>
                      {stock.assetCode} · {stock.market}
                    </small>
                  </span>
                  {unavailable.has(stock.assetId) ? (
                    <em>{unavailable.get(stock.assetId)}</em>
                  ) : (
                    <span aria-hidden="true">+</span>
                  )}
                </button>
              ))}
          </div>
          {loading ? (
            <p role="status">
              <span className="portfolio-bulk-search-spinner" aria-hidden="true" />
              종목 검색 중…
            </p>
          ) : error ? (
            <p role="alert">{error}</p>
          ) : !results.length ? (
            <p role="status">
              {query.trim() ? '검색 결과가 없어요.' : '종목명이나 코드를 검색해 주세요.'}
            </p>
          ) : null}
        </div>
      )}
    </div>
  );
}
