export function NewsListSkeleton() {
  return (
    <section className="article-list" aria-label="뉴스 목록 불러오는 중">
      {Array.from({ length: 9 }).map((_, index) => (
        <div className="article-card skeleton" key={index}>
          <div className="article-meta">
            <span />
          </div>
          <div className="article-thumbnail" />
          <div className="article-body">
            <h2 />
            <p />
            <time className="article-time" />
          </div>
        </div>
      ))}
    </section>
  );
}
