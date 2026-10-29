/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../../src/auth/AuthContext';
import { EmailSubscriptionPrompt } from '../../src/features/email-subscription/EmailSubscriptionPrompt';
import {
  getEmailSubscription,
  type EmailSubscriptionResponse,
} from '../../src/features/email-subscription/api';
import App from '../../src/App';

const subscription: EmailSubscriptionResponse = {
  status: 'UNDECIDED',
  email: 'member@example.invalid',
  promptRequired: true,
  subscribedAt: null,
};
const account: AuthContextValue = { isLoggedIn: true, loading: false, memberId: 1, role: 'USER' };
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });

function installApi(value = subscription) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = new URL(String(input), window.location.origin).pathname;
    if (path === '/api/auth/csrf') return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
    if (init?.method === 'PUT')
      return json({
        ...value,
        status: 'SUBSCRIBED',
        promptRequired: false,
        subscribedAt: '2026-10-28T00:00:00Z',
      });
    if (init?.method === 'POST')
      return json({ ...value, status: 'DECLINED', promptRequired: false });
    return json(value);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function Location() {
  return <p data-testid="path">{useLocation().pathname}</p>;
}
function renderPrompt(auth = account, path = '/news') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthContext.Provider value={auth}>
        <Location />
        <EmailSubscriptionPrompt key={auth.memberId ?? 'guest'} />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

const originalShow = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function () {
    this.removeAttribute('open');
  };
});
afterAll(() => {
  HTMLDialogElement.prototype.showModal = originalShow;
  HTMLDialogElement.prototype.close = originalClose;
});
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  sessionStorage.clear();
  localStorage.clear();
  window.history.replaceState(null, '', '/');
});

describe('최초 이메일 구독 안내', () => {
  it('서버 이메일과 확정 문구만 표시하고 수신 주소 입력은 제공하지 않는다', async () => {
    const fetchMock = installApi();
    const view = renderPrompt();
    expect(await view.findByRole('dialog', { name: /내 투자에 중요한 변화/ })).toBeTruthy();
    expect(view.getByText(subscription.email)).toBeTruthy();
    expect(view.getByText('내 자산의 변동')).toBeTruthy();
    expect(view.getByText('보유 종목의 가격 변화')).toBeTruthy();
    expect(view.getByText('새로 등록된 관련 영상과 글')).toBeTruthy();
    expect(view.queryByText(/분석 근거|투자 일정/)).toBeNull();
    expect(view.queryByRole('textbox')).toBeNull();
    expect(view.queryByText(/하루 최대|예시 계정|구독은 선택/)).toBeNull();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1]?.credentials).toBe('include');
  });

  it.each(['DECLINED', 'SUBSCRIBED', 'UNSUBSCRIBED'] as const)(
    '%s 회원에게는 다시 표시하지 않는다',
    async (status) => {
      const fetchMock = installApi({ ...subscription, status, promptRequired: false });
      const view = renderPrompt();
      await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(1));
      expect(view.queryByRole('dialog')).toBeNull();
    },
  );

  it.each([
    { ...account, isLoggedIn: false, memberId: null },
    { ...account, loading: true },
  ])('비로그인 또는 인증 확인 중에는 요청하지 않는다', async (auth) => {
    const fetchMock = installApi();
    const view = renderPrompt(auth);
    await act(async () => {});
    expect(fetchMock).not.toHaveBeenCalled();
    expect(view.queryByRole('dialog')).toBeNull();
  });

  it.each(['/admin/news', '/login', '/welcome', '/mypage'])(
    '%s에서는 사용자 구독 안내를 띄우지 않는다',
    async (path) => {
      const fetchMock = installApi();
      const view = renderPrompt(account, path);
      await act(async () => {});
      expect(fetchMock).not.toHaveBeenCalled();
      expect(view.queryByRole('dialog')).toBeNull();
    },
  );

  it('구독은 CSRF와 본문 없는 PUT을 사용하고 처리 중 중복 선택을 막는다', async () => {
    let finish!: (value: Response) => void;
    const fetchMock = installApi();
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (init?.method === 'PUT')
        return new Promise<Response>((resolve) => {
          finish = resolve;
        });
      return json(subscription);
    });
    const view = renderPrompt();
    const button = await view.findByRole('button', { name: '이메일 알림 구독하기' });
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() =>
      expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'PUT')).toHaveLength(1),
    );
    const [, init] = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')!;
    expect(init?.body).toBeUndefined();
    expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
    expect(view.getByRole('button', { name: '지금은 괜찮아요' })).toHaveProperty('disabled', true);
    expect(view.getByRole('button', { name: '구독 안내 닫기' })).toHaveProperty('disabled', true);
    await act(async () =>
      finish(
        json({
          ...subscription,
          status: 'SUBSCRIBED',
          promptRequired: false,
          subscribedAt: '2026-10-28T00:00:00Z',
        }),
      ),
    );
    expect(await view.findByRole('heading', { name: '이메일 알림 구독 완료' })).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '확인' }));
    expect(view.queryByRole('dialog')).toBeNull();
    expect(view.getByTestId('path').textContent).toBe('/news');
  });

  it.each(['지금은 괜찮아요', '구독 안내 닫기', 'Escape'])(
    '%s로 건너뛰면 POST 후 추가 팝업 없이 홈으로 이동한다',
    async (action) => {
      const fetchMock = installApi();
      const view = renderPrompt();
      const dialog = await view.findByRole('dialog');
      if (action === 'Escape') fireEvent(dialog, new Event('cancel', { cancelable: true }));
      else fireEvent.click(view.getByRole('button', { name: action }));
      await waitFor(() => expect(view.getByTestId('path').textContent).toBe('/'));
      expect(view.queryByRole('dialog')).toBeNull();
      expect(view.queryByText('이메일 알림 구독 완료')).toBeNull();
      const [url, init] = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!;
      expect(String(url)).toContain('/email-subscription/dismiss');
      expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
      expect(init?.body).toBeUndefined();
    },
  );

  it('선택 저장이 실패하면 팝업과 현재 경로를 유지하고 재시도한다', async () => {
    const fetchMock = installApi();
    let fail = true;
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (init?.method === 'POST')
        return fail
          ? json({}, 503)
          : json({ ...subscription, status: 'DECLINED', promptRequired: false });
      return json(subscription);
    });
    const view = renderPrompt();
    fireEvent.click(await view.findByRole('button', { name: '지금은 괜찮아요' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    expect(view.getByTestId('path').textContent).toBe('/news');
    expect(view.getByRole('dialog')).toBeTruthy();
    fail = false;
    fireEvent.click(view.getByRole('button', { name: '지금은 괜찮아요' }));
    await waitFor(() => expect(view.getByTestId('path').textContent).toBe('/'));
  });

  it('조회 실패는 현재 화면을 막지 않고 구독 동의를 추측하지 않는다', async () => {
    const fetchMock = installApi();
    fetchMock.mockResolvedValue(json({}, 503));
    const view = renderPrompt();
    await act(async () => {});
    expect(view.queryByRole('dialog')).toBeNull();
    expect(view.getByTestId('path').textContent).toBe('/news');
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
  });

  it('계정 변경 후 도착한 이전 계정의 구독 결과는 새 계정에 반영하지 않는다', async () => {
    let finish!: (value: Response) => void;
    let currentEmail = subscription.email;
    const fetchMock = installApi();
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (init?.method === 'PUT')
        return new Promise<Response>((resolve) => {
          finish = resolve;
        });
      return json({ ...subscription, email: currentEmail });
    });
    const content = (auth: AuthContextValue) => (
      <MemoryRouter>
        <AuthContext.Provider value={auth}>
          <EmailSubscriptionPrompt />
        </AuthContext.Provider>
      </MemoryRouter>
    );
    const view = render(content(account));
    fireEvent.click(await view.findByRole('button', { name: '이메일 알림 구독하기' }));
    await waitFor(() => expect(finish).toBeTypeOf('function'));
    currentEmail = 'another-member@example.invalid';
    view.rerender(content({ ...account, memberId: 2 }));
    expect(await view.findByText(currentEmail)).toBeTruthy();
    await act(async () =>
      finish(json({ ...subscription, status: 'SUBSCRIBED', promptRequired: false })),
    );
    expect(view.queryByText(subscription.email)).toBeNull();
    expect(view.queryByRole('heading', { name: '이메일 알림 구독 완료' })).toBeNull();
    expect(view.getByRole('button', { name: '이메일 알림 구독하기' })).toHaveProperty(
      'disabled',
      false,
    );
  });

  it('로그인 복귀 경로를 보존하고 건너뛴 후에는 실제 첫 화면을 표시한다', async () => {
    const fetchMock = installApi();
    fetchMock.mockImplementation(async (input) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path === '/api/users/me')
        return json({ id: 1, email: subscription.email, nickname: '회원', role: 'USER' });
      if (path === '/api/users/me/portfolio')
        return json({ holdings: [], totalRealizedProfitLoss: 0 });
      if (path === '/api/auth/csrf')
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (path.endsWith('/dismiss'))
        return json({ ...subscription, status: 'DECLINED', promptRequired: false });
      if (path.endsWith('/email-subscription')) return json(subscription);
      if (path === '/api/league/weeks') return json({ weeks: [] });
      return json({}, 404);
    });
    sessionStorage.setItem('daynomy:post-login-path', '/league');
    const view = render(<App />);
    await waitFor(() => expect(window.location.pathname).toBe('/league'));
    fireEvent.click(await view.findByRole('button', { name: '지금은 괜찮아요' }));
    expect(await view.findByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    expect(window.location.pathname).toBe('/');
    expect(view.queryByRole('dialog')).toBeNull();
    expect(sessionStorage.getItem('daynomy:post-login-path')).toBeNull();
  });
});

describe('이메일 구독 응답 검증', () => {
  it.each([
    { ...subscription, status: 'OTHER' },
    { ...subscription, status: ['SUBSCRIBED'], promptRequired: false },
    { ...subscription, email: '' },
    { ...subscription, promptRequired: 'true' },
    { ...subscription, status: 'SUBSCRIBED' },
    { ...subscription, subscribedAt: 'invalid' },
  ])('잘못된 응답은 구독 상태로 사용하지 않는다: %j', async (value) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(value)),
    );
    await expect(getEmailSubscription()).rejects.toThrow('이메일 구독 정보를 확인할 수 없습니다.');
  });
});
