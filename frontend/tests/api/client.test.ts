import assert from 'node:assert/strict';
import test from 'node:test';
import { ApiError, request, requestWithCsrf } from '../../src/api/client.ts';

test('CSRF 응답이 잘못되면 쓰기 요청을 보내지 않는다', async () => {
  const originalFetch = globalThis.fetch;
  const calls: string[] = [];

  try {
    globalThis.fetch = async (input) => {
      calls.push(String(input));
      return new Response(JSON.stringify({ token: 123, headerName: 'X-CSRF-TOKEN' }), {
        headers: { 'content-type': 'application/json' },
      });
    };

    await assert.rejects(
      () => requestWithCsrf<void>('/api/write', { method: 'POST' }),
      /CSRF 응답 형식이 올바르지 않습니다/,
    );
    assert.deepEqual(calls, ['/api/auth/csrf']);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('잘못된 오류 응답 필드는 기본 메시지로 처리한다', async () => {
  const originalFetch = globalThis.fetch;

  try {
    globalThis.fetch = async () =>
      new Response(JSON.stringify({ code: 42, message: { private: 'value' } }), {
        status: 400,
        headers: { 'content-type': 'application/json' },
      });

    await assert.rejects(
      () => request<unknown>('/api/data', {}, false),
      (error: unknown) =>
        error instanceof ApiError &&
        error.status === 400 &&
        error.code === undefined &&
        error.message === '요청을 처리하지 못했습니다.',
    );
  } finally {
    globalThis.fetch = originalFetch;
  }
});
