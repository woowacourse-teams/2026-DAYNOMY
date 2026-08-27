import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { trackEvent } from '../analytics';
import daynomyLogo from '../assets/daynomy-logo.png';
import { SearchOverlay } from '../features/search/components/SearchOverlay';
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
  const isNewsPage = location.pathname === '/' || location.pathname.startsWith('/news');
  const isStockPage = location.pathname.startsWith('/stocks');
  const [searchOpen, setSearchOpen] = useState(false);
  const searchButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (isLoggedIn) {
      if (!sessionStorage.getItem('daynomy:login-tracked')) {
        trackEvent('login_success', { method: 'google' });
        sessionStorage.setItem('daynomy:login-tracked', 'true');
      }
    }
  }, [isLoggedIn]);

  useEffect(() => {
    function openSearch(event: KeyboardEvent) {
      const target = event.target;
      const isEditing =
        target instanceof HTMLElement &&
        (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName));

      if (event.key !== '/' || event.metaKey || event.ctrlKey || event.altKey || isEditing) return;

      event.preventDefault();
      setSearchOpen(true);
    }

    document.addEventListener('keydown', openSearch);
    return () => document.removeEventListener('keydown', openSearch);
  }, []);

  function closeSearch(restoreFocus: boolean) {
    setSearchOpen(false);
    if (restoreFocus) searchButtonRef.current?.focus();
  }

  return (
    <header className="daynomy-header">
      <Link className="brand" to="/" aria-label="DAYNOMY 홈">
        <img className="brand-logo" src={daynomyLogo} alt="" />
      </Link>
      <nav className="header-tabs" aria-label="주요 메뉴">
        <Link className={isNewsPage ? 'header-tab active' : 'header-tab'} to="/">
          뉴스
        </Link>
        <Link className={isStockPage ? 'header-tab active' : 'header-tab'} to="/stocks">
          관심종목
        </Link>
      </nav>
      <div className="header-actions">
        <button
          ref={searchButtonRef}
          type="button"
          className="search-link"
          aria-label="검색 열기"
          aria-haspopup="dialog"
          aria-expanded={searchOpen}
          onClick={() => setSearchOpen(true)}
        >
          <SearchIcon />
          <kbd className="search-key">/</kbd>
          <span className="search-placeholder">를 눌러 검색하세요</span>
        </button>
        <Link
          className={isLoggedIn ? 'mypage-link' : 'login-button'}
          to={isLoggedIn ? '/mypage' : '/login'}
          onClick={() => {
            if (!isLoggedIn) trackEvent('click_login');
          }}
        >
          {isLoggedIn ? '마이페이지' : '로그인'}
        </Link>
      </div>
      <SearchOverlay open={searchOpen} onClose={closeSearch} />
    </header>
  );
}
