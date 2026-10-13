import { trackEvent, type AnalyticsParams } from '../../../analytics';

export function IssuePagination({
  page,
  totalPages,
  onChange,
  analytics,
}: {
  analytics?: AnalyticsParams;
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  function changePage(next: number) {
    if (next === page) return;
    if (analytics)
      trackEvent('change_content_page', { ...analytics, previous_page: page, page_number: next });
    onChange(next);
  }
  const first = Math.floor((page - 1) / 6) * 6 + 1;
  const numbers = Array.from(
    { length: Math.min(6, totalPages - first + 1) },
    (_, index) => first + index,
  );
  return (
    <nav className="issue-pagination" aria-label="콘텐츠 페이지">
      <button aria-label="이전 페이지" disabled={page === 1} onClick={() => changePage(page - 1)}>
        〈
      </button>
      {numbers.map((number) => (
        <button
          key={number}
          aria-label={`${number}페이지`}
          aria-current={page === number ? 'page' : undefined}
          onClick={() => changePage(number)}
        >
          {number}
        </button>
      ))}
      <button
        aria-label="다음 페이지"
        disabled={page === totalPages}
        onClick={() => changePage(page + 1)}
      >
        〉
      </button>
    </nav>
  );
}
