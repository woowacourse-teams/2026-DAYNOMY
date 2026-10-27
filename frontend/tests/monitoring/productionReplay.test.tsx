/** @vitest-environment jsdom */
/** @vitest-environment-options {"url":"https://daynomy.com/"} */

import { afterEach, expect, it, vi } from 'vitest';

const posthog = vi.hoisted(() => ({ init: vi.fn(), capture: vi.fn() }));
vi.mock('posthog-js', () => ({ default: posthog }));

afterEach(() => {
  vi.unstubAllEnvs();
  vi.resetModules();
  posthog.init.mockReset();
  posthog.capture.mockReset();
});

it('운영 도메인의 공개 페이지 이동을 수집하고 마스킹된 녹화 전송을 허용한다', async () => {
  vi.stubEnv('GA_MEASUREMENT_ID', '');
  vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', 'phc_test');
  vi.stubEnv('VITE_POSTHOG_HOST', 'https://example.posthog.test');
  const { trackPageView } = await import('../../src/analytics');

  trackPageView('/');
  window.history.replaceState({}, '', '/portfolio');
  trackPageView('/portfolio');
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(2));

  expect(posthog.capture).toHaveBeenNthCalledWith(
    2,
    '$pageview',
    expect.objectContaining({ page_location: 'https://daynomy.com/portfolio' }),
  );
  const config = posthog.init.mock.calls[0][1];
  expect(config).toMatchObject({
    disable_session_recording: false,
    disable_external_dependency_loading: false,
    advanced_disable_flags: false,
    session_recording: { maskAllInputs: true, maskTextSelector: '*' },
  });
  const snapshots = [{ type: 3, data: { source: 1, positions: [] } }];
  expect(
    config.before_send({
      event: '$snapshot',
      properties: { $snapshot_data: snapshots, $session_id: 'session-test' },
    }),
  ).toMatchObject({ properties: { $snapshot_data: snapshots, $session_id: 'session-test' } });
});
