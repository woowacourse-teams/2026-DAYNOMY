/** @vitest-environment jsdom */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const posthog = vi.hoisted(() => ({ init: vi.fn(), capture: vi.fn() }));
vi.mock('posthog-js', () => ({ default: posthog }));

function enablePostHog() {
  vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', 'phc_test');
  vi.stubEnv('VITE_POSTHOG_HOST', 'https://example.posthog.test');
}

function gaEvents() {
  return window.dataLayer
    .map((args) => Array.from(args as IArguments))
    .filter(([kind]) => kind === 'event');
}

beforeEach(() => {
  vi.resetModules();
  vi.stubEnv('GA_MEASUREMENT_ID', 'G-TEST');
  vi.stubEnv('VITE_POSTHOG_PROJECT_TOKEN', '');
  vi.stubEnv('VITE_POSTHOG_HOST', '');
  posthog.init.mockReset();
  posthog.capture.mockReset();
  window.history.replaceState({}, '', '/');
  window.dataLayer = [];
  delete window.gtag;
  Reflect.deleteProperty(window, 'ga-disable-G-TEST');
});

afterEach(() => {
  document.querySelector('script[src*="googletagmanager.com/gtag/js"]')?.remove();
  vi.unstubAllEnvs();
});

describe('포트폴리오 이벤트 수집 경계', () => {
  it('공통 전송 함수로 GA4와 PostHog에 대시보드 이벤트를 한 번씩 전달한다', async () => {
    enablePostHog();
    const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
    trackPortfolioEvent('portfolio_dashboard_viewed', { is_logged_in: false });
    trackPortfolioEvent('portfolio_period_changed', { period: '1M', view: 'inline' });
    expect(gaEvents().map(([, name]) => name)).toEqual([
      'portfolio_dashboard_viewed',
      'portfolio_period_changed',
    ]);
    await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(2));
    expect(posthog.capture).toHaveBeenNthCalledWith(
      2,
      'portfolio_period_changed',
      expect.objectContaining({ period: '1M', view: 'inline', page_path: '/' }),
    );
  });

  it('GA4 설정이 없어도 공통 함수가 연결한 PostHog에 전달한다', async () => {
    vi.stubEnv('GA_MEASUREMENT_ID', '');
    enablePostHog();
    const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
    trackPortfolioEvent('portfolio_dashboard_viewed');
    await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
    expect(gaEvents()).toEqual([]);
  });

  it('PostHog 연결이 없어도 GA4 이벤트를 보내고 SDK를 생성하지 않는다', async () => {
    const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
    expect(() => trackPortfolioEvent('portfolio_dashboard_viewed')).not.toThrow();
    expect(posthog.init).not.toHaveBeenCalled();
    expect(gaEvents()).toHaveLength(1);
  });

  it.each(['/login', '/admin/news', '/news/1', '/stocks/1'])(
    '%s에서는 대시보드 이벤트를 보내지 않는다',
    async (path) => {
      enablePostHog();
      window.history.replaceState({}, '', path);
      const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
      trackPortfolioEvent('portfolio_dashboard_viewed');
      expect(posthog.init).not.toHaveBeenCalled();
      expect(posthog.capture).not.toHaveBeenCalled();
      expect(gaEvents()).toEqual([]);
    },
  );

  it('URL 쿼리·보유정보·이메일을 이벤트 속성으로 전달하지 않는다', async () => {
    window.history.replaceState({}, '', '/?q=private-search#private-fragment');
    enablePostHog();
    const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
    const properties = { hidden: true, quantity: 123, email: 'private@example.com' };
    trackPortfolioEvent('portfolio_amount_visibility_changed', properties);
    expect(JSON.stringify(gaEvents())).not.toMatch(/private|quantity|email/);
    await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
    expect(JSON.stringify(posthog.capture.mock.calls)).not.toMatch(/private|quantity|email/);
    expect(posthog.capture).toHaveBeenCalledWith(
      'portfolio_amount_visibility_changed',
      expect.objectContaining({ hidden: true, page_path: '/' }),
    );
  });

  it('PostHog 전송 실패가 GA4 전송이나 사용자 작업을 중단하지 않는다', async () => {
    enablePostHog();
    posthog.capture.mockImplementation(() => {
      throw new Error('SDK unavailable');
    });
    const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
    expect(() =>
      trackPortfolioEvent('portfolio_save_succeeded', { storage: 'temporary' }),
    ).not.toThrow();
    await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
    expect(gaEvents()).toHaveLength(1);
  });

  it('공통 전송 함수의 예외가 사용자 작업을 중단하지 않는다', async () => {
    const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
    trackPortfolioEvent('portfolio_dashboard_viewed');
    window.gtag = () => {
      throw new Error('tag unavailable');
    };
    expect(() => trackPortfolioEvent('portfolio_dashboard_viewed')).not.toThrow();
  });

  it('공통 함수의 수집 거부를 유지한다', async () => {
    Reflect.set(window, 'ga-disable-G-TEST', true);
    enablePostHog();
    const { trackPortfolioEvent } = await import('../../src/features/portfolio/analytics');
    trackPortfolioEvent('portfolio_save_failed', { error_type: 'server_error' });
    expect(gaEvents()).toEqual([]);
    expect(posthog.capture).not.toHaveBeenCalled();
  });

  it('오류 메시지와 서버 코드 대신 오류 유형만 전달한다', async () => {
    enablePostHog();
    const { trackPortfolioEvent, portfolioErrorType } =
      await import('../../src/features/portfolio/analytics');
    const { ApiError } = await import('../../src/api/client');
    trackPortfolioEvent('portfolio_save_failed', {
      error_type: portfolioErrorType(new ApiError(500, 'private-code', 'private-message')),
    });
    await vi.waitFor(() => expect(posthog.capture).toHaveBeenCalledTimes(1));
    expect(JSON.stringify(gaEvents())).not.toContain('private-');
    expect(JSON.stringify(posthog.capture.mock.calls)).not.toContain('private-');
    expect(posthog.capture).toHaveBeenCalledWith(
      'portfolio_save_failed',
      expect.objectContaining({ error_type: 'server_error' }),
    );
    expect(portfolioErrorType(new ApiError(401, 'private-code', 'private-message'))).toBe(
      'unauthorized',
    );
    expect(portfolioErrorType(new ApiError(400, 'private-code', 'private-message'))).toBe(
      'client_error',
    );
    expect(portfolioErrorType(new Error('private-message'))).toBe('network_or_unknown');
  });
});
