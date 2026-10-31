/** @vitest-environment jsdom */
import { StrictMode } from 'react';
import { trackEvent } from '../../src/analytics';
import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { AggregationNotice } from '../../src/features/league/AggregationNotice';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const pending = {
  baseDate: '2026-10-19',
  expectedAt: '2026-10-22T08:20:00Z',
  firstReturnExpectedAt: null,
  reason: 'SNAPSHOT_PENDING',
  delayed: false,
};

it('누락된 날짜와 한국 시간의 다음 집계를 안내하고 새로고침할 수 있다', () => {
  const refresh = vi.fn();
  const view = render(<AggregationNotice aggregation={pending} onRefresh={refresh} />);
  expect(view.getByRole('heading', { name: '기록 확인 중' })).toBeTruthy();
  expect(view.getByText('10월 19일 기록')).toBeTruthy();
  expect(view.getByText(/10월 22일.*17:20/).getAttribute('datetime')).toBe(pending.expectedAt);
  fireEvent.click(view.getByRole('button', { name: '집계 현황 새로고침' }));
  expect(refresh).toHaveBeenCalledOnce();
  expect(trackEvent).toHaveBeenCalledWith(
    'league_aggregation_refresh',
    expect.objectContaining({
      trigger: 'manual',
      surface: 'ranking',
      aggregation_reason: 'SNAPSHOT_PENDING',
    }),
  );
  expect(view.queryByText('0.00%')).toBeNull();
});

it.each(['private-internal-error', '__proto__', 'constructor'])(
  '일정을 모르면 날짜나 알 수 없는 사유 %s를 노출하지 않는다',
  (reason) => {
    const view = render(
      <AggregationNotice
        aggregation={{ ...pending, expectedAt: null, reason }}
        onRefresh={vi.fn()}
      />,
    );
    expect(view.getByText('일정 확인 중')).toBeTruthy();
    expect(view.container.querySelector('time')).toBeNull();
    expect(view.getByRole('heading', { name: '집계 대기' })).toBeTruthy();
    expect(view.container.textContent).not.toContain(reason);
  },
);

it('첫 기준과 첫 수익률 변화의 예상 일정을 각각 표시한다', () => {
  const view = render(
    <AggregationNotice
      aggregation={{
        ...pending,
        reason: 'MARKET_CLOSE_PENDING',
        expectedAt: '2026-10-22T05:30:00Z',
        firstReturnExpectedAt: '2026-10-23T05:30:00Z',
      }}
      onRefresh={vi.fn()}
    />,
  );
  expect(view.getByText('집계 예정')).toBeTruthy();
  expect(view.getByText('첫 변동 예상')).toBeTruthy();
  expect([...view.container.querySelectorAll('time')].map((time) => time.dateTime)).toEqual([
    '2026-10-22T05:30:00Z',
    '2026-10-23T05:30:00Z',
  ]);
});

it('제공 지연을 오류 대신 대기 상태로 구분한다', () => {
  const view = render(
    <AggregationNotice
      aggregation={{ ...pending, reason: 'PRICE_COLLECTION_DELAYED', delayed: true }}
      onRefresh={vi.fn()}
    />,
  );
  expect(view.getByRole('heading', { name: '종가 제공 지연' })).toBeTruthy();
  expect(view.queryByRole('alert')).toBeNull();
});

it('예고 시각 30초 후 한 번 새로고침하고 반복 요청하지 않는다', async () => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-10-24T08:19:00Z'));
  const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
  try {
    const refresh = vi.fn();
    const view = render(
      <AggregationNotice
        aggregation={{
          ...pending,
          expectedAt: '2026-10-24T08:20:00Z',
          reason: 'PRICE_COLLECTION_PENDING',
        }}
        onRefresh={refresh}
      />,
    );
    expect(view.getByText(/10월 24일.*17:20/)).toBeTruthy();
    await act(() => vi.advanceTimersByTimeAsync(90_000));
    expect(refresh).toHaveBeenCalledOnce();
    expect(trackEvent).toHaveBeenCalledWith(
      'league_aggregation_refresh',
      expect.objectContaining({
        trigger: 'scheduled',
        aggregation_reason: 'PRICE_COLLECTION_PENDING',
      }),
    );
    await act(() => vi.advanceTimersByTimeAsync(120_000));
    expect(refresh).toHaveBeenCalledOnce();
    view.unmount();
  } finally {
    visibility.mockRestore();
    vi.useRealTimers();
  }
});

it('보이는 집계 안내만 상태별 한 번 기록하고 알 수 없는 사유 원문은 보내지 않는다', () => {
  let visible!: IntersectionObserverCallback;
  const disconnect = vi.fn();
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      constructor(callback: IntersectionObserverCallback) {
        visible = callback;
      }
      observe = vi.fn();
      unobserve = vi.fn();
      disconnect = disconnect;
    },
  );
  vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
  const refresh = vi.fn();
  const view = render(
    <StrictMode>
      <AggregationNotice aggregation={pending} onRefresh={refresh} surface="daily_history" />
    </StrictMode>,
  );
  expect(trackEvent).not.toHaveBeenCalled();
  act(() =>
    visible(
      [{ isIntersecting: true, intersectionRatio: 0.5 } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  );
  act(() =>
    visible(
      [{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  );
  expect(trackEvent).toHaveBeenCalledTimes(1);
  expect(trackEvent).toHaveBeenCalledWith(
    'league_aggregation_viewed',
    expect.objectContaining({
      surface: 'daily_history',
      aggregation_reason: 'SNAPSHOT_PENDING',
      has_expected_at: true,
    }),
  );
  view.rerender(
    <AggregationNotice
      aggregation={{ ...pending, reason: 'private-internal-reason' }}
      onRefresh={refresh}
    />,
  );
  act(() =>
    visible(
      [{ isIntersecting: true, intersectionRatio: 1 } as IntersectionObserverEntry],
      {} as IntersectionObserver,
    ),
  );
  expect(trackEvent).toHaveBeenLastCalledWith(
    'league_aggregation_viewed',
    expect.objectContaining({ aggregation_reason: 'UNKNOWN' }),
  );
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('private-internal-reason');
});
