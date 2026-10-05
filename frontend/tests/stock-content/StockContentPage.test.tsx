/** @vitest-environment jsdom */

import { cleanup, fireEvent, render } from '@testing-library/react';
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
            id: 9,
            assetId: 1,
            sourceType: 'INTERNAL_NEWS',
            title: '삼성전자 관련 DAYNOMY 이슈',
            url: '/news/30',
            imageUrl: 'https://example.com/news-thumbnail.jpg',
            createdAt: null,
          },
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
  it('DAYNOMY 이슈는 응답 이미지 URL을 썸네일로 표시한다', async () => {
    const view = renderPage();

    await view.findByText('삼성전자 관련 DAYNOMY 이슈');
    const thumbnail = view.container.querySelector('.stock-content-news-thumbnail');

    expect(thumbnail?.getAttribute('src')).toBe('https://example.com/news-thumbnail.jpg');
    expect(view.getAllByText('DAYNOMY 이슈')).toHaveLength(2);
  });

  it('재생 버튼을 누르면 선택한 영상만 큰 모달에서 재생한다', async () => {
    const view = renderPage();

    expect(view.container.querySelectorAll('iframe')).toHaveLength(0);

    const trigger = await view.findByRole('button', { name: '삼성전자 분석 영상 크게 재생' });
    fireEvent.click(trigger);

    expect(view.getByRole('dialog')).toBeTruthy();
    const closeButton = view.getByRole('button', { name: '영상 닫기' });
    expect(document.activeElement).toBe(closeButton);
    expect(view.getByTitle('삼성전자 분석 영상').getAttribute('src')).toBe(
      'https://www.youtube.com/embed/abc?autoplay=1&playsinline=1&rel=0',
    );
    expect(view.container.querySelectorAll('iframe')).toHaveLength(1);

    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(view.getByTitle('삼성전자 분석 영상'));
    fireEvent.keyDown(window, { key: 'Tab' });
    expect(document.activeElement).toBe(closeButton);

    fireEvent.click(closeButton);
    expect(view.queryByRole('dialog')).toBeNull();
    expect(document.activeElement).toBe(trigger);
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
