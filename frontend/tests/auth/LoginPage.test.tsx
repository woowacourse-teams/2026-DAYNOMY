/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
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

afterEach(() => {
  cleanup();
  sessionStorage.clear();
});

describe('로그인 화면', () => {
  it('Google 로그인 링크를 OAuth 엔드포인트에 연결하고 복귀 경로를 저장한다', () => {
    const view = renderLogin('/login?returnTo=%2Fleague');
    const link = view.getByRole('link', {
      name: 'Google로 시작하기',
    }) as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe(getApiUrl('/api/auth/google'));
    expect(view.getByRole('heading', { name: 'DAYNOMY 로그인' })).toBeTruthy();

    link.addEventListener('click', (event) => event.preventDefault());
    fireEvent.click(link);

    expect(sessionStorage.getItem('daynomy:post-login-path')).toBe('/league');
  });

  it('외부 주소 형태의 복귀 경로는 저장하지 않는다', () => {
    const view = renderLogin('/login?returnTo=%2F%2Fevil.example');
    const link = view.getByRole('link', { name: 'Google로 시작하기' });
    link.addEventListener('click', (event) => event.preventDefault());

    fireEvent.click(link);

    expect(sessionStorage.getItem('daynomy:post-login-path')).toBe('/');
  });

  it('포트폴리오 Google 로그인 링크를 OAuth 엔드포인트에 연결한다', () => {
    const view = renderLogin();
    const link = view.getByRole('link', { name: 'Google로 시작하기' });
    expect(link.getAttribute('href')).toBe(getApiUrl('/api/auth/google'));
    expect(view.getByRole('heading', { name: 'DAYNOMY 로그인' })).toBeTruthy();
    expect(view.getByRole('link', { name: '닫기' }).getAttribute('href')).toBe('/news');
    link.addEventListener('click', (event) => event.preventDefault());
    fireEvent.click(link);
    expect(sessionStorage.getItem('daynomy:post-login-path')).toBe('/');
  });

  it('관리자 로그인은 관리자 화면으로 돌아간다', () => {
    const view = renderLogin('/login?returnTo=%2Fadmin');
    const link = view.getByRole('link', { name: 'Google로 시작하기' });
    link.addEventListener('click', (event) => event.preventDefault());
    fireEvent.click(link);

    expect(view.getByRole('heading', { name: 'DAYNOMY 관리자 로그인' })).toBeTruthy();
    expect(view.getByRole('link', { name: '닫기' }).getAttribute('href')).toBe('/');
    expect(sessionStorage.getItem('daynomy:post-login-path')).toBe('/admin');
  });

  it('OAuth 실패를 사용자에게 안내한다', () => {
    const view = renderLogin('/login?error=oauth');

    expect(view.getByRole('alert').textContent).toContain('Google 로그인에 실패했습니다.');
  });

  it.each([
    '/\\evil.example',
    '/api/auth/google',
    '/oauth2/authorization/google',
    '/login?returnTo=/login',
  ])('위험하거나 반복되는 복귀 경로 %s는 거부한다', (path) => {
    const view = renderLogin(`/login?returnTo=${encodeURIComponent(path)}`);
    const link = view.getByRole('link', { name: 'Google로 시작하기' });
    link.addEventListener('click', (event) => event.preventDefault());
    fireEvent.click(link);
    expect(sessionStorage.getItem('daynomy:post-login-path')).toBe('/');
  });

  it('투자자 상세의 쿼리와 상세 위치를 복귀 경로에 유지한다', () => {
    const path = '/league/investor-1?week=2026-09-28#investment-details';
    const view = renderLogin(`/login?returnTo=${encodeURIComponent(path)}`);
    const link = view.getByRole('link', { name: 'Google로 시작하기' });
    link.addEventListener('click', (event) => event.preventDefault());
    fireEvent.click(link);
    expect(sessionStorage.getItem('daynomy:post-login-path')).toBe(path);
  });
});
