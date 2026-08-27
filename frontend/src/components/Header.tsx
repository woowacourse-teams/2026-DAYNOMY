import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { trackEvent } from '../analytics';
import { SearchOverlay } from '../features/search/components/SearchOverlay';
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

  function closeSearch() {
    setSearchOpen(false);
    searchButtonRef.current?.focus();
  }

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
          <kbd>/</kbd>
          <span>를 눌러 검색하세요</span>
        </button>
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
      <SearchOverlay open={searchOpen} onClose={closeSearch} />
    </header>
  );
}
