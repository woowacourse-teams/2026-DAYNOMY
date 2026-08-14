import './Header.css'

export function Header() {
  return (
    <header className="site-header">
      <a href="/" className="site-logo" aria-label="DAYNOMY 홈">
        DAY<span>NOMY</span>
      </a>
      <div className="site-actions">
        <button type="button" className="auth-button">
          로그인 / 회원가입
        </button>
        <button type="button" className="icon-button" aria-label="메뉴">
          ·
        </button>
      </div>
    </header>
  )
}
