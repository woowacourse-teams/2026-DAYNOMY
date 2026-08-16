import assert from 'node:assert/strict';
import test from 'node:test';
import type { ErrorEvent } from '@sentry/react';
import { sanitizeSentryEvent } from '../../src/monitoring/sanitizeSentryEvent.ts';

test('Sentry 이벤트에서 개인정보와 URL 쿼리를 제거한다', () => {
  const event: ErrorEvent = {
    type: undefined,
    user: { email: 'user@example.com' },
    request: {
      url: 'https://daynomy.example/news?token=secret#content',
      data: { password: 'secret' },
      headers: { authorization: 'Bearer secret' },
      cookies: { session: 'secret' },
      query_string: 'token=secret',
    },
    breadcrumbs: [{ data: { url: 'https://daynomy.example/api/news?token=secret' } }],
  };

  assert.deepEqual(sanitizeSentryEvent(event), {
    type: undefined,
    request: { url: 'https://daynomy.example/news' },
    breadcrumbs: [{ data: { url: 'https://daynomy.example/api/news' } }],
  });
});
