/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminStockContentsPage } from '../../src/features/admin/AdminStockContentsPage';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('관리자 종목 연결 화면', () => {
  it.each(['연결', '발행 취소', '종목 정보 누락'] as const)(
    '기존 뉴스 수정 API로 DAYNOMY 연결 처리: %s',
    async (scenario) => {
      let linked = false;
      const news = {
        id: 7,
        title: '반도체 실적 전망',
        imageUrl: null,
        sources: [],
        category: 'STOCK',
        publishedAt: '2026-10-14T00:00:00Z',
        status: 'PUBLISHED',
        createdAt: '2026-10-14T00:00:00Z',
      };
      const detail = {
        ...news,
        title: '조회 시점의 최신 제목',
        content: '기존 본문을 그대로 유지합니다.',
        sources: [{ name: '출처', url: 'https://example.com/article' }],
        relatedAssets: [
          {
            assetId: 2,
            assetCode: '000660',
            name: 'SK하이닉스',
            category: 'STOCK',
            market: 'KOSPI',
          },
        ],
      };
      const content = {
        id: 9,
        assetId: 1,
        sourceType: 'INTERNAL_NEWS',
        title: news.title,
        url: '/news/7',
        imageUrl: null,
        createdAt: null,
      };
      const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const path = String(input);
        if (path.includes('/api/stocks?'))
          return jsonResponse({
            stocks: [
              {
                assetId: 1,
                assetCode: '005930',
                name: '삼성전자',
                category: 'STOCK',
                market: 'KOSPI',
              },
            ],
          });
        if (path.includes('/api/admin/news?'))
          return jsonResponse({
            items: [news],
            page: 1,
            size: 20,
            totalPages: 1,
            totalElements: 1,
            hasNext: false,
          });
        if (path.endsWith('/api/auth/csrf'))
          return jsonResponse({ token: 'token', headerName: 'X-CSRF-TOKEN' });
        if (path.endsWith('/api/admin/news/7')) {
          if (init?.method === 'PUT') {
            linked = true;
            return jsonResponse(detail);
          }
          return jsonResponse({
            ...detail,
            status: scenario === '발행 취소' ? 'DRAFT' : 'PUBLISHED',
            relatedAssets: scenario === '종목 정보 누락' ? undefined : detail.relatedAssets,
          });
        }
        return jsonResponse({
          assetId: 1,
          assetCode: '005930',
          assetName: '삼성전자',
          contents: linked ? [content] : [],
        });
      });
      vi.stubGlobal('fetch', fetchMock);
      const view = render(<AdminStockContentsPage />);
      fireEvent.change(view.getByRole('searchbox', { name: '종목 검색' }), {
        target: { value: '삼성전자' },
      });
      fireEvent.click(view.getByRole('button', { name: '검색' }));
      fireEvent.click(await view.findByRole('button', { name: /삼성전자/ }));
      await view.findByText('등록된 링크가 없습니다.');
      fireEvent.click(view.getByRole('button', { name: '자료 추가' }));
      fireEvent.click(view.getByRole('button', { name: 'DAYNOMY에서 찾기' }));
      fireEvent.click(view.getByRole('button', { name: 'DAYNOMY 검색' }));
      fireEvent.click(await view.findByRole('button', { name: '이 뉴스 연결' }));
      if (scenario !== '연결') {
        await view.findByText(
          scenario === '발행 취소'
            ? '현재 발행된 뉴스만 연결할 수 있습니다. 다시 검색해 주세요.'
            : '기존 연결 종목을 확인하지 못했습니다. 다시 시도해 주세요.',
        );
        expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'PUT')).toBe(false);
        return;
      }
      expect(await view.findByRole('button', { name: '연결됨' })).toBeTruthy();
      await view.findByText('삼성전자에 DAYNOMY 뉴스를 연결했습니다.');
      fireEvent.click(view.getByRole('button', { name: '연결된 자료 보기' }));
      expect(await view.findByRole('link', { name: '/news/7' })).toBeTruthy();
      const updates = fetchMock.mock.calls.filter(
        ([path, init]) => String(path).endsWith('/api/admin/news/7') && init?.method === 'PUT',
      );
      expect(updates).toHaveLength(1);
      const form = updates[0][1]?.body as FormData;
      const requestBlob = form.get('request') as Blob;
      const requestText = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = reject;
        reader.readAsText(requestBlob);
      });
      expect(JSON.parse(requestText)).toEqual({
        title: detail.title,
        content: detail.content,
        sources: detail.sources,
        category: detail.category,
        assetIds: [2, 1],
      });
      expect(form.has('image')).toBe(false);
      expect(fetchMock.mock.calls.some(([path]) => String(path).includes('/contents/news/'))).toBe(
        false,
      );
    },
  );

  it('작성 중인 자료는 작업 전환 시 유지하고 종목 변경은 확인 후 초기화한다', async () => {
    vi.stubGlobal('confirm', vi.fn().mockReturnValue(false));
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const path = String(input);
        if (path.includes('/api/stocks?'))
          return jsonResponse({
            stocks: [
              {
                assetId: 1,
                assetCode: '005930',
                name: '삼성전자',
                category: 'STOCK',
                market: 'KOSPI',
              },
              {
                assetId: 2,
                assetCode: '000660',
                name: 'SK하이닉스',
                category: 'STOCK',
                market: 'KOSPI',
              },
            ],
          });
        const id = path.includes('/assets/2/') ? 2 : 1;
        return jsonResponse({
          assetId: id,
          assetCode: id === 1 ? '005930' : '000660',
          assetName: id === 1 ? '삼성전자' : 'SK하이닉스',
          contents: [],
        });
      }),
    );
    const view = render(<AdminStockContentsPage />);
    fireEvent.change(view.getByRole('searchbox', { name: '종목 검색' }), {
      target: { value: '반도체' },
    });
    fireEvent.click(view.getByRole('button', { name: '검색' }));
    fireEvent.click(await view.findByRole('button', { name: /삼성전자/ }));
    await view.findByText('등록된 링크가 없습니다.');
    fireEvent.click(view.getByRole('button', { name: '자료 추가' }));
    fireEvent.change(view.getByLabelText('URL'), {
      target: { value: 'https://x.com/investor/status/123' },
    });
    expect((view.getByLabelText('출처') as HTMLSelectElement).value).toBe('X');
    fireEvent.change(view.getByLabelText('제목'), { target: { value: '작성 중인 자료' } });
    fireEvent.click(view.getByRole('button', { name: 'YouTube에서 찾기' }));
    fireEvent.click(view.getByRole('button', { name: 'URL 직접 등록' }));
    expect(view.getByDisplayValue('작성 중인 자료')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: /SK하이닉스/ }));
    expect(window.confirm).toHaveBeenCalledTimes(1);
    expect(view.getByDisplayValue('작성 중인 자료')).toBeTruthy();
    vi.mocked(window.confirm).mockReturnValue(true);
    fireEvent.click(view.getByRole('button', { name: /SK하이닉스/ }));
    await waitFor(() =>
      expect((view.getByRole('button', { name: '자료 추가' }) as HTMLButtonElement).disabled).toBe(
        false,
      ),
    );
    fireEvent.click(view.getByRole('button', { name: '자료 추가' }));
    expect((view.getByLabelText('URL') as HTMLInputElement).value).toBe('');
    expect((view.getByLabelText('제목') as HTMLInputElement).value).toBe('');
  });

  it('검색 결과가 없을 때 다음 행동을 안내한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ stocks: [] })),
    );
    const view = render(<AdminStockContentsPage />);
    fireEvent.change(view.getByRole('searchbox', { name: '종목 검색' }), {
      target: { value: '없는 종목' },
    });
    fireEvent.click(view.getByRole('button', { name: '검색' }));
    expect(
      await view.findByText('검색 결과가 없습니다. 종목명이나 코드를 바꿔 검색해 주세요.'),
    ).toBeTruthy();
  });

  it('종목 관련 자료 관리 패널을 별도 화면으로 표시한다', () => {
    const view = render(<AdminStockContentsPage />);

    expect(view.getByRole('heading', { name: '종목 관련 링크' })).toBeTruthy();
    expect(view.getByRole('searchbox', { name: '종목 검색' })).toBeTruthy();
  });

  it('연결된 자료를 전체와 카테고리별로 필터링한다', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input);
        if (url.includes('/api/stocks?')) {
          return jsonResponse({
            stocks: [
              {
                assetId: 1,
                assetCode: '005930',
                name: '삼성전자',
                category: 'STOCK',
                market: 'KOSPI',
              },
            ],
          });
        }

        return jsonResponse({
          assetId: 1,
          assetCode: '005930',
          assetName: '삼성전자',
          contents: [
            {
              id: 1,
              assetId: 1,
              sourceType: 'YOUTUBE',
              title: '삼성전자 분석 영상',
              url: 'https://youtube.com/watch?v=abc',
              imageUrl: null,
              createdAt: null,
            },
            {
              id: 2,
              assetId: 1,
              sourceType: 'THREADS',
              title: '삼성전자 Threads 자료',
              url: 'https://threads.net/@daynomy/post/1',
              imageUrl: null,
              createdAt: null,
            },
          ],
        });
      }),
    );

    const view = render(<AdminStockContentsPage />);
    fireEvent.change(view.getByRole('searchbox', { name: '종목 검색' }), {
      target: { value: '삼성전자' },
    });
    fireEvent.click(view.getByRole('button', { name: '검색' }));
    fireEvent.click(await view.findByRole('button', { name: /삼성전자/ }));

    expect(await view.findByText('삼성전자 분석 영상')).toBeTruthy();
    expect(view.getByText('삼성전자 Threads 자료')).toBeTruthy();
    expect(view.getByRole('button', { name: '전체2' }).getAttribute('aria-pressed')).toBe('true');

    fireEvent.click(view.getByRole('button', { name: 'YouTube1' }));

    expect(view.getByText('삼성전자 분석 영상')).toBeTruthy();
    expect(view.queryByText('삼성전자 Threads 자료')).toBeNull();
  });

  it('Threads·X 게시물을 검색하고 선택한 URL을 관련 자료로 등록한다', async () => {
    let saved = false;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.includes('/api/stocks?')) {
        return jsonResponse({
          stocks: [
            {
              assetId: 1,
              assetCode: '005930',
              name: '삼성전자',
              category: 'STOCK',
              market: 'KOSPI',
            },
          ],
        });
      }

      if (url.includes('/social-search?')) {
        expect(url).toContain('keyword=%EC%82%BC%EC%84%B1%EC%A0%84%EC%9E%90');
        return jsonResponse({
          items: [
            {
              sourceType: 'THREADS',
              title: '삼성전자 실적 전망',
              url: 'https://www.threads.net/@investor/post/123',
              publishedDate: '2026-10-11',
            },
            {
              sourceType: 'X',
              title: '삼성전자 수급',
              url: 'https://x.com/investor/status/456',
              publishedDate: '2020-04-05',
            },
          ],
        });
      }

      if (url.endsWith('/api/auth/csrf')) {
        return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
      }

      if (url.endsWith('/api/admin/assets/1/contents') && init?.method === 'POST') {
        const body = JSON.parse(String(init.body)) as Record<string, unknown>;
        expect(body).toMatchObject({
          publishedDate: '2020-04-05',
          sourceType: 'X',
          title: '삼성전자 수급',
          url: 'https://x.com/investor/status/456',
        });
        saved = true;
        return jsonResponse({
          id: 10,
          assetId: 1,
          sourceType: 'X',
          title: '삼성전자 수급',
          url: 'https://x.com/investor/status/456',
          imageUrl: null,
          createdAt: null,
        });
      }

      return jsonResponse({
        assetId: 1,
        assetCode: '005930',
        assetName: '삼성전자',
        contents: saved
          ? [
              {
                id: 10,
                assetId: 1,
                sourceType: 'X',
                title: '삼성전자 수급',
                url: 'https://x.com/investor/status/456',
                imageUrl: null,
                createdAt: null,
              },
            ]
          : [],
      });
    });
    vi.stubGlobal('fetch', fetchMock);

    const view = render(<AdminStockContentsPage />);
    fireEvent.change(view.getByRole('searchbox', { name: '종목 검색' }), {
      target: { value: '삼성전자' },
    });
    fireEvent.click(view.getByRole('button', { name: '검색' }));
    fireEvent.click(await view.findByRole('button', { name: /삼성전자/ }));
    await view.findByText('등록된 링크가 없습니다.');

    fireEvent.click(view.getByRole('button', { name: '자료 추가' }));
    fireEvent.click(view.getByRole('button', { name: 'Threads·X에서 찾기' }));
    fireEvent.click(view.getByRole('button', { name: 'Threads·X 검색' }));

    expect(await view.findByText('삼성전자 실적 전망')).toBeTruthy();
    expect(view.getByText('Threads · 2026-10-11')).toBeTruthy();
    expect(view.getByText('삼성전자 수급')).toBeTruthy();

    fireEvent.click(view.getAllByRole('button', { name: '등록' })[1]);

    expect(
      ((await view.findByRole('button', { name: '등록됨' })) as HTMLButtonElement).disabled,
    ).toBe(true);
    fireEvent.click(view.getByRole('button', { name: '연결된 자료 보기' }));
    expect(await view.findByRole('button', { name: 'X1' })).toBeTruthy();
    expect(saved).toBe(true);
  });
});
