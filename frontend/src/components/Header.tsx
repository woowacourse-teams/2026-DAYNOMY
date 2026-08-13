export function Header() {
  return (
    <header className="daynomy-header">
      <a className="brand" href="/" aria-label="DAYNOMY 홈">
        <span>DAY</span>
        <span>NOMY</span>
      </a>
      <button className="login-button" type="button">
        로그인 / 회원가입
      </button>
    </header>
  )
}
