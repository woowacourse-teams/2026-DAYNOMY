/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { getApiUrl } from '../../src/api/client';
import LoginPage from '../../src/features/pages/components/LoginPage';

function renderLogin(path = '/login') {
  const router = createMemoryRouter([{ path: '/login', element: <LoginPage /> }], {
    initialEntries: [path],
  });

  return render(<RouterProvider router={router} />);
}

afterEach(cleanup);

describe('로그인 화면', () => {
  it('Google 로그인 링크를 OAuth 엔드포인트에 연결한다', () => {
    const view = renderLogin();
    const link = view.getByRole('link', { name: 'Google로 시작하기' });

    expect(link.getAttribute('href')).toBe(getApiUrl('/api/auth/google'));
  });

  it('OAuth 실패를 사용자에게 안내한다', () => {
    const view = renderLogin('/login?error=oauth');

    expect(view.getByRole('alert').textContent).toContain('Google 로그인에 실패했습니다.');
  });
});
