/** @vitest-environment jsdom */

import { cleanup, render, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../../src/App';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  cleanup();
  window.history.replaceState(null, '', '/');
  sessionStorage.clear();
  vi.unstubAllGlobals();
});

describe('포트폴리오 로그인 접근', () => {
  it('비로그인 사용자에게 포트폴리오 화면에서 로그인 안내를 보여준다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', '/');

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: '로그인해야 볼 수 있습니다' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    expect(
      within(view.getByRole('main')).getByRole('link', { name: '로그인' }).getAttribute('href'),
    ).toBe('/login?returnTo=%2F');
    expect(window.location.pathname).toBe('/');
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) => String(input).endsWith('/api/users/me/portfolio')),
    ).toBe(false);
  });

  it('로그인 사용자는 백엔드 포트폴리오를 조회한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.endsWith('/api/users/me')) {
          return jsonResponse({
            id: 1,
            email: 'member@example.com',
            nickname: '회원',
            role: 'USER',
          });
        }
        if (url.endsWith('/api/users/me/portfolio')) return jsonResponse({ holdings: [] });
        return jsonResponse({}, 404);
      }),
    );
    window.history.replaceState(null, '', '/');

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    await waitFor(() => {
      expect(
        vi
          .mocked(fetch)
          .mock.calls.some(([input]) => String(input).endsWith('/api/users/me/portfolio')),
      ).toBe(true);
    });
  });

  it('비로그인 사용자가 공유 포트폴리오에 접근하면 로그인 후 돌아올 경로를 보존한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', '/league/portfolio');

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: 'DAYNOMY 로그인' })).toBeTruthy();
    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('returnTo')).toBe('/league/portfolio');
  });
});
