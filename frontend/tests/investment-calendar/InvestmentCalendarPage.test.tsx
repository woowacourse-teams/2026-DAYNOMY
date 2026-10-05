/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { getInvestmentCalendar } from '../../src/features/investment-calendar/api';
import { InvestmentCalendarPage } from '../../src/features/investment-calendar/InvestmentCalendarPage';
import { getMockInvestmentCalendar } from '../../src/features/investment-calendar/mockData';

vi.mock('../../src/features/investment-calendar/api', () => ({
  getInvestmentCalendar: vi.fn(),
}));

const mockedGetInvestmentCalendar = vi.mocked(getInvestmentCalendar);

beforeEach(() => {
  mockedGetInvestmentCalendar.mockResolvedValue({
    calendar: getMockInvestmentCalendar(2026, 10),
    isDemo: true,
  });
});

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('투자 캘린더 화면', () => {
  it('일정을 선택하면 해당 자산 영향으로 상세 내용을 바꾼다', async () => {
    const view = render(<InvestmentCalendarPage />);

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
    const view = render(<InvestmentCalendarPage />);
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
    const view = render(<InvestmentCalendarPage />);
    await view.findByText('미국 소비자물가지수 발표');

    fireEvent.click(view.getByRole('button', { name: '다음 달' }));

    expect(await view.findByText('이번 달 예정된 일정이 없어요.')).toBeTruthy();
    expect(mockedGetInvestmentCalendar).toHaveBeenLastCalledWith(2026, 11, expect.any(AbortSignal));
  });

  it('요청이 실패하면 다시 시도할 수 있다', async () => {
    mockedGetInvestmentCalendar
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ calendar: { year: 2026, month: 10, events: [] }, isDemo: true });
    const view = render(<InvestmentCalendarPage />);

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

    const view = render(<InvestmentCalendarPage />);

    expect(await view.findByText('아직 비교할 과거 사례가 부족해요.')).toBeTruthy();
    expect(view.getByText(/표본이 3회 이상 쌓이면/)).toBeTruthy();
  });
});
