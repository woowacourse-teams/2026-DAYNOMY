/** @vitest-environment jsdom */

import { cleanup, render, waitFor } from '@testing-library/react';
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
  it('로그인 화면에는 푸터를 표시하지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', '/login?returnTo=%2F');

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: 'DAYNOMY 로그인' })).toBeTruthy();
    expect(view.queryByRole('contentinfo')).toBeNull();
  });

  it('비로그인 사용자도 빈 포트폴리오 대시보드를 사용한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', '/');

    const view = render(<App />);

    expect(await view.findByRole('button', { name: /자산 추가/ })).toBeTruthy();
    expect(view.getByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    expect(view.queryByText('로그인해야 볼 수 있습니다')).toBeNull();
    expect(window.location.pathname).toBe('/');
    expect(
      vi.mocked(fetch).mock.calls.some(([input]) => String(input).endsWith('/api/portfolio')),
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
        if (url.endsWith('/api/portfolio')) return jsonResponse({ holdings: [] });
        return jsonResponse({}, 404);
      }),
    );
    window.history.replaceState(null, '', '/');

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    await waitFor(() => {
      expect(
        vi.mocked(fetch).mock.calls.some(([input]) => String(input).endsWith('/api/portfolio')),
      ).toBe(true);
    });
  });
});
