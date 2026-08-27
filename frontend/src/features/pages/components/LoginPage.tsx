import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import '../LoginPage.css';
import { getApiUrl } from '../api';
import { trackEvent } from '../../../analytics';

function GoogleIcon() {
  return (
    <svg className="google-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M21.6 12.227c0-.709-.064-1.391-.182-2.045H12v3.868h5.382a4.6 4.6 0 0 1-1.995 3.018v2.509h3.231c1.891-1.741 2.982-4.304 2.982-7.35Z"
      />
      <path
        fill="#34A853"
        d="M12 22c2.7 0 4.964-.895 6.618-2.423l-3.231-2.509c-.895.6-2.041.955-3.387.955-2.605 0-4.81-1.76-5.595-4.123H3.064v2.591A9.997 9.997 0 0 0 12 22Z"
      />
      <path
        fill="#FBBC05"
        d="M6.405 13.9A6.01 6.01 0 0 1 6.091 12c0-.659.114-1.3.314-1.9V7.509H3.064A9.997 9.997 0 0 0 2 12c0 1.614.386 3.141 1.064 4.491L6.405 13.9Z"
      />
      <path
        fill="#EA4335"
        d="M12 5.977c1.468 0 2.786.505 3.823 1.496l2.868-2.868C16.959 2.991 14.695 2 12 2a9.997 9.997 0 0 0-8.936 5.509L6.405 10.1C7.19 7.737 9.395 5.977 12 5.977Z"
      />
    </svg>
  );
}

function LoginPage() {
  const [searchParams] = useSearchParams();
  const oauthError = searchParams.get('error') === 'oauth';
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(() =>
    oauthError ? 'Google 로그인에 실패했습니다.' : null,
  );

  useEffect(() => {
    if (oauthError) trackEvent('login_failure', { method: 'google', error_code: 'oauth' });
  }, [oauthError]);

  const handleGoogleLogin = () => {
    setErrorMessage(null);
    trackEvent('click_login');

    setIsLoading(true);
    window.location.assign(getApiUrl('/api/auth/google'));
  };

  return (
    <main className="login-page">
      <Link className="login-close" to="/" aria-label="닫기">
        ×
      </Link>
      <section className="login-card">
        <div className="login-heading">
          <h1>DAYNOMY 시작하기</h1>
          <p>오늘의 경제 흐름을 한눈에 확인해보세요</p>
        </div>

        <button
          className="google-login-button"
          type="button"
          onClick={handleGoogleLogin}
          disabled={isLoading}
          aria-busy={isLoading}
        >
          <GoogleIcon />
          {isLoading ? '로그인 중...' : 'Google로 시작하기'}
        </button>

        <div className="login-message" aria-live="polite">
          {errorMessage && (
            <p className="login-error" role="alert">
              {errorMessage}
            </p>
          )}
        </div>

        <p className="login-policy">
          계속하면 서비스 이용약관과 개인정보처리방침에 동의하게 됩니다.
        </p>
      </section>
    </main>
  );
}

export default LoginPage;
