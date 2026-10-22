/** @vitest-environment jsdom */
/** @vitest-environment-options {"url":"https://staff.daynomy.com/"} */

import { afterEach, expect, it, vi } from 'vitest';

const posthog = vi.hoisted(() => ({ init: vi.fn(), capture: vi.fn() }));
vi.mock('posthog-js', () => ({ default: posthog }));

afterEach(() => {
  document.querySelector('script[src*="googletagmanager.com/gtag/js"]')?.remove();
  vi.unstubAllEnvs();
  vi.resetModules();
  posthog.init.mockReset();
  posthog.capture.mockReset();
});

it('팀원 도메인에서는 GA4와 PostHog를 시작하지 않는다', async () => {
  vi.stubEnv('GA_MEASUREMENT_ID', 'G-TEST');
  vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', 'phc_test');
  vi.stubEnv('VITE_POSTHOG_HOST', 'https://example.posthog.test');
  window.dataLayer = [];
  delete window.gtag;

  const { trackEvent, trackPageView } = await import('../../src/analytics');
  trackPageView('/');
  trackEvent('view_news_list');
  await new Promise((resolve) => setTimeout(resolve, 0));

  expect(document.querySelector('script[src*="googletagmanager.com/gtag/js"]')).toBeNull();
  expect(window.dataLayer).toHaveLength(0);
  expect(posthog.init).not.toHaveBeenCalled();
  expect(posthog.capture).not.toHaveBeenCalled();
});
