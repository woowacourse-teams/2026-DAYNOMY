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
  return (
    <header className="daynomy-header">
      <a className="brand" href="/" aria-label="DAYNOMY 홈">
        <span>DAY</span>
        <span>NOMY</span>
      </a>
      <a className="search-link" href="/search" aria-label="뉴스 검색">
        <SearchIcon />
      </a>
      <a className="login-button" href="/login">
        로그인
      </a>
    </header>
  );
}
