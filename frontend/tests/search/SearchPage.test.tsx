/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import axios, { AxiosError, type AxiosAdapter, type InternalAxiosRequestConfig } from 'axios';
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

function response(config: InternalAxiosRequestConfig, content: (typeof article)[]) {
  return {
    data: {
      content,
      page: 1,
      size: 10,
      totalElements: content.length,
      totalPages: content.length > 0 ? 1 : 0,
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}

function submitSearch(keyword: string) {
  const view = render(<SearchPage />);
  const input = view.getByLabelText('뉴스 키워드 검색');

  fireEvent.change(input, { target: { value: keyword } });
  fireEvent.click(view.getByRole('button', { name: '뉴스 검색' }));

  return view;
}

afterEach(() => {
  cleanup();
  axios.defaults.adapter = originalAdapter;
});

describe('뉴스 검색 화면', () => {
  it('백엔드와 같은 검색어 조건을 적용한다', () => {
    const view = render(<SearchPage />);
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
    axios.defaults.adapter = (async (config) => response(config, [article])) satisfies AxiosAdapter;

    const view = submitSearch('금리');
    const link = (await view.findByRole('link', {
      name: /기준금리 동결 가능성 확대/,
    })) as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/news/1');
    expect(view.getByText('1건')).toBeTruthy();
  });

  it('검색 결과가 없으면 빈 결과를 표시한다', async () => {
    axios.defaults.adapter = (async (config) => response(config, [])) satisfies AxiosAdapter;

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

      return response(config, [article]);
    }) satisfies AxiosAdapter;

    const view = submitSearch('금리');

    expect((await view.findByRole('alert')).textContent).toContain(
      '검색 요청을 처리하지 못했습니다.',
    );
    fireEvent.click(view.getByRole('button', { name: '뉴스 검색' }));

    await waitFor(() => expect(requestCount).toBe(2));
    expect(await view.findByRole('link', { name: /기준금리 동결 가능성 확대/ })).toBeTruthy();
  });
});
