import { NavLink, useLocation } from 'react-router-dom';

const leagueLinks = [
  { to: '/league', label: '주간 순위' },
  { to: '/league/portfolio', label: '공유 포트폴리오' },
  { to: '/portfolio/publication', label: '리그 참여 설정' },
  { to: '/league/following', label: '관심 투자자' },
];

export function LeagueNavigation() {
  const { pathname } = useLocation();
  const isLeague = pathname.startsWith('/league') || pathname.startsWith('/portfolio/');
  if (!isLeague) return null;

  return (
    <nav className="league-feature-nav" aria-label="투자 리그 메뉴">
      {leagueLinks.map((item) => (
        <NavLink key={item.to} to={item.to} end={item.to === '/league'}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}
