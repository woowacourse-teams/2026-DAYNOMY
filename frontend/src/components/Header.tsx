import "./Header.css";

export function Header() {
  return (
    <header className="daynomy-header">
      <a className="brand" href="/" aria-label="DAYNOMY 홈">
        <span>DAY</span>
        <span>NOMY</span>
      </a>
      <a className="search-link" href="/search">
        검색
      </a>
      <a className="login-button" href="/login">
        로그인
      </a>
    </header>
  );
}
