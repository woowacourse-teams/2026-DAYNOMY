type StockPaginationProps = {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
};

export function StockPagination({ page, totalPages, onChange }: StockPaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <footer className="stock-pagination" aria-label="종목 목록 페이지">
      <button type="button" onClick={() => onChange(Math.max(1, page - 1))} disabled={page === 1}>
        이전
      </button>
      <span>
        {page} / {totalPages}
      </span>
      <button
        type="button"
        onClick={() => onChange(Math.min(totalPages, page + 1))}
        disabled={page === totalPages}
      >
        다음
      </button>
    </footer>
  );
}
