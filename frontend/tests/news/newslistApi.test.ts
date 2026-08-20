import assert from 'node:assert/strict';
import test from 'node:test';
import { getNews } from '../../src/features/news/newslist/api.ts';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

test('뉴스 목록 API에 1-based page를 요청하고 백엔드 응답을 매핑한다', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input) => {
    calls.push(String(input));
    return jsonResponse({
      items: [
        {
          id: 1,
          title: 'stock news',
          description: 'summary',
          imageUrl: 'https://example.com/news.png',
          category: 'STOCK',
          publishedAt: '2026-08-19T10:00:00',
        },
      ],
      page: 2,
      size: 15,
      totalPages: 3,
      totalElements: 31,
      hasNext: true,
    });
  };

  const page = await getNews('STOCK', 2, 15);

  assert.equal(calls[0], '/api/news?page=2&size=15&category=STOCK');
  assert.deepEqual(page, {
    content: [
      {
        id: 1,
        title: 'stock news',
        description: 'summary',
        category: 'STOCK',
        imageUrl: 'https://example.com/news.png',
        publishedAt: '2026-08-19T10:00:00',
      },
    ],
    page: 2,
    size: 15,
    totalPages: 3,
    totalElements: 31,
  });
});

test('뉴스 목록 응답 계약이 맞지 않으면 빈 목록으로 처리하지 않는다', async () => {
  globalThis.fetch = async () =>
    jsonResponse({
      page: 1,
      size: 10,
      totalPages: 1,
      totalElements: 0,
      hasNext: false,
    });

  await assert.rejects(() => getNews());
});
