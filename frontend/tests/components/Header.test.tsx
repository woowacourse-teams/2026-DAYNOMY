/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import { Header } from '../../src/components/Header';
import { AuthContext } from '../../src/auth/AuthContext';
import { ServiceHeader } from '../../src/features/navigation/ServiceHeader';

afterEach(cleanup);

describe('헤더', () => {
  it('인물 화면에서도 이슈 메뉴를 활성화하고 인물 탭은 표시하지 않는다', () => {
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={['/people']}>
          <ServiceHeader />
        </MemoryRouter>
      </AuthContext.Provider>,
    );

    const peopleLink = view.getByRole('link', { name: '소식' });
    expect(view.queryByRole('link', { name: '인물' })).toBeNull();
    expect(peopleLink.getAttribute('href')).toBe('/news');
    expect(peopleLink.getAttribute('aria-current')).toBe('page');
    expect(view.getByRole('link', { name: '투자 리그' }).getAttribute('aria-current')).toBeNull();
  });

  it('서비스 헤더의 로그인 링크에 현재 쿼리와 상세 위치를 보존한다', () => {
    const path = '/league/investor-1?week=2026-09-28#investment-details';
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={[path]}>
          <ServiceHeader />
        </MemoryRouter>
      </AuthContext.Provider>,
    );

    const href = view.getByRole('link', { name: '로그인' }).getAttribute('href')!;
    expect(new URL(href, window.location.origin).searchParams.get('returnTo')).toBe(path);
  });

  it('마이페이지 링크를 표시하지 않는다', () => {
    const view = render(
      <MemoryRouter>
        <Header />
      </MemoryRouter>,
    );

    expect(view.queryByRole('link', { name: '마이페이지' })).toBeNull();
    expect(view.getByRole('link', { name: 'DAYNOMY 홈' }).getAttribute('href')).toBe('/');
    expect(view.getByRole('link', { name: '포트폴리오' }).getAttribute('href')).toBe('/');
    expect(view.getByRole('link', { name: '소식' }).getAttribute('href')).toBe('/news');
    expect(view.queryByRole('link', { name: '인물' })).toBeNull();
    expect(view.queryByRole('link', { name: '로그인' })).toBeNull();
  });
});
