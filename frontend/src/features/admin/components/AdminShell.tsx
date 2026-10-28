import type { ReactNode } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import daynomyLogo from '../../../assets/daynomy-logo.png';

const sections = [
  { path: '/admin/news', label: '뉴스 관리', icon: 'M4 4h16v16H4z M8 8h8 M8 12h8 M8 16h4' },
  {
    path: '/admin/people',
    label: '주요 인물',
    icon: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M17 4a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87',
  },
  {
    path: '/admin/stock-contents',
    label: '종목 연결',
    icon: 'M10 13a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-2 2 M14 11a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l2-2',
  },
  {
    path: '/admin/content-sources',
    label: '소식 출처',
    icon: 'M4 17l5-5 4 4 7-8 M15 8h5v5',
  },
  {
    path: '/admin/content-reviews',
    label: '소식 검수',
    icon: 'M9 11l3 3L22 4 M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11',
  },
  {
    path: '/admin/feedbacks',
    label: '피드백',
    icon: 'M21 15a3 3 0 0 1-3 3H8l-5 3V6a3 3 0 0 1 3-3h12a3 3 0 0 1 3 3z M7 8h10 M7 12h6',
  },
  { path: '/admin/email-analyses', label: '이메일 알림 관리', icon: 'M4 4h16v16H4z M4 6l8 7 8-7' },
  { path: '/admin/stocks', label: '자산 데이터', icon: 'M4 20V10 M10 20V4 M16 20v-7 M22 20H2' },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const current = sections.find(({ path }) => pathname === path || pathname.startsWith(`${path}/`));
  return (
    <div className="admin-app">
      <a className="admin-skip-link" href="#admin-workspace">
        본문으로 이동
      </a>
      <header className="admin-header">
        <Link className="admin-brand" to="/admin/news" aria-label="DAYNOMY 관리자 홈">
          <img src={daynomyLogo} alt="DAYNOMY" />
          <span>관리자</span>
        </Link>
        <nav className="admin-navigation" aria-label="관리자 메뉴">
          {sections.map(({ path, label, icon }) => (
            <NavLink key={path} to={path}>
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
              >
                <path d={icon} />
              </svg>
              {label}
            </NavLink>
          ))}
        </nav>
        <Link className="admin-service-link" to="/">
          서비스 홈 <span aria-hidden="true">↗</span>
        </Link>
      </header>
      <div className="admin-workspace" id="admin-workspace" tabIndex={-1}>
        <div className="admin-topbar">
          <span>관리자 워크스페이스</span>
          <span aria-hidden="true">/</span>
          <strong>{current?.label ?? '접근 권한'}</strong>
        </div>
        {children}
      </div>
    </div>
  );
}
