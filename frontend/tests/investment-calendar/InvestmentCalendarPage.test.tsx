/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import type { ReactElement } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { getInvestmentCalendar } from '../../src/features/investment-calendar/api';
import { InvestmentCalendarPage } from '../../src/features/investment-calendar/InvestmentCalendarPage';
import { getMockInvestmentCalendar } from '../../src/features/investment-calendar/mockData';
import { saveGuestPortfolioHoldings } from '../../src/features/portfolio/portfolioHoldingsStorage';

vi.mock('../../src/features/investment-calendar/api', () => ({
  getInvestmentCalendar: vi.fn(),
}));

const mockedGetInvestmentCalendar = vi.mocked(getInvestmentCalendar);

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
  sessionStorage.clear();
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
  it('소식 홈에서는 중첩 main 없이 섹션 제목으로 표시한다', async () => {
    const view = renderCalendar(<InvestmentCalendarPage embedded />);

    expect(await view.findByRole('heading', { name: '투자 캘린더', level: 2 })).toBeTruthy();
    expect(view.container.querySelector('main')).toBeNull();
    expect(view.container.querySelector('.investment-calendar-embedded')).toBeTruthy();
    expect(view.queryByRole('button', { name: '일정 알림 설정' })).toBeNull();
  });

  it('일정을 선택하면 해당 자산 영향으로 상세 내용을 바꾼다', async () => {
    const view = renderCalendar();

    expect(
      await view.findByText('내 자산은 물가가 오른 뒤 약세였던 경우가 많았습니다.'),
    ).toBeTruthy();
    expect(view.getByText('18만원 ~ 31만원 증가')).toBeTruthy();
    expect(view.getByText('4만원 감소 ~ 7만원 증가')).toBeTruthy();
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
    expect(view.getByText('금리 결정별 내 자산의 과거 움직임')).toBeTruthy();
    expect(
      view.getByText(
        '기준금리가 인하·동결·인상됐던 과거 다음 거래일에, 지금과 같은 내 자산이 얼마나 움직였는지 보여드려요.',
      ),
    ).toBeTruthy();
    expect(view.getByText('금리 인하 사례')).toBeTruthy();
    expect(view.getByText('보통 +0.4%')).toBeTruthy();
    expect(view.getByText('약 6만원 ~ 21만원 증가')).toBeTruthy();
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
    expect(mockedGetInvestmentCalendar).toHaveBeenLastCalledWith(2026, 11, 'PORTFOLIO', {
      isLoggedIn: true,
      guestHoldings: [],
      signal: expect.any(AbortSignal),
    });
  });

  it('비회원은 세션 포트폴리오로 내 일정을 조회한다', async () => {
    saveGuestPortfolioHoldings([
      {
        assetId: 100,
        assetCode: '000150',
        name: '두산',
        category: 'STOCK',
        market: 'KOSPI',
        quantity: 3,
        averagePurchasePrice: 100_000,
      },
    ]);

    renderCalendar(<InvestmentCalendarPage />, { isLoggedIn: false });

    await waitFor(() =>
      expect(mockedGetInvestmentCalendar).toHaveBeenCalledWith(2026, 10, 'PORTFOLIO', {
        isLoggedIn: false,
        guestHoldings: [{ assetId: 100, quantity: 3 }],
        signal: expect.any(AbortSignal),
      }),
    );
  });

  it('비회원 세션 포트폴리오가 없으면 빈 보유자산으로 전체 일정도 조회한다', async () => {
    const view = renderCalendar(<InvestmentCalendarPage />, { isLoggedIn: false });
    await view.findByText('미국 소비자물가지수 발표');

    fireEvent.click(view.getByRole('button', { name: '달력으로 보기' }));
    fireEvent.click(view.getByRole('button', { name: '전체 일정' }));

    await waitFor(() =>
      expect(mockedGetInvestmentCalendar).toHaveBeenLastCalledWith(2026, 10, 'ALL', {
        isLoggedIn: false,
        guestHoldings: [],
        signal: expect.any(AbortSignal),
      }),
    );
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
});
