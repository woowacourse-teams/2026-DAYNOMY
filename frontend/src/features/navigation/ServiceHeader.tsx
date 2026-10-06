import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { trackEvent } from '../../analytics';
import { useAuth } from '../../hooks/useLoginStatus';
import { SearchOverlay } from '../search/components/SearchOverlay';
import './ServiceHeader.css';

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M9.5 3a6.5 6.5 0 0 0 0 13c1.61 0 3.09-.59 4.23-1.57l.35.35v1.02l5 4.99 1.49-1.49-4.99-5h-1.02l-.35-.35A6.47 6.47 0 0 0 16 9.5 6.5 6.5 0 0 0 9.5 3Zm0 2A4.5 4.5 0 1 1 5 9.5 4.5 4.5 0 0 1 9.5 5Z" />
    </svg>
  );
}

export function ServiceHeader() {
  const location = useLocation();
  const { isLoggedIn, loading, nickname } = useAuth();
  const [searchOpen, setSearchOpen] = useState(false);
  const searchButtonRef = useRef<HTMLButtonElement>(null);

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

  const tabs = [
    { label: '포트폴리오', to: '/', active: location.pathname === '/' },
    {
      label: '이슈',
      to: '/news',
      active: location.pathname.startsWith('/news') || location.pathname.startsWith('/search'),
    },
    {
      label: '투자 리그',
      to: '/league',
      active:
        location.pathname.startsWith('/league') || location.pathname.startsWith('/portfolio/'),
    },
  ];

  return (
    <header className="service-header">
      <Link className="service-brand" to="/" aria-label="DAYNOMY 홈">
        DAYNOMY
      </Link>
      <nav className="service-header-tabs" aria-label="주요 메뉴">
        {tabs.map((tab) => (
          <Link
            key={tab.to}
            className={tab.active ? 'service-header-tab active' : 'service-header-tab'}
            to={tab.to}
            aria-current={tab.active ? 'page' : undefined}
          >
            {tab.label}
          </Link>
        ))}
      </nav>
      <div className="service-header-actions">
        <button
          ref={searchButtonRef}
          type="button"
          className="service-search-link"
          aria-label="검색 열기"
          aria-haspopup="dialog"
          aria-expanded={searchOpen}
          onClick={() => {
            trackEvent('click_search_open');
            setSearchOpen(true);
          }}
        >
          <SearchIcon />
          <kbd>/</kbd>
          <span>를 눌러 검색하세요</span>
        </button>
        {!loading && !isLoggedIn ? (
          <Link
            className="service-login-link"
            to={`/login?returnTo=${encodeURIComponent(location.pathname)}`}
          >
            로그인
          </Link>
        ) : null}
        {!loading && isLoggedIn ? (
          <Link
            className="service-profile-link"
            to="/mypage"
            aria-label="마이페이지"
            title="마이페이지"
          >
            {nickname?.slice(0, 1) || '나'}
          </Link>
        ) : null}
      </div>
      <SearchOverlay
        open={searchOpen}
        onClose={(restoreFocus) => {
          setSearchOpen(false);
          if (restoreFocus) searchButtonRef.current?.focus();
        }}
      />
    </header>
  );
}
