/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import MyPage from '../../src/features/pages/components/MyPage';

afterEach(cleanup);

describe('마이페이지', () => {
  it('포트폴리오 관리 화면으로 이동할 수 있다', () => {
    const view = render(
      <MemoryRouter>
        <MyPage />
      </MemoryRouter>,
    );
    expect(view.getByRole('heading', { name: '포트폴리오 관리' })).toBeTruthy();
    expect(view.getByRole('link', { name: '내 포트폴리오 보기' }).getAttribute('href')).toBe(
      '/portfolio',
    );
  });
});
