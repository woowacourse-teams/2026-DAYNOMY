/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EmailSubscriptionSettings } from '../../src/features/email-subscription/EmailSubscriptionSettings';
import type { EmailSubscriptionResponse } from '../../src/features/email-subscription/api';
import App from '../../src/App';

const email = 'member@example.invalid';
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
function response(status: EmailSubscriptionResponse['status']): EmailSubscriptionResponse {
  return {
    status,
    email,
    promptRequired: status === 'UNDECIDED',
    subscribedAt:
      status === 'SUBSCRIBED' || status === 'UNSUBSCRIBED' ? '2026-10-28T00:00:00Z' : null,
  };
}
function installApi(initial: EmailSubscriptionResponse['status'] = 'SUBSCRIBED') {
  let current = response(initial);
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = new URL(String(input), window.location.origin).pathname;
    if (path === '/api/users/me')
      return json({ id: 1, email, name: '회원 이름', nickname: '회원', role: 'USER' });
    if (path === '/api/auth/csrf') return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
    if (path === '/api/users/me/email-subscription') {
      if (init?.method === 'PUT') current = response('SUBSCRIBED');
      if (init?.method === 'DELETE') current = response('UNSUBSCRIBED');
      return json(current);
    }
    if (path === '/api/users/me/portfolio')
      return json({ holdings: [], totalRealizedProfitLoss: 0 });
    return json({}, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState(null, '', '/');
});

describe('마이페이지 이메일 구독 설정', () => {
  it.each([
    ['UNDECIDED', '미구독', '구독하기'],
    ['DECLINED', '미구독', '구독하기'],
    ['SUBSCRIBED', '구독 중', '구독 해지'],
    ['UNSUBSCRIBED', '구독 해지', '재구독'],
  ] as const)('%s 상태와 서버 이메일을 표시한다', async (status, label, button) => {
    installApi(status);
    const view = render(<EmailSubscriptionSettings />);
    expect(await view.findByRole('heading', { name: label })).toBeTruthy();
    expect(view.getByText(email)).toBeTruthy();
    expect(
      view.getByText(
        '내 자산과 보유 종목의 가격 변화, 새 관련 자료와 분석 업데이트 알림을 로그인한 Google 이메일로 받아보세요.',
      ),
    ).toBeTruthy();
    expect(view.queryByText(/소식과 일정/)).toBeNull();
    expect(view.getByRole('button', { name: button })).toBeTruthy();
    expect(view.queryByRole('textbox')).toBeNull();
  });

  it('해지와 재구독은 CSRF와 본문 없는 DELETE·PUT으로 저장하고 서버 결과를 표시한다', async () => {
    const fetchMock = installApi();
    const view = render(<EmailSubscriptionSettings />);
    fireEvent.click(await view.findByRole('button', { name: '구독 해지' }));
    expect(await view.findByRole('button', { name: '재구독' })).toBeTruthy();
    expect(view.getByRole('status').textContent).toBe('이메일 알림을 해지했어요.');
    fireEvent.click(view.getByRole('button', { name: '재구독' }));
    expect(await view.findByRole('button', { name: '구독 해지' })).toBeTruthy();
    expect(view.getByRole('status').textContent).toBe('이메일 알림을 구독했어요.');
    const writes = fetchMock.mock.calls.filter(([, init]) =>
      ['DELETE', 'PUT'].includes(init?.method ?? ''),
    );
    expect(writes.map(([, init]) => init?.method)).toEqual(['DELETE', 'PUT']);
    for (const [, init] of writes) {
      expect(init?.body).toBeUndefined();
      expect(init?.credentials).toBe('include');
      expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
    }
  });

  it('서버 응답 전에는 기존 상태를 유지하고 중복 변경을 막는다', async () => {
    let finish!: (value: Response) => void;
    const fetchMock = installApi();
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (init?.method === 'DELETE')
        return new Promise<Response>((resolve) => {
          finish = resolve;
        });
      return json(response('SUBSCRIBED'));
    });
    const view = render(<EmailSubscriptionSettings />);
    const button = await view.findByRole('button', { name: '구독 해지' });
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() =>
      expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'DELETE')).toHaveLength(1),
    );
    expect(view.getByRole('heading', { name: '구독 중' })).toBeTruthy();
    expect(view.getByRole('button', { name: '저장 중…' })).toHaveProperty('disabled', true);
    await act(async () => finish(json(response('UNSUBSCRIBED'))));
    expect(view.getByRole('button', { name: '재구독' })).toBeTruthy();
  });

  it('저장 실패 시 상태를 유지하고 동일한 해지 요청을 재시도한다', async () => {
    const fetchMock = installApi();
    let fail = true;
    fetchMock.mockImplementation(async (input, init) => {
      if (String(input).endsWith('/api/auth/csrf'))
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (init?.method === 'DELETE') return fail ? json({}, 503) : json(response('UNSUBSCRIBED'));
      return json(response('SUBSCRIBED'));
    });
    const view = render(<EmailSubscriptionSettings />);
    fireEvent.click(await view.findByRole('button', { name: '구독 해지' }));
    expect(await view.findByRole('alert')).toBeTruthy();
    expect(view.getByRole('heading', { name: '구독 중' })).toBeTruthy();
    expect(view.queryByRole('button', { name: '재구독' })).toBeNull();
    fail = false;
    fireEvent.click(view.getByRole('button', { name: '구독 해지' }));
    expect(await view.findByRole('button', { name: '재구독' })).toBeTruthy();
    expect(view.queryByRole('alert')).toBeNull();
  });

  it('조회 중에는 변경 버튼을 표시하지 않고 조회 실패 시 다시 불러온다', async () => {
    const fetchMock = installApi();
    fetchMock.mockResolvedValueOnce(json({}, 503));
    const view = render(<EmailSubscriptionSettings />);
    expect(view.getByRole('status').textContent).toContain('불러오고 있습니다');
    expect(view.queryByRole('button')).toBeNull();
    expect(await view.findByRole('alert')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '구독 정보 다시 불러오기' }));
    expect(await view.findByRole('button', { name: '구독 해지' })).toBeTruthy();
    expect(view.queryByRole('alert')).toBeNull();
  });

  it('계정 작업 중에는 구독 변경 요청을 보내지 않는다', async () => {
    const fetchMock = installApi();
    const view = render(<EmailSubscriptionSettings disabled />);
    fireEvent.click(await view.findByRole('button', { name: '구독 해지' }));
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false);
  });

  it('화면을 떠나면 조회 요청을 취소한다', () => {
    const fetchMock = installApi();
    fetchMock.mockImplementation(() => new Promise<Response>(() => {}));
    const view = render(<EmailSubscriptionSettings />);
    const signal = fetchMock.mock.calls[0][1]?.signal;
    view.unmount();
    expect(signal?.aborted).toBe(true);
  });

  it('실제 마이페이지에서 최초 팝업 없이 구독하고 홈에 돌아와도 팝업을 다시 띄우지 않는다', async () => {
    installApi('UNDECIDED');
    window.history.replaceState(null, '', '/mypage');
    const view = render(<App />);
    const settings = within(await view.findByRole('region', { name: '이메일 알림' }));
    fireEvent.click(await settings.findByRole('button', { name: '구독하기' }));
    expect(await settings.findByRole('heading', { name: '구독 중' })).toBeTruthy();
    expect(view.queryByRole('dialog')).toBeNull();
    expect(view.getByRole('button', { name: '닉네임 변경' })).toBeTruthy();
    fireEvent.click(view.getByRole('link', { name: 'DAYNOMY 홈' }));
    expect(await view.findByRole('heading', { name: '내 포트폴리오' })).toBeTruthy();
    expect(view.queryByRole('dialog')).toBeNull();
  });
});
