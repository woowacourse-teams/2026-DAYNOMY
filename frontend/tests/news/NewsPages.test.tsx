/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { NewsDetailPage } from '../../src/features/news/newsdetail/NewsDetailPage';
import { IssueHomePage } from '../../src/features/news/issue/IssueHomePage';
import { NewsListPage } from '../../src/features/news/newslist/NewsListPage';
import { ArticleCard } from '../../src/features/news/newslist/components/ArticleCard';
import type { NewsListItem } from '../../src/features/news/newslist/types';

const article = {
  id: 7,
  title: '기준금리 동결 가능성 확대',
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

beforeEach(() => {
  vi.stubGlobal('matchMedia', () => ({
    matches: false,
    addEventListener() {},
    removeEventListener() {},
  }));
});

afterEach(() => {
  cleanup();
  localStorage.clear();
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
    await waitFor(() =>
      expect(view.container.querySelector('.article-list a[href="/news/7"]')).not.toBeNull(),
    );
    const link = view.container.querySelector<HTMLAnchorElement>('.article-list a[href="/news/7"]');

    expect(link).not.toBeNull();
    expect(link?.getAttribute('href')).toBe('/news/7');
    expect(view.getByRole('link', { name: /소식 홈/ }).getAttribute('href')).toBe('/news');
    const categoryTabs = view.getByRole('navigation', { name: '이슈 카테고리' });
    expect(categoryTabs.querySelectorAll('button')).toHaveLength(4);
    expect(view.getByRole('button', { name: '전체' })).toBeTruthy();
    expect(view.getByRole('button', { name: '주식' })).toBeTruthy();
    expect(view.getByRole('button', { name: '부동산' })).toBeTruthy();
    expect(view.getByRole('button', { name: 'ETF' })).toBeTruthy();
    expect(view.queryByRole('button', { name: '금' })).toBeNull();
    expect(view.queryByRole('button', { name: '채권' })).toBeNull();
    expect(view.queryByRole('button', { name: '이전 오늘의 이슈' })).toBeNull();
    expect(view.queryByRole('button', { name: '다음 오늘의 이슈' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '주식' }));
    await waitFor(() => expect(calls.some((url) => url.includes('category=STOCK'))).toBe(true));
  });

  it('페이지 범위 화살표를 누르면 현재 표시된 숫자의 끝으로 이동한다', async () => {
    const calls: string[] = [];
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPathWithSearch(input);
        calls.push(url);

        if (getPath(input) === '/api/news/today') {
          return jsonResponse({
            items: [],
            page: 1,
            size: 6,
            totalPages: 0,
            totalElements: 0,
            hasNext: false,
          });
        }

        return jsonResponse({
          items: [article],
          page: Number(new URL(String(input), 'http://localhost').searchParams.get('page')),
          size: 6,
          totalPages: 10,
          totalElements: 60,
          hasNext: true,
        });
      }),
    );

    const view = renderPage(<NewsListPage />);
    await waitFor(() =>
      expect(view.getByRole('button', { name: '표시된 페이지 범위의 마지막 페이지' })).toBeTruthy(),
    );

    fireEvent.click(view.getByRole('button', { name: '표시된 페이지 범위의 마지막 페이지' }));

    await waitFor(() => expect(calls.some((url) => url.includes('page=5'))).toBe(true));
    expect(view.getByRole('button', { name: '5', current: 'page' })).toBeTruthy();
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

    expect((await view.findByRole('alert')).textContent).toContain(
      '이슈 목록을 불러오지 못했습니다.',
    );
  });

  it('오늘의 뉴스가 없으면 최근 이슈를 대신 보여준다', async () => {
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

    const view = renderPage(<IssueHomePage />);
    await view.findByText('오늘 발행된 이슈가 없어 최근 이슈를 보여드려요.');
    const recentIssues = view.getByRole('region', { name: '최근 이슈' });

    expect(recentIssues.querySelector('input[type="date"]')).toBeNull();
    expect(recentIssues.querySelector('a[href="/news/contents"]')).toBeNull();
    expect(recentIssues.querySelector(`a[href="/news/${article.id}"]`)).toBeTruthy();
  });

  it('오늘의 뉴스와 뉴스 목록 오류를 소식 홈에 독립적으로 표시한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 500)),
    );
    const view = renderPage(<IssueHomePage />);
    expect(await view.findAllByText('데이노미 이슈를 불러오지 못했습니다.')).toHaveLength(2);
  });

  it('뉴스 상세 내용과 시장 분석을 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);
        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content: '금리 동결이 금융시장에 미치는 영향입니다.',
            imageSource: {
              name: '',
              url: '',
              author: '',
              license: '',
              licenseUrl: '',
              type: 'AI_GENERATED',
            },
            sources: [
              { name: '한국은행', url: 'https://example.com/news/7' },
              { name: 'DART', url: 'https://example.com/news/7/dart' },
            ],
          });
        }
        if (url === '/api/news/7/keywords') {
          return jsonResponse({
            keywords: [
              {
                category: 'POLICY',
                keyword: '금리 동결',
                definition: '기준금리를 현재 수준으로 유지하는 결정입니다.',
                context: '이번 기사에서는 통화정책 방향을 보여주는 결정입니다.',
                watchPoint: '다음 물가와 금리 발표를 확인해야 합니다.',
              },
            ],
          });
        }
        if (url === '/api/news/7/market-analysis') {
          return jsonResponse({
            risk: '물가가 다시 상승하면 금리 전망이 달라질 수 있어요.',
            watchPoint: '다음 소비자물가와 기준금리 발표를 확인하세요.',
          });
        }

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByRole('heading', { name: article.title })).toBeTruthy();
    expect(view.getByText('AI로 생성된 이미지입니다.')).toBeTruthy();
    expect(view.queryByRole('heading', { name: '핵심 요약' })).toBeNull();
    expect(view.queryByText('기준금리가 유지되고 있습니다.')).toBeNull();
    expect(view.queryByText('채권 시장의 관망세가 이어지고 있습니다.')).toBeNull();
    expect(view.queryByText('추가 지표를 확인해야 합니다.')).toBeNull();
    expect(view.container.querySelector('button.keyword')?.textContent).toContain('금리 동결');
    fireEvent.pointerEnter(view.getByRole('button', { name: '금리 동결' }));
    expect(view.getByText('뜻')).toBeTruthy();
    expect(view.getByText('기사 맥락')).toBeTruthy();
    expect(view.getByText('확인할 점')).toBeTruthy();
    expect(view.getByText('기준금리를 현재 수준으로 유지하는 결정입니다.')).toBeTruthy();
    expect(view.getByText('이번 기사에서는 통화정책 방향을 보여주는 결정입니다.')).toBeTruthy();
    expect(view.getByText('다음 물가와 금리 발표를 확인해야 합니다.')).toBeTruthy();
    expect(view.queryByText('확인된 사실')).toBeNull();
    expect(view.queryByText('해석')).toBeNull();
    expect(view.getByText('물가가 다시 상승하면 금리 전망이 달라질 수 있어요.')).toBeTruthy();
    expect(view.getByText('다음 소비자물가와 기준금리 발표를 확인하세요.')).toBeTruthy();
    expect(view.getByText('위험')).toBeTruthy();
    expect(view.getByText('확인할 지표')).toBeTruthy();
    expect(view.queryByText('분석 한계')).toBeNull();
    expect(view.queryByText('한국은행이 기준금리를 현재 수준으로 유지했습니다.')).toBeNull();
    expect(view.queryByText('금리 흐름이 채권 시장의 관망세에 영향을 주고 있습니다.')).toBeNull();
    expect(view.container.querySelectorAll('.market-analysis-card')).toHaveLength(1);
    expect(view.container.querySelectorAll('.market-analysis-item')).toHaveLength(2);
    expect(
      view.container.querySelectorAll(
        '.market-analysis-card dd strong, .market-analysis-card dd b',
      ),
    ).toHaveLength(0);
    expect(view.queryByRole('heading', { name: '발생 원인' })).toBeNull();
    expect(view.queryByRole('heading', { name: /가장 영향 가능성이 높은 자산/ })).toBeNull();
    expect(view.queryByRole('heading', { name: /시나리오/ })).toBeNull();
    expect(view.getByRole('link', { name: '한국은행' }).getAttribute('href')).toBe(
      'https://example.com/news/7',
    );
    expect(view.getByRole('link', { name: 'DART' }).getAttribute('href')).toBe(
      'https://example.com/news/7/dart',
    );
    expect(view.queryByRole('link', { name: 'Google로 시작하기' })).toBeNull();
    expect(view.container.querySelector('.news-image')?.hasAttribute('loading')).toBe(false);
  });

  it('뉴스 키워드는 종류별 최초 등장 위치에 한 번씩 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);
        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content:
              '금리 동결 이후 금리 동결 전망을 확인합니다.\n\n채권시장도 금리 동결의 영향을 받고 채권시장 변화를 주시합니다.',
            sources: [],
          });
        }
        if (url === '/api/news/7/keywords') {
          return jsonResponse({
            keywords: [
              {
                category: 'POLICY',
                keyword: '금리 동결',
                definition: '기준금리를 유지하는 결정입니다.',
                context: '기사의 통화정책 방향을 설명합니다.',
                watchPoint: '다음 금리 발표를 확인해야 합니다.',
              },
              {
                category: 'TREND',
                keyword: '채권시장',
                definition: '채권을 거래하는 시장입니다.',
                context: '금리 동결의 영향을 받는 시장으로 언급됩니다.',
                watchPoint: '채권 수익률 변화를 확인해야 합니다.',
              },
            ],
          });
        }
        if (url === '/api/news/7/market-analysis') return jsonResponse({}, 404);

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByRole('heading', { name: article.title })).toBeTruthy();
    const highlightedKeywords = Array.from(
      view.container.querySelectorAll<HTMLElement>('button.keyword'),
    ).map((keyword) => keyword.firstChild?.textContent);
    expect(highlightedKeywords).toEqual(['금리 동결', '채권시장']);
  });

  it('뉴스 이미지 출처명과 저작자를 함께 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);
        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content: 'Wikimedia 이미지 출처를 확인하는 뉴스 본문입니다.',
            imageUrl: 'https://upload.wikimedia.org/wikimedia.jpg',
            imageSource: {
              name: 'Wikimedia Commons',
              url: 'https://commons.wikimedia.org/wiki/File:Seoul.jpg',
              author: 'Jane Doe',
              license: 'CC BY 4.0',
              licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
              type: 'WIKIMEDIA',
            },
            sources: [],
          });
        }
        if (url === '/api/news/7/keywords') return jsonResponse({ keywords: [] });
        if (url === '/api/news/7/market-analysis') return jsonResponse({}, 404);
        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByRole('link', { name: 'Wikimedia Commons' })).toBeTruthy();
    expect(view.container.querySelector('.news-image-credit')?.textContent).toContain('Jane Doe');
    expect(view.getByRole('link', { name: 'CC BY 4.0' })).toBeTruthy();
  });

  it('뉴스 상세에서는 포트폴리오 분석 기능을 표시하지 않는다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);

        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content: '반도체 시장에 관한 뉴스입니다.',
            sources: [],
          });
        }
        if (url === '/api/news/7/keywords') return jsonResponse({ keywords: [] });
        if (url === '/api/news/7/market-analysis') {
          return jsonResponse({
            risk: '기업 한 곳의 실적을 시장 전체로 일반화하기 어렵습니다.',
            watchPoint: '다음 분기 매출과 재고를 확인해야 합니다.',
          });
        }

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);
    expect(await view.findByRole('heading', { name: article.title })).toBeTruthy();
    expect(view.queryByRole('button', { name: /포트폴리오 분석/ })).toBeNull();
  });

  it('뉴스 상세 API 실패 시 목데이터 대신 오류 안내를 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({}, 500)),
    );

    const view = renderPage(<NewsDetailPage />);

    const errorMessage = await view.findByRole('alert');
    expect(errorMessage.textContent).toBe(
      '뉴스를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    );
    expect(
      view.queryByRole('heading', { name: 'ETF 순자산 증가, 분산 투자 수요 확대' }),
    ).toBeNull();
  });

  it('뉴스 상세 응답의 출처 형식이 잘못되면 오류 안내를 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ ...article, content: '본문', sources: [42] })),
    );

    const view = renderPage(<NewsDetailPage />);

    expect((await view.findByRole('alert')).textContent).toContain('뉴스를 불러오지 못했습니다.');
    expect(view.queryByRole('heading', { name: article.title })).toBeNull();
  });

  it('시장 분석 데이터가 없어도 뉴스 본문과 데이터 없음 안내를 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);

        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content: '시장 분석 데이터가 없어도 표시되는 뉴스 본문입니다.',
            sources: [],
          });
        }
        if (url === '/api/news/7/keywords') {
          return jsonResponse({ keywords: [] });
        }
        if (url === '/api/news/7/market-analysis') {
          return jsonResponse({ code: 'MARKET_ANALYSIS_NOT_FOUND' }, 404);
        }

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByRole('heading', { name: article.title })).toBeTruthy();
    expect(view.getByText('시장 분석 데이터가 없어도 표시되는 뉴스 본문입니다.')).toBeTruthy();
    expect(view.getByRole('heading', { name: '시장 분석' })).toBeTruthy();
    expect(view.getByRole('status').textContent).toContain('아직 제공된 시장 분석이 없습니다.');
    expect(view.queryByRole('alert')).toBeNull();
  });

  it('시장 분석 API가 실패해도 뉴스 본문과 오류 안내를 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);

        if (url === '/api/news/7') {
          return jsonResponse({
            ...article,
            content: '시장 분석 API가 실패해도 표시되는 뉴스 본문입니다.',
            sources: [],
          });
        }
        if (url === '/api/news/7/keywords') {
          return jsonResponse({ keywords: [] });
        }
        if (url === '/api/news/7/market-analysis') {
          return jsonResponse({}, 500);
        }

        return jsonResponse({}, 500);
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByRole('heading', { name: article.title })).toBeTruthy();
    expect(view.getByText('시장 분석 API가 실패해도 표시되는 뉴스 본문입니다.')).toBeTruthy();
    expect(view.getByRole('heading', { name: '시장 분석' })).toBeTruthy();
    expect(view.getByRole('alert').textContent).toContain('시장 분석을 불러오지 못했습니다.');
    expect(view.queryByRole('status')).toBeNull();
  });

  it.each([
    { risk: '', watchPoint: '  ' },
    { risk: '', watchPoint: '', summary: '이전 요약', observedFact: 42 },
  ])(
    '표시할 두 항목이 비어 있으면 이전 필드와 무관하게 미제공 상태를 표시한다',
    async (analysis) => {
      window.history.replaceState(null, '', '/news/7');
      vi.stubGlobal(
        'fetch',
        vi.fn(async (input: RequestInfo | URL) => {
          const url = getPath(input);
          if (url === '/api/news/7')
            return jsonResponse({ ...article, content: '뉴스 본문', sources: [] });
          if (url === '/api/news/7/keywords') return jsonResponse({ keywords: [] });
          return jsonResponse(analysis);
        }),
      );
      const view = renderPage(<NewsDetailPage />);
      expect(await view.findByText('뉴스 본문')).toBeTruthy();
      expect(view.getByRole('status').textContent).toContain('아직 제공된 시장 분석이 없습니다.');
      expect(view.queryByRole('alert')).toBeNull();
      expect(view.container.querySelector('.keyword')).toBeNull();
    },
  );

  it('시장 분석·키워드 응답 형식이 잘못돼도 뉴스 본문을 표시한다', async () => {
    window.history.replaceState(null, '', '/news/7');
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = getPath(input);
        if (url === '/api/news/7') {
          return jsonResponse({ ...article, content: '정상 뉴스 본문', sources: [] });
        }
        if (url === '/api/news/7/keywords') {
          return jsonResponse({
            keywords: [
              {
                category: 'INVALID',
                keyword: '정상',
                definition: '뜻',
                context: '기사 맥락',
                watchPoint: '확인할 점',
              },
            ],
          });
        }
        return jsonResponse({
          risk: 42,
          watchPoint: '확인할 지표',
        });
      }),
    );

    const view = renderPage(<NewsDetailPage />);

    expect(await view.findByText('정상 뉴스 본문')).toBeTruthy();
    expect(view.getByRole('alert').textContent).toContain('시장 분석을 불러오지 못했습니다.');
    expect(view.container.querySelector('button.keyword')).toBeNull();
  });
});
