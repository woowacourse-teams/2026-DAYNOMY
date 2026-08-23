import type { StockCandidate } from '../types';
import { BookmarkIcon } from './BookmarkIcon';
import { StockPagination } from './StockPagination';

type StockListProps = {
  stocks: StockCandidate[];
  page: number;
  totalPages: number;
  isLoggedIn: boolean;
  bookmarkedCodeSet: Set<string>;
  onBookmarkToggle: (stock: StockCandidate) => void;
  onPageChange: (page: number) => void;
};

export function StockList({
  stocks,
  page,
  totalPages,
  isLoggedIn,
  bookmarkedCodeSet,
  onBookmarkToggle,
  onPageChange,
}: StockListProps) {
  return (
    <section className="stock-list-panel" aria-label="코스닥 대표 종목 순위">
      <div className={isLoggedIn ? 'stock-list-head' : 'stock-list-head guest'} aria-hidden="true">
        <span>순위</span>
        <span>종목명</span>
        {isLoggedIn ? <span>관심</span> : null}
      </div>
      <div className="stock-list">
        {stocks.map((stock) => {
          const isBookmarked = bookmarkedCodeSet.has(stock.code);

          return (
            <article
              className={['stock-row', isLoggedIn ? '' : 'guest', isBookmarked ? 'bookmarked' : '']
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
                  onClick={() => onBookmarkToggle(stock)}
                >
                  <BookmarkIcon selected={isBookmarked} />
                </button>
              ) : null}
            </article>
          );
        })}
      </div>
      <StockPagination page={page} totalPages={totalPages} onChange={onPageChange} />
    </section>
  );
}
