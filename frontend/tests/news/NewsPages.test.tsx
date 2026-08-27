/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { NewsDetailPage } from '../../src/features/news/newsdetail/NewsDetailPage';
import { NewsListPage } from '../../src/features/news/newslist/NewsListPage';

const article = {
  id: 7,
  title: '기준금리 동결 가능성 확대',
  description:
    '기준금리가 유지되고 있습니다. 채권 시장의 관망세가 이어지고 있습니다. 추가 지표를 확인해야 합니다.',
  category: 'ECONOMY',
  imageUrl: null,
  publishedAt: '2026-08-27T10:00:00Z',
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function renderPage(element: ReactNode) {
  return render(
    <AuthContext.Provider value={{ isLoggedIn: false, loading: false }}>
      <MemoryRouter>{element}</MemoryRouter>
    </AuthContext.Provider>,
  );
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  window.history.replaceState(null, '', '/');
});

describe('뉴스 탐색 화면', () => {
  it('뉴스 목록을 표시하고 카테고리 변경을 API 요청에 반영한다', async () => {
    const calls: string[] = [];
    vi.stubGlobal('scrollTo', vi.fn());
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push(url);

        if (url === '/api/news/today') return jsonResponse(article);

        return jsonResponse({
          items: [article],
          page: 1,
          size: 10,
          totalPages: 1,
          totalElements: 1,
          hasNext: false,
        });
      }),
    );

    const view = renderPage(<NewsListPage />);
    const link = (await view.findByRole('link', {
      name: /기준금리 동결 가능성 확대/,
    })) as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/news/7');
    fireEvent.click(view.getByRole('button', { name: '주식' }));
    await waitFor(() => expect(calls.some((url) => url.includes('category=STOCK'))).toBe(true));
  });

  it('뉴스 목록 API 실패를 사용자에게 안내한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input) === '/api/news/today' ? jsonResponse(article) : jsonResponse({}, 500),
      ),
    );

    const view = renderPage(<NewsListPage />);

    expect((await view.findByRole('status')).textContent).toContain(
      '뉴스 목록을 불러오지 못했습니다.',
    );
  });

  it('뉴스 상세 내용과 비로그인 포트폴리오 안내를 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content: '금리 동결이 금융시장에 미치는 영향입니다.',
            source: 'BOK',
            sourceUrl: 'https://example.com/news/7',
          });
        }
        if (url === '/api/news/7/keywords') {
          return jsonResponse({
            keywords: [
              {
                category: 'POLICY',
                keyword: '금리 동결',
                points: ['기준금리 동결은 정책 방향을 보여줍니다.', '시장 변화를 확인해야 합니다.'],
              },
            ],
          });
        }

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByRole('heading', { name: article.title })).toBeTruthy();
    expect(view.getByText('기준금리가 유지되고 있습니다.')).toBeTruthy();
    expect(view.getByText('채권 시장의 관망세가 이어지고 있습니다.')).toBeTruthy();
    expect(view.getByText('추가 지표를 확인해야 합니다.')).toBeTruthy();
    expect(view.container.querySelector('mark.keyword')?.textContent).toContain('금리 동결');
    expect(view.getByText('기준금리 동결은 정책 방향을 보여줍니다.')).toBeTruthy();
    expect(view.getByRole('link', { name: '한국은행' }).getAttribute('href')).toBe(
      'https://example.com/news/7',
    );
    expect(view.getByRole('link', { name: 'Google로 시작하기' }).getAttribute('href')).toBe(
      '/api/auth/google',
    );
  });
});
