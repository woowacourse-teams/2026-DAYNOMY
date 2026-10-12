/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
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
              publishedDate: '',
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

    fireEvent.click(view.getByRole('button', { name: 'Threads·X 검색' }));

    expect(await view.findByText('삼성전자 실적 전망')).toBeTruthy();
    expect(view.getByText('Threads · 2026-10-11')).toBeTruthy();
    expect(view.getByText('삼성전자 수급')).toBeTruthy();

    fireEvent.click(view.getAllByRole('button', { name: '등록' })[1]);

    expect(await view.findByRole('button', { name: 'X1' })).toBeTruthy();
    expect(saved).toBe(true);
  });
});
