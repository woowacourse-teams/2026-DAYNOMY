/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
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

const originalShowModal = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true;
  };
  HTMLDialogElement.prototype.close = function () {
    this.open = false;
  };
});
afterAll(() => {
  HTMLDialogElement.prototype.showModal = originalShowModal;
  HTMLDialogElement.prototype.close = originalClose;
});

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
  it('거래 이력이 없는 비회원은 재방문해도 그래프와 분석 예시를 바로 본다', async () => {
    const view = render(<App />);
    expect(await view.findByRole('heading', { name: '포트폴리오를 만들어 보세요' })).toBeTruthy();
    expect(view.getByRole('list', { name: '예시 자산 비중' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '반도체 수요가 회복된다면' })).toBeTruthy();
    expect(window.location.pathname).toBe('/');
    expect(view.container.querySelector('.service-header')).not.toBeNull();
    expect(view.queryByRole('button', { name: '종목 추가' })).toBeNull();

    view.unmount();
    const revisit = render(<App />);
    expect(await revisit.findByRole('button', { name: '포트폴리오 만들기' })).toBeTruthy();
  });

  it('종목 등록을 누르면 기존 거래 기록 창을 바로 연다', async () => {
    const view = render(<App />);
    fireEvent.click(await view.findByRole('button', { name: '포트폴리오 만들기' }));
    expect(await view.findByRole('dialog', { name: '거래 기록' })).toBeTruthy();
    expect(view.getByRole('searchbox', { name: '종목 검색' })).toBeTruthy();
    expect(view.queryByRole('button', { name: '포트폴리오 만들기' })).toBeNull();
    expect(document.title).toBe('내 포트폴리오 | DAYNOMY');
    expect(window.location.pathname).toBe('/');
  });

  it('공유 뉴스의 쿼리와 위치를 보존하고 거래 이력이 없으면 포트폴리오에서 소개를 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7?source=share#analysis');
    const view = render(<App />);
    expect(await view.findByRole('heading', { name: '공유 뉴스' })).toBeTruthy();
    expect(`${window.location.pathname}${window.location.search}${window.location.hash}`).toBe(
      '/news/7?source=share#analysis',
    );
    await waitFor(() => expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBe('1'));
    fireEvent.click(view.getByRole('link', { name: '포트폴리오' }));
    expect(await view.findByRole('button', { name: '포트폴리오 만들기' })).toBeTruthy();
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

      expect(await view.findByRole('button', { name: '포트폴리오 만들기' })).toBeTruthy();
      expect(window.location.pathname).toBe('/');
      expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBe('1');
      expect(localStorage.getItem('daynomy:financial-plans:v1')).toBe('보존할 기록');
    },
  );

  it.each([
    { hasPortfolioHistory: false, button: '포트폴리오 만들기' },
    { hasPortfolioHistory: true, button: '종목 추가' },
    { hasPortfolioHistory: undefined, button: '종목 추가' },
  ])(
    '보유 종목이 없는 회원의 거래 이력이 $hasPortfolioHistory이면 $button 버튼을 표시한다',
    async ({ hasPortfolioHistory, button }) => {
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
            return response({ holdings: [], totalRealizedProfitLoss: 0, hasPortfolioHistory });
          return response({}, 404);
        }),
      );
      const view = render(<App />);
      expect(await view.findByRole('button', { name: button })).toBeTruthy();
      expect(window.location.pathname).toBe('/');
      await waitFor(() => expect(localStorage.getItem(SERVICE_STARTED_KEY)).toBe('1'));
    },
  );

  it('기존 방문자도 서비스 소개 링크로 설명을 다시 볼 수 있다', async () => {
    localStorage.setItem(SERVICE_STARTED_KEY, '1');
    const view = render(<App />);
    fireEvent.click(await view.findByRole('link', { name: '서비스 소개' }));
    expect(await view.findByRole('heading', { name: 'DAYNOMY 서비스 소개' })).toBeTruthy();
    expect(window.location.pathname).toBe('/welcome');
    expect(view.container.querySelector('.service-header')).toBeNull();
    expect(view.container.querySelector('.daynomy-footer')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '시작하기' }));
    expect(await view.findByRole('button', { name: '포트폴리오 만들기' })).toBeTruthy();
  });

  it('모든 브라우저 저장소가 차단되어도 등록 창 없이 포트폴리오를 둘러볼 수 있다', async () => {
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
    fireEvent.click(await view.findByRole('button', { name: '포트폴리오 바로 보기' }));
    expect(await view.findByRole('button', { name: '종목 추가' })).toBeTruthy();
    expect(view.queryByRole('dialog', { name: '거래 기록' })).toBeNull();
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
