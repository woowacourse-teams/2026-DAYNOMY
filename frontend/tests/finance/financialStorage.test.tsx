/** @vitest-environment jsdom */
import { afterEach, expect, it, vi } from 'vitest';
import { loadFinancialPlans, saveFinancialPlan } from '../../src/features/finance/financialPlanApi';
import { loadLearningProgress, saveLearningProgress } from '../../src/features/finance/learningApi';

const PLANS_STORAGE_KEY = 'daynomy:financial-plans:v1';
const PROGRESS_STORAGE_KEY = 'daynomy:learning-progress:v1';
const PORTFOLIO_STORAGE_KEY = 'daynomy:portfolio-holdings:v1';

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

it('기록이 없으면 빈 목록을 읽으며 저장소에 쓰지 않는다', async () => {
  const write = vi.spyOn(Storage.prototype, 'setItem');
  expect(await loadFinancialPlans(false)).toEqual([]);
  expect(await loadLearningProgress(false)).toEqual([]);
  expect(write).not.toHaveBeenCalled();
});

it('저장된 빈 목록과 손상된 기록은 다른 저장 키에서 되살리지 않는다', async () => {
  localStorage.setItem(PLANS_STORAGE_KEY, '[]');
  localStorage.setItem(PROGRESS_STORAGE_KEY, '{broken');
  expect(await loadFinancialPlans(false)).toEqual([]);
  expect(await loadLearningProgress(false)).toEqual([]);
  expect(localStorage.getItem(PROGRESS_STORAGE_KEY)).toBe('{broken');
});

it('월 계획과 학습 기록을 기능명 키에 저장하고 원본 포트폴리오는 유지한다', async () => {
  const plan = {
    id: 'stored-plan',
    topic: '저축·투자 계획' as const,
    title: '기존 월 계획',
    summary: '월 저축 기록',
    details: [],
    createdAt: '2026-10-05T00:00:00Z',
  };
  const progress = {
    itemKey: 'guide-first-account',
    itemType: 'GUIDE' as const,
    completed: true,
    bookmarked: false,
  };
  const originalPortfolio = '[{"assetId":1,"quantity":2}]';
  localStorage.setItem(PORTFOLIO_STORAGE_KEY, originalPortfolio);
  localStorage.setItem(PLANS_STORAGE_KEY, JSON.stringify([plan]));
  localStorage.setItem(PROGRESS_STORAGE_KEY, JSON.stringify([progress]));
  expect(await loadFinancialPlans(false)).toEqual([plan]);
  expect(await loadLearningProgress(false)).toEqual([progress]);
  await saveFinancialPlan(false, { ...plan, title: '수정한 계획' });
  await saveLearningProgress(false, { ...progress, bookmarked: true });
  expect(await loadFinancialPlans(false)).toEqual([{ ...plan, title: '수정한 계획' }]);
  expect(await loadLearningProgress(false)).toEqual([{ ...progress, bookmarked: true }]);
  expect(localStorage.getItem(PORTFOLIO_STORAGE_KEY)).toBe(originalPortfolio);
});
