/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { AdminNewsFormPage } from '../../src/features/admin/AdminNewsFormPage';
import { AdminNewsPage } from '../../src/features/admin/AdminNewsPage';

const listItem = {
  id: 1,
  title: '금리 인상 전망에 시장 주목',
  description: '금리 결정에 대한 시장의 관심이 커지고 있습니다.',
  imageUrl: null,
  source: null,
  sourceUrl: 'https://example.com/news/1',
  category: 'STOCK',
  publishedAt: null,
  status: 'DRAFT',
  createdAt: '2026-08-17T09:00:00Z',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function renderAdmin(element: ReactNode) {
  return render(
    <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'ADMIN' }}>
      <MemoryRouter>{element}</MemoryRouter>
    </AuthContext.Provider>,
  );
}

function renderAdminEdit(element: ReactNode) {
  return render(
    <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'ADMIN' }}>
      <MemoryRouter initialEntries={['/admin/news/7/edit']}>
        <Routes>
          <Route path="/admin/news/:newsId/edit" element={element} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('관리자 뉴스 화면', () => {
  it('뉴스 목록을 표시하고 상태 필터를 API 요청에 반영한다', async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        calls.push(String(input));
        return jsonResponse({
          items: [listItem],
          page: 1,
          size: 15,
          totalPages: 1,
          totalElements: 1,
          hasNext: false,
        });
      }),
    );

    const view = renderAdmin(<AdminNewsPage />);
    expect(await view.findByRole('link', { name: listItem.title })).toBeTruthy();

    fireEvent.change(view.getByLabelText('상태'), { target: { value: 'PUBLISHED' } });
    await waitFor(() => expect(calls.some((url) => url.includes('status=PUBLISHED'))).toBe(true));
  });

  it('마지막 뉴스 삭제 후 줄어든 마지막 페이지로 이동한다', async () => {
    const calls: string[] = [];
    let listRequestCount = 0;
    const firstPageItem = { ...listItem, title: '첫 번째 페이지 뉴스' };
    const lastPageItem = { ...listItem, id: 3, title: '마지막 페이지 뉴스' };
    const replacementItem = { ...listItem, id: 2, title: '보정된 마지막 페이지 뉴스' };

    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        calls.push(url);

        if (url.endsWith('/api/auth/csrf')) {
          return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
        }

        if (url.endsWith('/api/admin/news/3') && init?.method === 'DELETE') {
          return new Response(null, { status: 204 });
        }

        if (url.includes('/api/admin/news?')) {
          listRequestCount += 1;

          if (listRequestCount === 1) {
            return jsonResponse({
              items: [firstPageItem],
              page: 1,
              size: 15,
              totalPages: 3,
              totalElements: 31,
              hasNext: true,
            });
          }

          if (listRequestCount === 2) {
            return jsonResponse({
              items: [lastPageItem],
              page: 3,
              size: 15,
              totalPages: 3,
              totalElements: 31,
              hasNext: false,
            });
          }

          if (listRequestCount === 3) {
            return jsonResponse({
              items: [],
              page: 3,
              size: 15,
              totalPages: 2,
              totalElements: 30,
              hasNext: false,
            });
          }

          return jsonResponse({
            items: [replacementItem],
            page: 2,
            size: 15,
            totalPages: 2,
            totalElements: 30,
            hasNext: false,
          });
        }

        return jsonResponse({}, 404);
      }),
    );
    vi.spyOn(window, 'confirm').mockReturnValue(true);

    const view = renderAdmin(<AdminNewsPage />);
    expect(await view.findByRole('link', { name: firstPageItem.title })).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '3' }));
    expect(await view.findByRole('link', { name: lastPageItem.title })).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: '삭제' }));

    await waitFor(() => {
      expect(calls.some((url) => url.includes('/api/admin/news?page=2&size=15'))).toBe(true);
      expect(view.getByRole('link', { name: replacementItem.title })).toBeTruthy();
    });
    expect(view.getByRole('button', { name: '2' }).getAttribute('aria-current')).toBe('page');
  });

  it('뉴스 등록 폼은 필수값과 URL 형식을 검증한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 500)),
    );
    const view = renderAdmin(<AdminNewsFormPage />);

    fireEvent.click(view.getByRole('button', { name: '초안으로 등록' }));

    expect(await view.findByText('제목을 입력해 주세요.')).toBeTruthy();
    expect(view.getByText('본문을 입력해 주세요.')).toBeTruthy();
    expect(view.getByText('원문 URL을 입력해 주세요.')).toBeTruthy();
    expect(view.getByText('카테고리를 선택해 주세요.')).toBeTruthy();
  });

  it('수정 대상 뉴스 상세 조회에 실패하면 폼 대신 오류 화면을 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        if (String(input).includes('/api/admin/news/7')) {
          return jsonResponse({ message: '뉴스 정보를 불러오지 못했습니다.' }, 404);
        }

        return jsonResponse({}, 404);
      }),
    );

    const view = renderAdminEdit(<AdminNewsFormPage />);

    expect((await view.findByRole('alert')).textContent).toContain(
      '뉴스 정보를 불러오지 못했습니다.',
    );
    expect(view.getByRole('link', { name: '뉴스 관리로 돌아가기' })).toBeTruthy();
    expect(view.queryByRole('button', { name: '수정 저장' })).toBeNull();
  });

  it('뉴스 목록 API 실패 시 오류와 재시도 버튼을 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 500)),
    );
    const view = renderAdmin(<AdminNewsPage />);

    expect(await view.findByRole('alert')).toBeTruthy();
    expect(view.getByRole('button', { name: '다시 시도' })).toBeTruthy();
  });
});
