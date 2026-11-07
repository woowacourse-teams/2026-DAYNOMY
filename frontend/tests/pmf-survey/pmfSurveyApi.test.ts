import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getCurrentPmfSurvey,
  submitPmfResponse,
  hidePmfSurveyToday,
} from '../../src/features/pmf-survey/api.ts';
const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const status = {
  surveyId: 805,
  eligible: true,
  responseId: null,
  isFirstVisit: false,
  hiddenUntil: null,
};
test('노출 판정의 방문·숨김 필드를 검증하고 API 미배포 시 표시하지 않는다', async () => {
  const previous = globalThis.fetch;
  try {
    for (const code of [401, 403, 404]) {
      const urls: string[] = [];
      globalThis.fetch = async (input) => {
        urls.push(String(input));
        return json({}, code);
      };
      assert.equal(await getCurrentPmfSurvey(), null);
      assert.deepEqual(urls, ['/api/pmf-surveys/current']);
    }
    globalThis.fetch = async () => new Response(null, { status: 204 });
    assert.equal(await getCurrentPmfSurvey(), null);
    globalThis.fetch = async () => json(status);
    assert.deepEqual(await getCurrentPmfSurvey(), status);
    for (const value of [
      {},
      { ...status, surveyId: -1 },
      { ...status, eligible: 'true' },
      { ...status, responseId: 0 },
      { ...status, isFirstVisit: null },
      { ...status, hiddenUntil: 'invalid' },
    ]) {
      globalThis.fetch = async () => json(value);
      await assert.rejects(getCurrentPmfSurvey(), /응답 형식/);
    }
  } finally {
    globalThis.fetch = previous;
  }
});
test('선택 이유와 필수 응답을 CSRF POST 한 번에 저장하고 오늘 숨김은 별도 요청이다', async () => {
  const previous = globalThis.fetch,
    calls: Array<{ url: string; init?: RequestInit }> = [];
  try {
    globalThis.fetch = async (input, init) => {
      const url = String(input);
      calls.push({ url, init });
      if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' });
      if (url.endsWith('/responses')) return json({ id: 21 }, 201);
      return new Response(null, { status: 204 });
    };
    const controller = new AbortController();
    assert.equal(await submitPmfResponse(805, 'VERY_DISAPPOINTED', null, controller.signal), 21);
    await hidePmfSurveyToday(805, controller.signal);
    assert.deepEqual(
      calls.map((c) => c.url),
      [
        '/api/auth/csrf',
        '/api/pmf-surveys/805/responses',
        '/api/auth/csrf',
        '/api/pmf-surveys/805/snooze',
      ],
    );
    assert.deepEqual(JSON.parse(String(calls[1]?.init?.body)), {
      answer: 'VERY_DISAPPOINTED',
      reason: null,
    });
    assert.equal(calls[1]?.init?.signal, controller.signal);
    assert.equal(calls[3]?.init?.signal, controller.signal);
    assert.equal(calls[3]?.init?.method, 'POST');
    assert.equal(new Headers(calls[1]?.init?.headers).get('X-CSRF-TOKEN'), 'csrf');
    assert.ok(calls.every((c) => c.init?.credentials === 'include'));
  } finally {
    globalThis.fetch = previous;
  }
});
test('저장된 응답 ID가 비정상이면 성공으로 처리하지 않는다', async () => {
  const previous = globalThis.fetch;
  try {
    for (const value of [null, {}, { id: 0 }, { id: '21' }]) {
      globalThis.fetch = async (input) =>
        String(input) === '/api/auth/csrf'
          ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
          : json(value);
      await assert.rejects(submitPmfResponse(805, 'NOT_DISAPPOINTED', '이유'), /저장 응답 형식/);
    }
  } finally {
    globalThis.fetch = previous;
  }
});
