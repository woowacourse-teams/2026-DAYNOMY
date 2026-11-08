import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createAdminNews,
  generateAdminNewsImageCandidate,
  getAdminNews,
  isSupportedNewsImage,
  publishAdminNews,
  rejectAdminNews,
  searchWikimediaImages,
  selectAdminNewsImage,
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
  imageUrl: 'https://example.com/news.png',
  sources: [{ name: 'DART', url: 'https://example.com/news/1' }],
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
  await getAdminNews(2, 'DRAFT', 'STOCK', undefined, ' 금리 인상 ');

  assert.equal(calls[0], '/api/admin/news?page=2&size=15&status=DRAFT&category=STOCK');
  assert.equal(new URL(calls[1], 'http://localhost').searchParams.get('q'), '금리 인상');
  assert.equal(page.items[0].status, 'DRAFT');
  assert.equal(page.totalElements, 31);
});

test('Wikimedia Commons 이미지 검색 API는 라이선스 메타데이터를 반환한다', async () => {
  let requestedUrl = '';
  globalThis.fetch = async (input) => {
    requestedUrl = String(input);
    return jsonResponse({
      items: [
        {
          title: 'File:Seoul.jpg',
          thumbnailUrl: 'https://upload.wikimedia.org/thumb.jpg',
          sourceUrl: 'https://commons.wikimedia.org/wiki/File:Seoul.jpg',
          author: 'Jane Doe',
          license: 'CC BY 4.0',
          licenseUrl: 'https://creativecommons.org/licenses/by/4.0/',
          width: 1200,
          height: 800,
        },
      ],
    });
  };

  const candidates = await searchWikimediaImages(' Seoul skyline ');

  assert.equal(requestedUrl, '/api/admin/news/image-search?keyword=Seoul+skyline');
  assert.equal(candidates[0]?.license, 'CC BY 4.0');
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
      sources: [
        { name: '직접 입력', url: 'https://example.com/news' },
        { name: '추가 출처', url: 'https://example.com/another-news' },
      ],
      category: 'ECONOMY',
    });
    assert.equal(body.get('image'), null);
    assert.equal(new Headers(init?.headers).get('X-XSRF-TOKEN'), 'csrf-token');
    return jsonResponse(
      {
        id: 2,
        title: '새 뉴스',
        content: '본문',
        imageUrl: null,
        sources: [{ name: '직접 입력', url: 'https://example.com/news' }],
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
      sources: [
        { name: '직접 입력', url: 'https://example.com/news' },
        { name: '추가 출처', url: 'https://example.com/another-news' },
      ],
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

test('관리자 뉴스 발행 API는 CSRF 토큰과 함께 발행 요청을 보낸다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }

    assert.equal(String(input), '/api/admin/news/1/publish');
    assert.equal(init?.method, 'POST');
    assert.equal(new Headers(init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
    return jsonResponse({
      ...listItem,
      content: '본문',
      status: 'PUBLISHED',
      publishedAt: '2026-09-06T10:00:00Z',
    });
  };

  const news = await publishAdminNews(1);

  assert.equal(news.status, 'PUBLISHED');
  assert.deepEqual(
    calls.map(({ url }) => url),
    ['/api/auth/csrf', '/api/admin/news/1/publish'],
  );
});

test('관리자 뉴스 이미지 후보 API는 CSRF 토큰과 함께 WebP 바이너리를 요청한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }

    assert.equal(String(input), '/api/admin/news/1/image-candidates');
    assert.equal(init?.method, 'POST');
    assert.equal(new Headers(init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
    return new Response(new Uint8Array([1, 2, 3]), {
      headers: { 'content-type': 'image/webp' },
    });
  };

  const image = await generateAdminNewsImageCandidate(1);

  assert.equal(image.type, 'image/webp');
  assert.equal(image.size, 3);
  assert.deepEqual(
    calls.map(({ url }) => url),
    ['/api/auth/csrf', '/api/admin/news/1/image-candidates'],
  );
});

test('관리자 뉴스 이미지 후보 API는 WebP가 아닌 응답을 거부한다', async () => {
  globalThis.fetch = async (input) =>
    String(input) === '/api/auth/csrf'
      ? jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' })
      : new Response(new Uint8Array([1]), { headers: { 'content-type': 'image/png' } });

  await assert.rejects(() => generateAdminNewsImageCandidate(1), /응답 형식/);
});

test('관리자 뉴스 이미지 선택 API는 선택한 후보 한 장만 multipart로 전송한다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }

    assert.equal(String(input), '/api/admin/news/1/image');
    assert.equal(init?.method, 'PUT');
    assert.equal(new Headers(init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
    const body = init?.body;
    assert.ok(body instanceof FormData);
    assert.deepEqual([...body.keys()], ['image']);
    const image = body.get('image');
    assert.ok(image instanceof Blob);
    assert.equal(image.type, 'image/webp');
    return jsonResponse({
      ...listItem,
      content: '본문',
      imageUrl: 'https://example.com/selected.webp',
    });
  };

  const news = await selectAdminNewsImage(1, new Blob(['image'], { type: 'image/webp' }));

  assert.equal(news.imageUrl, 'https://example.com/selected.webp');
  assert.deepEqual(
    calls.map(({ url }) => url),
    ['/api/auth/csrf', '/api/admin/news/1/image'],
  );
});

test('관리자 뉴스 거절 API는 CSRF 토큰과 함께 거절 요청을 보낸다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }

    assert.equal(String(input), '/api/admin/news/1/reject');
    assert.equal(init?.method, 'POST');
    assert.equal(new Headers(init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
    return jsonResponse({ ...listItem, content: '본문', status: 'REJECTED' });
  };

  const news = await rejectAdminNews(1);

  assert.equal(news.status, 'REJECTED');
  assert.deepEqual(
    calls.map(({ url }) => url),
    ['/api/auth/csrf', '/api/admin/news/1/reject'],
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
