import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import '../LoginPage.css';
import { getApiUrl } from '../api';
import { trackEvent } from '../../../analytics';

function LoginPage() {
  const [searchParams] = useSearchParams();
  const oauthError = searchParams.get('error') === 'oauth';
  const errorMessage = oauthError ? 'Google 로그인에 실패했습니다.' : null;

  useEffect(() => {
    if (oauthError) trackEvent('login_failure', { method: 'google', error_code: 'oauth' });
  }, [oauthError]);

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

        <a
          className="google-login-button"
          href={getApiUrl('/api/auth/google')}
          onClick={() => trackEvent('click_login')}
        >
          <span className="google-icon" aria-hidden="true" />
          Google로 계속하기
        </a>

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
