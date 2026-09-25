export function NewsListSkeleton() {
  return (
    <section className="article-list" aria-label="이슈 목록 불러오는 중">
      {Array.from({ length: 6 }).map((_, index) => (
        <div className="article-card skeleton" key={index}>
          <div className="article-thumbnail" />
          <div className="article-body">
            <div className="article-meta">
              <span />
              <span />
            </div>
            <h2 />
          </div>
          <span className="article-arrow" />
        </div>
      ))}
    </section>
  );
}
