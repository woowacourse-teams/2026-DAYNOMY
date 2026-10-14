/** @vitest-environment jsdom */
import { useAuth } from '../../src/hooks/useLoginStatus';
vi.mock('../../src/hooks/useLoginStatus', () => ({ useAuth: vi.fn() }));
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
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
  vi.mocked(useAuth).mockReturnValue({ isLoggedIn: true, loading: false } as ReturnType<
    typeof useAuth
  >);
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);

      if (url.endsWith('/api/users/me/portfolio')) {
        return jsonResponse({
          totalRealizedProfitLoss: 0,
          holdings: [
            {
              assetId: 1,
              assetCode: '005930',
              name: '삼성전자',
              category: 'STOCK',
              market: 'KOSPI',
              quantity: 10,
              averagePurchasePrice: 70000,
            },
            {
              assetId: 2,
              assetCode: '000660',
              name: 'SK하이닉스',
              category: 'STOCK',
              market: 'KOSPI',
              quantity: 5,
              averagePurchasePrice: 150000,
            },
          ],
        });
      }

      if (url.endsWith('/api/assets/2/contents')) {
        return jsonResponse({
          assetId: 2,
          assetCode: '000660',
          assetName: 'SK하이닉스',
          contents: [],
        });
      }

      return jsonResponse({
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
            publishedDate: '2020-04-05',
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
          {
            id: 13,
            assetId: 1,
            sourceType: 'THREADS',
            title: '삼성전자 Threads 게시물',
            url: 'https://www.threads.net/@investor/post/ABC',
            imageUrl: null,
            createdAt: null,
          },
          {
            id: 14,
            assetId: 1,
            sourceType: 'X',
            title: '삼성전자 X 게시물',
            url: 'https://x.com/investor/status/12345',
            imageUrl: null,
            createdAt: null,
          },
        ],
      });
    }),
  );
});

afterEach(() => {
  cleanup();
  sessionStorage.clear();
  vi.mocked(trackEvent).mockClear();
  document.getElementById('daynomy-threads-embed-script')?.remove();
  document.getElementById('daynomy-x-embed-script')?.remove();
  delete (window as typeof window & { twttr?: unknown }).twttr;
  vi.unstubAllGlobals();
});

describe('종목 관련 자료 화면의 YouTube 임베드', () => {
  it('제목과 별도로 확인된 원문 게시일만 표시한다', async () => {
    const view = renderPage();
    const date = await view.findByText('게시일 2020.04.05');
    expect(date.getAttribute('datetime')).toBe('2020-04-05');
    expect(view.getByText('삼성전자 분석 영상')).toBeTruthy();
    expect(view.container.querySelectorAll('time')).toHaveLength(1);
  });
  it('보유 종목을 사이드바에 표시하고 다른 종목 소식으로 이동한다', async () => {
    const view = renderPage();

    const sidebar = await view.findByLabelText('내 포트폴리오 종목');
    const samsungLink = within(sidebar).getByRole('link', { name: /삼성전자/ });
    expect(samsungLink.getAttribute('aria-current')).toBe('page');

    fireEvent.click(within(sidebar).getByRole('link', { name: /SK하이닉스/ }));

    expect(await view.findByRole('heading', { name: 'SK하이닉스 소식' })).toBeTruthy();
    expect(view.getByRole('button', { name: 'YouTube 0개' })).toBeTruthy();
    expect(view.getByRole('button', { name: 'Threads 0개' })).toBeTruthy();
    expect(view.getByRole('button', { name: 'X 0개' })).toBeTruthy();
    expect(view.getByText('아직 등록된 관련 자료가 없습니다.')).toBeTruthy();
  });

  it('모든 출처를 탭으로 노출하고 선택한 출처만 표시한다', async () => {
    const view = renderPage();

    await view.findByRole('heading', { name: '삼성전자 소식' });
    expect(view.getByRole('button', { name: 'YouTube 3개' })).toBeTruthy();
    expect(view.getByRole('button', { name: 'Threads 1개' })).toBeTruthy();
    expect(view.getByRole('button', { name: 'X 1개' })).toBeTruthy();

    fireEvent.click(view.getByRole('button', { name: 'X 1개' }));
    expect(trackEvent).toHaveBeenCalledWith('filter_content_source', {
      surface: 'stock_contents',
      previous_source: 'ALL',
      source_type: 'X',
      result_count: 1,
    });

    expect(view.getByText('삼성전자 X 게시물')).toBeTruthy();
    expect(view.queryByText('삼성전자 관련 DAYNOMY 이슈')).toBeNull();
  });

  it('DAYNOMY 이슈는 응답 이미지 URL을 썸네일로 표시한다', async () => {
    const view = renderPage();

    await view.findByText('삼성전자 관련 DAYNOMY 이슈');
    const thumbnail = view.container.querySelector('.stock-content-news-thumbnail');

    expect(thumbnail?.getAttribute('src')).toBe('https://example.com/news-thumbnail.jpg');
    expect(view.getByText('DAYNOMY 이슈')).toBeTruthy();
  });

  it('재생 버튼을 누르면 선택한 영상만 큰 모달에서 재생한다', async () => {
    const view = renderPage();

    expect(view.container.querySelectorAll('iframe')).toHaveLength(0);

    const trigger = await view.findByRole('button', { name: '삼성전자 분석 영상 크게 재생' });
    fireEvent.click(trigger);
    expect(trackEvent).toHaveBeenCalledWith(
      'open_content_video',
      expect.objectContaining({ surface: 'stock_contents', source_type: 'YOUTUBE' }),
    );
    expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('삼성전자');

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

  it('Threads와 X 링크를 공식 위젯 마크업과 원문 링크로 표시한다', async () => {
    const view = renderPage();

    await view.findByText('삼성전자 Threads 게시물');
    const threadsEmbed = view.container.querySelector('blockquote.text-post-media');
    const xEmbed = view.container.querySelector('blockquote.twitter-tweet');

    expect(threadsEmbed?.getAttribute('data-text-post-permalink')).toBe(
      'https://www.threads.net/@investor/post/ABC',
    );
    expect(xEmbed?.querySelector('a')?.getAttribute('href')).toBe(
      'https://x.com/investor/status/12345',
    );
    await waitFor(() => {
      expect(document.getElementById('daynomy-threads-embed-script')?.getAttribute('src')).toBe(
        'https://www.threads.com/embed.js',
      );
      expect(document.getElementById('daynomy-x-embed-script')?.getAttribute('src')).toBe(
        'https://platform.x.com/widgets.js',
      );
    });
    expect(view.getByRole('link', { name: 'Threads에서 원문 보기' })).toBeTruthy();
    expect(view.getByRole('link', { name: 'X에서 원문 보기' })).toBeTruthy();
  });

  it('X와 Threads 필터로 전환하면 해당 임베드를 다시 초기화한다', async () => {
    const loadXEmbeds = vi.fn();
    (window as typeof window & { twttr?: { widgets: { load: typeof loadXEmbeds } } }).twttr = {
      widgets: { load: loadXEmbeds },
    };
    const view = renderPage();

    await view.findByRole('heading', { name: '삼성전자 소식' });
    const initialThreadsScript = document.getElementById('daynomy-threads-embed-script');
    expect(initialThreadsScript).toBeTruthy();
    loadXEmbeds.mockClear();

    fireEvent.click(view.getByRole('button', { name: 'X 1개' }));

    expect(view.container.querySelector('blockquote.twitter-tweet')).toBeTruthy();
    expect(loadXEmbeds).toHaveBeenCalledTimes(1);

    fireEvent.click(view.getByRole('button', { name: 'Threads 1개' }));

    expect(view.container.querySelector('blockquote.text-post-media')).toBeTruthy();
    await waitFor(() => {
      expect(document.getElementById('daynomy-threads-embed-script')).not.toBe(
        initialThreadsScript,
      );
    });
  });
});

it('비회원 종목 화면은 임시 보유 종목을 표시하고 회원 API를 호출하지 않는다', async () => {
  vi.mocked(useAuth).mockReturnValue({ isLoggedIn: false, loading: false } as ReturnType<
    typeof useAuth
  >);
  sessionStorage.setItem(
    'daynomy:guest-portfolio-holdings:v1',
    JSON.stringify([
      {
        assetId: 1,
        assetCode: '005930',
        name: '삼성전자',
        category: 'STOCK',
        market: 'KOSPI',
        quantity: 1,
        averagePurchasePrice: 70000,
      },
    ]),
  );
  const view = renderPage();
  const sidebar = await view.findByRole('complementary', { name: '내 포트폴리오 종목' });
  expect(within(sidebar).getByText('삼성전자')).toBeTruthy();
  expect(
    vi
      .mocked(fetch)
      .mock.calls.some(([input]) => String(input).includes('/api/users/me/portfolio')),
  ).toBe(false);
  vi.mocked(useAuth).mockReturnValue({ isLoggedIn: true, loading: false } as ReturnType<
    typeof useAuth
  >);
  view.rerender(
    <MemoryRouter initialEntries={['/stocks/1']}>
      <Routes>
        <Route path="/stocks/:assetId" element={<StockContentPage />} />
      </Routes>
    </MemoryRouter>,
  );
  expect(await within(sidebar).findByText('SK하이닉스')).toBeTruthy();
  expect(
    vi
      .mocked(fetch)
      .mock.calls.some(([input]) => String(input).includes('/api/users/me/portfolio')),
  ).toBe(true);
});
