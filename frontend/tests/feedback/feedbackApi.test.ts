import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createFeedback,
  deleteAdminFeedback,
  getAdminFeedbacks,
  updateAdminFeedbackStatus,
} from '../../src/features/feedback/api.ts';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const feedback = {
  id: 1,
  content: '검색 결과를 저장하고 싶어요.',
  pagePath: '/search?q=금리',
  status: 'PENDING',
  createdAt: '2026-10-12T09:00:00Z',
  updatedAt: '2026-10-12T09:00:00Z',
};

test('공개 피드백 등록 API는 CSRF 토큰과 작성 화면을 함께 보낸다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }
    return new Response(null, { status: 204 });
  };

  await createFeedback('검색 결과를 저장하고 싶어요.', '/search?q=금리');

  assert.deepEqual(
    calls.map(({ url }) => url),
    ['/api/auth/csrf', '/api/feedback'],
  );
  assert.equal(calls[1]?.init?.method, 'POST');
  assert.equal(new Headers(calls[1]?.init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
  assert.deepEqual(JSON.parse(String(calls[1]?.init?.body)), {
    content: '검색 결과를 저장하고 싶어요.',
    pagePath: '/search?q=금리',
  });
});

test('관리자 피드백 API는 목록 조회와 상태 변경·삭제 요청을 보낸다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    const url = String(input);
    calls.push({ url, init });
    if (url === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }
    if (url.includes('?')) {
      return jsonResponse({
        items: [feedback],
        page: 2,
        size: 15,
        totalPages: 3,
        totalElements: 31,
        hasNext: true,
      });
    }
    if (init?.method === 'PATCH') {
      return jsonResponse({ ...feedback, status: 'RESOLVED' });
    }
    return new Response(null, { status: 204 });
  };

  const page = await getAdminFeedbacks(2);
  const updated = await updateAdminFeedbackStatus(1, 'RESOLVED');
  await deleteAdminFeedback(1);

  assert.equal(page.totalElements, 31);
  assert.equal(updated.status, 'RESOLVED');
  assert.equal(calls[0]?.url, '/api/admin/feedbacks?page=2&size=15');
  assert.equal(calls[2]?.init?.method, 'PATCH');
  assert.equal(calls[4]?.init?.method, 'DELETE');
});
