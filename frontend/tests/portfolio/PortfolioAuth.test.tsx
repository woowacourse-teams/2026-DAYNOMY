/** @vitest-environment jsdom */

import { cleanup, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../src/App';
import { SERVICE_STARTED_KEY } from '../../src/features/landing/startedService';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

beforeEach(() => {
  localStorage.setItem('daynomy:portfolio-tour:v1', 'done');
});

afterEach(() => {
  cleanup();
  window.history.replaceState(null, '', '/');
  sessionStorage.clear();
  localStorage.removeItem(SERVICE_STARTED_KEY);
  localStorage.removeItem('daynomy:portfolio-tour:v1');
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

    expect(await view.findByRole('button', { name: '종목 추가' })).toBeTruthy();
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

  it.each([
    ['/league/portfolio?week=2026-09-28#holdings', '/league/portfolio?week=2026-09-28#holdings'],
    ['/league/profile', '/league/profile'],
    ['/league/following', '/league/following'],
    ['/league/reviews', '/league/reviews'],
    ['/portfolio/publication?join=true', '/portfolio/publication?join=true'],
    ['/portfolio/records', '/league/portfolio'],
    ['/admin/news?page=2#draft', '/admin/news?page=2#draft'],
  ])('비로그인 사용자가 %s에 접근하면 로그인 후 돌아올 경로를 보존한다', async (path, returnTo) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 401)),
    );
    window.history.replaceState(null, '', path);

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: /DAYNOMY (관리자 )?로그인/ })).toBeTruthy();
    expect(window.location.pathname).toBe('/login');
    expect(new URLSearchParams(window.location.search).get('returnTo')).toBe(returnTo);
    expect(
      vi
        .mocked(fetch)
        .mock.calls.some(([input]) =>
          /\/api\/users\/me\/(shared-portfolio|portfolio\/|investor-profile|league\/)/.test(
            String(input),
          ),
        ),
    ).toBe(false);
  });

  it.each(['/league', '/membership'])(
    '%s에서 로그인 없이 실제 주간 순위를 조회한다',
    async (path) => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async (input: RequestInfo | URL) => {
          const pathname = new URL(String(input), window.location.origin).pathname;
          if (pathname === '/api/league/weeks') return jsonResponse({ weeks: [] });
          if (pathname === '/api/league/rankings')
            return jsonResponse({
              weekStart: '2026-10-12',
              weekEnd: '2026-10-18',
              leagueType: 'WEEKLY_RETURN',
              confirmed: false,
              totalCount: 0,
              asOfDate: null,
              rankings: [],
            });
          return jsonResponse({}, 401);
        }),
      );
      window.history.replaceState(null, '', path);

      const view = render(<App />);

      expect(await view.findByText('아직 이 주차에 집계된 투자자가 없습니다')).toBeTruthy();
      expect(view.getByRole('heading', { name: '주간 리그' })).toBeTruthy();
      expect(document.title).toBe('주간 리그 | DAYNOMY');
      expect(document.activeElement).toBe(view.getByRole('heading', { name: '주간 리그' }));
      expect(view.getByRole('link', { name: '투자리그 참여하기' })).toBeTruthy();
      expect(window.location.pathname).toBe('/league');
    },
  );

  it('공개 투자자 상세를 로그인 없이 조회하고 본인 관리 API는 호출하지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const pathname = new URL(String(input), window.location.origin).pathname;
        if (pathname === '/api/league/investors/investor-1')
          return jsonResponse({
            publicId: 'investor-1',
            displayName: '공개 투자자',
            bio: '',
            experienceLevel: 'BEGINNER',
            riskProfile: 'BALANCED',
            performance: {
              weeklyReturnRate: null,
              threeWeekReturnRate: null,
              maxDrawdownRate: null,
              volatilityRate: null,
              maxHoldingWeight: null,
            },
            allocation: null,
            history: [],
            decisionCount: 0,
            reviewCompletionRate: 0,
            followed: false,
            detailAvailable: false,
          });
        return jsonResponse({}, 401);
      }),
    );
    window.history.replaceState(null, '', '/league/investor-1');

    const view = render(<App />);

    expect(await view.findByRole('heading', { name: '공개 투자자' })).toBeTruthy();
    await waitFor(() => {
      expect(document.title).toBe('공개 투자자 공개 프로필 | DAYNOMY');
      expect(document.activeElement).toBe(view.getByRole('heading', { name: '공개 투자자' }));
    });
    expect(view.getByRole('link', { name: '로그인하고 팔로우' })).toBeTruthy();
    expect(view.getByRole('link', { name: '← 주간 리그' }).getAttribute('href')).toBe('/league');
    expect(
      vi.mocked(fetch).mock.calls.some(([input]) => /\/api\/users\/me\//.test(String(input))),
    ).toBe(false);
  });
});
