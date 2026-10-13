/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
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
  imageUrl: null,
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

  it('검색 결과가 없으면 빈 상태를 표시한다', async () => {
    axios.defaults.adapter = (async (config) => response(config, [])) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=없는소식&page=1');
    expect(await view.findByText('검색된 소식이 없습니다.')).toBeTruthy();
  });

  it('오류 후 같은 조건으로 다시 시도한다', async () => {
    let requestCount = 0;
    axios.defaults.adapter = (async (config) => {
      requestCount += 1;
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

    const view = renderSearch();
    expect((await view.findByRole('alert')).textContent).toContain(
      '검색 요청을 처리하지 못했습니다.',
    );
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));
    await waitFor(() => expect(requestCount).toBe(2));
    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();
  });
});
