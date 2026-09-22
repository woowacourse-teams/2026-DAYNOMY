import { Link } from 'react-router-dom';
import '../MyPage.css';

function MyPage() {
  return (
    <main className="my-page">
      <section className="mypage-content">
        <section className="bookmarked-assets" aria-labelledby="mypage-portfolio-title">
          <div className="bookmarked-assets-heading">
            <div>
              <p className="section-eyebrow">MY PORTFOLIO</p>
              <h1 id="mypage-portfolio-title">포트폴리오 관리</h1>
            </div>
          </div>
          <div className="bookmark-empty">
            <p>보유 자산과 수익률은 포트폴리오 화면에서 관리할 수 있습니다.</p>
            <Link className="mypage-portfolio-link" to="/portfolio">
              내 포트폴리오 보기
            </Link>
          </div>
        </section>
      </section>
    </main>
  );
}

export default MyPage;
