import { Link } from "react-router-dom";
import "../LoginPage.css";

function LoginPage() {
  return (
    <main className="login-page">
      <section className="login-card">
        <Link className="login-logo" to="/">
          <span>DAY</span>
          <span>NOMY</span>
        </Link>

        <div className="login-heading">
          <h1>로그인</h1>
          <p>Google 계정으로 간편하게 시작하세요.</p>
        </div>

        <button className="google-login-button" type="button">
          <span className="google-icon" aria-hidden="true"></span>
          Google로 계속하기
        </button>

        <p className="login-policy">
          로그인하면 서비스 이용약관과 개인정보 처리방침에 동의하게 됩니다.
        </p>
      </section>
    </main>
  );
}

export default LoginPage;
