import { useCallback, useEffect, useRef, useState } from 'react';
import { trackEvent } from '../../../analytics';
import { trackPortfolioEvent } from '../analytics';
import { ApiError } from '../../../api/client';
import {
  getGuestPortfolioAnalysis,
  startGuestPortfolioAnalysis,
  createPortfolioSnapshotKey,
  getSavedPortfolioAnalysis,
  updateSavedPortfolioAnalysis,
} from '../api';
import {
  clearPortfolioAnalysis,
  hasGuestPortfolioAnalysisTrialBeenUsed,
  loadPortfolioAnalysis,
  markGuestPortfolioAnalysisTrialUsed,
  savePortfolioAnalysis,
  matchesGuestPortfolioAnalysisHoldings,
} from '../portfolioAnalysisStorage';
import type {
  PortfolioAnalysisResponse,
  PortfolioAsset,
  SavedPortfolioAnalysisResponse,
  GuestPortfolioAnalysisTaskResponse,
} from '../types';

// 소유자 변경 시 이 Hook을 사용하는 컴포넌트를 key로 분리해 이전 결과를 즉시 제거한다.
export function usePortfolioAnalysis(
  assets: PortfolioAsset[],
  isLoggedIn: boolean,
  holdingsKey?: string,
) {
  const [analysis, setAnalysis] = useState<PortfolioAnalysisResponse | null>(null);
  const [saved, setSaved] = useState<SavedPortfolioAnalysisResponse | null>(null);
  const [guest, setGuest] = useState<GuestPortfolioAnalysisTaskResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [trialExhausted, setTrialExhausted] = useState(
    () => !isLoggedIn && hasGuestPortfolioAnalysisTrialBeenUsed(),
  );
  const active = useRef<AbortController | null>(null);
  // 이 화면에서 시작한 작업만 측정하고 복원·자동 분석에는 시작 시간을 붙이지 않는다.
  const pendingMeasurement = useRef<{
    startedAt: number;
    action: 'analyze' | 'refresh';
    user_type: 'member' | 'guest';
    asset_count: number;
  } | null>(null);
  const completeMeasurement = useCallback(
    (
      status:
        | Exclude<SavedPortfolioAnalysisResponse['status'], 'PROCESSING'>
        | 'ERROR'
        | 'CONFLICT'
        | 'TRIAL_EXHAUSTED'
        | 'REFRESH_LIMIT_EXCEEDED',
      summaryStatus?: string,
      lastCheckOutcome?: SavedPortfolioAnalysisResponse['lastCheckOutcome'],
    ) => {
      const attempt = pendingMeasurement.current;
      if (!attempt || summaryStatus === 'PENDING') return;
      pendingMeasurement.current = null;
      const { startedAt, ...properties } = attempt;
      trackPortfolioEvent('portfolio_analysis_completed', {
        ...properties,
        status: summaryStatus === 'FAILED' ? 'FAILED' : status,
        summary_status: summaryStatus,
        last_check_outcome: lastCheckOutcome ?? undefined,
        duration_ms: Math.max(0, Math.round(performance.now() - startedAt)),
      });
    },
    [],
  );
  const snapshotKey = createPortfolioSnapshotKey(assets);
  const processing = (isLoggedIn ? saved?.status : guest?.status) === 'PROCESSING';
  const summaryStatus = isLoggedIn ? saved?.summaryStatus : guest?.summaryStatus;

  const acceptGuest = useCallback(
    (response: GuestPortfolioAnalysisTaskResponse) => {
      if (response.status !== 'PROCESSING' && response.status !== 'EMPTY')
        completeMeasurement(response.status, response.summaryStatus ?? undefined);
      setGuest(response);
      setLoaded(true);
      if (response.analysis && matchesGuestPortfolioAnalysisHoldings(holdingsKey)) {
        setAnalysis(response.analysis);
        savePortfolioAnalysis(
          response.analysis.impacts,
          response.analysis,
          new Date(),
          false,
          holdingsKey,
        );
      }
      if (response.taskId) {
        markGuestPortfolioAnalysisTrialUsed();
        setTrialExhausted(!response.retryAllowed && response.status !== 'PROCESSING');
      }
      setError(
        response.status === 'FAILED' && response.retryAllowed ? '분석을 완료하지 못했어요.' : null,
      );
    },
    [holdingsKey, completeMeasurement],
  );

  const accept = useCallback(
    (response: SavedPortfolioAnalysisResponse) => {
      if (response.status !== 'PROCESSING' && response.status !== 'EMPTY')
        completeMeasurement(
          response.status,
          response.summaryStatus ?? undefined,
          response.lastCheckOutcome,
        );
      setSaved(response);
      setAnalysis(response.analysis);
      setLoaded(true);
      setError(
        response.status === 'FAILED'
          ? response.analysis &&
            response.matchesCurrentPortfolio &&
            createPortfolioSnapshotKey([
              ...response.analysis.impacts,
              ...(response.analysis.excludedAssets ?? []),
            ]) === snapshotKey
            ? '새 소식을 확인하지 못했어요. 이전 분석을 유지하고 있어요.'
            : '현재 보유 자산의 분석을 완료하지 못했어요.'
          : null,
      );
    },
    [snapshotKey, completeMeasurement],
  );

  const restore = useCallback(
    async (background = false) => {
      active.current?.abort();
      const controller = new AbortController();
      active.current = controller;
      if (!background) setLoading(true);
      setError(null);
      try {
        if (isLoggedIn) {
          const response = await getSavedPortfolioAnalysis(controller.signal);
          if (!controller.signal.aborted) accept(response);
        } else {
          const response = await getGuestPortfolioAnalysis(controller.signal);
          if (!controller.signal.aborted) acceptGuest(response);
        }
      } catch {
        if (!controller.signal.aborted)
          setError('저장된 분석을 불러오지 못했어요. 다시 시도해 주세요.');
      } finally {
        if (active.current === controller) {
          active.current = null;
          if (!controller.signal.aborted) setLoading(false);
        }
      }
    },
    [accept, acceptGuest, isLoggedIn],
  );

  useEffect(() => {
    if (isLoggedIn) {
      // 과거 회원 공용 캐시는 읽거나 새 계정으로 이전하지 않는다.
      clearPortfolioAnalysis(true);
      void restore();
    } else {
      if (snapshotKey === '[]') clearPortfolioAnalysis(false);
      const cached =
        snapshotKey === '[]' ? null : loadPortfolioAnalysis(new Date(), false, holdingsKey);
      setAnalysis(cached);
      setTrialExhausted(cached !== null || hasGuestPortfolioAnalysisTrialBeenUsed());
      setLoading(cached === null && snapshotKey !== '[]');
      setError(null);
      if (snapshotKey !== '[]') void restore(true);
    }
    return () => {
      active.current?.abort();
      active.current = null;
      pendingMeasurement.current = null;
    };
  }, [isLoggedIn, snapshotKey, restore, holdingsKey]);

  useEffect(() => {
    if ((!processing && summaryStatus !== 'PENDING') || loading || error) return;
    const timer = window.setTimeout(() => void restore(true), 1000);
    return () => window.clearTimeout(timer);
  }, [processing, summaryStatus, loading, error, restore, saved, guest]);

  const analyzedAssets = analysis ? [...analysis.impacts, ...(analysis.excludedAssets ?? [])] : [];
  const portfolioChanged = Boolean(
    analysis &&
    ((isLoggedIn && saved && !saved.matchesCurrentPortfolio) ||
      createPortfolioSnapshotKey(analyzedAssets) !== snapshotKey),
  );

  const analyze = async () => {
    if (active.current) return;
    if (processing || summaryStatus === 'PENDING') {
      await restore();
      return;
    }
    if (isLoggedIn && (!loaded || saved?.status === 'PROCESSING')) {
      await restore();
      return;
    }
    if (assets.length === 0 || (!isLoggedIn && trialExhausted)) return;
    if (isLoggedIn && analysis && !portfolioChanged && saved?.remainingRefreshCount === 0) return;
    const controller = new AbortController();
    active.current = controller;
    setLoading(true);
    setError(null);
    const startedAt = performance.now();
    const properties = {
      action: isLoggedIn && analysis && !portfolioChanged ? 'refresh' : 'analyze',
      user_type: isLoggedIn ? 'member' : 'guest',
      asset_count: assets.length,
    } as const;
    pendingMeasurement.current = { ...properties, startedAt };
    const recordResult = (status: string, outcome?: string | null) => {
      if (controller.signal.aborted) return;
      if (
        status === 'ERROR' ||
        status === 'CONFLICT' ||
        status === 'TRIAL_EXHAUSTED' ||
        status === 'REFRESH_LIMIT_EXCEEDED'
      )
        completeMeasurement(status);
      trackEvent('portfolio_analysis_result', {
        ...properties,
        status,
        last_check_outcome: outcome ?? undefined,
        duration_ms: Math.max(0, Math.round(performance.now() - startedAt)),
      });
    };
    trackEvent('start_portfolio_analysis', properties);
    try {
      if (isLoggedIn) {
        const response = await updateSavedPortfolioAnalysis(
          Boolean(analysis && !portfolioChanged),
          controller.signal,
        );
        if (!controller.signal.aborted) {
          recordResult(response.status, response.lastCheckOutcome);
          accept(response);
        }
      } else {
        // 서버 작업이 없는 경우에도 먼저 같은 세션 쿠키를 확보한다.
        await getGuestPortfolioAnalysis(controller.signal);
        const response = await startGuestPortfolioAnalysis(
          assets,
          controller.signal,
          guest?.retryAllowed ? (guest.taskId ?? undefined) : undefined,
        );
        if (!controller.signal.aborted) {
          recordResult(response.status);
          acceptGuest(response);
        }
      }
    } catch (caught) {
      if (controller.signal.aborted) return;
      if (
        !isLoggedIn &&
        caught instanceof ApiError &&
        caught.code === 'PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED'
      ) {
        recordResult('TRIAL_EXHAUSTED');
        markGuestPortfolioAnalysisTrialUsed();
        setTrialExhausted(true);
      } else if (
        isLoggedIn &&
        caught instanceof ApiError &&
        caught.code === 'PORTFOLIO_ANALYSIS_REFRESH_LIMIT_EXCEEDED'
      ) {
        recordResult('REFRESH_LIMIT_EXCEEDED');
        try {
          const response = await getSavedPortfolioAnalysis(controller.signal);
          if (!controller.signal.aborted) accept(response);
        } catch {
          if (!controller.signal.aborted)
            setError('오늘 남은 확인 횟수를 불러오지 못했어요. 새로고침해 주세요.');
        }
      } else if (isLoggedIn && caught instanceof ApiError && caught.status === 409) {
        recordResult('CONFLICT');
        // 다른 탭의 실행 여부와 자산 변경 상태를 서버에서 다시 확인한다.
        try {
          const response = await getSavedPortfolioAnalysis(controller.signal);
          if (!controller.signal.aborted) accept(response);
        } catch {
          if (!controller.signal.aborted)
            setError('분석 진행 상태를 확인하지 못했어요. 다시 시도해 주세요.');
        }
      } else {
        // 연결 실패 뒤에도 서버 작업은 계속될 수 있으므로 최종 상태부터 확인한다.
        if (!isLoggedIn && (!(caught instanceof ApiError) || caught.status >= 500)) {
          try {
            const response = await getGuestPortfolioAnalysis(controller.signal);
            if (controller.signal.aborted) return;
            if (response.status !== 'EMPTY') {
              recordResult(response.status);
              acceptGuest(response);
              return;
            }
          } catch {
            if (controller.signal.aborted) return;
          }
        }
        if (isLoggedIn && (!(caught instanceof ApiError) || caught.status >= 500)) {
          try {
            const response = await getSavedPortfolioAnalysis(controller.signal);
            if (controller.signal.aborted) return;
            if (
              response.status === 'PROCESSING' ||
              (response.analysis !== null &&
                response.status !== 'FAILED' &&
                (response.analysisId !== saved?.analysisId ||
                  response.lastSucceededAt !== saved?.lastSucceededAt))
            ) {
              recordResult(response.status, response.lastCheckOutcome);
              accept(response);
              return;
            }
          } catch {
            if (controller.signal.aborted) return;
          }
        }
        recordResult('ERROR');
        setError(
          analysis && !portfolioChanged
            ? '새 소식을 확인하지 못했어요. 이전 분석을 유지하고 있어요.'
            : '포트폴리오 분석을 완료하지 못했어요.',
        );
      }
    } finally {
      if (active.current === controller) {
        active.current = null;
        if (!controller.signal.aborted) setLoading(false);
      }
    }
  };

  useEffect(() => {
    if (
      !isLoggedIn ||
      saved?.remainingRefreshCount === undefined ||
      !saved.refreshResetAt ||
      loading ||
      processing ||
      summaryStatus === 'PENDING'
    )
      return;
    const timer = window.setTimeout(
      () => void restore(true),
      Math.max(1000, Date.parse(saved.refreshResetAt) - Date.now()),
    );
    return () => window.clearTimeout(timer);
  }, [
    isLoggedIn,
    saved?.remainingRefreshCount,
    saved?.refreshResetAt,
    loading,
    processing,
    summaryStatus,
    restore,
  ]);

  return {
    analysis: portfolioChanged ? null : analysis,
    saved,
    loading,
    loaded,
    error,
    trialExhausted,
    portfolioChanged,
    analyze,
    processing,
    summaryStatus,
  };
}
