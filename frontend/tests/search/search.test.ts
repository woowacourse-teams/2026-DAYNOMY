import assert from 'node:assert/strict';
import test from 'node:test';
import axios, { AxiosError, type AxiosAdapter } from 'axios';
import { ApiError } from '../../src/api/error.ts';
import { buildContentSearchUrl, searchContents } from '../../src/features/search/api.ts';

test('검색어와 출처·페이지로 소식 통합 검색 URL을 만든다', () => {
  assert.equal(
    buildContentSearchUrl('기준 금리', 'ALL'),
    '/api/search/contents?q=%EA%B8%B0%EC%A4%80+%EA%B8%88%EB%A6%AC&page=1&size=12',
  );
  assert.equal(
    buildContentSearchUrl('금리', 'YOUTUBE', 2),
    '/api/search/contents?q=%EA%B8%88%EB%A6%AC&page=2&size=12&source=YOUTUBE',
  );
});

const originalAdapter = axios.defaults.adapter;
test.afterEach(() => {
  axios.defaults.adapter = originalAdapter;
});

test('기사·인물 글·외부 콘텐츠 응답을 소식 피드 데이터로 읽는다', async () => {
  axios.defaults.adapter = (async (config) => ({
    data: {
      items: [
        {
          kind: 'NEWS',
          id: 1,
          source: 'DAYNOMY',
          title: '기준금리 동결',
          date: '2026-08-14T10:00:00Z',
          imageUrl: null,
          category: 'ECONOMY',
        },
        {
          kind: 'PERSON_POST',
          id: 2,
          source: 'X',
          title: '기준금리 전망',
          date: '2026-08-14T09:00:00Z',
          url: 'https://x.com/example/status/123',
          personId: 3,
          personName: '경제 연구원',
        },
        {
          kind: 'ASSET_CONTENT',
          id: 4,
          source: 'YOUTUBE',
          title: '기준금리 해설',
          date: '2026-08-14T08:00:00Z',
          url: 'https://youtu.be/abcdefghijk',
          assetId: 5,
          imageUrl: null,
          publishedDate: null,
        },
      ],
      page: 1,
      size: 12,
      totalElements: 3,
      totalPages: 1,
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  })) satisfies AxiosAdapter;

  const result = await searchContents('금리', 'ALL');
  assert.deepEqual(
    result.items.map((item) => item.key),
    ['news-1', 'person-3-2', 'content-4'],
  );
  assert.equal('person' in result.items[1] ? result.items[1].person.name : null, '경제 연구원');
  assert.equal(result.totalElements, 3);
});

test('검색 API 오류를 기존 오류 형식으로 전달한다', async () => {
  axios.defaults.adapter = (async (config) => {
    throw new AxiosError('Bad Request', 'ERR_BAD_REQUEST', config, undefined, {
      data: { code: 'INVALID_REQUEST', message: '검색 페이지 조건이 올바르지 않습니다.' },
      status: 400,
      statusText: 'Bad Request',
      headers: {},
      config,
    });
  }) satisfies AxiosAdapter;

  await assert.rejects(
    () => searchContents('금리', 'ALL', 0),
    (error: unknown) =>
      error instanceof ApiError &&
      error.code === 'INVALID_REQUEST' &&
      error.message === '검색 페이지 조건이 올바르지 않습니다.',
  );
});
