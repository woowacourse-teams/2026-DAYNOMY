import '../portfolio.css';

export function PortfolioLoginPrompt() {
  return (
    <section className="section portfolio-section" aria-labelledby="portfolio-login-title">
      <div className="portfolio-heading">
        <div>
          <h2 id="portfolio-login-title">내 포트폴리오 영향</h2>
          <p>북마크한 자산을 기준으로 분석한 결과입니다.</p>
        </div>
      </div>

      <div className="portfolio-state portfolio-login-prompt">
        <span className="portfolio-state-icon" aria-hidden="true">
          ↗
        </span>
        <strong>로그인하고 내 자산의 영향을 확인하세요</strong>
        <p>북마크한 자산을 기준으로 뉴스의 예상 영향과 판단 근거를 분석해 드립니다.</p>
        <a href="/login">로그인하기</a>
      </div>
    </section>
  );
}
