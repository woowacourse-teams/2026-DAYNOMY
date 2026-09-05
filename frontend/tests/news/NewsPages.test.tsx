/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { NewsDetailPage } from '../../src/features/news/newsdetail/NewsDetailPage';
import { NewsListPage } from '../../src/features/news/newslist/NewsListPage';
import { ArticleCard } from '../../src/features/news/newslist/components/ArticleCard';
import type { NewsListItem } from '../../src/features/news/newslist/types';

const article = {
  id: 7,
  title: '기준금리 동결 가능성 확대',
  description:
    '기준금리가 유지되고 있습니다. 채권 시장의 관망세가 이어지고 있습니다. 추가 지표를 확인해야 합니다.',
  category: 'ECONOMY',
  imageUrl: null,
  publishedAt: '2026-08-27T10:00:00Z',
} satisfies NewsListItem;

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function getPath(input: RequestInfo | URL) {
  return new URL(String(input), 'http://localhost').pathname;
}

function getPathWithSearch(input: RequestInfo | URL) {
  const url = new URL(String(input), 'http://localhost');
  return `${url.pathname}${url.search}`;
}

function renderPage(element: ReactNode, isLoggedIn = false) {
  return render(
    <AuthContext.Provider value={{ isLoggedIn, loading: false, role: isLoggedIn ? 'USER' : null }}>
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
  it('뉴스 카드 이미지를 지연 로딩하고 로드 실패 시 기본 이미지로 교체한다', () => {
    const view = render(
      <ArticleCard article={{ ...article, imageUrl: 'https://example.com/news.webp' }} />,
    );
    const image = view.container.querySelector('img')!;

    expect(image.getAttribute('src')).toBe('https://example.com/news.webp');
    expect(view.queryByText(article.description)).toBeNull();
    expect(image.getAttribute('loading')).toBe('lazy');
    expect(image.getAttribute('decoding')).toBe('async');

    fireEvent.error(image);

    expect(image.getAttribute('src')).toMatch(/default-news-real-estate\.webp$/);

    view.rerender(<ArticleCard article={article} />);
    expect(image.getAttribute('src')).toMatch(/default-news-real-estate\.webp$/);
  });

  it('뉴스 목록을 표시하고 카테고리 변경을 API 요청에 반영한다', async () => {
    const calls: string[] = [];
    vi.stubGlobal('scrollTo', vi.fn());
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPathWithSearch(input);
        calls.push(url);

        if (getPath(input) === '/api/news/today') {
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
    const todayBanner = view.getByRole('button', { name: '오늘의 뉴스' });
    expect(todayBanner.querySelector('p')).toBeNull();
    expect(todayBanner.textContent).not.toContain(article.description);
    const categoryTabs = view.getByRole('navigation', { name: '뉴스 카테고리' });
    expect(categoryTabs.querySelectorAll('button')).toHaveLength(4);
    expect(view.getByRole('button', { name: '전체' })).toBeTruthy();
    expect(view.getByRole('button', { name: '주식' })).toBeTruthy();
    expect(view.getByRole('button', { name: '부동산' })).toBeTruthy();
    expect(view.getByRole('button', { name: 'ETF' })).toBeTruthy();
    expect(view.queryByRole('button', { name: '금' })).toBeNull();
    expect(view.queryByRole('button', { name: '채권' })).toBeNull();
    expect(view.getAllByRole('button', { name: /번째 배너 보기/ })).toHaveLength(2);
    expect(view.container.querySelector('.banner-visual img')?.hasAttribute('loading')).toBe(false);
    fireEvent.click(view.getByRole('button', { name: '주식' }));
    await waitFor(() => expect(calls.some((url) => url.includes('category=STOCK'))).toBe(true));
  });

  it('뉴스 목록 API 실패를 사용자에게 안내한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        getPath(input) === '/api/news/today'
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

  it('오늘의 뉴스가 없으면 배너 디자인을 유지한 빈 상태를 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        getPath(input) === '/api/news/today'
          ? jsonResponse({
              items: [],
              page: 1,
              size: 9,
              totalPages: 0,
              totalElements: 0,
              hasNext: false,
            })
          : jsonResponse({
              items: [article],
              page: 1,
              size: 10,
              totalPages: 1,
              totalElements: 1,
              hasNext: false,
            }),
      ),
    );

    const view = renderPage(<NewsListPage />);
    const emptyBanner = await view.findByRole('region', { name: '오늘의 뉴스' });

    expect(emptyBanner.classList.contains('today-news-empty')).toBe(true);
    expect(emptyBanner.textContent).toContain('오늘의 뉴스는 없습니다!');
    expect(emptyBanner.compareDocumentPosition(view.getByRole('button', { name: '전체' }))).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('오늘의 뉴스와 뉴스 목록 오류를 화면 순서대로 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 500)),
    );

    const view = renderPage(<NewsListPage />);

    await waitFor(() => expect(view.container.querySelectorAll('.state-panel')).toHaveLength(2));

    const panels = [...view.container.querySelectorAll('.state-panel')];
    expect(panels[0].textContent).toContain('오늘의 뉴스를 불러오지 못했습니다.');
    expect(panels[1].textContent).toContain('뉴스 목록을 불러오지 못했습니다.');
  });

  it('뉴스 상세 내용과 비로그인 포트폴리오 안내를 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);
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
            summary:
              '금리 흐름이 채권 시장의 관망세에 영향을 주고 있습니다. 기준금리 변화는 대출과 채권 수익률에 연결됩니다.',
          });
        }

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByRole('heading', { name: article.title })).toBeTruthy();
    expect(view.queryByRole('heading', { name: '핵심 요약' })).toBeNull();
    expect(view.queryByText('기준금리가 유지되고 있습니다.')).toBeNull();
    expect(view.queryByText('채권 시장의 관망세가 이어지고 있습니다.')).toBeNull();
    expect(view.queryByText('추가 지표를 확인해야 합니다.')).toBeNull();
    expect(view.container.querySelector('mark.keyword')?.textContent).toContain('금리 동결');
    expect(view.getByText('기준금리 동결은 정책 방향을 보여줍니다.')).toBeTruthy();
    expect(view.getByText('금리 흐름이 채권 시장의 관망세에 영향을 주고 있습니다.')).toBeTruthy();
    expect(view.getByText('기준금리 변화는 대출과 채권 수익률에 연결됩니다.')).toBeTruthy();
    expect(view.container.querySelectorAll('.market-summary-card')).toHaveLength(1);
    expect(view.container.querySelectorAll('.market-summary-card li')).toHaveLength(2);
    expect(view.queryByRole('heading', { name: '발생 원인' })).toBeNull();
    expect(view.queryByRole('heading', { name: /가장 영향 가능성이 높은 자산/ })).toBeNull();
    expect(view.queryByRole('heading', { name: /시나리오/ })).toBeNull();
    expect(view.getByRole('link', { name: '한국은행' }).getAttribute('href')).toBe(
      'https://example.com/news/7',
    );
    expect(view.queryByRole('link', { name: 'Google로 시작하기' })).toBeNull();
    expect(view.container.querySelector('.news-image')?.hasAttribute('loading')).toBe(false);
  });

  it('로그인 사용자의 포트폴리오 영향 분석을 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);
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
    expect(view.getByText('반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.')).toBeTruthy();
    expect(calls).toContain('/api/news/7/portfolio-analysis');
    expect(view.queryByRole('link', { name: 'Google로 시작하기' })).toBeNull();
    expect(view.getByRole('heading', { name: '시장 분석' })).toBeTruthy();
    expect(view.getByRole('alert').textContent).toContain('시장 분석을 불러오지 못했습니다.');
  });
});
