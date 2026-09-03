import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createAdminNews,
  getAdminNews,
  isSupportedNewsImage,
} from '../../src/features/admin/api.ts';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const listItem = {
  id: 1,
  title: '금리 인상 전망에 시장 주목',
  description: '금리 결정에 대한 시장의 관심이 커지고 있습니다.',
  imageUrl: 'https://example.com/news.png',
  source: null,
  sourceUrl: 'https://example.com/news/1',
  category: 'STOCK',
  publishedAt: null,
  status: 'DRAFT',
  createdAt: '2026-08-17T09:00:00Z',
};

test('관리자 뉴스 목록 API에 상태·카테고리 필터와 1-based page를 요청한다', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input) => {
    calls.push(String(input));
    return jsonResponse({
      items: [listItem],
      page: 2,
      size: 15,
      totalPages: 3,
      totalElements: 31,
      hasNext: true,
    });
  };

  const page = await getAdminNews(2, 'DRAFT', 'STOCK');

  assert.equal(calls[0], '/api/admin/news?page=2&size=15&status=DRAFT&category=STOCK');
  assert.equal(page.items[0].status, 'DRAFT');
  assert.equal(page.totalElements, 31);
});

test('관리자 뉴스 등록 API는 CSRF 토큰과 JSON request multipart 파트를 함께 보낸다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-XSRF-TOKEN' });
    }

    const body = init?.body;
    assert.ok(body instanceof FormData);
    const requestPart = body.get('request');
    assert.ok(requestPart instanceof Blob);
    assert.deepEqual(JSON.parse(await requestPart.text()), {
      title: '새 뉴스',
      content: '본문',
      description: '',
      sourceUrl: 'https://example.com/news',
      category: 'ECONOMY',
    });
    assert.equal(body.get('image'), null);
    assert.equal(new Headers(init?.headers).get('X-XSRF-TOKEN'), 'csrf-token');
    return jsonResponse(
      {
        id: 2,
        title: '새 뉴스',
        content: '본문',
        description: null,
        imageUrl: null,
        source: null,
        sourceUrl: 'https://example.com/news',
        category: 'ECONOMY',
        publishedAt: null,
        status: 'DRAFT',
      },
      201,
    );
  };

  const news = await createAdminNews(
    {
      title: '새 뉴스',
      content: '본문',
      description: '',
      sourceUrl: 'https://example.com/news',
      category: 'ECONOMY',
    },
    null,
  );

  assert.equal(news.status, 'DRAFT');
  assert.deepEqual(
    calls.map(({ url }) => url),
    ['/api/auth/csrf', '/api/admin/news'],
  );
});

test('관리자 뉴스 이미지 입력은 JPG·PNG·WEBP 5MB 이하만 허용한다', () => {
  assert.equal(isSupportedNewsImage(new File(['image'], 'news.png', { type: 'image/png' })), true);
  assert.equal(isSupportedNewsImage(new File(['image'], 'news.gif', { type: 'image/gif' })), false);
  assert.equal(
    isSupportedNewsImage(
      new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'large.png', { type: 'image/png' }),
    ),
    false,
  );
});
