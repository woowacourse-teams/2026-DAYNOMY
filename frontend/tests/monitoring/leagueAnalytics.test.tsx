/** @vitest-environment jsdom */
import { afterEach, expect, it, vi } from 'vitest';
import { ApiError } from '../../src/api/client';
import { trackEvent } from '../../src/analytics';
import { leagueErrorType, trackLeagueEvent } from '../../src/features/league/analytics';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
afterEach(() => vi.resetAllMocks());

it('허용된 동작·상태만 남기고 식별자·투자 내용·금융 값은 제외한다', () => {
  const input = {
    surface: 'holdings' as const,
    action: 'import_holdings' as const,
    status: 'success' as const,
    result_count: 2,
    overwrite_existing: false,
    hidden: undefined,
    publicId: 'private-id',
    displayName: 'private-name',
    reason: 'private-reason',
    quantity: 70,
    averagePurchasePrice: 123456,
    requestKey: 'private-key',
  };
  trackLeagueEvent('league_action', input);
  expect(trackEvent).toHaveBeenCalledWith('league_action', {
    surface: 'holdings',
    action: 'import_holdings',
    status: 'success',
    result_count: 2,
    overwrite_existing: false,
  });
});

it('동기 분석 전송 오류를 사용자 동작으로 전파하지 않는다', () => {
  vi.mocked(trackEvent).mockImplementation(() => {
    throw new Error('SDK failed');
  });
  expect(() => trackLeagueEvent('publish_portfolio', { scope: 'private' })).not.toThrow();
});

it('원문 오류를 전달하지 않고 HTTP 상태로 실패를 분류한다', () => {
  expect(leagueErrorType(new Error('private-text'))).toBe('network_or_unknown');
  expect(leagueErrorType(new ApiError(401, undefined, 'private-text'))).toBe('unauthorized');
  expect(leagueErrorType(new ApiError(400, undefined, 'private-text'))).toBe('client_error');
  expect(leagueErrorType(new ApiError(503, undefined, 'private-text'))).toBe('server_error');
});
