import { BookmarkIcon } from '../../stocks/components/BookmarkIcon';
import { useStockBookmarks } from '../../stocks/hooks/useStockBookmarks';
import '../MyPage.css';

function MyPage() {
  const { bookmarkedStocks, removeBookmark } = useStockBookmarks();

  return (
    <main className="my-page">
      <section className="mypage-content">
        <section className="bookmarked-assets" aria-labelledby="bookmarked-assets-title">
          <div className="bookmarked-assets-heading">
            <div>
              <p className="section-eyebrow">MY ASSETS</p>
              <h1 id="bookmarked-assets-title">관심자산</h1>
            </div>
            <span className="bookmark-count">{bookmarkedStocks.length}개</span>
          </div>

          {bookmarkedStocks.length > 0 ? (
            <div className="bookmark-grid" aria-label="저장한 관심자산 목록">
              {bookmarkedStocks.map((stock) => (
                <article className="bookmark-card" key={stock.code}>
                  <div className="bookmark-card-category">
                    <span aria-hidden="true" />
                    주식
                  </div>
                  <button
                    type="button"
                    className="bookmark-remove-button"
                    aria-label={`${stock.name} 북마크 해제`}
                    title={`${stock.name} 북마크 해제`}
                    onClick={() => removeBookmark(stock.code)}
                  >
                    <BookmarkIcon selected />
                  </button>
                  <h3>{stock.name}</h3>
                  <p>{stock.code}</p>
                </article>
              ))}
            </div>
          ) : (
            <div className="bookmark-empty">
              <span aria-hidden="true">
                <BookmarkIcon selected />
              </span>
              <p>아직 저장한 관심자산이 없습니다.</p>
            </div>
          )}
        </section>
      </section>
    </main>
  );
}

export default MyPage;
