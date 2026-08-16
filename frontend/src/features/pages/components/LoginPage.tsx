import { useState } from 'react';
import { Link } from 'react-router-dom';
import '../LoginPage.css';

function LoginPage() {
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleGoogleLogin = () => {
    setErrorMessage(null);

    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

    if (!apiBaseUrl) {
      setErrorMessage('Google 로그인 기능을 준비 중입니다.');
      return;
    }

    setIsLoading(true);

    const normalizedApiBaseUrl = apiBaseUrl.replace(/\/$/, '');

    window.location.assign(`${normalizedApiBaseUrl}/api/auth/google`);
  };

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

        <button
          className="google-login-button"
          type="button"
          onClick={handleGoogleLogin}
          disabled={isLoading}
          aria-busy={isLoading}
        >
          <span className="google-icon" aria-hidden="true" />
          {isLoading ? '로그인 중...' : 'Google로 계속하기'}
        </button>

        <div className="login-message" aria-live="polite">
          {errorMessage && (
            <p className="login-error" role="alert">
              {errorMessage}
            </p>
          )}
        </div>

        <p className="login-policy">
          로그인하면 서비스 이용약관과 개인정보 처리방침에 동의하게 됩니다.
        </p>
      </section>
    </main>
  );
}

export default LoginPage;
