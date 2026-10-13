import { Link } from 'react-router-dom';
import './league-coming-soon.css';

export function LeagueComingSoonPage() {
  return (
    <main className="league-coming-soon">
      <div className="league-coming-soon-preview" aria-hidden="true">
        <div className="league-coming-soon-preview-title" />
        <div className="league-coming-soon-preview-tabs" />
        <div className="league-coming-soon-preview-row" />
        <div className="league-coming-soon-preview-row" />
        <div className="league-coming-soon-preview-row" />
      </div>
      <section className="league-coming-soon-message">
        <span className="league-coming-soon-label">COMING SOON</span>
        <h1>투자 리그를 준비하고 있어요</h1>
        <p>서로의 투자 기록을 나누고 성장할 수 있는 공간을 만들고 있어요. 조금만 기다려 주세요.</p>
        <Link to="/">내 포트폴리오로 돌아가기</Link>
      </section>
    </main>
  );
}
