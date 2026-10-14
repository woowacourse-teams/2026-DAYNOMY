/** @vitest-environment jsdom */
import { StrictMode } from 'react';
import { act, cleanup, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { trackEvent } from '../../src/analytics';
import { NewsDetailPage } from '../../src/features/news/newsdetail/NewsDetailPage';
import { getNewsDetail } from '../../src/features/news/newsdetail/api';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
const news = {
  id: 7,
  title: '기사',
  category: 'ECONOMY',
  publishedAt: '2026-10-13',
  content: '본문',
  sources: [],
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  window.history.replaceState({}, '', '/');
});

it.each([200, 404, 500])(
  '키워드 조회 %s의 빈 결과와 실패를 구분하고 시장 분석 성공을 보존한다',
  async (status) => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        if (url.endsWith('/keywords')) return json({ keywords: [] }, status);
        if (url.endsWith('/market-analysis')) return json({ risk: '위험', watchPoint: '지표' });
        return json(news);
      }),
    );
    const onLoad = vi.fn();
    const result = await getNewsDetail('7', undefined, onLoad);
    expect(result.keywords).toEqual([]);
    expect(result.marketAnalysis.status).toBe('success');
    expect(onLoad).toHaveBeenCalledWith(
      'keywords',
      expect.any(Number),
      status === 500 ? 'error' : 'empty',
      0,
    );
    expect(onLoad).toHaveBeenCalledWith('market-analysis', expect.any(Number), 'success', 2);
  },
);

it('각 분석 요청의 완료 시점에 별도로 기록하고 취소 이후에는 기록하지 않는다', async () => {
  let finishKeywords!: (response: Response) => void;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (url.endsWith('/keywords'))
        return new Promise<Response>((resolve) => {
          finishKeywords = resolve;
        });
      if (url.endsWith('/market-analysis')) return json({ risk: '', watchPoint: '' });
      return json(news);
    }),
  );
  const controller = new AbortController();
  const onLoad = vi.fn();
  const pending = getNewsDetail('7', controller.signal, onLoad);
  await waitFor(() =>
    expect(onLoad).toHaveBeenCalledWith('market-analysis', expect.any(Number), 'empty', 0),
  );
  controller.abort();
  finishKeywords(json({ keywords: [] }));
  await pending;
  expect(onLoad).toHaveBeenCalledTimes(1);
});

it('StrictMode에서 유효한 요청만 집계하고 시장 분석 실제 노출은 한 번만 기록한다', async () => {
  let intersect!: IntersectionObserverCallback;
  const observe = vi.fn();
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      constructor(callback: IntersectionObserverCallback) {
        intersect = callback;
      }
      observe = observe;
      disconnect() {}
    },
  );
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      if (url.endsWith('/keywords')) return json({ keywords: [] });
      if (url.endsWith('/market-analysis')) return json({ risk: '위험', watchPoint: '지표' });
      return json(news);
    }),
  );
  window.history.replaceState({}, '', '/news/7');
  const view = render(
    <StrictMode>
      <MemoryRouter>
        <NewsDetailPage />
      </MemoryRouter>
    </StrictMode>,
  );
  await view.findByText('지표');
  // DOM 표시만으로 useEffect의 관찰자 등록 완료를 보장할 수 없다.
  await waitFor(() =>
    expect(observe).toHaveBeenCalledWith(view.getByRole('heading', { name: '시장 분석' })),
  );
  const calls = () => vi.mocked(trackEvent).mock.calls;
  expect(calls().filter(([name]) => name === 'content_load_result')).toHaveLength(2);
  expect(calls().filter(([name]) => name === 'view_market_analysis')).toHaveLength(0);
  act(() =>
    intersect(
      [{ isIntersecting: true, intersectionRatio: 0.2 } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  );
  expect(calls().filter(([name]) => name === 'view_market_analysis')).toHaveLength(0);
  act(() =>
    intersect(
      [{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  );
  act(() =>
    intersect(
      [{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  );
  expect(calls().filter(([name]) => name === 'view_market_analysis')).toHaveLength(1);
  expect(trackEvent).toHaveBeenCalledWith(
    'content_load_result',
    expect.objectContaining({
      surface: 'news_detail',
      area: 'keywords',
      status: 'empty',
      attempt: 0,
    }),
  );
});
