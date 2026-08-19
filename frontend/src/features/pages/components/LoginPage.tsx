import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import '../LoginPage.css';
import { getApiUrl } from '../api';

function LoginPage() {
  const [searchParams] = useSearchParams();
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(() =>
    searchParams.get('error') === 'oauth' ? 'Google 로그인에 실패했습니다.' : null,
  );

  const handleGoogleLogin = () => {
    setErrorMessage(null);

    setIsLoading(true);
    window.location.assign(getApiUrl('/api/auth/google'));
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
