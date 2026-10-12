import { createPortfolioSnapshotKey, isPortfolioAnalysisResponse } from './api';
import type { PortfolioAnalysisResponse, PortfolioAsset } from './types';

export const PORTFOLIO_ANALYSIS_STORAGE_KEY = 'daynomy:portfolio-analysis:v1';
export const GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY = 'daynomy:guest-portfolio-analysis:v1';
export const GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY =
  'daynomy:guest-portfolio-analysis-trial:v1';

type StoredPortfolioAnalysis = {
  date: string;
  portfolioSnapshotKey: string;
  analysis: PortfolioAnalysisResponse;
};

function formatLocalDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function isStoredPortfolioAnalysis(value: unknown): value is StoredPortfolioAnalysis {
  if (!value || typeof value !== 'object') return false;
  const stored = value as Partial<StoredPortfolioAnalysis>;

  return (
    typeof stored.date === 'string' &&
    typeof stored.portfolioSnapshotKey === 'string' &&
    isPortfolioAnalysisResponse(stored.analysis)
  );
}

function getAnalysisStorage(isLoggedIn: boolean) {
  return isLoggedIn ? localStorage : sessionStorage;
}

function getAnalysisStorageKey(isLoggedIn: boolean) {
  return isLoggedIn ? PORTFOLIO_ANALYSIS_STORAGE_KEY : GUEST_PORTFOLIO_ANALYSIS_STORAGE_KEY;
}

export function hasGuestPortfolioAnalysisTrialBeenUsed() {
  try {
    return sessionStorage.getItem(GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY) === 'used';
  } catch {
    return false;
  }
}

export function markGuestPortfolioAnalysisTrialUsed() {
  try {
    sessionStorage.setItem(GUEST_PORTFOLIO_ANALYSIS_TRIAL_STORAGE_KEY, 'used');
  } catch {
    // 서버가 체험 사용 여부를 검증하므로 저장소에 접근할 수 없어도 흐름을 유지한다.
  }
}

export function loadPortfolioAnalysis(
  now = new Date(),
  isLoggedIn = true,
): PortfolioAnalysisResponse | null {
  if (isLoggedIn) return null;
  try {
    const saved = getAnalysisStorage(isLoggedIn).getItem(getAnalysisStorageKey(isLoggedIn));
    if (!saved) return null;

    const parsed: unknown = JSON.parse(saved);
    if (!isStoredPortfolioAnalysis(parsed)) return null;
    if (parsed.date !== formatLocalDate(now)) return null;

    return parsed.analysis;
  } catch {
    return null;
  }
}

export function savePortfolioAnalysis(
  assets: PortfolioAsset[],
  analysis: PortfolioAnalysisResponse,
  now = new Date(),
  isLoggedIn = true,
) {
  if (isLoggedIn) return;
  const stored: StoredPortfolioAnalysis = {
    date: formatLocalDate(now),
    portfolioSnapshotKey: createPortfolioSnapshotKey(assets),
    analysis,
  };

  try {
    getAnalysisStorage(isLoggedIn).setItem(
      getAnalysisStorageKey(isLoggedIn),
      JSON.stringify(stored),
    );
  } catch {
    // 저장 공간을 사용할 수 없어도 현재 분석 결과는 그대로 표시한다.
  }

  if (!isLoggedIn) markGuestPortfolioAnalysisTrialUsed();
}

export function clearPortfolioAnalysis(isLoggedIn = true) {
  try {
    getAnalysisStorage(isLoggedIn).removeItem(getAnalysisStorageKey(isLoggedIn));
  } catch {
    // 저장 공간을 사용할 수 없어도 빈 포트폴리오 상태는 그대로 표시한다.
  }
}
