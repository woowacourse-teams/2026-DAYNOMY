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
