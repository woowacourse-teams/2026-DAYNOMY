/** @vitest-environment jsdom */
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const posthog = vi.hoisted(() => ({
  init: vi.fn(),
  capture: vi.fn(),
  stopSessionRecording: vi.fn(),
}));
vi.mock('posthog-js', () => ({ default: posthog }));
const commands = () => window.dataLayer.map((args) => Array.from(args as IArguments));
const gaEvents = () => commands().filter(([kind]) => kind === 'event');

beforeEach(() => {
  vi.resetModules();
  vi.stubEnv('GA_MEASUREMENT_ID', 'G-TEST');
  vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', 'phc_test');
  vi.stubEnv('VITE_POSTHOG_HOST', 'https://example.posthog.test');
  posthog.init.mockReset();
  posthog.capture.mockReset();
  posthog.stopSessionRecording.mockReset();
  window.history.replaceState({}, '', '/');
  window.dataLayer = [];
  delete window.gtag;
  Reflect.deleteProperty(window, 'ga-disable-G-TEST');
});
afterEach(() => {
  document.querySelector('script[src*="googletagmanager.com/gtag/js"]')?.remove();
  vi.unstubAllEnvs();
  vi.restoreAllMocks();
});

it('집계 상태와 시장 분류는 두 공급자에 전달하고 식별자·금융 값은 제외한다', async () => {
  window.history.replaceState({}, '', '/league/private-person?secret=value#private');
  const { trackLeagueEvent } = await import('../../src/features/league/analytics');
  const properties = {
    surface: 'ranking' as const,
    status: 'success' as const,
    page: 1,
    collection_status: 'COLLECTION_PENDING' as const,
    confirmed: false,
    market_scope: 'mixed' as const,
    has_result: true,
    aggregation_reason: 'EXCHANGE_RATE_PENDING',
    publicId: 'private-person',
    quantity: 1000,
    weeklyReturnRate: 45.67,
    reason: 'private-reason',
  };
  trackLeagueEvent('league_load_result', properties);
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
  const payload = gaEvents()[0][2];
  expect(payload).toMatchObject({
    page: 1,
    confirmed: false,
    collection_status: 'COLLECTION_PENDING',
    market_scope: 'mixed',
    has_result: true,
    aggregation_reason: 'EXCHANGE_RATE_PENDING',
    journey_source: 'league',
  });
  expect(posthog.capture).toHaveBeenCalledWith(
    'league_load_result',
    expect.objectContaining(payload as object),
  );
  const beforeSend = posthog.init.mock.calls[0][1].before_send;
  const sanitized = beforeSend({
    event: 'league_load_result',
    properties: { ...posthog.capture.mock.calls[0][1], publicId: 'private-person', quantity: 1000 },
  });
  expect(sanitized.properties).toMatchObject(payload as object);
  expect(JSON.stringify([commands(), posthog.capture.mock.calls, sanitized])).not.toMatch(
    /private-person|private-reason|secret|quantity|weeklyReturnRate/,
  );
});

it('리그 이후 대시보드·첫 저장·포트폴리오 분석·시장 분석을 같은 유입으로 구분하고 30분 후 만료한다', async () => {
  let now = 1000;
  vi.spyOn(Date, 'now').mockImplementation(() => now);
  const { trackEvent, trackPageView } = await import('../../src/analytics');
  const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
  window.history.replaceState({}, '', '/league');
  trackPageView('/league');
  window.history.replaceState({}, '', '/');
  trackPageView('/');
  trackPortfolioEvent('portfolio_dashboard_loaded', { has_holdings: false, market_scope: 'none' });
  trackPortfolioEvent('portfolio_save_succeeded', {
    operation: 'transaction_create',
    storage: 'server',
    has_holdings: false,
    market_scope: 'us',
  });
  trackEvent('start_portfolio_analysis', { action: 'analyze', user_type: 'member' });
  window.history.replaceState({}, '', '/news/12');
  trackPageView('/news/12');
  trackEvent('view_market_analysis', { surface: 'news_detail', news_id: '12' });
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(7));
  const conversions = gaEvents().filter(([, name]) =>
    [
      'portfolio_dashboard_loaded',
      'portfolio_save_succeeded',
      'start_portfolio_analysis',
      'view_market_analysis',
    ].includes(String(name)),
  );
  expect(conversions).toHaveLength(4);
  for (const [, name, payload] of conversions) {
    expect(payload).toMatchObject({ journey_source: 'league' });
    expect(posthog.capture).toHaveBeenCalledWith(name, expect.objectContaining(payload as object));
  }
  expect(conversions[1][2]).toMatchObject({
    has_holdings: false,
    market_scope: 'us',
    storage: 'server',
  });
  now += 30 * 60_000;
  trackEvent('view_market_analysis', { surface: 'news_detail' });
  expect(gaEvents().at(-1)?.[2]).not.toHaveProperty('journey_source');
});

it('로그인 화면 진입 후에는 리그 전환 이벤트도 수집하지 않는다', async () => {
  const { trackEvent, trackPageView } = await import('../../src/analytics');
  window.history.replaceState({}, '', '/league');
  trackPageView('/league');
  await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
  window.history.replaceState({}, '', '/login');
  trackPageView('/login');
  window.history.replaceState({}, '', '/');
  trackEvent('portfolio_save_succeeded', { journey_source: 'league' });
  expect(gaEvents()).toHaveLength(1);
  expect(posthog.capture).toHaveBeenCalledTimes(1);
});

it('시장 분류와 개인·순위 기준일 관계는 원문 전송 없이 계산한다', async () => {
  const { portfolioMarketScope } = await import('../../src/features/portfolio/analytics');
  const { leagueBasisRelation } = await import('../../src/features/league/analytics');
  expect(portfolioMarketScope([])).toBe('none');
  expect(portfolioMarketScope([{ market: 'KOSPI' }])).toBe('domestic');
  expect(portfolioMarketScope([{ market: 'NASDAQ' }])).toBe('us');
  expect(portfolioMarketScope([{ market: 'KOSDAQ' }, { market: 'NYSE', currency: 'USD' }])).toBe(
    'mixed',
  );
  expect(leagueBasisRelation({ asOfDate: '2026-10-27', rankingAsOfDate: '2026-10-26' })).toBe(
    'personal_ahead',
  );
  expect(leagueBasisRelation({ asOfDate: '2026-10-26', rankingAsOfDate: '2026-10-27' })).toBe(
    'personal_behind',
  );
  expect(leagueBasisRelation({ asOfDate: '2026-10-26', rankingAsOfDate: '2026-10-26' })).toBe(
    'same',
  );
  expect(leagueBasisRelation({})).toBe('unknown');
});
