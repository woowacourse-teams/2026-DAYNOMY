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

const posthog = vi.hoisted(() => ({
  init: vi.fn(),
  capture: vi.fn(),
  stopSessionRecording: vi.fn(),
}));
vi.mock('posthog-js', () => ({ default: posthog }));

beforeEach(() => {
  posthog.init.mockReset();
  posthog.capture.mockReset();
  posthog.stopSessionRecording.mockReset();
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
  trackEvent('filter_content_source', { source_type: 'X', token: 'private-input-token' });
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
  const config = posthog.init.mock.calls[0][1];
  expect(config).toMatchObject({
    autocapture: false,
    capture_pageview: false,
    capture_pageleave: false,
    disable_session_recording: false,
    person_profiles: 'never',
    save_referrer: false,
    save_campaign_params: false,
  });
  const result = config.before_send({
    event: 'filter_content_source',
    properties: {
      ...posthog.capture.mock.calls[0][1],
      token: 'phc_test',
      $current_url: window.location.href,
      $referrer: document.referrer,
      $initial_current_url: window.location.href,
      $set: { email: 'private-email' },
      $set_once: { $initial_referrer: document.referrer },
      $title: 'private-title',
      distinct_id: 'anonymous-id',
    },
  });
  expect(JSON.stringify([commands(), posthog.capture.mock.calls])).not.toContain('private-');
  expect(JSON.stringify(result)).not.toContain('private-');
  expect(result.properties).toMatchObject({
    source_type: 'X',
    token: 'phc_test',
    distinct_id: 'anonymous-id',
    $current_url: 'http://localhost:3000/search',
    $referrer: 'https://example.com/',
  });
  expect(config.before_send({ event: '$autocapture', properties: {} })).toBeNull();
});

it('세션 진입 출처와 캠페인을 내부 이동 후에도 유지하고 URL·비허용 UTM을 정리한다', async () => {
  enablePostHog();
  const { trackPageView } = await import('../../src/analytics');
  trackPageView('/');
  window.history.replaceState({}, '', '/news/1');
  trackPageView('/news/1');
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(2));
  const config = posthog.init.mock.calls[0][1];
  // These properties are attached by the SDK's SessionPropsManager to each event.
  const entry = {
    $session_entry_url:
      'http://localhost:3000/?utm_source=kakao&utm_medium=share&utm_campaign=launch&utm_term=private-search&q=private-query#private-fragment',
    $session_entry_referrer: 'https://blog.example.com/private-path?email=private-email',
    $session_entry_utm_content: 'private-content',
  };
  for (const [, properties] of posthog.capture.mock.calls) {
    const event = config.before_send({
      event: '$pageview',
      properties: { ...properties, ...entry },
    });
    expect(event.properties).toMatchObject({
      $referring_domain: 'blog.example.com',
      $session_entry_referring_domain: 'blog.example.com',
      $session_entry_referrer: 'https://blog.example.com/',
      $session_entry_url: 'http://localhost:3000/',
      utm_source: 'kakao',
      utm_medium: 'share',
      utm_campaign: 'launch',
      $session_entry_utm_source: 'kakao',
    });
    expect(JSON.stringify(event)).not.toContain('private-');
  }
  // A new SDK session must use its own entry properties, never stale campaign data.
  const nextSession = config.before_send({
    event: '$pageview',
    properties: {
      ...posthog.capture.mock.calls[1][1],
      $session_entry_url: 'http://localhost:3000/news/1?utm_source=private%40email.com',
      $session_entry_referrer: '',
      utm_source: 'stale-campaign',
    },
  });
  expect(nextSession.properties.$referring_domain).toBe('$direct');
  expect(nextSession.properties).not.toHaveProperty('utm_source');
});

it('자체 사이트와 잘못된 유입 주소는 외부 사이트로 집계하지 않는다', async () => {
  enablePostHog();
  const { trackPageView } = await import('../../src/analytics');
  trackPageView('/');
  await vi.waitFor(() => expect(posthog.init).toHaveBeenCalled());
  const { before_send } = posthog.init.mock.calls[0][1];
  for (const referrer of ['', 'invalid url', 'javascript:private-input', window.location.href]) {
    const event = before_send({
      event: '$pageview',
      properties: { $session_entry_referrer: referrer, $session_entry_url: 'invalid url' },
    });
    expect(event.properties.$referring_domain).toBe('$direct');
    expect(event.properties.$session_entry_referrer).toBe('');
  }
});

it('실제 SDK에서도 세션 출처와 UTM이 유지되고 세션 교체 시 갱신된다', async () => {
  enablePostHog();
  window.history.replaceState({}, '', '/?utm_source=kakao&utm_medium=share&q=private-search');
  Object.defineProperty(document, 'referrer', {
    configurable: true,
    value: 'https://blog.example.com/private-path?email=private-email',
  });
  const { trackPageView } = await import('../../src/analytics');
  trackPageView('/');
  await vi.waitFor(() => expect(posthog.init).toHaveBeenCalled());
  const config = posthog.init.mock.calls[0][1];
  const actual = await vi.importActual<typeof import('posthog-js')>('posthog-js');
  const sdk = new actual.PostHog();
  // Use the real enrichment/filter pipeline without sending test traffic or loading extensions.
  vi.spyOn(sdk, '_send_request').mockImplementation(() => {});
  sdk.init('phc_sdk_test', {
    ...config,
    advanced_disable_flags: true,
    disable_external_dependency_loading: true,
    disable_session_recording: true,
    request_batching: false,
  });
  const first = sdk.capture('$pageview', {
    page_location: 'http://localhost:3000/',
    page_path: '/',
    page_referrer: 'https://blog.example.com/',
  });
  window.history.replaceState({}, '', '/news/1');
  const second = sdk.capture('$pageview', {
    page_location: window.location.href,
    page_path: '/news/1',
    page_referrer: 'http://localhost:3000/',
  });
  for (const event of [first, second]) {
    expect(event?.properties).toMatchObject({
      $referring_domain: 'blog.example.com',
      $session_entry_url: 'http://localhost:3000/',
      utm_source: 'kakao',
      utm_medium: 'share',
    });
    expect(JSON.stringify(event)).not.toContain('private-');
  }
  Object.defineProperty(document, 'referrer', { configurable: true, value: '' });
  sdk.sessionManager?.resetSessionId();
  const next = sdk.capture('$pageview', {
    page_location: window.location.href,
    page_path: '/news/1',
    page_referrer: '',
  });
  expect(next?.properties).toMatchObject({ $referring_domain: '$direct' });
  expect(next?.properties).not.toHaveProperty('utm_source');
  expect(next?.properties.$session_id).not.toBe(first?.properties.$session_id);
  sdk.opt_out_capturing();
});

it('마스킹된 녹화 배열과 세션 식별자를 보존하되 SDK의 추가 원문 속성은 제거한다', async () => {
  enablePostHog();
  const { trackPageView } = await import('../../src/analytics');
  trackPageView('/');
  await vi.waitFor(() => expect(posthog.init).toHaveBeenCalled());
  const config = posthog.init.mock.calls[0][1];
  expect(config).toMatchObject({
    disable_external_dependency_loading: false,
    advanced_disable_flags: false,
    enable_recording_console_log: false,
    session_recording: {
      maskAllInputs: true,
      maskTextSelector: '*',
      recordHeaders: false,
      recordBody: false,
    },
  });
  const snapshots = [
    { type: 3, data: { source: 1, positions: [{ x: 20, y: 30, id: 1, timeOffset: 0 }] } },
  ];
  const event = config.before_send({
    event: '$snapshot',
    $set_once: { $initial_current_url: 'private-url' },
    properties: {
      $snapshot_data: snapshots,
      $snapshot_bytes: 100,
      $session_id: 'session-1',
      $window_id: 'window-1',
      token: 'phc_test',
      $current_url: 'private-url',
      $set: { email: 'private-email' },
    },
  });
  expect(event.properties.$snapshot_data).toBe(snapshots);
  expect(event.properties).toMatchObject({
    $session_id: 'session-1',
    $window_id: 'window-1',
    $snapshot_bytes: 100,
  });
  expect(JSON.stringify(event)).not.toContain('private-');
});

it('녹화의 입력 외 속성·링크와 URL을 정리하고 API 요청은 녹화하지 않는다', async () => {
  enablePostHog();
  const { trackPageView } = await import('../../src/analytics');
  trackPageView('/');
  await vi.waitFor(() => expect(posthog.init).toHaveBeenCalled());
  const recording = posthog.init.mock.calls[0][1].session_recording;
  for (const attribute of ['value', 'title', 'data-email', 'aria-label', 'href', 'src', 'style']) {
    expect(recording.maskAttributeFn(attribute, 'private-value', document.createElement('a'))).toBe(
      '',
    );
  }
  expect(recording.maskAttributeFn('class', 'portfolio-card')).toBe('portfolio-card');
  expect(
    recording.maskCapturedNetworkRequestFn({
      name: 'https://example.com/league/private-id?q=private-query#private-fragment',
    }),
  ).toEqual({ name: 'https://example.com/league/investor' });
  expect(
    recording.maskCapturedNetworkRequestFn({
      name: 'https://example.com/api/users/me',
      method: 'GET',
      responseBody: 'private-body',
    }),
  ).toBeNull();
});

it.each(['/login', '/admin/news'])(
  '녹화 시작 후 %s에 진입하면 녹화를 중지하고 이후 스냅샷도 차단한다',
  async (path) => {
    enablePostHog();
    const { trackPageView } = await import('../../src/analytics');
    trackPageView('/');
    await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalled());
    const { before_send, session_recording } = posthog.init.mock.calls[0][1];
    window.history.replaceState({}, '', path);
    trackPageView(path);
    expect(posthog.stopSessionRecording).toHaveBeenCalledTimes(1);
    expect(
      session_recording.maskCapturedNetworkRequestFn({ name: window.location.href }),
    ).toBeNull();
    expect(before_send({ event: '$snapshot', properties: { $snapshot_data: [] } })).toBeNull();
    window.history.replaceState({}, '', '/news');
    trackPageView('/news');
    expect(before_send({ event: '$snapshot', properties: { $snapshot_data: [] } })).toBeNull();
    expect(posthog.capture).toHaveBeenCalledTimes(1);
  },
);

it('허용 목록에 없는 이벤트 속성은 GA4와 PostHog에서 모두 제거한다', async () => {
  enablePostHog();
  const { trackEvent } = await import('../../src/analytics');
  trackEvent('change_content_page', {
    surface: 'portfolio_contents',
    page_number: 2,
    selection_valid: true,
    asset_id: 123,
  });
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));

  expect(
    commands().find(([kind, name]) => kind === 'event' && name === 'change_content_page')?.[2],
  ).toMatchObject({ surface: 'portfolio_contents', page_number: 2, selection_valid: true });
  expect(JSON.stringify(commands())).not.toContain('asset_id');

  const config = posthog.init.mock.calls[0][1];
  const result = config.before_send({
    event: 'change_content_page',
    properties: {
      ...posthog.capture.mock.calls[0][1],
      asset_id: 123,
    },
  });
  expect(result.properties).not.toHaveProperty('asset_id');
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
