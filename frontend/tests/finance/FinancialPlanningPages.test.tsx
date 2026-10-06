/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  FinancialPlanPage,
  WeeklyPracticePage,
} from '../../src/features/finance/FinancialPlanningPages';
import { FinancialGuidePage } from '../../src/features/finance/FinancialGuidePage';
import * as learningApi from '../../src/features/finance/learningApi';
import { CONSULTATIONS_STORAGE_KEY } from '../../src/features/finance/financialPlanning';

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe('금융 학습 상담', () => {
  it('계좌 준비 체크리스트는 계획 입력이 아닌 지식 가이드에서 저장한다', async () => {
    const view = render(
      <MemoryRouter initialEntries={['/finance/guides/first-account']}>
        <Routes>
          <Route path="/finance/guides/:guideId" element={<FinancialGuidePage />} />
        </Routes>
      </MemoryRouter>,
    );

    const checkbox = await view.findByLabelText(/계좌 목적 정하기/);
    await waitFor(() => expect(checkbox).toHaveProperty('disabled', false));
    fireEvent.click(checkbox);
    await waitFor(() =>
      expect(localStorage.getItem('daynomy:learning-progress:v1')).toContain('account-goal'),
    );
  });

  it('저축과 투자를 함께 배분하고 계획을 저장한다', async () => {
    const view = render(
      <MemoryRouter>
        <FinancialPlanPage />
      </MemoryRouter>,
    );

    fireEvent.click(view.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
    expect(view.getByRole('heading', { name: '매달 1,200,000원을 나눌 수 있어요' })).toBeTruthy();
    expect(view.getByText('일반 적금')).toBeTruthy();
    expect(view.getByText('정책 적금 · 청년미래적금')).toBeTruthy();
    expect(view.getByText('장기 투자')).toBeTruthy();
    expect(view.getAllByText('180,000원').length).toBeGreaterThan(0);
    expect(view.getByText('375,000원')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '이 계획 저장하기' }));
    expect(localStorage.getItem(CONSULTATIONS_STORAGE_KEY)).toContain('저축·투자 계획');
    await waitFor(() =>
      expect(view.getByRole('status').textContent).toContain('주간 기록에 저장했어요'),
    );
    expect(view.getByRole('link', { name: '주간 기록으로 →' }).getAttribute('href')).toBe(
      '/finance/weekly-records',
    );
  });

  it('입력값을 수정해도 마지막으로 계산한 계획을 저장한다', async () => {
    const view = render(
      <MemoryRouter>
        <FinancialPlanPage />
      </MemoryRouter>,
    );
    fireEvent.click(view.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
    fireEvent.change(view.getByLabelText('세후 월급'), {
      target: { value: '4000000' },
    });
    fireEvent.click(view.getByRole('button', { name: '이 계획 저장하기' }));
    const saved = JSON.parse(localStorage.getItem(CONSULTATIONS_STORAGE_KEY) ?? '[]');
    expect(saved[0].monthlyPlan.monthlyIncome).toBe(3000000);
    expect(saved[0].summary).toContain('1,200,000원');
    await waitFor(() =>
      expect(view.queryByRole('button', { name: '이 계획 저장하기' })).toBeNull(),
    );
  });

  it('지출 초과와 목표 부족을 경고하고 없는 투자액을 권하지 않는다', () => {
    const view = render(
      <MemoryRouter>
        <FinancialPlanPage />
      </MemoryRouter>,
    );
    fireEvent.change(view.getByLabelText('세후 월급'), { target: { value: '1700000' } });
    fireEvent.click(view.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
    expect(
      view
        .getAllByRole('alert')
        .map((item) => item.textContent)
        .join(' '),
    ).toContain('지출이 월급보다 100,000원 많아요');
    fireEvent.click(view.getByRole('button', { name: '이 계획 저장하기' }));
    expect(localStorage.getItem(CONSULTATIONS_STORAGE_KEY)).not.toContain(
      '소액으로 분산투자 시작하기',
    );
  });

  it('브라우저 저장 실패는 성공으로 표시하지 않고 다시 저장할 수 있게 한다', async () => {
    const view = render(
      <MemoryRouter>
        <FinancialPlanPage />
      </MemoryRouter>,
    );
    fireEvent.click(view.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
    vi.spyOn(Storage.prototype, 'setItem').mockImplementationOnce(() => {
      throw new Error('storage full');
    });
    fireEvent.click(view.getByRole('button', { name: '이 계획 저장하기' }));
    await waitFor(() =>
      expect(view.getByRole('alert').textContent).toContain('계획을 저장하지 못했어요'),
    );
    expect(view.queryByRole('status')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '이 계획 저장하기' }));
    await waitFor(() => expect(view.getByRole('status').textContent).toContain('저장했어요'));
  });

  it('저장한 월 계획을 주간 목표로 가져오고 실제 실행 금액과 비교한다', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-05T12:00:00+09:00'));
    const planner = render(
      <MemoryRouter>
        <FinancialPlanPage />
      </MemoryRouter>,
    );
    fireEvent.click(planner.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
    fireEvent.click(planner.getByRole('button', { name: '이 계획 저장하기' }));
    planner.unmount();
    const view = render(
      <MemoryRouter>
        <WeeklyPracticePage />
      </MemoryRouter>,
    );
    const importButton = await view.findByRole('button', {
      name: '월 계획을 이번 주 목표로 가져오기',
    });
    await waitFor(() => expect(importButton.hasAttribute('disabled')).toBe(false));
    fireEvent.click(importButton);
    expect((view.getAllByLabelText('목표')[0] as HTMLInputElement).value).toBe('255000');
    expect((view.getAllByLabelText('목표')[1] as HTMLInputElement).value).toBe('45000');
    fireEvent.change(view.getAllByLabelText('실제')[0], { target: { value: '200000' } });
    fireEvent.click(view.getByRole('button', { name: '이번 주 기록 저장' }));
    await waitFor(() => expect(view.getByRole('status').textContent).toContain('저장했어요'));
    expect(
      view.getByRole('row', { name: '저축·비상금 1,020,000원 200,000원 820,000원' }),
    ).toBeTruthy();
  });

  it('가상의 배분을 실제 사용자 평균이라고 표시하지 않는다', () => {
    const view = render(
      <MemoryRouter>
        <FinancialPlanPage />
      </MemoryRouter>,
    );
    fireEvent.click(view.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
    expect(view.getByText('실제 사용자 평균이 아닌 가상의 예시')).toBeTruthy();
  });

  it('주간 기록 조회 실패 시 0원 비교나 덮어쓰기를 막고 재시도를 제공한다', async () => {
    const planner = render(
      <MemoryRouter>
        <FinancialPlanPage />
      </MemoryRouter>,
    );
    fireEvent.click(planner.getByRole('button', { name: '내 저축·투자 계획 만들기' }));
    fireEvent.click(planner.getByRole('button', { name: '이 계획 저장하기' }));
    planner.unmount();
    vi.spyOn(learningApi, 'loadWeeklyCheckIns')
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce([]);
    const view = render(
      <MemoryRouter>
        <WeeklyPracticePage />
      </MemoryRouter>,
    );
    await waitFor(() =>
      expect(view.getByRole('alert').textContent).toContain('주간 기록을 불러오지 못했습니다'),
    );
    expect(view.getByRole('button', { name: '이번 주 기록 저장' }).hasAttribute('disabled')).toBe(
      true,
    );
    expect(view.queryByRole('row', { name: /저축·비상금/ })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '주간 기록 다시 불러오기' }));
    await waitFor(() =>
      expect(view.getByRole('button', { name: '이번 주 기록 저장' }).hasAttribute('disabled')).toBe(
        false,
      ),
    );
    expect(view.getByRole('row', { name: /저축·비상금/ })).toBeTruthy();
  });

  it('기존 계획은 유지하고 잘못된 구조의 월 계획은 불러오지 않는다', async () => {
    const valid = {
      id: 'old',
      topic: '저축·투자 계획',
      title: '이전 계획',
      summary: '월 80만원',
      details: [],
      createdAt: '2026-10-03T00:00:00Z',
    };
    localStorage.setItem(
      CONSULTATIONS_STORAGE_KEY,
      JSON.stringify([
        valid,
        { ...valid, id: 'bad', title: '잘못된 계획', monthlyPlan: { monthlyIncome: -1 } },
      ]),
    );
    const view = render(
      <MemoryRouter>
        <WeeklyPracticePage />
      </MemoryRouter>,
    );
    expect(await view.findByRole('heading', { name: '이전 계획' })).toBeTruthy();
    expect(view.queryByText('잘못된 계획')).toBeNull();
  });

  it('잘못된 저장값은 무시하고 확인 후 저장한 상담을 삭제한다', async () => {
    localStorage.setItem(CONSULTATIONS_STORAGE_KEY, '{broken');
    const emptyView = render(
      <MemoryRouter>
        <WeeklyPracticePage />
      </MemoryRouter>,
    );
    expect(await emptyView.findByText('아직 저장한 계획이 없어요')).toBeTruthy();
    emptyView.unmount();

    localStorage.setItem(
      CONSULTATIONS_STORAGE_KEY,
      JSON.stringify([
        {
          id: 'saved-1',
          topic: '청년미래적금',
          title: '청년미래적금 3년 예상',
          summary: '예상 만기액 2,019만원',
          details: ['납입액 1,800만원'],
          createdAt: '2026-10-02T00:00:00.000Z',
        },
      ]),
    );
    const historyView = render(
      <MemoryRouter>
        <WeeklyPracticePage />
      </MemoryRouter>,
    );
    expect(await historyView.findByText('청년미래적금 3년 예상')).toBeTruthy();
    fireEvent.click(historyView.getByRole('button', { name: '기록 삭제' }));
    fireEvent.click(historyView.getByRole('button', { name: '삭제 확인' }));
    expect(await historyView.findByText('아직 저장한 계획이 없어요')).toBeTruthy();
  });

  it('저장한 계획의 실행 단계를 체크한다', async () => {
    localStorage.setItem(
      CONSULTATIONS_STORAGE_KEY,
      JSON.stringify([
        {
          id: 'plan-1',
          topic: '저축·투자 계획',
          title: '5년 저축·투자 계획',
          summary: '월 80만원 배분',
          details: ['예·적금 28만원 · 투자 12만원'],
          actions: [{ id: 'invest', label: '소액으로 분산투자 시작하기', completed: false }],
          createdAt: '2026-10-03T00:00:00.000Z',
        },
      ]),
    );
    const view = render(
      <MemoryRouter>
        <WeeklyPracticePage />
      </MemoryRouter>,
    );

    fireEvent.click(await view.findByLabelText('소액으로 분산투자 시작하기'));
    await waitFor(() =>
      expect(localStorage.getItem(CONSULTATIONS_STORAGE_KEY)).toContain('"completed":true'),
    );
  });

  it('주간 목표와 실행 금액을 저장해 반복 사용한다', async () => {
    const view = render(
      <MemoryRouter>
        <WeeklyPracticePage />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(view.getByRole('button', { name: '이번 주 기록 저장' }).hasAttribute('disabled')).toBe(
        false,
      ),
    );

    fireEvent.change(view.getAllByLabelText('실제')[0], {
      target: { value: '100000' },
    });
    fireEvent.click(view.getByRole('button', { name: '이번 주 기록 저장' }));

    await waitFor(() => expect(view.getByRole('status').textContent).toContain('저장했어요'));
    expect(localStorage.getItem('daynomy:weekly-check-ins:v1')).toContain('100000');
  });

  it('신용카드 가이드에서 소비 습관에 따라 결제수단을 진단한다', () => {
    const view = render(
      <MemoryRouter initialEntries={['/finance/guides/credit-card-20s']}>
        <Routes>
          <Route path="/finance/guides/:guideId" element={<FinancialGuidePage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(view.getByLabelText('매달 전액 결제가 가능한가요?'), {
      target: { value: 'no' },
    });
    fireEvent.click(view.getByRole('button', { name: '내 결제수단 진단하기' }));
    expect(view.getByText('지금은 체크카드가 더 안전해요')).toBeTruthy();
  });
});
