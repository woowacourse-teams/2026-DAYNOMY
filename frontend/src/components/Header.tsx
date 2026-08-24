import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { trackEvent } from '../analytics';
import { useLoginStatus } from '../hooks/useLoginStatus';
import './Header.css';

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9.5 3a6.5 6.5 0 0 0 0 13c1.61 0 3.09-.59 4.23-1.57l.35.35v1.02l5 4.99 1.49-1.49-4.99-5h-1.02l-.35-.35A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 0 0 9.5 3Zm0 2A4.5 4.5 0 1 1 5 9.5 4.5 4.5 0 0 1 9.5 5Z" />
    </svg>
  );
}

export function Header() {
  const isLoggedIn = useLoginStatus();
  const location = useLocation();
  const isStockPage = location.pathname.startsWith('/stocks');

  useEffect(() => {
    if (isLoggedIn) {
      if (!sessionStorage.getItem('daynomy:login-tracked')) {
        trackEvent('login_success', { method: 'google' });
        sessionStorage.setItem('daynomy:login-tracked', 'true');
      }
    }
  }, [isLoggedIn]);

  return (
    <header className="daynomy-header">
      <a className="brand" href="/" aria-label="DAYNOMY 홈">
        <span>DAY</span>
        <span>NOMY</span>
      </a>
      <nav className="header-tabs" aria-label="주요 메뉴">
        <a className={isStockPage ? 'header-tab' : 'header-tab active'} href="/">
          뉴스
        </a>
        <a className={isStockPage ? 'header-tab active' : 'header-tab'} href="/stocks">
          관심종목
        </a>
      </nav>
      <div className="header-actions">
        <a className="search-link" href="/search" aria-label="뉴스와 종목 검색">
          <SearchIcon />
          <span className="search-key">/</span>
          <span className="search-placeholder">를 눌러 검색하세요</span>
        </a>
        <a
          className={isLoggedIn ? 'mypage-link' : 'login-button'}
          href={isLoggedIn ? '/mypage' : '/login'}
          onClick={() => {
            if (!isLoggedIn) trackEvent('click_login');
          }}
        >
          {isLoggedIn ? '마이페이지' : '로그인'}
        </a>
      </div>
    </header>
  );
}
