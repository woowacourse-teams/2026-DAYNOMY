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

function renderPage(element: ReactNode, isLoggedIn = false) {
  return render(
    <AuthContext.Provider value={{ isLoggedIn, loading: false }}>
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

        if (url === '/api/news/today') {
          return jsonResponse({
            items: [article, { ...article, id: 8, title: '오늘의 두 번째 뉴스' }],
            page: 1,
            size: 9,
            totalPages: 1,
            totalElements: 2,
            hasNext: false,
          });
        }

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
    expect(view.getAllByRole('button', { name: /번째 배너 보기/ })).toHaveLength(2);
    fireEvent.click(view.getByRole('button', { name: '주식' }));
    await waitFor(() => expect(calls.some((url) => url.includes('category=STOCK'))).toBe(true));
  });

  it('뉴스 목록 API 실패를 사용자에게 안내한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input) === '/api/news/today'
          ? jsonResponse({
              items: [],
              page: 1,
              size: 9,
              totalPages: 0,
              totalElements: 0,
              hasNext: false,
            })
          : jsonResponse({}, 500),
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
        if (url === '/api/news/7/market-analysis') {
          return jsonResponse({
            cause: '금리 흐름이 채권 시장의 관망세에 영향을 주고 있습니다.',
            importance: '기준금리 변화는 대출과 채권 수익률에 연결됩니다.',
            assets: [
              {
                category: 'BOND',
                direction: 'NEGATIVE',
                impactLevel: 'MEDIUM',
                reason: '금리 불확실성이 채권 투자 심리를 제한할 수 있습니다.',
              },
            ],
            scenarios: [
              {
                timeHorizon: 'LONG_TERM',
                prediction: '장기적으로 정책 방향에 따라 시장 흐름이 달라질 수 있습니다.',
                probability: 45,
                reason: '추가 경제 지표 확인이 필요합니다.',
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
    expect(view.getByText('금리 흐름이 채권 시장의 관망세에 영향을 주고 있습니다.')).toBeTruthy();
    expect(view.getByText('기준금리 변화는 대출과 채권 수익률에 연결됩니다.')).toBeTruthy();
    expect(view.getByRole('heading', { name: '채권' })).toBeTruthy();
    expect(view.getByRole('heading', { name: '장기 시나리오' })).toBeTruthy();
    expect(view.getByRole('link', { name: '한국은행' }).getAttribute('href')).toBe(
      'https://example.com/news/7',
    );
    expect(view.getByRole('link', { name: 'Google로 시작하기' }).getAttribute('href')).toBe(
      '/api/auth/google',
    );
  });

  it('로그인 사용자의 포트폴리오 영향 분석을 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        calls.push(url);

        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content: '뉴스 본문입니다.',
            source: 'BOK',
          });
        }
        if (url === '/api/news/7/portfolio-analysis') {
          return jsonResponse({
            impacts: [
              {
                bookmarkId: 1,
                assetId: 2,
                name: '삼성전자',
                category: 'STOCK',
                assetCode: '005930',
                direction: 'POSITIVE',
                impactLevel: 'HIGH',
                expectedReaction: '주가가 상승할 수 있습니다.',
                reason: '반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.',
                sortOrder: 1,
              },
            ],
          });
        }

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />, true);

    expect(await view.findByRole('heading', { name: '삼성전자' })).toBeTruthy();
    expect(view.getByText('주가가 상승할 수 있습니다.')).toBeTruthy();
    expect(
      view.getByText('반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.'),
    ).toBeTruthy();
    expect(calls).toContain('/api/news/7/portfolio-analysis');
    expect(view.queryByRole('link', { name: 'Google로 시작하기' })).toBeNull();
  });
});
