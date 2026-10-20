/** @vitest-environment jsdom */
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getPortfolioPerformance } from '../../src/features/portfolio/api';
import { usePortfolioPerformance } from '../../src/features/portfolio/hooks/usePortfolioPerformance';

vi.mock('../../src/features/portfolio/api', () => ({ getPortfolioPerformance: vi.fn() }));
vi.mock('../../src/features/portfolio/analytics', () => ({
  trackPortfolioEvent: vi.fn(),
  portfolioErrorType: vi.fn(),
}));
const point = {
  baseDate: '2026-10-19',
  totalPurchaseAmount: 100,
  totalEvaluationAmount: 110,
  totalProfitLoss: 10,
  totalReturnRate: 10,
  dailyProfitLoss: null,
  dailyReturnRate: null,
};
const response = {
  status: 'READY' as const,
  reason: null,
  baseDate: point.baseDate,
  previousBaseDate: '2026-10-16',
  points: [point],
  currentPoint: null,
};
async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}
beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-10-19T14:59:30Z'));
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
  vi.mocked(getPortfolioPerformance).mockResolvedValue(response);
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.clearAllMocks();
});

describe('자산 추이 자동 갱신', () => {
  it('서울 자정이 지나면 새 날짜 범위로 스냅샷을 다시 조회한다', async () => {
    renderHook(() => usePortfolioPerformance('1M', []));
    await flush();
    expect(getPortfolioPerformance).toHaveBeenLastCalledWith(
      '2026-09-19',
      '2026-10-19',
      expect.any(AbortSignal),
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(60_000);
    });
    expect(getPortfolioPerformance).toHaveBeenLastCalledWith(
      '2026-09-20',
      '2026-10-20',
      expect.any(AbortSignal),
    );
  });
  it('보이는 화면만 5분마다 갱신하고 숨긴 화면은 복귀할 때 갱신한다', async () => {
    vi.setSystemTime(new Date('2026-10-19T01:00:00Z'));
    renderHook(() => usePortfolioPerformance('YTD', []));
    await flush();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300_000);
    });
    expect(getPortfolioPerformance).toHaveBeenCalledTimes(2);
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
    await act(async () => {
      await vi.advanceTimersByTimeAsync(600_000);
    });
    expect(getPortfolioPerformance).toHaveBeenCalledTimes(2);
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    expect(getPortfolioPerformance).toHaveBeenCalledTimes(3);
  });
  it('순서가 다른 일별 기록을 보존하고 오늘 중복 기록만 현재 값으로 대체한다', async () => {
    vi.mocked(getPortfolioPerformance).mockResolvedValue({
      ...response,
      points: [point, { ...point, baseDate: '2026-10-16' }, { ...point, baseDate: '2026-10-15' }],
      currentPoint: { ...point, totalEvaluationAmount: 120, priceBaseDate: '2026-10-16' },
    });
    const { result } = renderHook(() => usePortfolioPerformance('1M', []));
    await flush();
    expect(result.current.points.map(({ baseDate }) => baseDate)).toEqual([
      '2026-10-15',
      '2026-10-16',
      '2026-10-19',
    ]);
    expect(result.current.points.at(-1)?.totalEvaluationAmount).toBe(120);
  });
  it('비회원과 닫힌 화면에서는 자동 조회하지 않고 진행 요청도 취소한다', async () => {
    const { rerender, unmount } = renderHook(
      ({ enabled }) => usePortfolioPerformance('1M', [], 0, enabled),
      { initialProps: { enabled: false } },
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300_000);
    });
    expect(getPortfolioPerformance).not.toHaveBeenCalled();
    rerender({ enabled: true });
    await flush();
    const signal = vi.mocked(getPortfolioPerformance).mock.calls[0][2]!;
    unmount();
    expect(signal.aborted).toBe(true);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(300_000);
    });
    expect(getPortfolioPerformance).toHaveBeenCalledTimes(1);
  });
});
