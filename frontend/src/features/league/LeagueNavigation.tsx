import { Link, useLocation } from 'react-router-dom';
import './league.css';

export function LeagueNavigation() {
  const { pathname } = useLocation();
  if (
    pathname === '/league' ||
    (pathname !== '/portfolio/publication' && !pathname.startsWith('/league/'))
  )
    return null;
  return (
    <nav
      className={`league-breadcrumb${pathname === '/league/portfolio' ? ' league-holdings-breadcrumb' : ''}`}
      aria-label="리그로 돌아가기"
    >
      <Link to="/league">← 주간 리그</Link>
    </nav>
  );
}
