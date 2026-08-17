import assert from 'node:assert/strict';
import test from 'node:test';
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
    '/api/search/news?q=%EA%B8%B0%EC%A4%80+%EA%B8%88%EB%A6%AC&page=0&size=10',
  );
  assert.equal(
    buildNewsSearchUrl('금', 'GOLD', 2, 10),
    '/api/search/news?q=%EA%B8%88&page=2&size=10&category=GOLD',
  );
});

test('백엔드 공통 응답의 검색 결과를 읽는다', async () => {
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        status: 'SUCCESS',
        code: null,
        message: '뉴스 검색 결과를 조회했습니다.',
        body: {
          content: [
            {
              id: 1,
              title: '기준금리 동결 가능성 확대',
              category: 'BOND',
              publishedAt: '2026-08-14T10:00:00',
            },
          ],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        },
      }),
      { headers: { 'content-type': 'application/json' } },
    );

  try {
    const results = await searchNews('금리', 'BOND');

    assert.equal(results.content.length, 1);
    assert.equal(results.content[0]?.category, 'BOND');
    assert.equal(results.totalElements, 1);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
