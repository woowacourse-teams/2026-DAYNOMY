/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  AdminEmailAnalysesPage,
  AdminEmailAnalysisDetailPage,
} from '../../src/features/admin/AdminEmailAnalysesPage';
import {
  getAdminEmailAnalyses,
  getAdminEmailAnalysis,
} from '../../src/features/admin/emailAnalysisApi';
import { emailAnalysisDetail, emailAnalysisPage } from './emailAnalysisFixtures';
import App from '../../src/App';

const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
function installApi() {
  const mock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
    const url = new URL(String(input), window.location.origin);
    if (url.pathname === '/api/users/me')
      return json({ id: 1, email: 'admin@example.invalid', nickname: '관리자', role: 'ADMIN' });
    if (url.pathname === '/api/admin/email-analyses')
      return json(emailAnalysisPage(Number(url.searchParams.get('page') ?? '0'), 2));
    const match = url.pathname.match(/^\/api\/admin\/email-analyses\/(\d+)$/);
    if (match)
      return json({
        ...emailAnalysisDetail,
        analysis: { ...emailAnalysisDetail.analysis, id: Number(match[1]) },
      });
    return json({}, 404);
  });
  vi.stubGlobal('fetch', mock);
  return mock;
}
function renderPage(path = '/admin/email-analyses') {
  const router = createMemoryRouter(
    [
      { path: '/admin/email-analyses', element: <AdminEmailAnalysesPage /> },
      { path: '/admin/email-analyses/:analysisId', element: <AdminEmailAnalysisDetailPage /> },
    ],
    { initialEntries: [path] },
  );
  return { ...render(<RouterProvider router={router} />), router };
}
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState(null, '', '/');
});

describe('관리자 이메일 분석 목록·상세', () => {
  it('페이지·필터를 API 명세대로 전달하고 상세 왕복 시 유지한다', async () => {
    const mock = installApi();
    const view = renderPage('/admin/email-analyses?status=VERIFIED&assetId=1&page=1');
    fireEvent.click(await view.findByRole('link', { name: '삼성전자 분석 #78 상세' }));
    expect(await view.findByRole('heading', { name: '삼성전자 분석' })).toBeTruthy();
    expect(view.getByText('검증용 실적 발표 소식')).toBeTruthy();
    expect(view.getByText('This is a test quote.')).toBeTruthy();
    expect(view.getByText(/게시일: 2026-10-27/)).toBeTruthy();
    expect(view.getAllByText(/07:03/).length).toBeGreaterThan(0);
    const link = view.getAllByRole('link', {
      name: emailAnalysisDetail.content.sources[0].url!,
    })[0];
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    expect(view.queryByRole('button', { name: /무효화/ })).toBeNull();
    fireEvent.click(view.getByRole('link', { name: '목록으로 돌아가기' }));
    expect(await view.findByRole('link', { name: '삼성전자 분석 #78 상세' })).toBeTruthy();
    expect(view.router.state.location.search).toBe('?status=VERIFIED&assetId=1&page=1');
    const query = new URL(String(mock.mock.calls[0][0]), window.location.origin).searchParams;
    expect(query.get('page')).toBe('1');
    expect(query.get('size')).toBe('20');
    expect(query.get('status')).toBe('VERIFIED');
    expect(query.get('assetId')).toBe('1');
    expect(mock.mock.calls[0][1]?.credentials).toBe('include');
  });
  it('다음 페이지로 이동하고 필터 적용 시 첫 페이지로 돌아간다', async () => {
    const mock = installApi();
    const view = renderPage();
    await view.findByRole('link', { name: '삼성전자 분석 #77 상세' });
    fireEvent.click(view.getByRole('button', { name: '다음 페이지' }));
    await view.findByRole('link', { name: '삼성전자 분석 #78 상세' });
    fireEvent.change(view.getByLabelText('상태'), { target: { value: 'REJECTED' } });
    fireEvent.change(view.getByLabelText('자산 ID'), { target: { value: '2' } });
    fireEvent.submit(view.getByRole('form', { name: '분석 목록 필터' }));
    await view.findByRole('link', { name: '삼성전자 분석 #77 상세' });
    expect(view.router.state.location.search).toBe('?status=REJECTED&assetId=2');
    expect(
      new URL(String(mock.mock.calls.at(-1)![0]), window.location.origin).searchParams.get('page'),
    ).toBe('0');
    fireEvent.click(view.getByRole('button', { name: '초기화' }));
    await waitFor(() => expect(view.router.state.location.search).toBe(''));
  });
  it.each(['0', '-1', '1.5', 'abc'])('잘못된 자산 ID %s는 전송하지 않는다', async (input) => {
    const mock = installApi();
    const view = renderPage();
    await view.findByRole('link', { name: '삼성전자 분석 #77 상세' });
    const count = mock.mock.calls.length;
    fireEvent.change(view.getByLabelText('자산 ID'), { target: { value: input } });
    fireEvent.submit(view.getByRole('form', { name: '분석 목록 필터' }));
    expect(view.getByRole('alert').textContent).toContain('1 이상의 정수');
    expect(mock.mock.calls).toHaveLength(count);
  });
  it.each(['?status=OTHER', '?assetId=-1', '?page=-1'])(
    '유효하지 않은 조회 주소 %s에서는 요청하지 않는다',
    async (query) => {
      const mock = installApi();
      const view = renderPage(`/admin/email-analyses${query}`);
      expect(view.getByRole('alert')).toBeTruthy();
      await act(async () => {});
      expect(mock).not.toHaveBeenCalled();
    },
  );
  it('조회 결과가 없으면 빈 목록을 표시하고 생성이나 변경 요청을 하지 않는다', async () => {
    const mock = installApi();
    mock.mockResolvedValue(
      json({ items: [], page: 0, size: 20, totalPages: 0, totalElements: 0, hasNext: false }),
    );
    const view = renderPage();
    expect(await view.findByText('조회된 분석이 없습니다.')).toBeTruthy();
    expect(mock.mock.calls.every(([, init]) => !init?.method || init.method === 'GET')).toBe(true);
  });
  it('목록 조회 오류 후 다시 시도할 수 있다', async () => {
    const mock = installApi();
    mock.mockResolvedValueOnce(json({}, 503));
    const view = renderPage();
    await view.findByRole('alert');
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    expect(await view.findByRole('link', { name: '삼성전자 분석 #77 상세' })).toBeTruthy();
  });
  it('범위를 벗어난 페이지는 서버의 마지막 페이지로 이동한다', async () => {
    installApi();
    const view = renderPage('/admin/email-analyses?page=8');
    expect(await view.findByRole('link', { name: '삼성전자 분석 #78 상세' })).toBeTruthy();
    expect(view.router.state.location.search).toBe('?page=1');
  });
  it.each([
    [404, '분석을 찾을 수 없습니다.'],
    [403, '관리자 권한이 필요합니다.'],
  ] as const)('상세 조회 오류 %s를 구분한다', async (status, message) => {
    const mock = installApi();
    mock.mockResolvedValue(json({}, status));
    const view = renderPage('/admin/email-analyses/77');
    expect((await view.findByRole('alert')).textContent).toContain(message);
    expect(view.queryByText('검증용 실적 발표 소식')).toBeNull();
  });
  it.each(['0', 'abc'])('상세의 잘못된 ID %s는 전송하지 않는다', async (id) => {
    const mock = installApi();
    const view = renderPage(`/admin/email-analyses/${id}`);
    expect(view.getByRole('alert').textContent).toContain('올바른 분석 ID');
    await act(async () => {});
    expect(mock).not.toHaveBeenCalled();
  });
  it('검증 전 결과의 빈 내용과 위험한 링크를 통과한 근거로 표시하지 않는다', async () => {
    const mock = installApi();
    mock.mockResolvedValue(
      json({
        ...emailAnalysisDetail,
        analysis: { ...emailAnalysisDetail.analysis, status: 'PENDING' },
        content: {
          ...emailAnalysisDetail.content,
          direction: null,
          impactLevel: null,
          reason: null,
          sources: [
            {
              title: '<img src=x onerror=alert(1)>',
              url: 'javascript:alert(1)',
              publishedOn: null,
              occurredOn: null,
              excerpt: null,
              factQuote: null,
            },
          ],
        },
        verification: null,
      }),
    );
    const view = renderPage('/admin/email-analyses/77');
    expect(await view.findByText('검증 대기')).toBeTruthy();
    expect(view.getByText('저장된 원문 검증 결과가 없습니다.')).toBeTruthy();
    expect(view.queryByRole('link', { name: /javascript:/ })).toBeNull();
    expect(view.getByText('<img src=x onerror=alert(1)>')).toBeTruthy();
    expect(view.container.querySelector('img')).toBeNull();
  });
  it('무효화 상태·사유·시각을 보존해 표시한다', async () => {
    const mock = installApi();
    mock.mockResolvedValue(
      json({
        ...emailAnalysisDetail,
        analysis: {
          ...emailAnalysisDetail.analysis,
          status: 'INVALIDATED',
          invalidatedAt: '2026-10-28T00:00:00Z',
        },
        invalidationReason: '공시 정정으로 무효화했습니다.',
      }),
    );
    const view = renderPage('/admin/email-analyses/77');
    expect(await view.findByText('공시 정정으로 무효화했습니다.')).toBeTruthy();
    expect(view.getByText('무효화')).toBeTruthy();
  });
  it('늦게 도착한 이전 분석 결과가 새 상세 화면을 덮어쓰지 않는다', async () => {
    const mock = installApi();
    let finish!: (value: Response) => void;
    mock.mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    );
    const view = renderPage('/admin/email-analyses/77');
    await act(async () => {
      await view.router.navigate('/admin/email-analyses/78');
    });
    await view.findByRole('heading', { name: '삼성전자 분석' });
    await act(async () =>
      finish(
        json({
          ...emailAnalysisDetail,
          analysis: { ...emailAnalysisDetail.analysis, assetName: '이전 종목' },
        }),
      ),
    );
    expect(view.queryByRole('heading', { name: '이전 종목 분석' })).toBeNull();
  });
  it('기존 관리자 경로에서 메뉴를 표시한다', async () => {
    installApi();
    window.history.replaceState(null, '', '/admin/email-analyses');
    const view = render(<App />);
    expect(await view.findByRole('link', { name: '이메일 알림 관리' })).toHaveProperty(
      'href',
      `${window.location.origin}/admin/email-analyses`,
    );
    expect(await view.findByRole('link', { name: '삼성전자 분석 #77 상세' })).toBeTruthy();
  });
  it('일반 회원에게는 분석 API를 호출하지 않고 관리자 접근을 차단한다', async () => {
    const mock = installApi();
    mock.mockResolvedValue(
      json({ id: 1, email: 'user@example.invalid', nickname: '회원', role: 'USER' }),
    );
    window.history.replaceState(null, '', '/admin/email-analyses');
    const view = render(<App />);
    expect(await view.findByRole('heading', { name: '관리자 권한이 필요합니다.' })).toBeTruthy();
    expect(mock.mock.calls.some(([url]) => String(url).includes('/api/admin/email-analyses'))).toBe(
      false,
    );
  });
});

describe('관리자 분석 응답 검증', () => {
  it.each([
    { ...emailAnalysisPage(), page: -1 },
    { ...emailAnalysisPage(), totalElements: '1' },
    { ...emailAnalysisPage(), items: [{ ...emailAnalysisDetail.analysis, status: ['VERIFIED'] }] },
    { ...emailAnalysisPage(), items: [{ ...emailAnalysisDetail.analysis, analyzedAt: 'invalid' }] },
  ])('잘못된 목록을 거부한다: %j', async (value) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(value)),
    );
    await expect(getAdminEmailAnalyses({ page: 0 })).rejects.toThrow('분석 목록 응답 형식');
  });
  it.each([
    { ...emailAnalysisDetail, analysis: { ...emailAnalysisDetail.analysis, id: 78 } },
    {
      ...emailAnalysisDetail,
      content: { ...emailAnalysisDetail.content, direction: ['POSITIVE'] },
    },
    {
      ...emailAnalysisDetail,
      verification: {
        ...emailAnalysisDetail.verification,
        sources: [{ ...emailAnalysisDetail.verification!.sources[0], passed: 'true' }],
      },
    },
  ])('잘못된 상세를 거부한다: %j', async (value) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json(value)),
    );
    await expect(getAdminEmailAnalysis(77)).rejects.toThrow('분석 상세 응답 형식');
  });
});
