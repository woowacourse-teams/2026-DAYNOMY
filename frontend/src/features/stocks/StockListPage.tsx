import { useEffect, useState } from 'react';
import { trackEvent } from '../../analytics';
import { StockList } from './components/StockList';
import { StockState } from './components/StockState';
import { StockSummary } from './components/StockSummary';
import { STOCKS_PER_PAGE } from './constants';
import { useLoginStatus } from '../../hooks/useLoginStatus';
import { useStockBookmarks } from './hooks/useStockBookmarks';
import { useStockCandidates } from './hooks/useStockCandidates';
import './stockList.css';

export function StockListPage() {
  const [page, setPage] = useState(1);
  const isLoggedIn = useLoginStatus();
  const { stocks, baseDate, totalPages, totalElements, loading, error, isFallback } =
    useStockCandidates(page, STOCKS_PER_PAGE);
  const { bookmarkedCodeSet, toggleBookmark } = useStockBookmarks(isLoggedIn);
  const visibleBookmarkCount = stocks.filter((stock) => bookmarkedCodeSet.has(stock.code)).length;

  useEffect(() => {
    trackEvent('view_stock_list');
  }, []);

  useEffect(() => {
    if (page > totalPages) {
      setPage(totalPages);
    }
  }, [page, totalPages]);

  return (
    <main className="stock-page">
      <section className="stock-heading">
        <div>
          <p>코스닥 2026년 6월 정기변경 기준</p>
          <h1>시가총액 상위 150</h1>
        </div>
        <StockSummary
          baseDate={baseDate}
          stockCount={totalElements}
          bookmarkCount={visibleBookmarkCount}
          isLoggedIn={isLoggedIn}
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
          stocks={stocks}
          page={page}
          totalPages={totalPages}
          isLoggedIn={isLoggedIn}
          bookmarkedCodeSet={bookmarkedCodeSet}
          onBookmarkToggle={toggleBookmark}
          onPageChange={setPage}
        />
      ) : null}
    </main>
  );
}
