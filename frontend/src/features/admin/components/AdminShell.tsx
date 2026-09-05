import type { ReactNode } from 'react';
import { Link, NavLink } from 'react-router-dom';
import daynomyLogo from '../../../assets/daynomy-logo.png';

export function AdminShell({ children }: { children: ReactNode }) {
  return (
    <div className="admin-app">
      <header className="admin-header">
        <Link className="admin-brand" to="/admin/news" aria-label="DAYNOMY 관리자 홈">
          <img src={daynomyLogo} alt="DAYNOMY" />
          <span>관리자</span>
        </Link>
        <nav className="admin-navigation" aria-label="관리자 메뉴">
          <NavLink to="/admin/news">뉴스 관리</NavLink>
          <NavLink to="/admin/assets">관심 자산</NavLink>
          <Link to="/">서비스 홈</Link>
        </nav>
      </header>
      {children}
    </div>
  );
}
