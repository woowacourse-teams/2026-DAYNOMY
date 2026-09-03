import { BookmarkIcon } from '../../stocks/components/BookmarkIcon';
import { useStockBookmarks } from '../../stocks/hooks/useStockBookmarks';
import type { StockCandidate } from '../../stocks/types';

type StockSearchResultsProps = {
  keyword: string;
  stocks: StockCandidate[];
  isLoggedIn: boolean;
};

export function StockSearchResults({ keyword, stocks, isLoggedIn }: StockSearchResultsProps) {
  const { bookmarkedCodeSet, toggleBookmark } = useStockBookmarks(isLoggedIn);

  return (
    <section className="stock-search-results" aria-labelledby="stock-result-title">
      <h1 id="stock-result-title">{keyword} 검색 결과</h1>
      <div className="stock-search-scroll" role="region" aria-label="검색된 종목 목록" tabIndex={0}>
        <ul className="stock-search-list">
          {stocks.map((stock) => {
            const isBookmarked = bookmarkedCodeSet.has(stock.code);

            return (
              <li className="stock-search-card" key={stock.code}>
                <div className="stock-search-category">
                  <span aria-hidden="true" />
                  주식
                </div>
                <strong>{stock.name}</strong>
                <span>{stock.code}</span>
                {isLoggedIn ? (
                  <button
                    type="button"
                    className="stock-search-bookmark"
                    aria-pressed={isBookmarked}
                    aria-label={`${stock.name} 북마크 ${isBookmarked ? '해제' : '추가'}`}
                    onClick={() => toggleBookmark(stock)}
                  >
                    <BookmarkIcon selected={isBookmarked} />
                  </button>
                ) : null}
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
