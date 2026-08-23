import { useEffect, useMemo, useState } from 'react';
import { trackEvent } from '../../analytics';
import { Header } from '../../components/Header';
import { getMyProfile } from '../pages/api';
import { getKosdaqTopStocks } from './api';
import { mockStockCandidates } from './mock';
import type { StockCandidate } from './types';
import './stockList.css';

const BOOKMARK_STORAGE_KEY = 'daynomy:stock-bookmarks';
const STOCKS_PER_PAGE = 15;

function readBookmarkedCodes() {
  try {
    const storedValue = localStorage.getItem(BOOKMARK_STORAGE_KEY);
    const parsedValue = storedValue ? JSON.parse(storedValue) : [];

    return Array.isArray(parsedValue)
      ? parsedValue.filter((value): value is string => typeof value === 'string')
      : [];
  } catch {
    return [];
  }
}

function formatBaseDate(baseDate: string | null) {
  if (!baseDate) {
    return '기준일 없음';
  }

  return baseDate.replaceAll('-', '.');
}

function BookmarkIcon({ selected }: { selected: boolean }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="m12 3.2 2.72 5.51 6.08.88-4.4 4.29 1.04 6.06L12 17.08l-5.44 2.86 1.04-6.06-4.4-4.29 6.08-.88L12 3.2Z"
        fill={selected ? 'currentColor' : 'none'}
      />
    </svg>
  );
}

export function StockListPage() {
  const [stocks, setStocks] = useState<StockCandidate[]>([]);
  const [baseDate, setBaseDate] = useState<string | null>(null);
  const [bookmarkedCodes, setBookmarkedCodes] = useState<string[]>([]);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(1);

  const bookmarkedCodeSet = useMemo(() => new Set(bookmarkedCodes), [bookmarkedCodes]);
  const visibleBookmarkCount = stocks.filter((stock) => bookmarkedCodeSet.has(stock.code)).length;
  const totalPages = Math.max(1, Math.ceil(stocks.length / STOCKS_PER_PAGE));
  const visibleStocks = stocks.slice((page - 1) * STOCKS_PER_PAGE, page * STOCKS_PER_PAGE);

  useEffect(() => {
    trackEvent('view_stock_list');
  }, []);

  useEffect(() => {
    const controller = new AbortController();

    getMyProfile(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(true);
          setBookmarkedCodes(readBookmarkedCodes());
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) {
          setIsLoggedIn(false);
          setBookmarkedCodes([]);
        }
      });

    return () => controller.abort();
  }, []);

  useEffect(() => {
    let ignore = false;

    async function loadStocks() {
      setLoading(true);
      setError(null);

      try {
        const response = await getKosdaqTopStocks();

        if (!ignore) {
          setStocks(response.rankings);
          setBaseDate(response.baseDate);
        }
      } catch {
        if (!ignore) {
          setStocks(mockStockCandidates.rankings);
          setBaseDate(mockStockCandidates.baseDate);
          setError(null);
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    }

    loadStocks();

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    if (isLoggedIn) {
      localStorage.setItem(BOOKMARK_STORAGE_KEY, JSON.stringify(bookmarkedCodes));
    }
  }, [bookmarkedCodes, isLoggedIn]);

  useEffect(() => {
    setPage(1);
  }, [stocks]);

  function toggleBookmark(stock: StockCandidate) {
    if (!isLoggedIn) {
      trackEvent('click_stock_bookmark_login_required', { stockCode: stock.code });
      window.location.assign('/login');
      return;
    }

    setBookmarkedCodes((currentCodes) => {
      const isBookmarked = currentCodes.includes(stock.code);

      trackEvent(isBookmarked ? 'remove_stock_bookmark' : 'add_stock_bookmark', {
        stockCode: stock.code,
      });

      return isBookmarked
        ? currentCodes.filter((code) => code !== stock.code)
        : [...currentCodes, stock.code];
    });
  }

  return (
    <main className="stock-page">
      <Header />

      <section className="stock-heading">
        <div>
          <p>코스닥 대표 종목</p>
          <h1>시가총액 상위 종목</h1>
        </div>
        <dl className="stock-summary" aria-label="종목 목록 요약">
          <div>
            <dt>기준일</dt>
            <dd>{formatBaseDate(baseDate)}</dd>
          </div>
          <div>
            <dt>목록</dt>
            <dd>{stocks.length}개</dd>
          </div>
          {isLoggedIn ? (
            <div>
              <dt>관심</dt>
              <dd>{visibleBookmarkCount}</dd>
            </div>
          ) : null}
        </dl>
      </section>

      <span className="sr-only" role="status">
        {error ? `종목 목록 API 응답을 받지 못했습니다. ${error}` : ''}
      </span>

      {loading ? (
        <section className="stock-state" aria-busy="true">
          <strong>종목 목록을 불러오는 중입니다.</strong>
        </section>
      ) : null}

      {!loading && error ? (
        <section className="stock-state" role="alert">
          <strong>종목 목록을 불러오지 못했습니다.</strong>
          <p>{error}</p>
        </section>
      ) : null}

      {!loading && !error && stocks.length === 0 ? (
        <section className="stock-state">
          <strong>표시할 종목이 없습니다.</strong>
          <p>랭킹 데이터가 생성된 뒤 다시 확인해 주세요.</p>
        </section>
      ) : null}

      {!loading && stocks.length > 0 ? (
        <section className="stock-list-panel" aria-label="코스닥 대표 종목 순위">
          <div className={isLoggedIn ? 'stock-list-head' : 'stock-list-head guest'} aria-hidden="true">
            <span>순위</span>
            <span>종목명</span>
            {isLoggedIn ? <span>관심</span> : null}
          </div>
          <div className="stock-list">
            {visibleStocks.map((stock) => {
              const isBookmarked = bookmarkedCodeSet.has(stock.code);

              return (
                <article
                  className={[
                    'stock-row',
                    isLoggedIn ? '' : 'guest',
                    isBookmarked ? 'bookmarked' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  key={stock.code}
                >
                  <div className="stock-rank" aria-label={`${stock.rank}위`}>
                    {stock.rank}
                  </div>
                  <strong>{stock.name}</strong>
                  {isLoggedIn ? (
                    <button
                      type="button"
                      className="stock-bookmark-button"
                      aria-pressed={isBookmarked}
                      aria-label={`${stock.name} 북마크 ${isBookmarked ? '해제' : '추가'}`}
                      title={`${stock.name} 북마크 ${isBookmarked ? '해제' : '추가'}`}
                      onClick={() => toggleBookmark(stock)}
                    >
                      <BookmarkIcon selected={isBookmarked} />
                    </button>
                  ) : null}
                </article>
              );
            })}
          </div>
          {totalPages > 1 ? (
            <footer className="stock-pagination" aria-label="종목 목록 페이지">
              <button
                type="button"
                onClick={() => setPage((currentPage) => Math.max(1, currentPage - 1))}
                disabled={page === 1}
              >
                이전
              </button>
              <span>
                {page} / {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage((currentPage) => Math.min(totalPages, currentPage + 1))}
                disabled={page === totalPages}
              >
                다음
              </button>
            </footer>
          ) : null}
        </section>
      ) : null}
    </main>
  );
}
