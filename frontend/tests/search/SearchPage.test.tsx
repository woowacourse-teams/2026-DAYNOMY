/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import SearchPage from '../../src/features/search/SearchPage';

const originalAdapter = axios.defaults.adapter;
const article = {
  id: 1,
  title: '기준금리 동결 가능성 확대',
  imageUrl: null,
  category: 'ECONOMY',
  publishedAt: '2026-08-14T10:00:00',
};

function response(
  config: InternalAxiosRequestConfig,
  content: (typeof article)[],
  totalPages = content.length ? 1 : 0,
) {
  return {
    data: { content, page: 1, size: 10, totalElements: content.length, totalPages },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}

function renderSearch(path = '/search?q=금리&category=ALL&page=1') {
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

describe('뉴스 검색 화면', () => {
  it('검색 카테고리와 뉴스 결과를 표시한다', async () => {
    axios.defaults.adapter = (async (config) => response(config, [article])) satisfies AxiosAdapter;
    const view = renderSearch();
    const categories = view.getByRole('navigation', { name: '뉴스 카테고리' });

    expect(
      within(categories)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['전체', '주식', 'ETF', '부동산']);
    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();
  });

  it('검색 결과가 없으면 빈 상태를 표시한다', async () => {
    axios.defaults.adapter = (async (config) => response(config, [])) satisfies AxiosAdapter;
    const view = renderSearch('/search?q=없는뉴스&category=ALL&page=1');
    expect(await view.findByText('검색된 결과가 없습니다.')).toBeTruthy();
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

  it('카테고리를 URL에 반영한다', async () => {
    axios.defaults.adapter = (async (config) =>
      response(config, [article], 2)) satisfies AxiosAdapter;
    const view = renderSearch();
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });
    fireEvent.click(view.getByRole('button', { name: 'ETF' }));
    await waitFor(() =>
      expect(new URLSearchParams(view.router.state.location.search).get('category')).toBe('ETF'),
    );
  });
});
