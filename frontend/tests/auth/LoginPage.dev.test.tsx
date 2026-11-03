/** @vitest-environment jsdom */
/** @vitest-environment-options {"url":"https://dev.daynomy.com/"} */

import { cleanup, render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, expect, it } from 'vitest';
import LoginPage from '../../src/features/pages/components/LoginPage';

afterEach(cleanup);

it('개발 도메인에서 팀원 로그인을 안내한다', () => {
  const router = createMemoryRouter([{ path: '/login', element: <LoginPage /> }], {
    initialEntries: ['/login?returnTo=%2Fnews'],
  });
  const view = render(<RouterProvider router={router} />);

  expect(view.getByRole('heading', { name: 'DAYNOMY 팀원 로그인' })).toBeTruthy();
  expect(document.title).toBe('팀원 로그인 | DAYNOMY');
});
