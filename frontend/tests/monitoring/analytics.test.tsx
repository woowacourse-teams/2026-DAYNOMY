/** @vitest-environment jsdom */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const tagSelector = 'script[src*="googletagmanager.com/gtag/js"]';

function commands() {
  return window.dataLayer.map((args) => Array.from(args as IArguments));
}

beforeEach(() => {
  vi.stubEnv('GA_MEASUREMENT_ID', 'G-TEST');
  vi.resetModules();
  window.history.replaceState({}, '', '/');
  window.dataLayer = [];
  delete window.gtag;
  Reflect.deleteProperty(window, 'ga-disable-G-TEST');
  Object.defineProperty(document, 'referrer', { configurable: true, value: '' });
});

afterEach(() => {
  document.querySelector(tagSelector)?.remove();
  vi.unstubAllEnvs();
});

describe('GA4 수집 경계', () => {
  it.each(['/league/profile', '/league/reviews', '/league/portfolio', '/league/following'])(
    '고정된 리그 화면 %s를 투자자 식별자로 바꾸지 않는다',
    async (path) => {
      window.history.replaceState({}, '', `${path}?private=query#private-fragment`);
      const { trackPageView } = await import('../../src/analytics');
      trackPageView(window.location.href);
      expect(
        commands().find(([kind, name]) => kind === 'event' && name === 'page_view')?.[2],
      ).toMatchObject({ page_path: path });
      expect(JSON.stringify(commands())).not.toContain('private');
    },
  );
  it('투자자 식별자는 페이지 URL과 이벤트에서 제거한다', async () => {
    const { trackEvent, trackPageView } = await import('../../src/analytics');
    for (const path of [
      '/league/private-investor-id',
      '/league/private-investor-id/',
      '/LEAGUE/private-investor-id///',
    ]) {
      window.history.replaceState({}, '', path);
      trackPageView(path);
      trackEvent('view_investor_profile', { source: 'ranking', rank_bucket: 'top_3' });
    }
    expect(JSON.stringify(commands())).not.toContain('private-investor-id');
    expect(
      commands().find(([kind, name]) => kind === 'event' && name === 'view_investor_profile')?.[2],
    ).toMatchObject({ page_path: '/league/investor', source: 'ranking' });
  });

  it('검색 URL과 referrer의 쿼리를 초기화 및 이벤트에 싣지 않는다', async () => {
    const keyword = 'private-search-term';
    window.history.replaceState({}, '', `/search?q=${keyword}&category=ALL`);
    Object.defineProperty(document, 'referrer', {
      configurable: true,
      value: `https://example.com/search?q=${keyword}`,
    });
    const { trackEvent, trackPageView } = await import('../../src/analytics');

    trackPageView(`/search?q=${keyword}`);
    trackPageView(`/search?q=another-search`);
    trackEvent('search_news', { search_length: keyword.length });

    expect(document.querySelector(tagSelector)).not.toBeNull();
    expect(JSON.stringify(commands())).not.toContain(keyword);
    expect(JSON.stringify(commands())).not.toContain('another-search');
    expect(
      commands().filter(([kind, name]) => kind === 'event' && name === 'page_view'),
    ).toHaveLength(1);
    expect(
      commands().find(([kind, name]) => kind === 'event' && name === 'search_news')?.[2],
    ).toMatchObject({ search_length: keyword.length, page_path: '/search' });

    window.history.replaceState({}, '', '/news/1');
    trackPageView('/news/1');
    expect(
      commands().filter(([kind, name]) => kind === 'event' && name === 'page_view')[1]?.[2],
    ).toMatchObject({ page_referrer: 'http://localhost:3000/search' });
  });

  it('관리자 경로에서는 태그를 시작하지 않고 공개 화면으로 이동해도 중지 상태를 유지한다', async () => {
    window.history.replaceState({}, '', '/admin/news');
    const { trackEvent, trackPageView } = await import('../../src/analytics');

    trackPageView('/admin/news');
    trackEvent('view_news_list');
    expect(document.querySelector(tagSelector)).toBeNull();
    expect(commands()).toHaveLength(0);
    expect(Reflect.get(window, 'ga-disable-G-TEST')).toBe(true);

    window.history.replaceState({}, '', '/news/1');
    trackPageView('/news/1');
    expect(document.querySelector(tagSelector)).toBeNull();
    expect(commands()).toHaveLength(0);
  });

  it('공개 화면에서 시작해 관리자 경로로 이동하면 전송을 중지한다', async () => {
    window.history.replaceState({}, '', '/news/1');
    const { trackEvent, trackPageView } = await import('../../src/analytics');
    trackPageView('/news/1');
    const commandCount = commands().length;

    window.history.replaceState({}, '', '/login');
    trackPageView('/login');
    trackEvent('click_login');
    expect(Reflect.get(window, 'ga-disable-G-TEST')).toBe(true);
    expect(commands()).toHaveLength(commandCount);
  });

  it('이미 수집을 거부한 브라우저에서는 태그를 시작하지 않는다', async () => {
    Reflect.set(window, 'ga-disable-G-TEST', true);
    const { trackPageView } = await import('../../src/analytics');

    trackPageView('/');

    expect(document.querySelector(tagSelector)).toBeNull();
    expect(commands()).toHaveLength(0);
  });
});

const posthog = vi.hoisted(() => ({ init: vi.fn(), capture: vi.fn() }));
vi.mock('posthog-js', () => ({ default: posthog }));

beforeEach(() => {
  posthog.init.mockReset();
  posthog.capture.mockReset();
  vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', '');
  vi.stubEnv('VITE_POSTHOG_HOST', '');
});

function enablePostHog() {
  vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', 'phc_test');
  vi.stubEnv('VITE_POSTHOG_HOST', 'https://example.posthog.test');
}

it('리그 행동을 GA4와 PostHog에 같은 속성으로 한 번씩 전송하고 식별자를 제거한다', async () => {
  enablePostHog();
  window.history.replaceState({}, '', '/league/private-id?secret=query#private-fragment');
  const { trackLeagueEvent } = await import('../../src/features/league/analytics');
  trackLeagueEvent('league_action', { surface: 'investor', action: 'follow', status: 'success' });
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
  const events = commands().filter(([kind, name]) => kind === 'event' && name === 'league_action');
  expect(posthog.init).toHaveBeenCalledWith(
    'phc_test',
    expect.objectContaining({ api_host: 'https://example.posthog.test' }),
  );
  expect(events).toHaveLength(1);
  expect(events[0][2]).toMatchObject({
    page_path: '/league/investor',
    action: 'follow',
    status: 'success',
  });
  expect(posthog.capture).toHaveBeenCalledWith(
    'league_action',
    expect.objectContaining(events[0][2]),
  );
  expect(JSON.stringify([commands(), posthog.capture.mock.calls])).not.toMatch(
    /private-id|secret|private-fragment/,
  );
});

it('같은 행동은 두 공급자에 전송하고 페이지 조회만 공급자 표준 이름으로 한 번 보낸다', async () => {
  enablePostHog();
  const { trackEvent, trackPageView } = await import('../../src/analytics');
  trackPageView('/news');
  trackPageView('/news');
  trackEvent('expand_content', { surface: 'news_home', content_id: 'content-1' });
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(2));
  expect(posthog.init).toHaveBeenCalledTimes(1);
  expect(posthog.capture).toHaveBeenCalledWith(
    '$pageview',
    expect.objectContaining({ page_path: '/news' }),
  );
  expect(posthog.capture).toHaveBeenCalledWith(
    'expand_content',
    expect.objectContaining({ surface: 'news_home', content_id: 'content-1' }),
  );
  expect(commands().filter(([kind]) => kind === 'event')).toHaveLength(2);
});

it('GA4 없이 PostHog만 설정해도 수집하며 GA 태그를 로드하지 않는다', async () => {
  vi.stubEnv('GA_MEASUREMENT_ID', '');
  enablePostHog();
  const { trackEvent } = await import('../../src/analytics');
  trackEvent('click_content', { surface: 'portfolio_news' });
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
  expect(document.querySelector(tagSelector)).toBeNull();
  expect(commands()).toHaveLength(0);
});

it.each(['none', 'token_only', 'host_only'])(
  'PostHog 설정이 불완전하면 초기화하지 않는다 (%s)',
  async (mode) => {
    if (mode === 'token_only') vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', 'phc_test');
    if (mode === 'host_only') vi.stubEnv('VITE_POSTHOG_HOST', 'https://example.posthog.test');
    const { trackEvent } = await import('../../src/analytics');
    trackEvent('click_content');
    expect(posthog.init).not.toHaveBeenCalled();
    expect(commands().some(([kind, name]) => kind === 'event' && name === 'click_content')).toBe(
      true,
    );
  },
);

it('SDK 기본 속성의 원문 URL·본문·프로필 정보를 제거하고 자동 수집을 끈다', async () => {
  enablePostHog();
  window.history.replaceState({}, '', '/search?q=private-input#private-fragment');
  Object.defineProperty(document, 'referrer', {
    configurable: true,
    value: 'https://example.com/?email=private-email',
  });
  const { trackEvent } = await import('../../src/analytics');
  trackEvent('filter_content_source', { source_type: 'X' });
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
  const config = posthog.init.mock.calls[0][1];
  expect(config).toMatchObject({
    autocapture: false,
    capture_pageview: false,
    capture_pageleave: false,
    disable_session_recording: true,
    person_profiles: 'never',
    save_referrer: false,
    save_campaign_params: false,
  });
  const result = config.before_send({
    event: 'filter_content_source',
    properties: {
      ...posthog.capture.mock.calls[0][1],
      $current_url: window.location.href,
      $referrer: document.referrer,
      $initial_current_url: window.location.href,
      $set: { email: 'private-email' },
      $set_once: { $initial_referrer: document.referrer },
      $title: 'private-title',
      distinct_id: 'anonymous-id',
    },
  });
  expect(JSON.stringify(result)).not.toContain('private-');
  expect(result.properties).toMatchObject({
    source_type: 'X',
    distinct_id: 'anonymous-id',
    $current_url: 'http://localhost:3000/search',
    $referrer: 'https://example.com/',
  });
  expect(config.before_send({ event: '$autocapture', properties: {} })).toBeNull();
});

it.each(['/admin/news', '/login'])(
  '보호 경로에서는 지연 중인 PostHog 이벤트도 보내지 않는다 (%s)',
  async (path) => {
    enablePostHog();
    const { trackEvent, trackPageView } = await import('../../src/analytics');
    trackEvent('click_content');
    window.history.replaceState({}, '', path);
    trackPageView(path);
    await new Promise((resolve) => setTimeout(resolve, 0));
    window.history.replaceState({}, '', '/news');
    trackEvent('expand_content');
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(posthog.capture).not.toHaveBeenCalled();
  },
);

it('PostHog 전송 예외가 사용자 동작과 GA4 수집을 중단하지 않는다', async () => {
  enablePostHog();
  posthog.capture.mockImplementation(() => {
    throw new Error('sdk unavailable');
  });
  const { trackEvent } = await import('../../src/analytics');
  expect(() => trackEvent('click_content')).not.toThrow();
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
  expect(commands().some(([kind, name]) => kind === 'event' && name === 'click_content')).toBe(
    true,
  );
});
