import { formatBaseDate } from '../utils';

type StockSummaryProps = {
  baseDate: string | null;
  stockCount: number;
  bookmarkCount: number;
  isFallback: boolean;
};

export function StockSummary({
  baseDate,
  stockCount,
  bookmarkCount,
  isFallback,
}: StockSummaryProps) {
  return (
    <dl className="stock-summary" aria-label="종목 목록 요약">
      <div>
        <dt>기준일</dt>
        <dd>기준일 {formatBaseDate(baseDate)}</dd>
      </div>
      <div>
        <dt>목록</dt>
        <dd>
          {stockCount}개{isFallback ? <span className="stock-fallback-badge">mock</span> : null}
        </dd>
      </div>
      <div>
        <dt>관심</dt>
        <dd>{bookmarkCount}</dd>
      </div>
    </dl>
  );
}
