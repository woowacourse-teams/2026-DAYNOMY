import { Link, useLocation } from 'react-router-dom';

export function LeagueNavigation() {
  const { pathname } = useLocation();
  const isDetail = pathname.startsWith('/league/') || pathname.startsWith('/portfolio/');
  if (!isDetail) return null;

  return (
    <nav className="league-feature-nav" aria-label="상위 화면">
      <Link to="/league">← 투자 리그</Link>
    </nav>
  );
}
