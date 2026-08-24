import assert from 'node:assert/strict';
import test from 'node:test';
import axios, { AxiosError, type AxiosAdapter } from 'axios';
import { ApiError } from '../../src/api/error.ts';
import { buildNewsSearchUrl, searchNews } from '../../src/features/search/api.ts';
import {
  CATEGORY_LABELS,
  getCategoryLabel,
  isCategory,
} from '../../src/features/news/newslist/types.ts';

test('백엔드 Category enum과 같은 카테고리를 사용한다', () => {
  assert.deepEqual(
    ['ALL', ...Object.keys(CATEGORY_LABELS)],
    [
      'ALL',
      'REAL_ESTATE',
      'DEPOSIT_SAVINGS',
      'STOCK',
      'ECONOMY',
      'ETF',
      'BOND',
      'PENSION',
      'FOREIGN_EXCHANGE',
      'VIRTUAL_ASSET',
      'GOLD',
    ],
  );
  assert.equal(getCategoryLabel('FOREIGN_EXCHANGE'), '외화·환율');
  assert.equal(isCategory('POLICY'), false);
});

test('검색어와 선택한 카테고리로 뉴스 검색 URL을 만든다', () => {
  assert.equal(
    buildNewsSearchUrl('기준 금리', 'ALL'),
    '/api/search/news?q=%EA%B8%B0%EC%A4%80+%EA%B8%88%EB%A6%AC&page=1&size=10',
  );
  assert.equal(
    buildNewsSearchUrl('금', 'GOLD', 2, 10),
    '/api/search/news?q=%EA%B8%88&page=2&size=10&category=GOLD',
  );
});

const originalAdapter = axios.defaults.adapter;

test.afterEach(() => {
  axios.defaults.adapter = originalAdapter;
});

test('검색 성공 응답을 뉴스 카드 데이터로 읽는다', async () => {
  axios.defaults.adapter = (async (config) => ({
    data: {
      content: [
        {
          id: 1,
          title: '기준금리 동결 가능성 확대',
          description: '기준금리가 유지되며 채권 시장의 관심이 커지고 있습니다.',
          imageUrl: 'https://example.com/base-rate.webp',
          category: 'ECONOMY',
          publishedAt: '2026-08-14T10:00:00',
        },
      ],
      page: 1,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })) satisfies AxiosAdapter;

  const results = await searchNews('금리', 'ECONOMY');

  assert.equal(results.content.length, 1);
  assert.equal(results.content[0]?.category, 'ECONOMY');
  assert.equal(
    results.content[0]?.description,
    '기준금리가 유지되며 채권 시장의 관심이 커지고 있습니다.',
  );
  assert.equal(results.content[0]?.imageUrl, 'https://example.com/base-rate.webp');
  assert.equal(results.page, 1);
  assert.equal(results.totalElements, 1);
});

test('검색 페이지 조건 실패 응답의 도메인 code와 message를 전달한다', async () => {
  axios.defaults.adapter = (async (config) => {
    throw new AxiosError('Bad Request', 'ERR_BAD_REQUEST', config, undefined, {
      data: {
        code: 'INVALID_REQUEST',
        message: '검색 페이지 조건이 올바르지 않습니다.',
      },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config,
    });
  }) satisfies AxiosAdapter;

  await assert.rejects(
    () => searchNews('금리', 'ALL', 0),
    (error: unknown) =>
      error instanceof ApiError &&
      error.code === 'INVALID_REQUEST' &&
      error.message === '검색 페이지 조건이 올바르지 않습니다.',
  );
});
