import { BookmarkIcon } from '../../stocks/components/BookmarkIcon';
import '../MyPage.css';

function MyPage() {
  return (
    <main className="my-page">
      <section className="mypage-content">
        <section className="bookmarked-assets" aria-labelledby="bookmarked-assets-title">
          <div className="bookmarked-assets-heading">
            <div>
              <p className="section-eyebrow">MY ASSETS</p>
              <h1 id="bookmarked-assets-title">관심자산</h1>
            </div>
            <span className="bookmark-count">0개</span>
          </div>

          <div className="bookmark-empty">
            <span aria-hidden="true">
              <BookmarkIcon selected />
            </span>
            <p>아직 저장한 관심자산이 없습니다.</p>
          </div>
        </section>
      </section>
    </main>
  );
}

export default MyPage;
