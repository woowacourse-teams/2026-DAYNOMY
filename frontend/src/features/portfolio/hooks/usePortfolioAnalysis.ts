import { useCallback, useEffect, useRef, useState } from 'react';
import { trackEvent } from '../../../analytics';
import { ApiError } from '../../../api/client';
import {
  analyzePortfolio,
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
} from '../portfolioAnalysisStorage';
import type {
  PortfolioAnalysisResponse,
  PortfolioAsset,
  SavedPortfolioAnalysisResponse,
} from '../types';

// 소유자 변경 시 이 Hook을 사용하는 컴포넌트를 key로 분리해 이전 결과를 즉시 제거한다.
export function usePortfolioAnalysis(
  assets: PortfolioAsset[],
  isLoggedIn: boolean,
  holdingsKey?: string,
) {
  const [analysis, setAnalysis] = useState<PortfolioAnalysisResponse | null>(null);
  const [saved, setSaved] = useState<SavedPortfolioAnalysisResponse | null>(null);
  const [loading, setLoading] = useState(isLoggedIn);
  const [loaded, setLoaded] = useState(!isLoggedIn);
  const [error, setError] = useState<string | null>(null);
  const [trialExhausted, setTrialExhausted] = useState(
    () => !isLoggedIn && hasGuestPortfolioAnalysisTrialBeenUsed(),
  );
  const active = useRef<AbortController | null>(null);
  const snapshotKey = createPortfolioSnapshotKey(assets);

  const accept = useCallback(
    (response: SavedPortfolioAnalysisResponse) => {
      setSaved(response);
      setAnalysis(response.analysis);
      setLoaded(true);
      setError(
        response.status === 'FAILED'
          ? response.analysis &&
            response.matchesCurrentPortfolio &&
            createPortfolioSnapshotKey(response.analysis.impacts) === snapshotKey
            ? '새 소식을 확인하지 못했어요. 이전 분석을 유지하고 있어요.'
            : '현재 보유 자산의 분석을 완료하지 못했어요.'
          : null,
      );
    },
    [snapshotKey],
  );

  const restore = useCallback(
    async (background = false) => {
      active.current?.abort();
      const controller = new AbortController();
      active.current = controller;
      if (!background) setLoading(true);
      setError(null);
      try {
        const response = await getSavedPortfolioAnalysis(controller.signal);
        if (!controller.signal.aborted) accept(response);
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
    [accept],
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
      setLoading(false);
      setError(null);
    }
    return () => {
      active.current?.abort();
      active.current = null;
    };
  }, [isLoggedIn, snapshotKey, restore, holdingsKey]);

  useEffect(() => {
    if (!isLoggedIn || saved?.status !== 'PROCESSING' || loading || error) return;
    const timer = window.setTimeout(() => void restore(true), 3000);
    return () => window.clearTimeout(timer);
  }, [isLoggedIn, saved, loading, error, restore]);

  const analyzedAssets = analysis?.impacts.map(({ assetName, weight }) => ({ assetName, weight }));
  const portfolioChanged = Boolean(
    analysis &&
    ((isLoggedIn && saved && !saved.matchesCurrentPortfolio) ||
      createPortfolioSnapshotKey(analyzedAssets ?? []) !== snapshotKey),
  );

  const analyze = async () => {
    if (active.current) return;
    if (isLoggedIn && (!loaded || saved?.status === 'PROCESSING')) {
      await restore();
      return;
    }
    if (assets.length === 0 || (!isLoggedIn && trialExhausted)) return;
    const controller = new AbortController();
    active.current = controller;
    setLoading(true);
    setError(null);
    const startedAt = performance.now();
    const properties = {
      action: isLoggedIn && analysis && !portfolioChanged ? 'refresh' : 'analyze',
      user_type: isLoggedIn ? 'member' : 'guest',
      asset_count: assets.length,
    };
    const recordResult = (status: string, outcome?: string | null) => {
      if (controller.signal.aborted) return;
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
          accept(response);
          recordResult(response.status, response.lastCheckOutcome);
        }
      } else {
        const response = await analyzePortfolio(assets, controller.signal);
        if (!controller.signal.aborted) {
          recordResult(response.impacts.length ? 'READY' : 'EMPTY');
          setAnalysis(response);
          savePortfolioAnalysis(assets, response, new Date(), false, holdingsKey);
          setTrialExhausted(true);
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
              accept(response);
              recordResult(response.status, response.lastCheckOutcome);
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

  return {
    analysis: portfolioChanged ? null : analysis,
    saved,
    loading,
    loaded,
    error,
    trialExhausted,
    portfolioChanged,
    analyze,
  };
}
