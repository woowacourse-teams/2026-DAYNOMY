import { createPortfolioSnapshotKey, isPortfolioAnalysisResponse } from './api';
import type { PortfolioAnalysisResponse, PortfolioAsset } from './types';

export const PORTFOLIO_ANALYSIS_STORAGE_KEY = 'daynomy:portfolio-analysis:v1';

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

export function loadPortfolioAnalysis(now = new Date()): PortfolioAnalysisResponse | null {
  try {
    const saved = localStorage.getItem(PORTFOLIO_ANALYSIS_STORAGE_KEY);
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
) {
  const stored: StoredPortfolioAnalysis = {
    date: formatLocalDate(now),
    portfolioSnapshotKey: createPortfolioSnapshotKey(assets),
    analysis,
  };

  try {
    localStorage.setItem(PORTFOLIO_ANALYSIS_STORAGE_KEY, JSON.stringify(stored));
  } catch {
    // 저장 공간을 사용할 수 없어도 현재 분석 결과는 그대로 표시한다.
  }
}

export function clearPortfolioAnalysis() {
  try {
    localStorage.removeItem(PORTFOLIO_ANALYSIS_STORAGE_KEY);
  } catch {
    // 저장 공간을 사용할 수 없어도 빈 포트폴리오 상태는 그대로 표시한다.
  }
}
