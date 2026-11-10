import { Link } from 'react-router-dom';
import './portfolioContentsNext.css';

export function PortfolioReadingNext() {
  return (
    <div className="portfolio-reading-next">
      <h3>보유 비중까지 반영하면 어떤 영향이 있을까요?</h3>
      <Link to="/">
        내 포트폴리오 AI 분석 보기
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          aria-hidden="true"
        >
          <path d="M4 12h15m-6-6 6 6-6 6" />
        </svg>
      </Link>
    </div>
  );
}
