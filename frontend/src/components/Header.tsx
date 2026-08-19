import { useEffect, useState } from 'react';

import { getMyProfile } from '../features/pages/api';
import './Header.css';

export function Header() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);

  useEffect(() => {
    const controller = new AbortController();

    getMyProfile(controller.signal)
      .then(() => {
        if (!controller.signal.aborted) setIsLoggedIn(true);
      })
      .catch(() => {
        if (!controller.signal.aborted) setIsLoggedIn(false);
      });

    return () => controller.abort();
  }, []);

  return (
    <header className="daynomy-header">
      <a className="brand" href="/" aria-label="DAYNOMY 홈">
        <span>DAY</span>
        <span>NOMY</span>
      </a>
      <a className="search-link" href="/search">
        검색
      </a>
      <a className="login-button" href={isLoggedIn ? '/mypage' : '/login'}>
        {isLoggedIn ? '마이페이지' : '로그인'}
      </a>
    </header>
  );
}
