import { useEffect, useMemo, useState } from 'react';
import { trackEvent } from '../../analytics';
import { StockList } from './components/StockList';
import { StockState } from './components/StockState';
import { StockSummary } from './components/StockSummary';
import { STOCKS_PER_PAGE } from './constants';
import { useStockBookmarks } from './hooks/useStockBookmarks';
import { useStockCandidates } from './hooks/useStockCandidates';
import './stockList.css';

export function StockListPage() {
  const [page, setPage] = useState(1);
  const { stocks, baseDate, loading, error, isFallback } = useStockCandidates();
  const { bookmarkedCodeSet, toggleBookmark } = useStockBookmarks();
  const visibleBookmarkCount = stocks.filter((stock) => bookmarkedCodeSet.has(stock.code)).length;
  const totalPages = Math.max(1, Math.ceil(stocks.length / STOCKS_PER_PAGE));
  const visibleStocks = useMemo(
    () => stocks.slice((page - 1) * STOCKS_PER_PAGE, page * STOCKS_PER_PAGE),
    [page, stocks],
  );

  useEffect(() => {
    trackEvent('view_stock_list');
  }, []);

  useEffect(() => {
    setPage(1);
  }, [stocks]);

  return (
    <main className="stock-page">
      <section className="stock-heading">
        <div>
          <p>코스닥 2026년 6월 정기변경 기준</p>
          <h1>시가총액 상위 150</h1>
        </div>
        <StockSummary
          baseDate={baseDate}
          stockCount={stocks.length}
          bookmarkCount={visibleBookmarkCount}
          isFallback={isFallback}
        />
      </section>

      <span className="sr-only" role="status">
        {error ? `종목 목록 API 응답을 받지 못했습니다. ${error}` : ''}
      </span>

      {loading && stocks.length === 0 ? (
        <StockState title="종목 목록을 불러오는 중입니다." busy />
      ) : null}

      {!loading && error && !isFallback ? (
        <StockState title="종목 목록을 불러오지 못했습니다." description={error} role="alert" />
      ) : null}

      {!loading && !error && !isFallback && stocks.length === 0 ? (
        <StockState
          title="표시할 종목이 없습니다."
          description="랭킹 데이터가 생성된 뒤 다시 확인해 주세요."
        />
      ) : null}

      {stocks.length > 0 ? (
        <StockList
          stocks={visibleStocks}
          page={page}
          totalPages={totalPages}
          bookmarkedCodeSet={bookmarkedCodeSet}
          onBookmarkToggle={toggleBookmark}
          onPageChange={setPage}
        />
      ) : null}
    </main>
  );
}
