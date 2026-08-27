type StockPaginationProps = {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
};

export function StockPagination({ page, totalPages, onChange }: StockPaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  const maxVisiblePages = 5;
  const visiblePageCount = Math.min(totalPages, maxVisiblePages);
  const firstPage = Math.min(
    Math.max(page - Math.floor(visiblePageCount / 2), 1),
    Math.max(totalPages - visiblePageCount + 1, 1),
  );
  const pages = Array.from({ length: visiblePageCount }, (_, index) => firstPage + index);

  return (
    <footer className="stock-pagination" aria-label="종목 목록 페이지">
      <button
        type="button"
        onClick={() => onChange(Math.max(1, page - 1))}
        disabled={page === 1}
        aria-label="이전 페이지"
        className="stock-pagination-arrow"
      >
        &lt;
      </button>
      {pages.map((pageNumber) => (
        <button
          className={pageNumber === page ? 'active' : undefined}
          type="button"
          key={pageNumber}
          onClick={() => onChange(pageNumber)}
          aria-current={pageNumber === page ? 'page' : undefined}
        >
          {pageNumber}
        </button>
      ))}
      <button
        type="button"
        onClick={() => onChange(Math.min(totalPages, page + 1))}
        disabled={page === totalPages}
        aria-label="다음 페이지"
        className="stock-pagination-arrow"
      >
        &gt;
      </button>
    </footer>
  );
}
