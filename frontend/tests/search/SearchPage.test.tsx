/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import SearchPage from '../../src/features/search/SearchPage';

const originalAdapter = axios.defaults.adapter;
const article = {
  kind: 'NEWS',
  id: 1,
  source: 'DAYNOMY',
  title: '기준금리 동결 가능성 확대',
  date: '2026-08-14T10:00:00Z',
  imageUrl: '/test-news.jpg',
  category: 'ECONOMY',
};
const personPost = {
  kind: 'PERSON_POST',
  id: 2,
  source: 'X',
  title: '기준금리 전망',
  date: '2026-08-14T09:00:00Z',
  url: 'https://x.com/example/status/123',
  personId: 3,
  personName: '경제 연구원',
};
const video = {
  kind: 'ASSET_CONTENT',
  id: 4,
  source: 'YOUTUBE',
  title: '기준금리 해설 영상',
  date: '2026-08-14T08:00:00Z',
  url: 'https://www.youtube.com/watch?v=abcdefghijk',
  assetId: 5,
  imageUrl: null,
  publishedDate: null,
};

function response(
  config: InternalAxiosRequestConfig,
  items: object[],
  totalPages = items.length ? 1 : 0,
) {
  return {
    data: {
      items,
      page: Number(new URL(config.url ?? '', 'http://localhost').searchParams.get('page') ?? 1),
      size: 12,
      totalElements: items.length,
      totalPages,
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}

function renderSearch(path = '/search?q=금리&source=ALL&page=1') {
  const router = createMemoryRouter([{ path: '/search', element: <SearchPage /> }], {
    initialEntries: [path],
  });
  return { router, ...render(<RouterProvider router={router} />) };
}

Element.prototype.scrollIntoView = () => undefined;

afterEach(() => {
  cleanup();
  axios.defaults.adapter = originalAdapter;
});

describe('소식 검색 화면', () => {
  it('기사·인물 글·영상을 소식 목록으로 표시하고 외부 콘텐츠를 펼친다', async () => {
    axios.defaults.adapter = (async (config) =>
      response(config, [article, personPost, video])) satisfies AxiosAdapter;
    const view = renderSearch();

    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();
    expect(view.getByRole('link', { name: '경제 연구원' })).toBeTruthy();
    expect(view.container.querySelectorAll('.issue-feed-row--visual')).toHaveLength(3);
    const thumbnail = view.container.querySelector<HTMLImageElement>('img[src="/test-news.jpg"]')!;
    expect(thumbnail).toBeTruthy();
    fireEvent.error(thumbnail);
    expect(thumbnail.hidden).toBe(true);
    expect(
      view.container.querySelector('img[src="https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"]'),
    ).toBeTruthy();
    expect(
      view.container.querySelector('.issue-feed-thumbnail-x .issue-feed-thumbnail-fallback img'),
    ).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: /기준금리 해설 영상/ }));
    expect(view.getByTitle('기준금리 해설 영상')).toBeTruthy();
    expect(view.getByRole('link', { name: /YouTube에서 원문 보기/ })).toBeTruthy();
  });

  it('출처를 바꾸면 첫 페이지부터 다시 검색한다', async () => {
    const urls: string[] = [];
    axios.defaults.adapter = (async (config) => {
      urls.push(config.url ?? '');
      return response(config, [video], 2);
    }) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=금리&source=ALL&page=2');
    await view.findByRole('button', { name: /기준금리 해설 영상/ });

    fireEvent.click(view.getByRole('button', { name: 'YouTube' }));
    await waitFor(() =>
      expect(view.router.state.location.search).toBe('?q=%EA%B8%88%EB%A6%AC&source=YOUTUBE&page=1'),
    );
    await waitFor(() => expect(urls.at(-1)).toContain('source=YOUTUBE'));
    expect(urls.at(-1)).toContain('page=1');
  });

  it('페이지 이동과 뒤로 가기에서 URL 검색 조건을 복원한다', async () => {
    const urls: string[] = [];
    axios.defaults.adapter = (async (config) => {
      urls.push(config.url ?? '');
      return response(config, [article], 3);
    }) satisfies AxiosAdapter;
    const view = renderSearch();
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });

    fireEvent.click(view.getByRole('button', { name: '2페이지' }));
    await waitFor(() => expect(view.router.state.location.search).toContain('page=2'));
    await waitFor(() => expect(urls.at(-1)).toContain('page=2'));
    await view.router.navigate(-1);
    await waitFor(() => expect(view.router.state.location.search).toContain('page=1'));
    await waitFor(() => expect(urls.at(-1)).toContain('page=1'));
  });

  it('과거 카테고리 URL은 전체 출처로 안전하게 처리한다', async () => {
    axios.defaults.adapter = (async (config) => response(config, [article])) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=금리&category=ETF&page=1');
    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();
    expect(view.getByRole('button', { name: '전체' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('인기순 전환은 첫 페이지로 이동하고 출처·페이지·이력 이동에서도 정렬을 유지한다', async () => {
    const urls: URL[] = [];
    axios.defaults.adapter = (async (config) => {
      const url = new URL(config.url!, 'http://localhost');
      urls.push(url);
      return response(
        config,
        url.searchParams.get('sort') === 'POPULAR' ? [video, article] : [article, video],
        3,
      );
    }) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=금리&source=ALL&page=2');
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });
    expect(view.getByRole('button', { name: '최신순' }).getAttribute('aria-pressed')).toBe('true');

    fireEvent.click(view.getByRole('button', { name: '인기순' }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get('sort')).toBe('POPULAR'));
    expect(urls.at(-1)?.searchParams.get('page')).toBe('1');
    expect(new URLSearchParams(view.router.state.location.search).get('sort')).toBe('popular');
    await waitFor(() =>
      expect(view.container.querySelector('.issue-row-title')?.textContent).toContain(video.title),
    );

    fireEvent.click(view.getByRole('button', { name: '2페이지' }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get('page')).toBe('2'));
    expect(urls.at(-1)?.searchParams.get('sort')).toBe('POPULAR');
    fireEvent.click(view.getByRole('button', { name: 'YouTube' }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get('source')).toBe('YOUTUBE'));
    expect(urls.at(-1)?.searchParams.get('page')).toBe('1');
    expect(urls.at(-1)?.searchParams.get('sort')).toBe('POPULAR');

    await act(() => view.router.navigate(-1));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get('page')).toBe('2'));
    expect(urls.at(-1)?.searchParams.has('source')).toBe(false);
    expect(urls.at(-1)?.searchParams.get('sort')).toBe('POPULAR');
    await act(() => view.router.navigate(1));
    await waitFor(() => expect(urls.at(-1)?.searchParams.get('source')).toBe('YOUTUBE'));

    const path = `/search${view.router.state.location.search}`;
    view.unmount();
    const reloaded = renderSearch(path);
    await reloaded.findByRole('button', { name: /기준금리 해설 영상/ });
    expect(reloaded.getByRole('button', { name: '인기순' }).getAttribute('aria-pressed')).toBe(
      'true',
    );
    fireEvent.click(reloaded.getByRole('button', { name: '최신순' }));
    await waitFor(() => expect(urls.at(-1)?.searchParams.has('sort')).toBe(false));
    expect(urls.at(-1)?.searchParams.get('source')).toBe('YOUTUBE');
    expect(urls.at(-1)?.searchParams.get('page')).toBe('1');
  });

  it('알 수 없는 URL 정렬은 최신순으로 처리한다', async () => {
    axios.defaults.adapter = (async (config) => {
      expect(new URL(config.url!, 'http://localhost').searchParams.has('sort')).toBe(false);
      return response(config, [article]);
    }) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=금리&sort=unknown');
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });
    expect(view.getByRole('button', { name: '최신순' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('범위를 벗어난 페이지를 보정할 때 인기순을 유지한다', async () => {
    const urls: URL[] = [];
    axios.defaults.adapter = (async (config) => {
      urls.push(new URL(config.url!, 'http://localhost'));
      return response(config, [article], 2);
    }) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=금리&source=ALL&sort=popular&page=99');
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });
    expect(new URLSearchParams(view.router.state.location.search).get('page')).toBe('2');
    expect(new URLSearchParams(view.router.state.location.search).get('sort')).toBe('popular');
    expect(urls.at(-1)?.searchParams.get('page')).toBe('2');
    expect(urls.at(-1)?.searchParams.get('sort')).toBe('POPULAR');
  });

  it('늦게 도착한 최신순 응답이 선택한 인기순 결과를 덮어쓰지 않는다', async () => {
    let finishLatest: (() => void) | undefined;
    axios.defaults.adapter = (async (config) => {
      if (new URL(config.url!, 'http://localhost').searchParams.get('sort') === 'POPULAR')
        return response(config, [video]);
      return new Promise<ReturnType<typeof response>>((resolve) => {
        finishLatest = () => resolve(response(config, [article]));
      });
    }) satisfies AxiosAdapter;
    const view = renderSearch();
    await waitFor(() => expect(finishLatest).toBeTypeOf('function'));
    fireEvent.click(view.getByRole('button', { name: '인기순' }));
    await view.findByRole('button', { name: /기준금리 해설 영상/ });
    await act(async () => finishLatest!());
    expect(view.queryByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeNull();
    expect(view.getByRole('button', { name: /기준금리 해설 영상/ })).toBeTruthy();
  });

  it('검색 결과가 없으면 빈 상태를 표시한다', async () => {
    axios.defaults.adapter = (async (config) => response(config, [])) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=없는소식&page=1');
    expect(await view.findByText('검색된 소식이 없습니다.')).toBeTruthy();
  });

  it('오류 후 같은 조건으로 다시 시도한다', async () => {
    let requestCount = 0;
    const sorts: (string | null)[] = [];
    axios.defaults.adapter = (async (config) => {
      requestCount += 1;
      sorts.push(new URL(config.url!, 'http://localhost').searchParams.get('sort'));
      if (requestCount === 1) {
        throw new AxiosError('Bad Request', 'ERR_BAD_REQUEST', config, undefined, {
          data: { code: 'INVALID_REQUEST', message: '검색 요청을 처리하지 못했습니다.' },
          status: 400,
          statusText: 'Bad Request',
          headers: {},
          config,
        });
      }
      return response(config, [article]);
    }) satisfies AxiosAdapter;

    const view = renderSearch('/search?q=금리&sort=popular');
    expect((await view.findByRole('alert')).textContent).toContain(
      '검색 요청을 처리하지 못했습니다.',
    );
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(requestCount).toBe(2));
    expect(sorts).toEqual(['POPULAR', 'POPULAR']);
    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();
  });
});
