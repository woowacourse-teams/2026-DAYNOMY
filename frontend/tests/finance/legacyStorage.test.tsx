/** @vitest-environment jsdom */
import { afterEach, expect, it } from 'vitest';
import { readFinancialStorage } from '../../src/features/finance/legacyStorage';
import { loadFinancialPlans, saveFinancialPlan } from '../../src/features/finance/financialPlanApi';
import { loadLearningProgress, saveLearningProgress } from '../../src/features/finance/learningApi';

afterEach(() => localStorage.clear());

it('이전 저장 키는 읽을 수 있고 읽기만으로 기존 기록을 변경하지 않는다', () => {
  const keys = [
    ['daynomy:financial-plans:v1', 'daynomy:romi-consultations'],
    ['daynomy:account-steps:v1', 'daynomy:romi-account-steps'],
    ['daynomy:learning-progress:v1', 'daynomy:romi-progress:v1'],
    ['daynomy:weekly-check-ins:v1', 'daynomy:romi-check-ins:v1'],
    ['daynomy:simulated-trades:v1', 'daynomy:romi-mock-trades:v1'],
  ];
  for (const [current, legacy] of keys) {
    localStorage.setItem(legacy, '기존 기록');
    expect(readFinancialStorage(current)).toBe('기존 기록');
    expect(localStorage.getItem(current)).toBeNull();
    expect(localStorage.getItem(legacy)).toBe('기존 기록');
  }
});

it('새 키의 빈 목록도 우선하므로 삭제한 이전 기록을 되살리지 않는다', () => {
  localStorage.setItem('daynomy:romi-consultations', '["이전 기록"]');
  localStorage.setItem('daynomy:financial-plans:v1', '[]');
  expect(readFinancialStorage('daynomy:financial-plans:v1')).toBe('[]');
  expect(readFinancialStorage('존재하지 않는 키')).toBeNull();
});

it('이전 월 계획과 학습 기록을 재조회하고 새 키에만 변경 내용을 저장한다', async () => {
  const plan = {
    id: 'legacy-plan',
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
  localStorage.setItem('daynomy:romi-consultations', JSON.stringify([plan]));
  localStorage.setItem('daynomy:romi-progress:v1', JSON.stringify([progress]));
  expect(await loadFinancialPlans(false)).toEqual([plan]);
  expect(await loadLearningProgress(false)).toEqual([progress]);
  await saveFinancialPlan(false, { ...plan, title: '수정한 계획' });
  await saveLearningProgress(false, { ...progress, bookmarked: true });
  expect(await loadFinancialPlans(false)).toEqual([{ ...plan, title: '수정한 계획' }]);
  expect(await loadLearningProgress(false)).toEqual([{ ...progress, bookmarked: true }]);
  expect(JSON.parse(localStorage.getItem('daynomy:romi-consultations')!)).toEqual([plan]);
  expect(JSON.parse(localStorage.getItem('daynomy:romi-progress:v1')!)).toEqual([progress]);
});
