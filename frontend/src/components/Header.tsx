import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { trackEvent } from '../analytics';
import { useLoginStatus } from '../hooks/useLoginStatus';
import './Header.css';

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4 4" />
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
          종목
        </a>
      </nav>
      <div className="header-actions">
        <a className="search-link" href="/search" aria-label="뉴스 검색">
          <SearchIcon />
        </a>
        <a
          className="login-button"
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
