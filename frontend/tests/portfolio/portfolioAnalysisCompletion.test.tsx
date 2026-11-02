/** @vitest-environment jsdom */
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { trackEvent } from '../../src/analytics';
import { usePortfolioAnalysis } from '../../src/features/portfolio/hooks/usePortfolioAnalysis';
import {
  getSavedPortfolioAnalysis,
  updateSavedPortfolioAnalysis,
  getGuestPortfolioAnalysis,
  startGuestPortfolioAnalysis,
} from '../../src/features/portfolio/api';
import type { SavedPortfolioAnalysisResponse } from '../../src/features/portfolio/types';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
vi.mock('../../src/features/portfolio/api', () => ({
  createPortfolioSnapshotKey: (assets: unknown) => JSON.stringify(assets),
  getSavedPortfolioAnalysis: vi.fn(),
  updateSavedPortfolioAnalysis: vi.fn(),
  getGuestPortfolioAnalysis: vi.fn(),
  startGuestPortfolioAnalysis: vi.fn(),
}));
const assets = [{ assetName: 'private-asset', weight: 100 }];
const empty: SavedPortfolioAnalysisResponse = {
  status: 'EMPTY',
  analysisId: null,
  analysis: null,
  summaryStatus: null,
  lastCheckOutcome: null,
  matchesCurrentPortfolio: true,
  lastSucceededAt: null,
  lastFailedAt: null,
  failureCode: null,
  changeReason: null,
};
const completed = () =>
  vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'portfolio_analysis_completed');
afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.resetAllMocks();
  sessionStorage.clear();
  localStorage.clear();
});

it('즉시 완료 이벤트의 계측 예외가 결과 상태 반영을 막지 않는다', async () => {
  vi.mocked(getSavedPortfolioAnalysis).mockResolvedValue(empty);
  vi.mocked(updateSavedPortfolioAnalysis).mockResolvedValue({ ...empty, status: 'READY' });
  vi.mocked(trackEvent).mockImplementation((name) => {
    if (name === 'portfolio_analysis_completed') throw new Error('measurement unavailable');
  });
  const { result } = renderHook(() => usePortfolioAnalysis(assets, true));
  await waitFor(() => expect(result.current.loaded).toBe(true));
  await act(async () => {
    await result.current.analyze();
  });
  expect(result.current.saved?.status).toBe('READY');
  expect(result.current.error).toBeNull();
  expect(completed()).toHaveLength(1);
});

it.each(['READY', 'FAILED'] as const)(
  '접수와 최종 %s를 분리하고 요약 완료까지 기다린 뒤 한 번만 기록한다',
  async (terminal) => {
    vi.mocked(getSavedPortfolioAnalysis).mockResolvedValue(empty);
    vi.mocked(updateSavedPortfolioAnalysis).mockResolvedValue({ ...empty, status: 'PROCESSING' });
    const { result } = renderHook(() => usePortfolioAnalysis(assets, true));
    await waitFor(() => expect(result.current.loaded).toBe(true));
    expect(completed()).toHaveLength(0);
    vi.useFakeTimers();
    await act(async () => {
      await result.current.analyze();
    });
    expect(trackEvent).toHaveBeenCalledWith(
      'portfolio_analysis_result',
      expect.objectContaining({ status: 'PROCESSING' }),
    );
    expect(completed()).toHaveLength(0);
    vi.mocked(getSavedPortfolioAnalysis).mockResolvedValue({
      ...empty,
      status: 'READY',
      summaryStatus: 'PENDING',
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(completed()).toHaveLength(0);
    vi.mocked(getSavedPortfolioAnalysis).mockResolvedValue({
      ...empty,
      status: 'READY',
      summaryStatus: terminal,
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1000);
    });
    expect(completed()).toHaveLength(1);
    expect(completed()[0][1]).toMatchObject({
      status: terminal,
      summary_status: terminal,
      action: 'analyze',
      user_type: 'member',
      duration_ms: 2000,
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(5000);
    });
    expect(completed()).toHaveLength(1);
    expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('private-asset');
  },
);

it('재접속한 진행 작업과 화면 이탈 후 응답은 완료 이벤트로 세지 않는다', async () => {
  vi.mocked(getSavedPortfolioAnalysis).mockResolvedValue({ ...empty, status: 'PROCESSING' });
  const view = renderHook(() => usePortfolioAnalysis(assets, true));
  await waitFor(() => expect(view.result.current.processing).toBe(true));
  vi.useFakeTimers();
  vi.mocked(getSavedPortfolioAnalysis).mockResolvedValue({ ...empty, status: 'READY' });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(1000);
  });
  expect(completed()).toHaveLength(0);
  view.unmount();
  vi.useRealTimers();
  vi.mocked(getSavedPortfolioAnalysis).mockResolvedValue(empty);
  const newView = renderHook(() => usePortfolioAnalysis(assets, true));
  await waitFor(() => expect(newView.result.current.loaded).toBe(true));
  let finish!: (value: SavedPortfolioAnalysisResponse) => void;
  vi.mocked(updateSavedPortfolioAnalysis).mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  let request!: Promise<void>;
  act(() => {
    request = newView.result.current.analyze();
  });
  newView.unmount();
  await act(async () => {
    finish({ ...empty, status: 'READY' });
    await request;
  });
  expect(completed()).toHaveLength(0);
});

it('비회원 비동기 분석도 최종 실패를 한 번 기록한다', async () => {
  const guest = {
    taskId: null,
    status: 'EMPTY',
    analysis: null,
    summaryStatus: null,
    failureCode: null,
    retryAllowed: false,
  } as const;
  vi.mocked(getGuestPortfolioAnalysis).mockResolvedValue(guest);
  vi.mocked(startGuestPortfolioAnalysis).mockResolvedValue({
    ...guest,
    taskId: 'private-task',
    status: 'PROCESSING',
  });
  const { result } = renderHook(() => usePortfolioAnalysis(assets, false));
  await waitFor(() => expect(result.current.loaded).toBe(true));
  vi.useFakeTimers();
  await act(async () => {
    await result.current.analyze();
  });
  vi.mocked(getGuestPortfolioAnalysis).mockResolvedValue({
    ...guest,
    taskId: 'private-task',
    status: 'FAILED',
    retryAllowed: true,
  });
  await act(async () => {
    await vi.advanceTimersByTimeAsync(1000);
  });
  expect(completed()).toHaveLength(1);
  expect(completed()[0][1]).toMatchObject({
    status: 'FAILED',
    user_type: 'guest',
    duration_ms: 1000,
  });
  expect(JSON.stringify(completed())).not.toContain('private-task');
});
