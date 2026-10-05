/** @vitest-environment jsdom */

import { cleanup, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { StockContentPage } from '../../src/features/stock-content/StockContentPage';

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/stocks/1']}>
      <Routes>
        <Route path="/stocks/:assetId" element={<StockContentPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () =>
      jsonResponse({
        assetId: 1,
        assetCode: '005930',
        assetName: '삼성전자',
        contents: [
          {
            id: 10,
            assetId: 1,
            sourceType: 'YOUTUBE',
            title: '삼성전자 분석 영상',
            url: 'https://youtube.com/watch?v=abc',
            imageUrl: null,
            createdAt: null,
          },
          {
            id: 11,
            assetId: 1,
            sourceType: 'YOUTUBE',
            title: '임베드 URL 영상',
            url: 'https://www.youtube.com/embed/def',
            imageUrl: null,
            createdAt: null,
          },
          {
            id: 12,
            assetId: 1,
            sourceType: 'YOUTUBE',
            title: '허용되지 않은 영상 링크',
            url: 'https://example.com/video',
            imageUrl: null,
            createdAt: null,
          },
        ],
      }),
    ),
  );
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('종목 관련 자료 화면의 YouTube 임베드', () => {
  it('watch URL과 embed URL을 공식 embed URL로 변환해 iframe으로 표시한다', async () => {
    const view = renderPage();

    expect((await view.findByTitle('삼성전자 분석 영상')).getAttribute('src')).toBe(
      'https://www.youtube.com/embed/abc',
    );
    expect(view.getByTitle('임베드 URL 영상').getAttribute('src')).toBe(
      'https://www.youtube.com/embed/def',
    );
    expect(view.container.querySelectorAll('iframe')).toHaveLength(2);
  });

  it('YouTube로 확인할 수 없는 URL은 기존 링크로 표시한다', async () => {
    const view = renderPage();

    expect(await view.findByText('허용되지 않은 영상 링크')).toBeTruthy();
    expect(view.queryByTitle('허용되지 않은 영상 링크')).toBeNull();
    expect(
      view.getByRole('link', { name: /https:\/\/example.com\/video/ }).getAttribute('href'),
    ).toBe('https://example.com/video');
  });
});
