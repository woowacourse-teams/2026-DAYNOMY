/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import SearchPage from '../../src/features/search/SearchPage';

const originalAdapter = axios.defaults.adapter;
const article = {
  id: 1,
  title: '기준금리 동결 가능성 확대',
  description: '기준금리가 유지되고 있습니다.',
  imageUrl: null,
  category: 'ECONOMY',
  publishedAt: '2026-08-14T10:00:00',
};

type ResponseOptions = {
  content: (typeof article)[];
  page?: number;
  totalElements?: number;
  totalPages?: number;
};

function response(
  config: InternalAxiosRequestConfig,
  {
    content,
    page = 1,
    totalElements = content.length,
    totalPages = content.length ? 1 : 0,
  }: ResponseOptions,
) {
  return {
    data: {
      content,
      page,
      size: 10,
      totalElements,
      totalPages,
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}

function renderSearch(initialEntries = ['/search']) {
  const router = createMemoryRouter([{ path: '/search', element: <SearchPage /> }], {
    initialEntries,
  });

  return { router, ...render(<RouterProvider router={router} />) };
}

function submitSearch(keyword: string) {
  const view = renderSearch();
  const input = view.getByLabelText('뉴스 키워드 검색');

  fireEvent.change(input, { target: { value: keyword } });
  fireEvent.click(view.getByRole('button', { name: '뉴스 검색' }));

  return view;
}

function getCurrentSearchParams(router: ReturnType<typeof createMemoryRouter>) {
  return new URLSearchParams(router.state.location.search);
}

Element.prototype.scrollIntoView = () => undefined;

afterEach(() => {
  cleanup();
  axios.defaults.adapter = originalAdapter;
});

describe('뉴스 검색 화면', () => {
  it('백엔드와 같은 검색어 조건을 적용한다', () => {
    const view = renderSearch();
    const input = view.getByLabelText('뉴스 키워드 검색') as HTMLInputElement;

    expect(input.required).toBe(true);
    expect(input.maxLength).toBe(100);
    expect(input.pattern).toBe('.*[\\p{L}\\p{N}].*');

    fireEvent.change(input, { target: { value: '   ' } });
    expect(input.checkValidity()).toBe(false);

    fireEvent.change(input, { target: { value: '!!!' } });
    expect(input.checkValidity()).toBe(false);
  });

  it('검색 결과를 뉴스 링크로 표시한다', async () => {
    axios.defaults.adapter = (async (config) =>
      response(config, { content: [article] })) satisfies AxiosAdapter;

    const view = submitSearch('금리');
    const link = (await view.findByRole('link', {
      name: /기준금리 동결 가능성 확대/,
    })) as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/news/1');
    expect(view.getByText('1건')).toBeTruthy();
  });

  it('검색 결과가 없으면 빈 결과를 표시한다', async () => {
    axios.defaults.adapter = (async (config) =>
      response(config, { content: [] })) satisfies AxiosAdapter;

    const view = submitSearch('없는 뉴스');

    expect(await view.findByText('검색 결과가 없습니다.')).toBeTruthy();
    expect(view.getByText('0건')).toBeTruthy();
  });

  it('오류 후 같은 검색 조건으로 다시 시도한다', async () => {
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

      return response(config, { content: [article] });
    }) satisfies AxiosAdapter;

    const view = submitSearch('금리');

    expect((await view.findByRole('alert')).textContent).toContain(
      '검색 요청을 처리하지 못했습니다.',
    );
    fireEvent.click(view.getByRole('button', { name: '뉴스 검색' }));

    await waitFor(() => expect(requestCount).toBe(2));
    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();
  });

  it('URL 직접 접근 시 검색 조건을 복원한다', async () => {
    let requestedUrl = '';
    axios.defaults.adapter = (async (config) => {
      requestedUrl = config.url ?? '';
      return response(config, { content: [article], page: 2, totalElements: 11, totalPages: 2 });
    }) satisfies AxiosAdapter;

    const view = renderSearch(['/search?q=금리&category=ECONOMY&page=2']);

    expect((view.getByLabelText('뉴스 키워드 검색') as HTMLInputElement).value).toBe('금리');
    expect(view.getByRole('button', { name: '경제지표' }).className).toBe('active');
    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();

    const requestParams = new URL(requestedUrl, 'http://localhost').searchParams;
    expect(requestParams.get('q')).toBe('금리');
    expect(requestParams.get('category')).toBe('ECONOMY');
    expect(requestParams.get('page')).toBe('2');
  });

  it('검색어와 카테고리와 페이지를 URL에 동기화한다', async () => {
    axios.defaults.adapter = (async (config) =>
      response(config, {
        content: [article],
        totalElements: 21,
        totalPages: 3,
      })) satisfies AxiosAdapter;

    const view = submitSearch('금리');
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });

    expect(Object.fromEntries(getCurrentSearchParams(view.router))).toEqual({
      q: '금리',
      category: 'ALL',
      page: '1',
    });

    fireEvent.click(view.getByRole('button', { name: '채권' }));
    await waitFor(() => expect(getCurrentSearchParams(view.router).get('category')).toBe('BOND'));
    expect(getCurrentSearchParams(view.router).get('page')).toBe('1');

    fireEvent.click(view.getByRole('button', { name: '2' }));
    await waitFor(() => expect(getCurrentSearchParams(view.router).get('page')).toBe('2'));
  });

  it('브라우저 뒤로 가기와 앞으로 가기 시 검색 상태를 복원한다', async () => {
    axios.defaults.adapter = (async (config) =>
      response(config, { content: [article] })) satisfies AxiosAdapter;

    const view = submitSearch('금리');
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });
    fireEvent.click(view.getByRole('button', { name: '채권' }));
    await waitFor(() => expect(getCurrentSearchParams(view.router).get('category')).toBe('BOND'));

    await act(async () => {
      await view.router.navigate(-1);
    });

    expect(getCurrentSearchParams(view.router).get('category')).toBe('ALL');
    expect(view.getByRole('button', { name: '전체' }).className).toBe('active');

    await act(async () => {
      await view.router.navigate(1);
    });

    expect(getCurrentSearchParams(view.router).get('category')).toBe('BOND');
    expect(view.getByRole('button', { name: '채권' }).className).toBe('active');
  });

  it('유효하지 않은 URL 카테고리와 페이지를 기본값으로 처리한다', async () => {
    let requestedUrl = '';
    axios.defaults.adapter = (async (config) => {
      requestedUrl = config.url ?? '';
      return response(config, { content: [article] });
    }) satisfies AxiosAdapter;

    const view = renderSearch(['/search?q=금리&category=UNKNOWN&page=invalid']);

    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });
    expect(view.getByRole('button', { name: '전체' }).className).toBe('active');

    const requestParams = new URL(requestedUrl, 'http://localhost').searchParams;
    expect(requestParams.has('category')).toBe(false);
    expect(requestParams.get('page')).toBe('1');
  });

  it('현재 페이지 주변의 페이지 번호만 표시한다', async () => {
    axios.defaults.adapter = (async (config) => {
      const requestedPage = Number(
        new URL(config.url ?? '', 'http://localhost').searchParams.get('page'),
      );
      return response(config, {
        content: [article],
        page: requestedPage,
        totalElements: 200,
        totalPages: 20,
      });
    }) satisfies AxiosAdapter;

    const view = renderSearch(['/search?q=금리&category=ALL&page=1']);
    await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ });

    let pagination = within(view.getByRole('navigation', { name: '검색 결과 페이지' }));
    expect(pagination.getAllByRole('button').map((button) => button.textContent)).toEqual([
      '이전',
      '1',
      '2',
      '3',
      '4',
      '5',
      '다음',
    ]);
    expect((pagination.getByRole('button', { name: '이전' }) as HTMLButtonElement).disabled).toBe(
      true,
    );

    await act(async () => {
      await view.router.navigate('/search?q=금리&category=ALL&page=10');
    });
    await waitFor(() =>
      expect(view.getByRole('button', { current: 'page' }).textContent).toBe('10'),
    );

    pagination = within(view.getByRole('navigation', { name: '검색 결과 페이지' }));
    expect(pagination.getAllByRole('button').map((button) => button.textContent)).toEqual([
      '이전',
      '8',
      '9',
      '10',
      '11',
      '12',
      '다음',
    ]);

    await act(async () => {
      await view.router.navigate('/search?q=금리&category=ALL&page=20');
    });
    await waitFor(() =>
      expect(view.getByRole('button', { current: 'page' }).textContent).toBe('20'),
    );

    pagination = within(view.getByRole('navigation', { name: '검색 결과 페이지' }));
    expect(pagination.getAllByRole('button').map((button) => button.textContent)).toEqual([
      '이전',
      '16',
      '17',
      '18',
      '19',
      '20',
      '다음',
    ]);
    expect((pagination.getByRole('button', { name: '다음' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
  });
});
