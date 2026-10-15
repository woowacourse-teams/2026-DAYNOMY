/** @vitest-environment jsdom */

import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../../src/App';
import { SERVICE_STARTED_KEY } from '../../src/features/landing/startedService';

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
  localStorage.removeItem(SERVICE_STARTED_KEY);
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
    localStorage.setItem(SERVICE_STARTED_KEY, '1');
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', '/');

    const view = render(<App />);

    expect(await view.findByRole('button', { name: '거래 기록' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    expect(view.queryByText('로그인해야 볼 수 있습니다')).toBeNull();
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
        if (url.endsWith('/api/users/me/portfolio'))
          return jsonResponse({ holdings: [], totalRealizedProfitLoss: 0 });
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

  it('비로그인 사용자가 관리자 화면에 접근하면 로그인 후 돌아올 전체 경로를 보존한다', async () => {
    const path = '/admin/news?page=2#draft';
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', path);

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: /DAYNOMY (관리자 )?로그인/ })).toBeTruthy();
    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('returnTo')).toBe(path);
  });

  it.each([
    ['/league', '/league'],
    ['/league/portfolio?week=2026-09-28#holdings', '/league/portfolio'],
    ['/league/investor-1', '/league/investor-1'],
    ['/portfolio/publication?join=true', '/portfolio/publication'],
    ['/portfolio/records', '/league/portfolio'],
    ['/membership', '/league'],
  ])('%s에서 실제 리그 화면 대신 준비 중 안내를 보여준다', async (path, target) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', path);

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: '투자 리그를 준비하고 있어요' })).toBeTruthy();
    expect(window.location.pathname).toBe(target);
    expect(view.getByRole('link', { name: '내 포트폴리오로 돌아가기' }).getAttribute('href')).toBe(
      '/',
    );
    expect(view.queryByRole('link', { name: '← 주간 리그' })).toBeNull();
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) =>
          /\/api\/(league|users\/me\/(shared-portfolio|portfolio\/publication|investor-profile))/.test(
            String(input),
          ),
        ),
    ).toBe(false);
  });
});
