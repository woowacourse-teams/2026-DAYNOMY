/** @vitest-environment jsdom */
/** @vitest-environment-options {"url":"https://daynomy.com/"} */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it } from 'vitest';
import { ServiceDomainBoundary } from '../src/App';
import { AuthContext } from '../src/auth/AuthContext';

afterEach(cleanup);

it('사용자 도메인의 관리자 경로를 사용자 홈으로 보낸다', () => {
  const view = render(
    <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'ADMIN' }}>
      <MemoryRouter initialEntries={['/admin/news']}>
        <ServiceDomainBoundary>
          <Routes>
            <Route path="/" element={<main>사용자 홈</main>} />
            <Route path="/admin/news" element={<main>관리자 화면</main>} />
          </Routes>
        </ServiceDomainBoundary>
      </MemoryRouter>
    </AuthContext.Provider>,
  );

  expect(view.getByText('사용자 홈')).toBeTruthy();
  expect(view.queryByText('관리자 화면')).toBeNull();
});
