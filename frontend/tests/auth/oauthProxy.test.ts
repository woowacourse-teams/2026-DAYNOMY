import assert from 'node:assert/strict';
import test from 'node:test';
import config from '../../vite.config.ts';

test('로컬 Google OAuth 시작과 콜백도 API와 동일한 백엔드로 전달한다', () => {
  const proxy = config.server?.proxy;
  assert.ok(proxy?.['/api']);
  assert.deepEqual(proxy['/oauth2'], proxy['/api']);
  assert.deepEqual(proxy['/login/oauth2'], proxy['/api']);
});
