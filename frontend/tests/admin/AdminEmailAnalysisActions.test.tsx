/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminEmailAnalysisDetailPage } from '../../src/features/admin/AdminEmailAnalysesPage';
import {
  getAdminEmailCorrectionReviewIds,
  invalidateAdminEmailAnalysis,
} from '../../src/features/admin/emailAnalysisApi';
import { emailAnalysisDetail } from './emailAnalysisFixtures';

const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const result = { analysisId: 77, invalidatedAt: '2026-10-28T00:00:00Z', cancelledDeliveries: 2 };
const ids = Array.from(
  { length: 50 },
  (_, index) => `00000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`,
);
function installApi(
  options: {
    invalidated?: boolean;
    count?: number;
    syncFails?: boolean;
    postFails?: boolean;
    review?: string[];
  } = {},
) {
  let invalidated = !!options.invalidated;
  const mock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit): Promise<Response> => {
    const url = new URL(String(input), window.location.origin);
    if (url.pathname === '/api/auth/csrf')
      return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
    if (url.pathname.endsWith('/invalidate')) {
      if (options.postFails) return json({}, 503);
      invalidated = true;
      return json({ ...result, cancelledDeliveries: options.count ?? 2 });
    }
    if (url.pathname.endsWith('/correction-review-deliveries'))
      return json(url.searchParams.get('page') === '0' ? (options.review ?? []) : []);
    if (invalidated && options.syncFails) return json({}, 503);
    return json(
      invalidated
        ? {
            ...emailAnalysisDetail,
            analysis: {
              ...emailAnalysisDetail.analysis,
              status: 'INVALIDATED',
              invalidatedAt: result.invalidatedAt,
            },
            invalidationReason: '서버에 최초 저장된 사유',
          }
        : emailAnalysisDetail,
    );
  });
  vi.stubGlobal('fetch', mock);
  return mock;
}
function renderDetail() {
  const router = createMemoryRouter(
    [{ path: '/admin/email-analyses/:analysisId', element: <AdminEmailAnalysisDetailPage /> }],
    { initialEntries: ['/admin/email-analyses/77'] },
  );
  return { ...render(<RouterProvider router={router} />), router };
}
async function openForm(
  view: ReturnType<typeof renderDetail>,
  reason = '  원문 수치가 잘못 반영됨  ',
) {
  fireEvent.click(await view.findByRole('button', { name: '분석 무효화' }));
  fireEvent.change(view.getByLabelText('무효화 사유'), { target: { value: reason } });
  return view.getByRole('button', { name: '무효화 확정' });
}
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('관리자 분석 무효화', () => {
  it('공백 사유는 전송하지 않고 사유 입력에 초점을 유지하며 취소 시 변경하지 않는다', async () => {
    const mock = installApi();
    const view = renderDetail();
    fireEvent.click(await openForm(view, '   '));
    expect((await view.findByRole('alert')).textContent).toContain('1~500자');
    expect(document.activeElement).toBe(view.getByLabelText('무효화 사유'));
    fireEvent.click(view.getByRole('button', { name: '취소' }));
    expect(view.queryByLabelText('무효화 사유')).toBeNull();
    expect(mock.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });
  it.each([0, 2])(
    '성공 시 취소 건수 %s와 서버의 최초 사유를 표시하고 재요청을 막는다',
    async (count) => {
      const mock = installApi({ count });
      const view = renderDetail();
      fireEvent.click(await openForm(view));
      expect(await view.findByText('서버에 최초 저장된 사유')).toBeTruthy();
      expect(
        view.getByText(new RegExp(`이번 요청에서 취소된 대기·재시도 메일은 ${count}건`)),
      ).toBeTruthy();
      expect(view.getByText('무효화')).toBeTruthy();
      expect(view.queryByRole('button', { name: '무효화 확정' })).toBeNull();
      const posts = mock.mock.calls.filter(([, init]) => init?.method === 'POST');
      expect(posts).toHaveLength(1);
      expect(posts[0][1]?.body).toBe(JSON.stringify({ reason: '원문 수치가 잘못 반영됨' }));
      expect(new Headers(posts[0][1]?.headers).get('X-CSRF-TOKEN')).toBe('test-csrf');
      expect(posts[0][1]?.credentials).toBe('include');
    },
  );
  it('이미 무효화된 분석에는 재무효화 입력을 표시하지 않고 정정 검토 조회는 제공한다', async () => {
    installApi({ invalidated: true, review: [ids[0]] });
    const view = renderDetail();
    expect(
      await view.findByText(
        '이미 무효화된 분석입니다. 최초 사유와 시각은 분석 정보에서 확인할 수 있습니다.',
      ),
    ).toBeTruthy();
    expect(await view.findByText(ids[0])).toBeTruthy();
    expect(view.queryByRole('button', { name: '분석 무효화' })).toBeNull();
  });
  it('무효화 성공 뒤 상세 재조회 실패를 요청 실패와 구분하고 재조회만 재시도한다', async () => {
    const options = { syncFails: true };
    const mock = installApi(options);
    const view = renderDetail();
    fireEvent.click(await openForm(view));
    expect(
      await view.findByText('무효화는 완료됐지만 최신 분석 정보를 불러오지 못했어요.'),
    ).toBeTruthy();
    expect(view.getByText('무효화')).toBeTruthy();
    expect(view.getByText(/이번 요청에서 취소된 대기·재시도 메일은 2건/)).toBeTruthy();
    options.syncFails = false;
    fireEvent.click(view.getByRole('button', { name: '분석 정보 다시 확인' }));
    expect(await view.findByText('서버에 최초 저장된 사유')).toBeTruthy();
    expect(view.queryByText('무효화는 완료됐지만 최신 분석 정보를 불러오지 못했어요.')).toBeNull();
    expect(mock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
  });
  it('요청 실패 시 사유를 보존하고 성공 건수를 표시하지 않으며 재시도할 수 있다', async () => {
    const options = { postFails: true };
    installApi(options);
    const view = renderDetail();
    fireEvent.click(await openForm(view));
    expect(
      await view.findByText('무효화 요청 결과를 확인하지 못했어요. 다시 시도해 주세요.'),
    ).toBeTruthy();
    expect((view.getByLabelText('무효화 사유') as HTMLTextAreaElement).value).toBe(
      '  원문 수치가 잘못 반영됨  ',
    );
    expect(view.queryByText(/이번 요청에서 취소된/)).toBeNull();
    options.postFails = false;
    fireEvent.click(view.getByRole('button', { name: '무효화 확정' }));
    expect(await view.findByText('서버에 최초 저장된 사유')).toBeTruthy();
  });
  it('처리 중 중복 확정과 화면 이탈 후 이전 결과 반영을 막는다', async () => {
    const mock = installApi();
    const base = mock.getMockImplementation()!;
    let finish!: (value: Response) => void;
    mock.mockImplementation((input, init) =>
      String(input).endsWith('/invalidate')
        ? new Promise<Response>((resolve) => {
            finish = resolve;
          })
        : base(input, init),
    );
    const view = renderDetail();
    const button = await openForm(view);
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() => expect(finish).toBeDefined());
    expect(mock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(1);
    expect(
      (view.getByRole('button', { name: '무효화 처리 중…' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    const request = mock.mock.calls.find(([, init]) => init?.method === 'POST')!;
    await act(async () => {
      await view.router.navigate('/admin/email-analyses/78');
    });
    expect(request[1]?.signal?.aborted).toBe(true);
    await act(async () => {
      finish(json(result));
    });
    expect(view.queryByText(/이번 요청에서 취소된/)).toBeNull();
  });
});

describe('관리자 정정 검토 대상', () => {
  it('페이지당 ID만 표시하고 다음 빈 페이지에서 이전으로 돌아갈 수 있다', async () => {
    const mock = installApi({ review: ids });
    const view = renderDetail();
    const list = await view.findByRole('list', { name: '정정 검토 대상 발송 ID' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(50);
    expect(view.getByText(/수신함 도착이나 정정 필요 여부가 확정된 목록은 아닙니다/)).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '다음 정정 검토 페이지' }));
    expect(
      await view.findByText('이 페이지에는 조회된 대상이 없습니다. 이전 페이지를 확인해 주세요.'),
    ).toBeTruthy();
    expect(
      (view.getByRole('button', { name: '다음 정정 검토 페이지' }) as HTMLButtonElement).disabled,
    ).toBe(true);
    fireEvent.click(view.getByRole('button', { name: '이전 정정 검토 페이지' }));
    expect(await view.findByText(ids[0])).toBeTruthy();
    const queries = mock.mock.calls
      .filter(([url]) => String(url).includes('correction-review-deliveries'))
      .map(([url]) => new URL(String(url), window.location.origin).search);
    expect(queries).toEqual(['?page=0&size=50', '?page=1&size=50', '?page=0&size=50']);
  });
  it('조회 실패는 빈 결과와 구분하고 다시 조회할 수 있다', async () => {
    const mock = installApi();
    const base = mock.getMockImplementation()!;
    let fail = true;
    mock.mockImplementation((input, init) =>
      fail && String(input).includes('correction-review-deliveries')
        ? Promise.resolve(json({}, 503))
        : base(input, init),
    );
    const view = renderDetail();
    expect(
      await view.findByText('정정 검토 대상을 불러오지 못했어요. 다시 조회해 주세요.'),
    ).toBeTruthy();
    expect(view.queryByText('조회된 정정 검토 대상이 없습니다.')).toBeNull();
    fail = false;
    fireEvent.click(view.getByRole('button', { name: '다시 조회' }));
    expect(await view.findByText('조회된 정정 검토 대상이 없습니다.')).toBeTruthy();
  });
});

describe('무효화·정정 검토 API 검증', () => {
  it.each(['', ' ', 'a'.repeat(501)])(
    '유효하지 않은 사유는 API 호출 전에 거부한다',
    async (reason) => {
      const mock = installApi();
      await expect(invalidateAdminEmailAnalysis(77, reason)).rejects.toThrow('1~500자');
      expect(mock).not.toHaveBeenCalled();
    },
  );
  it.each([
    { ...result, analysisId: 78 },
    { ...result, invalidatedAt: null },
    { ...result, cancelledDeliveries: -1 },
    { ...result, cancelledDeliveries: '2' },
  ])('잘못된 무효화 응답은 성공 처리하지 않는다: %j', async (value) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input) =>
        json(
          String(input).endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'test' } : value,
        ),
      ),
    );
    await expect(invalidateAdminEmailAnalysis(77, '사유')).rejects.toThrow('무효화 응답 형식');
  });
  it.each([{}, ['not-a-uuid'], [ids[0], ids[0]], [...ids, '00000000-0000-0000-0000-000000000099']])(
    '잘못된 발송 ID 목록은 거부한다: %j',
    async (value) => {
      vi.stubGlobal(
        'fetch',
        vi.fn(async () => json(value)),
      );
      await expect(getAdminEmailCorrectionReviewIds(77, 0)).rejects.toThrow(
        '정정 검토 대상 응답 형식',
      );
    },
  );
});
