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
    expect(view.getByRole('button', { name: '전체2' }).getAttribute('aria-pressed')).toBe(
      'true',
    );

    fireEvent.click(view.getByRole('button', { name: 'YouTube1' }));

    expect(view.getByText('삼성전자 분석 영상')).toBeTruthy();
    expect(view.queryByText('삼성전자 Threads 자료')).toBeNull();
  });
});
