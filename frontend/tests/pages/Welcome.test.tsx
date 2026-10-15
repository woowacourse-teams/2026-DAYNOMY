/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../../src/App';
import {
  hasStartedService,
  rememberServiceStart,
  SERVICE_STARTED_KEY,
} from '../../src/features/landing/startedService';

vi.mock('../../src/features/landing/LandingPage', () => ({
  default: ({ onStart }: { onStart: () => void }) => (
    <main>
      <h1>DAYNOMY 서비스 소개</h1>
      <button onClick={() => onStart()}>시작하기</button>
    </main>
  ),
}));
vi.mock('../../src/features/news/newsdetail/NewsDetailPage', () => ({
  NewsDetailPage: () => (
    <main>
      <h1>공유 뉴스</h1>
    </main>
  ),
}));

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

beforeEach(() => {
  window.history.replaceState(null, '', '/');
  localStorage.clear();
  sessionStorage.clear();
  vi.stubGlobal('scrollTo', vi.fn());
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => response({}, 401)),
  );
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  localStorage.clear();
  sessionStorage.clear();
  vi.restoreAllMocks();
});

describe('첫 방문 서비스 소개', () => {
  it('새 비회원은 소개로 이동하며 서비스 헤더와 푸터를 겹쳐 표시하지 않는다', async () => {
    const view = render(<App />);
    expect(await view.findByRole('heading', { name: 'DAYNOMY 서비스 소개' })).toBeTruthy();
    expect(window.location.pathname).toBe('/welcome');
    expect(view.container.querySelector('.service-header')).toBeNull();
    expect(view.container.querySelector('.daynomy-footer')).toBeNull();
    expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBeNull();
  });

  it('시작하기 후에는 기록을 남기고 새로 접속해도 포트폴리오를 표시한다', async () => {
    const view = render(<App />);
    fireEvent.click(await view.findByRole('button', { name: '시작하기' }));
    expect(await view.findByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    expect(window.location.pathname).toBe('/');
    expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBe('1');
    cleanup();
    const revisit = render(<App />);
    expect(await revisit.findByRole('button', { name: '거래 기록' })).toBeTruthy();
    expect(window.location.pathname).toBe('/');
  });

  it('직접 공유된 뉴스의 쿼리와 위치를 보존하고 이후 홈에서도 소개를 반복하지 않는다', async () => {
    window.history.replaceState(null, '', '/news/7?source=share#analysis');
    const view = render(<App />);
    expect(await view.findByRole('heading', { name: '공유 뉴스' })).toBeTruthy();
    expect(`${window.location.pathname}${window.location.search}${window.location.hash}`).toBe(
      '/news/7?source=share#analysis',
    );
    await waitFor(() => expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBe('1'));
    fireEvent.click(view.getByRole('link', { name: '포트폴리오' }));
    expect(await view.findByRole('button', { name: '거래 기록' })).toBeTruthy();
    expect(window.location.pathname).toBe('/');
  });

  it.each(['/finance', '/finance/guide/first-account', '/portfolio', '/stocks'])(
    '로그인 확인이 늦어도 기존 주소 %s에서 포트폴리오로 바로 진입한다',
    async (path) => {
      let finishAuth!: (value: Response) => void;
      const authResponse = new Promise<Response>((resolve) => {
        finishAuth = resolve;
      });
      vi.stubGlobal(
        'fetch',
        vi.fn(() => authResponse),
      );
      localStorage.setItem('daynomy:financial-plans:v1', '보존할 기록');
      window.history.replaceState(null, '', path);

      const view = render(<App />);
      await waitFor(() => expect(window.location.pathname).toBe('/'));
      finishAuth(response({}, 401));

      expect(await view.findByRole('button', { name: '거래 기록' })).toBeTruthy();
      expect(window.location.pathname).toBe('/');
      expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBe('1');
      expect(localStorage.getItem('daynomy:financial-plans:v1')).toBe('보존할 기록');
    },
  );

  it('기존 로그인 사용자는 시작 기록이 없어도 소개를 건너뛴다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).endsWith('/api/users/me'))
          return response({
            id: 1,
            nickname: '회원',
            email: 'member@example.invalid',
            role: 'USER',
          });
        if (String(input).endsWith('/api/users/me/portfolio'))
          return response({ holdings: [], totalRealizedProfitLoss: 0 });
        return response({}, 404);
      }),
    );
    const view = render(<App />);
    expect(await view.findByRole('button', { name: '거래 기록' })).toBeTruthy();
    expect(window.location.pathname).toBe('/');
    await waitFor(() => expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBe('1'));
  });

  it('기존 방문자도 서비스 소개 링크로 설명을 다시 볼 수 있다', async () => {
    localStorage.setItem(SERVICE_STARTED_KEY, '1');
    const view = render(<App />);
    fireEvent.click(await view.findByRole('link', { name: '서비스 소개' }));
    expect(await view.findByRole('heading', { name: 'DAYNOMY 서비스 소개' })).toBeTruthy();
    expect(window.location.pathname).toBe('/welcome');
    fireEvent.click(view.getByRole('button', { name: '시작하기' }));
    expect(await view.findByRole('button', { name: '거래 기록' })).toBeTruthy();
  });

  it('모든 브라우저 저장소가 차단되어도 시작하기로 서비스에 들어간다', async () => {
    const blocked = {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
    };
    vi.stubGlobal('localStorage', blocked);
    vi.stubGlobal('sessionStorage', blocked);
    const view = render(<App />);
    fireEvent.click(await view.findByRole('button', { name: '시작하기' }));
    expect(await view.findByRole('button', { name: '거래 기록' })).toBeTruthy();
    expect(window.location.pathname).toBe('/');
  });

  it('영구 저장소가 차단되면 세션 저장소로 재방문을 기억한다', () => {
    vi.stubGlobal('localStorage', {
      getItem: () => {
        throw new Error('blocked');
      },
      setItem: () => {
        throw new Error('blocked');
      },
    });
    rememberServiceStart();
    expect(sessionStorage.getItem(SERVICE_STARTED_KEY)).toBe('1');
    expect(hasStartedService()).toBe(true);
  });
});
