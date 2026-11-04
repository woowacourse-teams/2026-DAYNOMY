import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getCurrentPmfSurvey,
  submitPmfAnswer,
  submitPmfReason,
} from '../../src/features/pmf-survey/api.ts';

const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });

test('대상 여부는 서버 응답을 검증하며 미배포·접근 불가 상태에서는 설문을 노출하지 않는다', async () => {
  const previous = globalThis.fetch;
  try {
    for (const status of [401, 403, 404]) {
      const calls: string[] = [];
      globalThis.fetch = async (input) => {
        calls.push(String(input));
        return json({}, status);
      };
      assert.equal(await getCurrentPmfSurvey(), null);
      assert.deepEqual(calls, ['/api/pmf-surveys/current']);
    }
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal(await getCurrentPmfSurvey(), null);
    globalThis.fetch = async () => json({ surveyId: 805, eligible: true, responseId: null });
    assert.deepEqual(await getCurrentPmfSurvey(), {
      surveyId: 805,
      eligible: true,
      responseId: null,
    });
    for (const value of [
      {},
      { surveyId: -1, eligible: true, responseId: null },
      { surveyId: 805, eligible: 'true', responseId: null },
      { surveyId: 805, eligible: true, responseId: 0 },
    ]) {
      globalThis.fetch = async () => json(value);
      await assert.rejects(getCurrentPmfSurvey(), /응답 형식/);
    }
  } finally {
    globalThis.fetch = previous;
  }
});

test('첫 응답과 추가 의견을 별도 CSRF 요청으로 저장하며 입력을 URL이나 분석 속성에 넣지 않는다', async () => {
  const previous = globalThis.fetch;
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  try {
    globalThis.fetch = async (input, init) => {
      const url = String(input);
      calls.push({ url, init });
      if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' });
      if (init?.method === 'POST') return json({ id: 21 }, 201);
      return new Response(null, { status: 204 });
    };
    const controller = new AbortController();
    const id = await submitPmfAnswer(805, 'VERY_DISAPPOINTED', controller.signal);
    await submitPmfReason(805, id, '자산에 관련된 소식을 볼 수 있어요.', controller.signal);
    assert.deepEqual(
      calls.map(({ url }) => url),
      [
        '/api/auth/csrf',
        '/api/pmf-surveys/805/responses',
        '/api/auth/csrf',
        '/api/pmf-surveys/805/responses/21/reason',
      ],
    );
    assert.deepEqual(JSON.parse(String(calls[1]?.init?.body)), { answer: 'VERY_DISAPPOINTED' });
    assert.deepEqual(JSON.parse(String(calls[3]?.init?.body)), {
      reason: '자산에 관련된 소식을 볼 수 있어요.',
    });
    assert.equal(calls[3]?.init?.method, 'PATCH');
    assert.equal(calls[1]?.init?.signal, controller.signal);
    assert.equal(calls[3]?.init?.signal, controller.signal);
    assert.equal(new Headers(calls[3]?.init?.headers).get('X-CSRF-TOKEN'), 'csrf');
    assert.ok(calls.every(({ init }) => init?.credentials === 'include'));
  } finally {
    globalThis.fetch = previous;
  }
});

test('저장된 응답 ID가 없거나 비정상이면 다음 단계로 사용할 수 없다', async () => {
  const previous = globalThis.fetch;
  try {
    for (const value of [null, {}, { id: 0 }, { id: '21' }]) {
      globalThis.fetch = async (input) =>
        String(input) === '/api/auth/csrf'
          ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
          : json(value);
      await assert.rejects(submitPmfAnswer(805, 'NOT_DISAPPOINTED'), /저장 응답 형식/);
    }
  } finally {
    globalThis.fetch = previous;
  }
});
