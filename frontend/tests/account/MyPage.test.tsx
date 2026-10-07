/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../src/auth/AuthProvider';
import { useAuth } from '../../src/hooks/useLoginStatus';
import { ServiceHeader } from '../../src/features/navigation/ServiceHeader';
import { MyPage } from '../../src/features/account/MyPage';
import { getMyProfile, updateMyProfile } from '../../src/features/pages/api';
import App from '../../src/App';

const account = {
  id: 42,
  email: 'account@example.invalid',
  name: 'Google 계정 이름',
  nickname: '테스트회원',
  role: 'USER',
};
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
function AuthStatus() {
  const { isLoggedIn } = useAuth();
  return <p>{isLoggedIn ? '로그인 상태' : '로그아웃 완료'}</p>;
}
function renderAccount() {
  return render(
    <MemoryRouter initialEntries={['/mypage']}>
      <AuthProvider>
        <ServiceHeader />
        <Routes>
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/news" element={<AuthStatus />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
  sessionStorage.clear();
});

describe('실제 인증을 사용하는 마이페이지', () => {
  it('프로필 버튼이 마이페이지로 연결되고 계정·공유 관리 링크를 제공한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(account)),
    );
    const view = renderAccount();
    expect(await view.findByText(account.email)).toBeTruthy();
    expect(view.getByText(account.name)).toBeTruthy();
    expect(view.getAllByText('비공개')).toHaveLength(2);
    expect(view.getByText('공개용')).toBeTruthy();
    expect(view.queryByRole('link', { name: '초보 돈 관리' })).toBeNull();
    expect(view.getByRole('link', { name: '마이페이지' }).getAttribute('href')).toBe('/mypage');
    expect(view.getByRole('link', { name: /내 포트폴리오/ }).getAttribute('href')).toBe('/');
    expect(view.getByRole('link', { name: /공유 포트폴리오/ }).getAttribute('href')).toBe(
      '/league/portfolio',
    );
  });

  it('닉네임 저장은 CSRF를 사용하고 헤더·재방문에 반영하며 저장 중 중복 요청을 막는다', async () => {
    let current = { ...account };
    let finishSave: (response: Response) => void = () => {};
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path === '/api/auth/csrf')
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (init?.method === 'PATCH') {
        expect(path).toBe('/api/users/me');
        expect(init.credentials).toBe('include');
        expect(new Headers(init.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
        expect(JSON.parse(String(init.body))).toEqual({ nickname: '새서비스닉네임' });
        current = { ...current, nickname: '새서비스닉네임' };
        return new Promise<Response>((resolve) => {
          finishSave = resolve;
        });
      }
      return json(current);
    });
    vi.stubGlobal('fetch', fetchMock);
    const view = renderAccount();
    await view.findByText(account.email);
    fireEvent.click(view.getByRole('button', { name: '닉네임 변경' }));
    fireEvent.change(view.getByLabelText('새 닉네임'), { target: { value: '  새서비스닉네임  ' } });
    fireEvent.click(view.getByRole('button', { name: '닉네임 저장' }));
    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PATCH')).toBe(true),
    );
    expect(view.getByLabelText('새 닉네임')).toHaveProperty('disabled', true);
    expect(view.getByRole('button', { name: '취소' })).toHaveProperty('disabled', true);
    expect(view.getByRole('button', { name: '로그아웃' })).toHaveProperty('disabled', true);
    fireEvent.click(view.getByRole('button', { name: '저장 중…' }));
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'PATCH')).toHaveLength(1);
    finishSave(json(current));
    expect(await view.findByText(current.nickname)).toBeTruthy();
    expect(view.getByRole('link', { name: '마이페이지' }).textContent).toBe('새');
    expect(view.getByRole('status').textContent).toBe('닉네임을 변경했어요.');
    view.unmount();
    const revisit = renderAccount();
    expect(await revisit.findByText(current.nickname)).toBeTruthy();
  });

  it.each([
    [409, '이미 사용 중인 닉네임입니다.'],
    [503, '일시적인 서버 오류입니다.'],
  ])(
    '닉네임 저장 실패(%s) 시 기존 이름과 초안을 유지하고 수정·재시도한다',
    async (status, message) => {
      let fail = true;
      vi.stubGlobal(
        'fetch',
        vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
          const path = new URL(String(input), window.location.origin).pathname;
          if (path === '/api/auth/csrf')
            return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
          if (init?.method === 'PATCH')
            return fail
              ? json({ code: 'NICKNAME_ALREADY_EXISTS', message }, status)
              : json({ ...account, nickname: JSON.parse(String(init.body)).nickname });
          return json(account);
        }),
      );
      const view = renderAccount();
      await view.findByText(account.email);
      fireEvent.click(view.getByRole('button', { name: '닉네임 변경' }));
      fireEvent.change(view.getByLabelText('새 닉네임'), { target: { value: '중복닉네임' } });
      fireEvent.click(view.getByRole('button', { name: '닉네임 저장' }));
      expect((await view.findByRole('alert')).textContent).toBe(message);
      expect(view.getByLabelText('새 닉네임')).toHaveProperty('value', '중복닉네임');
      expect(view.getByRole('link', { name: '마이페이지' }).textContent).toBe('테');
      fail = false;
      fireEvent.change(view.getByLabelText('새 닉네임'), { target: { value: '다른닉네임' } });
      fireEvent.click(view.getByRole('button', { name: '닉네임 저장' }));
      expect(await view.findByText('다른닉네임')).toBeTruthy();
      expect(view.queryByRole('alert')).toBeNull();
    },
  );

  it('빈 닉네임과 20자 초과 입력을 전송하지 않고 취소 시 기존 이름을 유지한다', async () => {
    const fetchMock = vi.fn(async (_input: string | URL | Request) => json(account));
    vi.stubGlobal('fetch', fetchMock);
    const view = renderAccount();
    await view.findByText(account.email);
    fireEvent.click(view.getByRole('button', { name: '닉네임 변경' }));
    for (const value of ['  ', '가'.repeat(21)]) {
      fireEvent.change(view.getByLabelText('새 닉네임'), { target: { value } });
      fireEvent.click(view.getByRole('button', { name: '닉네임 저장' }));
      expect(view.getByRole('alert').textContent).toContain('1~20자');
    }
    expect(fetchMock.mock.calls.every(([input]) => String(input).includes('/api/users/me'))).toBe(
      true,
    );
    fireEvent.click(view.getByRole('button', { name: '취소' }));
    expect(view.getByText(account.nickname)).toBeTruthy();
    expect(view.queryByLabelText('새 닉네임')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '닉네임 변경' }));
    expect(view.getByLabelText('새 닉네임')).toHaveProperty('value', account.nickname);
  });

  it('저장 응답의 계정 구조도 런타임 검증한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: string | URL | Request) =>
        String(input).includes('/api/auth/csrf')
          ? json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' })
          : json({ ...account, role: 'OWNER' }),
      ),
    );
    await expect(updateMyProfile('새닉네임')).rejects.toThrow('계정 정보를 확인할 수 없습니다');
  });

  it('기존 계정의 이름을 닉네임으로 추측하지 않고 잘못된 이름 응답도 거부한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json({ ...account, name: null })),
    );
    const view = renderAccount();
    expect(await view.findByText('다시 Google 로그인하면 이름을 불러옵니다.')).toBeTruthy();
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json({ ...account, name: 123 })),
    );
    await expect(getMyProfile()).rejects.toThrow('계정 정보를 확인할 수 없습니다');
  });

  it('CSRF를 포함한 서버 로그아웃 성공 후 인증 상태와 화면을 전환한다', async () => {
    const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
      const path = new URL(String(input), window.location.origin).pathname;
      if (path === '/api/auth/csrf')
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (path === '/api/auth/logout') {
        expect(init?.method).toBe('POST');
        expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
        return new Response(null, { status: 204 });
      }
      return json(account);
    });
    vi.stubGlobal('fetch', fetchMock);
    const view = renderAccount();
    await view.findByText(account.email);
    fireEvent.click(view.getByRole('button', { name: '로그아웃' }));
    expect(await view.findByText('로그아웃 완료')).toBeTruthy();
    expect(view.queryByRole('link', { name: '마이페이지' })).toBeNull();
    expect(view.getByRole('link', { name: '로그인' })).toBeTruthy();
  });

  it('로그아웃 실패 시 계정 상태를 유지하고 재시도할 수 있다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: string | URL | Request) => {
        const path = new URL(String(input), window.location.origin).pathname;
        if (path === '/api/auth/csrf')
          return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
        if (path === '/api/auth/logout') return json({ message: '서버 연결 실패' }, 503);
        return json(account);
      }),
    );
    const view = renderAccount();
    await view.findByText(account.email);
    fireEvent.click(view.getByRole('button', { name: '로그아웃' }));
    expect((await view.findByRole('alert')).textContent).toContain('로그아웃하지 못했습니다');
    expect(view.getByRole('link', { name: '마이페이지' })).toBeTruthy();
    expect(view.getByRole('button', { name: '로그아웃' })).toHaveProperty('disabled', false);
    expect(view.queryByText('로그아웃 완료')).toBeNull();
  });

  it('보호된 실제 마이페이지에서 로그아웃하면 로그인 화면으로 튕기지 않고 공개 이슈로 이동한다', async () => {
    vi.stubEnv('DEV', false);
    vi.stubGlobal('scrollTo', vi.fn());
    window.history.replaceState({}, '', '/mypage');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: string | URL | Request) => {
        const path = new URL(String(input), window.location.origin).pathname;
        if (path === '/api/auth/csrf')
          return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
        if (path === '/api/auth/logout') return new Response(null, { status: 204 });
        if (path === '/api/users/me') return json(account);
        return json({ stocks: [], prices: [] });
      }),
    );
    const view = render(<App />);
    await view.findByText(account.email);
    fireEvent.click(view.getByRole('button', { name: '로그아웃' }));
    await view.findByRole('link', { name: '로그인' });
    await waitFor(() => expect(window.location.pathname).toBe('/news'));
    expect(view.queryByRole('heading', { name: 'DAYNOMY 로그인' })).toBeNull();
  });

  it('계정 조회 오류를 보여주고 다시 불러오기를 지원한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json({ message: '계정 조회 실패' }, 503)),
    );
    const view = renderAccount();
    expect((await view.findByRole('alert')).textContent).toContain('계정 조회 실패');
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(account)),
    );
    fireEvent.click(view.getByRole('button', { name: '계정 정보 다시 불러오기' }));
    expect(await view.findByText(account.email)).toBeTruthy();
    await waitFor(() => expect(view.queryByRole('alert')).toBeNull());
  });

  it('잘못된 계정 응답을 로그인된 사용자로 간주하지 않는다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json({ ...account, role: 'OWNER' })),
    );
    await expect(getMyProfile()).rejects.toThrow('계정 정보를 확인할 수 없습니다');
    const view = renderAccount();
    expect(await view.findByRole('link', { name: '로그인' })).toBeTruthy();
    expect(view.queryByRole('link', { name: '마이페이지' })).toBeNull();
  });
});
