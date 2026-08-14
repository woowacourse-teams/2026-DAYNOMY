export function NewsListSkeleton() {
  return (
    <section className="article-list" aria-label="뉴스 목록 불러오는 중">
      {Array.from({ length: 5 }).map((_, index) => (
        <div className="article-card skeleton" key={index}>
          <div className="article-thumbnail" />
          <div className="article-body">
            <span />
            <h2 />
            <p />
          </div>
        </div>
      ))}
    </section>
  )
}
