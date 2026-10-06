/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import {
  FinancialPlanPage,
  WeeklyPracticePage,
} from '../../src/features/finance/FinancialPlanningPages';
import * as plans from '../../src/features/finance/financialPlanApi';
import * as api from '../../src/features/finance/learningApi';

vi.mock('../../src/features/finance/financialPlanApi', () => ({
  loadFinancialPlans: vi.fn(),
  saveFinancialPlan: vi.fn(),
  deleteFinancialPlan: vi.fn(),
}));
vi.mock('../../src/features/finance/learningApi', () => ({
  loadWeeklyCheckIns: vi.fn(),
  saveWeeklyCheckIn: vi.fn(),
}));
beforeEach(() => {
  vi.mocked(api.loadWeeklyCheckIns).mockResolvedValue([]);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
  localStorage.clear();
});
const plan = {
  id: 'account-plan',
  topic: '저축·투자 계획' as const,
  title: '계정 계획',
  summary: '월 계획',
  details: [],
  actions: [{ id: 'saving', label: '자동이체 설정', completed: false }],
  createdAt: '2026-10-05T00:00:00Z',
  monthlyPlan: {
    monthlyIncome: 3000000,
    monthlyExpenses: 1800000,
    goalAmount: 10000000,
    goalSaved: 1000000,
    goalMonths: 24,
    savings: 420000,
    emergency: 600000,
    investment: 180000,
    debt: 0,
  },
};
function renderPage(page: React.ReactNode) {
  return render(
    <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
      <MemoryRouter>{page}</MemoryRouter>
    </AuthContext.Provider>,
  );
}

it('로그인 계획 저장은 서버 성공을 기다리고 실패하면 다시 저장할 수 있다', async () => {
  vi.mocked(plans.saveFinancialPlan)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(plan);
  const view = renderPage(<FinancialPlanPage />);
  fireEvent.click(view.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
  fireEvent.click(view.getByRole('button', { name: '이 계획 저장하기' }));
  expect(await view.findByRole('alert')).toHaveProperty(
    'textContent',
    expect.stringContaining('저장하지 못했어요'),
  );
  expect(view.queryByRole('status')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '이 계획 저장하기' }));
  expect(await view.findByRole('status')).toHaveProperty(
    'textContent',
    expect.stringContaining('계정과'),
  );
  expect(plans.saveFinancialPlan).toHaveBeenCalledWith(
    true,
    expect.objectContaining({ monthlyPlan: plan.monthlyPlan }),
  );
  expect(localStorage.getItem('daynomy:financial-plans:v1')).toBeNull();
});

it('계정 계획을 불러와 할 일과 삭제를 서버에 저장하고 비로그인 기록을 섞지 않는다', async () => {
  localStorage.setItem(
    'daynomy:financial-plans:v1',
    JSON.stringify([{ ...plan, title: '브라우저 계획' }]),
  );
  vi.mocked(plans.loadFinancialPlans).mockResolvedValue([plan]);
  vi.mocked(plans.saveFinancialPlan).mockResolvedValue({
    ...plan,
    actions: [{ ...plan.actions[0], completed: true }],
  });
  vi.mocked(plans.deleteFinancialPlan).mockResolvedValue();
  const view = renderPage(<WeeklyPracticePage />);
  expect(await view.findByRole('heading', { name: '계정 계획' })).toBeTruthy();
  expect(view.queryByRole('heading', { name: '브라우저 계획' })).toBeNull();
  fireEvent.click(view.getByLabelText('자동이체 설정'));
  await waitFor(() => expect(view.getByLabelText('자동이체 설정')).toHaveProperty('checked', true));
  fireEvent.click(view.getByRole('button', { name: '기록 삭제' }));
  fireEvent.click(view.getByRole('button', { name: '삭제 확인' }));
  await waitFor(() => expect(plans.deleteFinancialPlan).toHaveBeenCalledWith(true, 'account-plan'));
  expect(localStorage.getItem('daynomy:financial-plans:v1')).toContain('브라우저 계획');
});

it('계획 조회 실패를 빈 기록으로 표시하지 않고 재시도를 제공한다', async () => {
  vi.mocked(plans.loadFinancialPlans).mockRejectedValue(new Error('offline'));
  const view = renderPage(<WeeklyPracticePage />);
  expect(await view.findByRole('alert')).toHaveProperty(
    'textContent',
    expect.stringContaining('불러오지 못했어요'),
  );
  expect(view.queryByText('아직 저장한 계획이 없어요')).toBeNull();
  expect(view.getByRole('button', { name: '저장한 계획 다시 불러오기' })).toBeTruthy();
});
