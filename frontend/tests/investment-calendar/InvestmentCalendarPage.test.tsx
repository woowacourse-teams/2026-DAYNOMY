/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { getInvestmentCalendar } from '../../src/features/investment-calendar/api';
import { NOTIFICATION_PROMPT_DISMISSED_KEY } from '../../src/features/investment-calendar/InvestmentCalendarNotification';
import { InvestmentCalendarPage } from '../../src/features/investment-calendar/InvestmentCalendarPage';
import { getMockInvestmentCalendar } from '../../src/features/investment-calendar/mockData';
import {
  deleteInvestmentCalendarNotification,
  getInvestmentCalendarNotification,
  requestNotificationEmailVerification,
  updateInvestmentCalendarNotification,
} from '../../src/features/investment-calendar/notificationApi';

vi.mock('../../src/features/investment-calendar/api', () => ({
  getInvestmentCalendar: vi.fn(),
}));
vi.mock('../../src/features/investment-calendar/notificationApi', () => ({
  getInvestmentCalendarNotification: vi.fn(),
  updateInvestmentCalendarNotification: vi.fn(),
  deleteInvestmentCalendarNotification: vi.fn(),
  requestNotificationEmailVerification: vi.fn(),
}));

const mockedGetInvestmentCalendar = vi.mocked(getInvestmentCalendar);
const mockedGetNotification = vi.mocked(getInvestmentCalendarNotification);
const mockedUpdateNotification = vi.mocked(updateInvestmentCalendarNotification);
const mockedDeleteNotification = vi.mocked(deleteInvestmentCalendarNotification);
const mockedRequestVerification = vi.mocked(requestNotificationEmailVerification);

function renderCalendar(
  ui: ReactElement = <InvestmentCalendarPage />,
  { isLoggedIn = true, loading = false }: { isLoggedIn?: boolean; loading?: boolean } = {},
) {
  return render(
    <AuthContext.Provider value={{ isLoggedIn, loading, role: isLoggedIn ? 'USER' : null }}>
      {ui}
    </AuthContext.Provider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  mockedGetInvestmentCalendar.mockResolvedValue({
    calendar: getMockInvestmentCalendar(2026, 10),
    isDemo: true,
  });
  mockedGetNotification.mockResolvedValue(null);
  mockedUpdateNotification.mockImplementation(async (setting) => ({
    ...setting,
    emailVerified: true,
  }));
  mockedDeleteNotification.mockResolvedValue();
  mockedRequestVerification.mockResolvedValue();
});

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('투자 캘린더 화면', () => {
  it('일정을 선택하면 해당 자산 영향으로 상세 내용을 바꾼다', async () => {
    const view = renderCalendar();

    expect(
      await view.findByText('내 자산은 물가가 오른 뒤 약세였던 경우가 많았습니다.'),
    ).toBeTruthy();
    expect(view.getByText('18만원 – 31만원 증가')).toBeTruthy();
    expect(view.getByText('4만원 감소 – 7만원 증가')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: /두산 3분기 실적/ }));

    expect(view.getByText('두산은 영업이익이 늘어난 뒤 강세였던 경우가 많았습니다.')).toBeTruthy();
    expect(view.getByText('직접 관련')).toBeTruthy();
  });

  it('목록과 달력을 전환하고 달력에서 일정을 선택한다', async () => {
    const view = renderCalendar();
    await view.findByText('미국 소비자물가지수 발표');

    fireEvent.click(view.getByRole('button', { name: '달력으로 보기' }));
    expect(view.getByRole('button', { name: '목록으로 보기' })).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '10월 22일 한국 기준금리 결정' }));

    expect(view.getByText('내 자산은 기준금리가 오른 뒤 약세였던 경우가 많았습니다.')).toBeTruthy();
  });

  it('다음 달에 일정이 없으면 빈 화면을 안내한다', async () => {
    mockedGetInvestmentCalendar.mockImplementation(async (year, month) => ({
      calendar: getMockInvestmentCalendar(year, month),
      isDemo: true,
    }));
    const view = renderCalendar();
    await view.findByText('미국 소비자물가지수 발표');

    fireEvent.click(view.getByRole('button', { name: '다음 달' }));

    expect(await view.findByText('이번 달 예정된 일정이 없어요.')).toBeTruthy();
    expect(mockedGetInvestmentCalendar).toHaveBeenLastCalledWith(2026, 11, expect.any(AbortSignal));
  });

  it('요청이 실패하면 다시 시도할 수 있다', async () => {
    mockedGetInvestmentCalendar
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ calendar: { year: 2026, month: 10, events: [] }, isDemo: true });
    const view = renderCalendar();

    expect(await view.findByText('일정을 불러오지 못했어요.')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '다시 시도' }));

    await waitFor(() => expect(mockedGetInvestmentCalendar).toHaveBeenCalledTimes(2));
    expect(await view.findByText('이번 달 예정된 일정이 없어요.')).toBeTruthy();
  });

  it('표본이 부족하면 숫자 대신 제공 조건을 알려준다', async () => {
    const calendar = getMockInvestmentCalendar(2026, 10);
    const firstEvent = calendar.events[0];
    if (!firstEvent) throw new Error('테스트 일정이 필요합니다.');
    mockedGetInvestmentCalendar.mockResolvedValue({
      calendar: {
        ...calendar,
        events: [
          {
            ...firstEvent,
            portfolioAnalysis: {
              ...firstEvent.portfolioAnalysis,
              status: 'INSUFFICIENT_DATA',
            },
          },
        ],
      },
      isDemo: false,
    });

    const view = renderCalendar();

    expect(await view.findByText('아직 비교할 과거 사례가 부족해요.')).toBeTruthy();
    expect(view.getByText(/표본이 3회 이상 쌓이면/)).toBeTruthy();
  });

  it('알림을 설정하지 않은 사용자에게 작은 추천 팝업을 보여주고 다시 보지 않기를 기억한다', async () => {
    const view = renderCalendar();

    expect(await view.findByText('투자 일정, 이메일로 미리 받아보세요')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '다시 보지 않기' }));

    expect(view.queryByText('투자 일정, 이메일로 미리 받아보세요')).toBeNull();
    expect(localStorage.getItem(NOTIFICATION_PROMPT_DISMISSED_KEY)).toBe('true');
    expect(view.queryByText(/다시 표시하지 않아요/)).toBeNull();
  });

  it('원하는 이메일로 인증을 요청하고 알림 설정을 저장한다', async () => {
    const view = renderCalendar();
    await view.findByText('투자 일정, 이메일로 미리 받아보세요');
    fireEvent.click(view.getByRole('button', { name: '알림 설정' }));

    const emailInput = view.getByLabelText('알림 받을 이메일');
    fireEvent.change(emailInput, { target: { value: 'investor@example.com' } });
    fireEvent.click(view.getByRole('button', { name: '인증하기' }));
    await waitFor(() =>
      expect(mockedRequestVerification).toHaveBeenCalledWith({
        email: 'investor@example.com',
      }),
    );
    fireEvent.click(view.getByRole('button', { name: '설정 저장' }));

    await waitFor(() =>
      expect(mockedUpdateNotification).toHaveBeenCalledWith(
        expect.objectContaining({
          email: 'investor@example.com',
          scopes: ['PORTFOLIO', 'MAJOR_ECONOMIC'],
          timings: ['ONE_DAY_BEFORE', 'SAME_DAY_MORNING'],
        }),
      ),
    );
    expect(await view.findByText('이메일 알림 설정을 저장했어요.')).toBeTruthy();
  });

  it('로그인하지 않은 사용자는 설정 화면에서 로그인을 안내한다', async () => {
    const view = renderCalendar(<InvestmentCalendarPage />, { isLoggedIn: false });
    await view.findByText('투자 일정, 이메일로 미리 받아보세요');
    fireEvent.click(view.getByRole('button', { name: '알림 설정' }));

    expect(view.getByText('로그인 후 알림을 설정할 수 있어요')).toBeTruthy();
    expect(view.getByRole('link', { name: '로그인 후 설정하기' }).getAttribute('href')).toBe(
      '/login',
    );
  });
});
