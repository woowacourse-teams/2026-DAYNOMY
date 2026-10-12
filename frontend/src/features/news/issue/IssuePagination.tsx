export function IssuePagination({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  const first = Math.floor((page - 1) / 6) * 6 + 1;
  const numbers = Array.from(
    { length: Math.min(6, totalPages - first + 1) },
    (_, index) => first + index,
  );
  return (
    <nav className="issue-pagination" aria-label="콘텐츠 페이지">
      <button aria-label="이전 페이지" disabled={page === 1} onClick={() => onChange(page - 1)}>
        〈
      </button>
      {numbers.map((number) => (
        <button
          key={number}
          aria-label={`${number}페이지`}
          aria-current={page === number ? 'page' : undefined}
          onClick={() => onChange(number)}
        >
          {number}
        </button>
      ))}
      <button
        aria-label="다음 페이지"
        disabled={page === totalPages}
        onClick={() => onChange(page + 1)}
      >
        〉
      </button>
    </nav>
  );
}
