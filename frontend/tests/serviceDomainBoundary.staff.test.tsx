/** @vitest-environment jsdom */
/** @vitest-environment-options {"url":"https://staff.daynomy.com/"} */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { ServiceDomainBoundary } from '../src/App';
import { AuthContext, type AuthContextValue } from '../src/auth/AuthContext';

function CurrentLocation() {
  const location = useLocation();
  return <p>{`${location.pathname}${location.search}`}</p>;
}

function renderBoundary(auth: AuthContextValue, path = '/') {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <ServiceDomainBoundary>
          <Routes>
            <Route path="/login" element={<CurrentLocation />} />
            <Route path="*" element={<main>사용자 화면</main>} />
          </Routes>
        </ServiceDomainBoundary>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

afterEach(cleanup);

describe('팀원 도메인 접근 경계', () => {
  it('로그인 확인이 끝날 때까지 화면을 렌더링하지 않는다', () => {
    const view = renderBoundary({ isLoggedIn: false, loading: true, role: null });

    expect(view.getByLabelText('팀원 로그인 확인 중').getAttribute('aria-busy')).toBe('true');
    expect(view.queryByText('사용자 화면')).toBeNull();
  });

  it('비로그인 사용자를 현재 경로를 보존한 로그인 화면으로 보낸다', () => {
    const view = renderBoundary(
      { isLoggedIn: false, loading: false, role: null },
      '/news?category=ECONOMY',
    );

    expect(view.getByText('/login?returnTo=%2Fnews%3Fcategory%3DECONOMY')).toBeTruthy();
  });

  it('ADMIN 팀원에게 요청한 사용자 화면을 그대로 보여준다', () => {
    const view = renderBoundary({ isLoggedIn: true, loading: false, role: 'ADMIN' }, '/news');

    expect(view.getByText('사용자 화면')).toBeTruthy();
  });

  it('ADMIN이 아닌 계정은 팀원 도메인 화면을 볼 수 없다', () => {
    const view = renderBoundary({ isLoggedIn: true, loading: false, role: 'USER' });

    expect(view.getByRole('heading', { name: '팀원 권한이 필요합니다.' })).toBeTruthy();
    expect(view.queryByText('사용자 화면')).toBeNull();
    expect(view.getByRole('link', { name: '사용자 서비스로 이동' }).getAttribute('href')).toBe(
      'https://daynomy.com/',
    );
  });
});
