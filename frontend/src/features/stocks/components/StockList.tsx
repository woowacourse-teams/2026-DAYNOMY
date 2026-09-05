import type { StockCandidate } from '../types';
import { BookmarkIcon } from './BookmarkIcon';
import { StockPagination } from './StockPagination';

type StockListProps = {
  stocks: StockCandidate[];
  page: number;
  totalPages: number;
  bookmarkedCodeSet: Set<string>;
  onBookmarkToggle: (stock: StockCandidate) => void;
  onPageChange: (page: number) => void;
};

function RankChange({ value }: { value?: number | null }) {
  if (value == null || value === 0) {
    return <span className="stock-rank-change empty" aria-hidden="true" />;
  }

  const isUp = value > 0;
  const directionLabel = isUp ? '상승' : '하락';

  return (
    <span
      className={`stock-rank-change ${isUp ? 'up' : 'down'}`}
      aria-label={`순위 ${directionLabel} ${Math.abs(value)}`}
    >
      <span aria-hidden="true">{isUp ? '▲' : '▼'}</span>
      {Math.abs(value)}
    </span>
  );
}

export function StockList({
  stocks,
  page,
  totalPages,
  bookmarkedCodeSet,
  onBookmarkToggle,
  onPageChange,
}: StockListProps) {
  return (
    <section className="stock-list-panel" aria-label="코스닥 대표 종목 순위">
      <div className="stock-list-head" aria-hidden="true">
        <span>순위</span>
        <span>종목명</span>
        <span />
        <span />
      </div>
      <div className="stock-list">
        {stocks.map((stock) => {
          const isBookmarked = bookmarkedCodeSet.has(stock.code);

          return (
            <article className={`stock-row${isBookmarked ? ' bookmarked' : ''}`} key={stock.code}>
              <div className="stock-rank" aria-label={`${stock.rank}위`}>
                {stock.rank}
              </div>
              <div className="stock-info">
                <strong>{stock.name}</strong>
                <span>{stock.code}</span>
              </div>
              <RankChange value={stock.rankChange} />
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
            </article>
          );
        })}
      </div>
      <StockPagination page={page} totalPages={totalPages} onChange={onPageChange} />
    </section>
  );
}
