import assert from 'node:assert/strict';
import test from 'node:test';
import { getFeaturedPeople } from '../../src/features/people/api.ts';

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
}

test('인물 게시물 페이지 조건과 인물 ID를 API에 전달한다', async () => {
  const calls: string[] = [];
  globalThis.fetch = async (input) => {
    calls.push(String(input));
    return jsonResponse({
      people: [],
      page: 2,
      size: 6,
      totalElements: 8,
      totalPages: 2,
      hasNext: false,
    });
  };

  const response = await getFeaturedPeople(2, 6, 7);

  assert.equal(calls[0], '/api/people?page=2&size=6&personId=7');
  assert.equal(response.page, 2);
  assert.equal(response.totalElements, 8);
});

test('인물 게시물 페이지 메타데이터가 없으면 응답을 거부한다', async () => {
  globalThis.fetch = async () => jsonResponse({ people: [] });

  await assert.rejects(() => getFeaturedPeople(), /페이지 API 응답 형식이 올바르지 않습니다/);
});
