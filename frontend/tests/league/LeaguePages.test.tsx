import { concentrationLabel } from '../../src/features/league/labels';
/** @vitest-environment jsdom */
import { StrictMode } from 'react';

import { act, cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { BrowserRouter, MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { InvestorProfilePage } from '../../src/features/league/InvestorProfilePage';
import { DailyReturns } from '../../src/features/league/DailyReturns';
import { LeagueNavigation } from '../../src/features/league/LeagueNavigation';
import { LeaguePage } from '../../src/features/league/LeaguePage';
import { FollowingPage } from '../../src/features/league/FollowingPage';
import * as leagueApi from '../../src/features/league/api';
import { trackEvent } from '../../src/analytics';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

vi.mock('../../src/features/league/api', () => ({
  followInvestor: vi.fn(),
  getLeagueWeeks: vi.fn(),
  getMyInvestorProfile: vi.fn(),
  getInvestorDetail: vi.fn(),
  getPublicInvestor: vi.fn(),
  getDailyHistory: vi.fn(),
  getRankings: vi.fn(),
  getFollowSummary: vi.fn(),
  unfollowInvestor: vi.fn(),
}));

const ranking = {
  weekStart: '2026-12-07',
  weekEnd: '2026-12-13',
  leagueType: 'WEEKLY_RETURN' as const,
  confirmed: true,
  totalCount: 1,
  asOfDate: '2026-12-11',
  rankings: [
    {
      rank: 1,
      publicId: 'investor-1',
      displayName: '차분한초보',
      experienceLevel: 'BEGINNER' as const,
      riskProfile: 'BALANCED' as const,
      weeklyReturnRate: 2.5,
      threeWeekReturnRate: 4.2,
      maxDrawdownRate: -1.1,
      volatilityRate: 0.7,
      maxHoldingWeight: 55,
      decisionCount: 4,
      reviewCompletionRate: 75,
      followed: false,
    },
  ],
};

const investor = {
  publicId: 'investor-1',
  displayName: '차분한초보',
  bio: '손실 조건부터 기록합니다.',
  experienceLevel: 'BEGINNER' as const,
  riskProfile: 'BALANCED' as const,
  performance: {
    weeklyReturnRate: 2.5,
    threeWeekReturnRate: 4.2,
    maxDrawdownRate: -1.1,
    volatilityRate: 0.7,
    maxHoldingWeight: 55,
  },
  allocation: { stockWeight: 60, etfWeight: 40 },
  history: [{ weekStart: '2026-12-07', weeklyReturnRate: 2.5, maxDrawdownRate: -1.1 }],
  decisionCount: 4,
  reviewCompletionRate: 75,
  followed: false,
  detailAvailable: true,
};

const daily = {
  weekStart: '2026-12-07',
  weekEnd: '2026-12-13',
  asOfDate: '2026-12-07',
  eligibleFrom: '2026-10-12',
  confirmed: true,
  weeklyReturnRate: 2.5,
  days: [
    {
      baseDate: '2026-12-07',
      dailyReturnRate: 2.5,
      cumulativeReturnRate: 2.5,
      status: 'CALCULATED' as const,
      reason: null,
    },
  ],
};

const originalShowModal = HTMLDialogElement.prototype.showModal;
const originalClose = HTMLDialogElement.prototype.close;
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function showModal() {
    this.setAttribute('open', '');
  };
  HTMLDialogElement.prototype.close = function close() {
    this.removeAttribute('open');
    this.dispatchEvent(new Event('close'));
  };
});
afterAll(() => {
  HTMLDialogElement.prototype.showModal = originalShowModal;
  HTMLDialogElement.prototype.close = originalClose;
});

beforeEach(() => {
  vi.mocked(leagueApi.getMyInvestorProfile).mockResolvedValue(null);
  vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue([
    { weekStart: '2026-12-07', weekEnd: '2026-12-13', confirmed: true },
    { weekStart: '2026-11-30', weekEnd: '2026-12-06', confirmed: true },
  ]);
  vi.mocked(leagueApi.getDailyHistory).mockResolvedValue(daily);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  window.history.replaceState(null, '', '/');
  vi.clearAllMocks();
});

describe('투자 리그 화면', () => {
  it('월요일 데이터 누락은 주간 성과를 숨기고 새로고침 후 지표와 그래프를 함께 갱신한다', async () => {
    const waiting = {
      ...daily,
      confirmed: false,
      asOfDate: '2026-12-08',
      weeklyReturnRate: null,
      aggregation: {
        baseDate: '2026-12-07',
        expectedAt: '2026-12-10T08:20:00Z',
        firstReturnExpectedAt: null,
        reason: 'SNAPSHOT_PENDING',
        delayed: false,
      },
      days: [
        {
          baseDate: '2026-12-07',
          dailyReturnRate: null,
          cumulativeReturnRate: null,
          status: 'PENDING' as const,
          reason: 'SNAPSHOT_PENDING',
        },
        {
          baseDate: '2026-12-08',
          dailyReturnRate: 1,
          cumulativeReturnRate: null,
          status: 'CALCULATED' as const,
          reason: null,
        },
      ],
    };
    vi.mocked(leagueApi.getDailyHistory)
      .mockResolvedValueOnce(waiting)
      .mockResolvedValue({
        ...waiting,
        aggregation: null,
        weeklyReturnRate: 2.01,
        days: waiting.days.map((day, index) => ({
          ...day,
          status: 'CALCULATED',
          reason: null,
          dailyReturnRate: 1,
          cumulativeReturnRate: index ? 2.01 : 1,
        })),
      });
    vi.mocked(leagueApi.getPublicInvestor)
      .mockResolvedValueOnce({
        ...investor,
        detailAvailable: false,
        performance: { ...investor.performance, weeklyReturnRate: null, weeklyRank: null },
      })
      .mockResolvedValue({
        ...investor,
        detailAvailable: false,
        performance: { ...investor.performance, weeklyReturnRate: 2.01, weeklyRank: 3 },
      });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );

    await view.findByText('보유 데이터 대기');
    expect(view.getByRole('heading', { name: '이번 주 성과를 준비하고 있어요' })).toBeTruthy();
    expect(view.getByText('+1.00%')).toBeTruthy();
    expect(view.queryByText('0.00%')).toBeNull();
    expect(view.queryByText('아직 집계된 수익률이 없어요.')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '집계 현황 새로고침' }));
    await view.findByText('+2.01%');
    expect(view.getByText('3위')).toBeTruthy();
    await view.findByText('2일 집계');
    expect(
      within(view.getByRole('group', { name: '일별 수익률 그래프' })).getAllByText('+1.00%'),
    ).toHaveLength(2);
    expect(view.queryByText('보유 데이터 대기')).toBeNull();
    expect(leagueApi.getPublicInvestor).toHaveBeenCalledTimes(2);
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    fireEvent(document, new Event('visibilitychange'));
    await waitFor(() => expect(leagueApi.getPublicInvestor).toHaveBeenCalledTimes(3));
    await view.findByText('+2.01%');
  });

  it('운영 순위표에 서버의 집계 일정을 연결하고 순위를 유지한 채 재조회한다', async () => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue({
      ...ranking,
      confirmed: false,
      aggregation: {
        baseDate: '2026-12-07',
        expectedAt: '2026-12-10T08:20:00Z',
        firstReturnExpectedAt: null,
        reason: 'CALCULATION_PENDING',
        delayed: false,
      },
    });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByRole('heading', { name: '수익률과 순위를 계산하고 있어요' });
    expect(view.getByText(/12월 10일.*17:20/)).toBeTruthy();
    expect(view.getByText('차분한초보')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '집계 현황 새로고침' }));
    await waitFor(() => expect(leagueApi.getRankings).toHaveBeenCalledTimes(2));
    await view.findByText('차분한초보');
  });

  it('StrictMode에서도 진입·조회 완료를 한 번 기록하고 잠정 순위와 상세 지표 동작을 구분한다', async () => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue({
      ...ranking,
      confirmed: false,
      collectionStatus: 'COLLECTION_PENDING',
      aggregation: {
        baseDate: '2026-12-11',
        expectedAt: '2026-12-14T05:30:00Z',
        firstReturnExpectedAt: null,
        reason: 'PRICE_COLLECTION_DELAYED',
        delayed: true,
      },
    });
    const view = render(
      <StrictMode>
        <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
          <MemoryRouter>
            <LeaguePage />
          </MemoryRouter>
        </AuthContext.Provider>
      </StrictMode>,
    );
    await view.findByText('잠정 순위');
    expect(view.getByText('종가 수집 대기')).toBeTruthy();
    expect(view.queryByText(/집계된 종가 기준의 잠정 순위/)).toBeNull();
    expect(view.getByText('다음 집계 예정')).toBeTruthy();
    expect(view.getByRole('heading', { name: '종가 데이터 제공이 늦어지고 있어요' })).toBeTruthy();
    expect(view.container.querySelector('time')?.textContent).toContain('14:30');
    expect(
      vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'view_league'),
    ).toHaveLength(1);
    expect(
      vi
        .mocked(trackEvent)
        .mock.calls.filter(
          ([name, params]) => name === 'league_load_result' && params?.surface === 'ranking',
        ),
    ).toHaveLength(1);
    expect(leagueApi.getRankings).toHaveBeenCalledTimes(1);
    fireEvent.click(view.getByRole('button', { name: '차분한초보 상세 지표' }));
    expect(trackEvent).toHaveBeenCalledWith('league_action', {
      surface: 'ranking',
      action: 'open_metrics',
      status: 'clicked',
      rank_bucket: 'top_3',
    });
    expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('investor-1');
  });

  it('팔로우 실패는 성공으로 기록하지 않고 원문 오류와 투자자 식별자를 전송하지 않는다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
      detailAvailable: false,
    });
    vi.mocked(leagueApi.followInvestor).mockRejectedValueOnce(new Error('private-error'));
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    fireEvent.click(await view.findByRole('button', { name: '차분한초보 팔로우' }));
    await view.findByRole('alert');
    const actions = vi
      .mocked(trackEvent)
      .mock.calls.filter(
        ([name, params]) => name === 'league_action' && params?.action === 'follow',
      );
    expect(actions.map(([, params]) => params?.status)).toEqual(['submitted', 'error']);
    expect(
      view.getByRole('button', { name: '차분한초보 팔로우' }).getAttribute('aria-pressed'),
    ).toBe('false');
    expect(JSON.stringify(actions)).not.toMatch(/investor-1|private-error|차분한초보/);
  });

  it('투자자 이름과 팔로우 상태를 전달하고 화면을 닫으면 원래 제목을 복원한다', async () => {
    const previousTitle = document.title;
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
      detailAvailable: false,
    });
    vi.mocked(leagueApi.followInvestor).mockResolvedValue(undefined);
    vi.mocked(leagueApi.unfollowInvestor).mockResolvedValue(undefined);
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    const button = await view.findByRole('button', { name: '차분한초보 팔로우' });
    await waitFor(() => {
      expect(document.title).toBe('차분한초보 공개 프로필 | DAYNOMY');
      expect(document.activeElement).toBe(view.getByRole('heading', { name: '차분한초보' }));
    });
    button.focus();
    expect(button.getAttribute('aria-pressed')).toBe('false');
    fireEvent.click(button);
    await waitFor(() => expect(button.getAttribute('aria-pressed')).toBe('true'));
    expect(view.getByRole('button', { name: '차분한초보 팔로잉 취소' })).toBe(button);
    expect(document.activeElement).toBe(button);
    fireEvent.click(button);
    await waitFor(() => expect(button.getAttribute('aria-pressed')).toBe('false'));
    expect(leagueApi.followInvestor).toHaveBeenCalledWith('investor-1');
    expect(leagueApi.unfollowInvestor).toHaveBeenCalledWith('investor-1');
    view.unmount();
    expect(document.title).toBe(previousTitle);
  });

  it('다음 주에는 이번 주와 시작 주만 드롭다운으로 선택한다', async () => {
    vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue([
      { weekStart: '2026-10-19', weekEnd: '2026-10-25', confirmed: false },
      { weekStart: '2026-10-12', weekEnd: '2026-10-18', confirmed: true },
      { weekStart: '2026-10-05', weekEnd: '2026-10-11', confirmed: true },
    ]);
    vi.mocked(leagueApi.getRankings).mockResolvedValue(ranking);
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    const select = await view.findByRole('combobox', { name: '조회 주차' });
    expect(view.getAllByRole('option').map((option) => option.textContent)).toEqual([
      '이번 주 · 10-19 ~ 10-25',
      '지난주 · 10-12 ~ 10-18',
    ]);
    fireEvent.change(select, { target: { value: '2026-10-12' } });
    await waitFor(() =>
      expect(leagueApi.getRankings).toHaveBeenLastCalledWith(
        'WEEKLY_RETURN',
        '2026-10-12',
        expect.any(AbortSignal),
        0,
      ),
    );
  });

  it('시작 첫 주에는 순위와 일별 조회에서 과거 주차와 선택 UI를 보여주지 않는다', async () => {
    vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue([
      { weekStart: '2026-10-05', weekEnd: '2026-10-11', confirmed: true },
      { weekStart: '2026-10-12', weekEnd: '2026-10-18', confirmed: false },
      { weekStart: '2026-09-28', weekEnd: '2026-10-04', confirmed: true },
    ]);
    vi.mocked(leagueApi.getRankings).mockResolvedValue({ ...ranking, rankings: [], totalCount: 0 });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByText('이번 주 · 10-12 ~ 10-18');
    expect(view.queryByRole('navigation', { name: '조회 주차' })).toBeNull();
    expect(view.queryByRole('combobox')).toBeNull();
    expect(view.queryByText(/지난주/)).toBeNull();
    expect(view.container.querySelector('.league-controls details')).toBeNull();
    view.unmount();
    const dailyView = render(
      <DailyReturns publicId="investor-1" weekStart="2026-10-05" onWeekChange={vi.fn()} />,
    );
    await dailyView.findByRole('group', { name: '일별 수익률 그래프' });
    expect(dailyView.getByText('이번 주 · 10-12 ~ 10-18')).toBeTruthy();
    expect(dailyView.queryByRole('navigation', { name: '조회 주차' })).toBeNull();
    expect(leagueApi.getDailyHistory).toHaveBeenLastCalledWith(
      'investor-1',
      '2026-10-12',
      expect.any(AbortSignal),
    );
  });

  it('주간 그래프에서도 리그 시작 전 기록을 제외한다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
      detailAvailable: false,
      history: ['2026-09-28', '2026-10-05', '2026-10-12'].map((weekStart) => ({
        weekStart,
        weeklyReturnRate: null,
        maxDrawdownRate: null,
      })),
    });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    fireEvent.click(await view.findByRole('button', { name: '주간' }));
    await view.findByText('첫 주 기록');
    expect(view.getAllByRole('button', { name: /주간.*일별 기록 보기/ })).toHaveLength(1);
    expect(view.getByRole('button', { name: /2026-10-12 주간/ })).toBeTruthy();
    expect(view.queryByLabelText('최근 3주 주간 수익률')).toBeNull();
  });

  it('일별 조회는 서버 주차를 최신 3주로 정렬하고 오래된 선택은 이번 주로 바꾼다', async () => {
    vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue(
      Array.from({ length: 8 }, (_, index) => ({
        weekStart: new Date(Date.UTC(2026, 11, 7 - index * 7)).toISOString().slice(0, 10),
        weekEnd: new Date(Date.UTC(2026, 11, 13 - index * 7)).toISOString().slice(0, 10),
        confirmed: index > 0,
      })).reverse(),
    );
    const onWeekChange = vi.fn();
    const view = render(
      <DailyReturns publicId="investor-1" weekStart="2026-10-19" onWeekChange={onWeekChange} />,
    );
    await view.findByRole('group', { name: '일별 수익률 그래프' });
    expect(view.getByRole('combobox', { name: '조회 주차' })).toHaveProperty('value', '2026-12-07');
    expect(view.getAllByRole('option').map((option) => option.textContent)).toEqual([
      '이번 주 · 12-07 ~ 12-13',
      '지난주 · 11-30 ~ 12-06',
      '지지난주 · 11-23 ~ 11-29',
    ]);
    expect(leagueApi.getDailyHistory).toHaveBeenCalledExactlyOnceWith(
      'investor-1',
      '2026-12-07',
      expect.any(AbortSignal),
    );
    expect(view.getByRole('group', { name: /12\. 7\. .*하루 수익률 \+2.50%/ })).toBeTruthy();
    expect(view.queryByRole('table')).toBeNull();
    fireEvent.change(view.getByRole('combobox', { name: '조회 주차' }), {
      target: { value: '2026-11-23' },
    });
    expect(onWeekChange).toHaveBeenCalledWith('2026-11-23');
  });

  it.each(['NOT_PARTICIPATING', 'PENDING', 'EXCLUDED'] as const)(
    '일별 값이 없는 %s 상태는 빈 표와 중복 수치 없이 안내한다',
    async (status) => {
      vi.mocked(leagueApi.getDailyHistory).mockResolvedValue({
        ...daily,
        confirmed: false,
        asOfDate: null,
        weeklyReturnRate: null,
        eligibleFrom: status === 'NOT_PARTICIPATING' ? '2026-12-14' : '2026-10-12',
        days: [
          {
            ...daily.days[0],
            dailyReturnRate: null,
            cumulativeReturnRate: null,
            status,
            reason: status === 'EXCLUDED' ? 'MISSING_PRICE' : null,
          },
        ],
      });
      const view = render(
        <DailyReturns publicId="investor-1" weekStart="" onWeekChange={vi.fn()} />,
      );
      await view.findByText('아직 집계된 수익률이 없어요.');
      expect(view.queryByRole('table')).toBeNull();
      expect(view.queryByRole('group', { name: '일별 수익률 그래프' })).toBeNull();
      expect(view.queryByText(/데이터 부족|주간 누적 집계 대기|집계 중/)).toBeNull();
      expect(view.queryByText(/0.00%/)).toBeNull();
      expect(
        view.getByText(
          status === 'NOT_PARTICIPATING'
            ? '이 주에는 참여 기록이 없어요.'
            : status === 'PENDING'
              ? '집계 대기'
              : '종가 누락 · 집계 대기',
        ),
      ).toBeTruthy();
    },
  );

  it.each(['button', 'visibility'] as const)(
    '실제 대기 날짜를 안내하고 %s 재조회로 첫 0%% 집계를 표시한다',
    async (refresh) => {
      const pending = {
        ...daily,
        asOfDate: null,
        weeklyReturnRate: null,
        days: [
          {
            ...daily.days[0],
            dailyReturnRate: null,
            cumulativeReturnRate: null,
            status: 'NOT_PARTICIPATING' as const,
          },
          {
            ...daily.days[0],
            baseDate: '2026-12-08',
            dailyReturnRate: null,
            cumulativeReturnRate: null,
            status: 'EXCLUDED' as const,
            reason: 'PRICE_COLLECTION_PENDING',
          },
        ],
      };
      vi.mocked(leagueApi.getDailyHistory)
        .mockResolvedValueOnce(pending)
        .mockResolvedValue({
          ...daily,
          asOfDate: '2026-12-08',
          weeklyReturnRate: 0,
          days: [
            {
              ...daily.days[0],
              baseDate: '2026-12-08',
              dailyReturnRate: 0,
              cumulativeReturnRate: 0,
              reason: 'TRACKING_BASELINE',
            },
          ],
        });
      const view = render(
        <DailyReturns publicId="investor-1" weekStart="" onWeekChange={vi.fn()} />,
      );
      expect(await view.findByText(/12\. 8\. .*종가 수집 대기/)).toBeTruthy();
      expect(view.queryByText(/참여 전 \/ 미참여/)).toBeNull();
      expect(view.queryByRole('table')).toBeNull();
      if (refresh === 'button') {
        fireEvent.click(view.getByRole('button', { name: '일별 기록 다시 확인' }));
      } else {
        const visibility = vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
        fireEvent(document, new Event('visibilitychange'));
        expect(leagueApi.getDailyHistory).toHaveBeenCalledTimes(1);
        visibility.mockReturnValue('visible');
        fireEvent(document, new Event('visibilitychange'));
      }
      const chart = await view.findByRole('group', { name: '일별 수익률 그래프' });
      expect(within(chart).getByText('0.00%')).toBeTruthy();
      expect(leagueApi.getDailyHistory).toHaveBeenCalledTimes(2);
    },
  );

  it('브라우저 탭으로 돌아오면 순위를 다시 조회한다', async () => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue(ranking);
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByText('+2.50%');
    vi.mocked(leagueApi.getRankings).mockResolvedValue({
      ...ranking,
      rankings: [{ ...ranking.rankings[0], weeklyReturnRate: 5 }],
    });
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    fireEvent(document, new Event('visibilitychange'));
    expect(await view.findByText('+5.00%')).toBeTruthy();
  });

  it('신규 참여자의 첫 0% 기록을 이번 주 성과로 표시한다', async () => {
    vi.mocked(leagueApi.getDailyHistory).mockResolvedValue({
      ...daily,
      confirmed: false,
      weeklyReturnRate: 0,
      eligibleFrom: '2026-12-14',
      days: [
        {
          ...daily.days[0],
          dailyReturnRate: 0,
          cumulativeReturnRate: 0,
          reason: 'TRACKING_BASELINE',
        },
      ],
    });
    const view = render(<DailyReturns publicId="investor-1" weekStart="" onWeekChange={vi.fn()} />);
    const chart = await view.findByRole('group', { name: '일별 수익률 그래프' });
    expect(chart.querySelector('circle')).toBeTruthy();
    expect(within(chart).getByText('0.00%')).toBeTruthy();
    expect(view.queryByRole('table')).toBeNull();
    expect(view.queryByText('아직 집계된 수익률이 없어요.')).toBeNull();
    expect(view.queryByText('참여 후 누적 0.00%')).toBeNull();
  });

  it('첫 집계와 첫 수익률 변화의 예상 시각을 구분해 안내한다', async () => {
    vi.mocked(leagueApi.getDailyHistory).mockResolvedValue({
      ...daily,
      confirmed: false,
      asOfDate: null,
      weeklyReturnRate: null,
      days: [],
      aggregation: {
        baseDate: '2026-12-09',
        expectedAt: '2026-12-10T05:30:00Z',
        firstReturnExpectedAt: '2026-12-11T05:30:00Z',
        reason: 'PRICE_COLLECTION_PENDING',
        delayed: false,
      },
    });
    const view = render(<DailyReturns publicId="investor-1" weekStart="" onWeekChange={vi.fn()} />);
    expect(await view.findByText('다음 집계 예정')).toBeTruthy();
    expect(view.getByText('첫 수익률 변화 예상')).toBeTruthy();
    expect(
      Array.from(view.container.querySelectorAll('time')).map((time) => time.dateTime),
    ).toEqual(['2026-12-10T05:30:00Z', '2026-12-11T05:30:00Z']);
    expect(view.queryByText('0.00%')).toBeNull();
  });

  it('예상 시각이 없는 종가 누락 상태에 임의의 시간을 표시하지 않는다', async () => {
    vi.mocked(leagueApi.getDailyHistory).mockResolvedValue({
      ...daily,
      confirmed: false,
      aggregation: {
        baseDate: '2026-12-09',
        expectedAt: null,
        firstReturnExpectedAt: null,
        reason: 'MISSING_PRICE',
        delayed: false,
      },
    });
    const view = render(<DailyReturns publicId="investor-1" weekStart="" onWeekChange={vi.fn()} />);
    expect(
      await view.findByRole('heading', { name: '일부 종가 데이터를 확인하고 있어요' }),
    ).toBeTruthy();
    expect(view.getByText('일정 확인 중')).toBeTruthy();
    expect(view.container.querySelector('.league-aggregation time')).toBeNull();
  });

  it('일별 꺾은선은 하루 수익률만 연결하고 중복 상세 카드를 표시하지 않는다', async () => {
    const rates = [0, 2, -1, null];
    const cumulativeRates = [0, 2, 0.98, null];
    vi.mocked(leagueApi.getDailyHistory).mockResolvedValue({
      ...daily,
      confirmed: false,
      asOfDate: '2026-12-09',
      weeklyReturnRate: 0.98,
      days: rates.map((rate, index) => ({
        baseDate: `2026-12-${String(7 + index).padStart(2, '0')}`,
        dailyReturnRate: rate,
        cumulativeReturnRate: cumulativeRates[index],
        status: rate === null ? 'EXCLUDED' : 'CALCULATED',
        reason:
          rate === null ? 'PRICE_COLLECTION_PENDING' : index === 0 ? 'TRACKING_BASELINE' : null,
      })),
    });
    const view = render(<DailyReturns publicId="investor-1" weekStart="" onWeekChange={vi.fn()} />);
    const chart = within(await view.findByRole('group', { name: '일별 수익률 그래프' }));
    const latest = chart.getByRole('group', { name: /12\. 9\. .*하루 수익률 -1.00%/ });
    expect(within(latest).getByText('-1.00%').className).toBe('negative');
    expect(chart.queryByText('+0.98%')).toBeNull();
    expect(view.queryByRole('group', { name: '선택한 날짜의 성과' })).toBeNull();
    expect(chart.getByText('종가 수집 대기')).toBeTruthy();
    const graph = view.container.querySelector('.league-return-line')!;
    expect(graph.querySelectorAll('circle')).toHaveLength(3);
    expect(graph.querySelectorAll('g line')).toHaveLength(2);
    expect(view.getByText('2026-12-09 종가 기준')).toBeTruthy();
    expect(view.getByText('잠정 수익률')).toBeTruthy();
    expect(view.queryByRole('table')).toBeNull();
  });

  it('일별 주차를 바꾼 뒤 늦게 도착한 이전 응답을 표시하지 않는다', async () => {
    let resolvePrevious!: (value: typeof daily) => void;
    vi.mocked(leagueApi.getDailyHistory)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolvePrevious = resolve;
          }),
      )
      .mockResolvedValueOnce({
        ...daily,
        weekStart: '2026-11-30',
        weekEnd: '2026-12-06',
        asOfDate: '2026-11-30',
        weeklyReturnRate: -3,
        days: [
          {
            ...daily.days[0],
            baseDate: '2026-11-30',
            dailyReturnRate: -3,
            cumulativeReturnRate: -3,
          },
        ],
      });
    const onWeekChange = vi.fn();
    const view = render(
      <DailyReturns publicId="investor-1" weekStart="" onWeekChange={onWeekChange} />,
    );
    await waitFor(() => expect(leagueApi.getDailyHistory).toHaveBeenCalledTimes(1));
    view.rerender(
      <DailyReturns publicId="investor-1" weekStart="2026-11-30" onWeekChange={onWeekChange} />,
    );
    await view.findByText('2026-11-30 종가 기준');
    expect(view.getByText('순위 확정')).toBeTruthy();
    await act(async () => resolvePrevious(daily));
    expect(
      within(view.getByRole('group', { name: '일별 수익률 그래프' })).getByText('-3.00%'),
    ).toBeTruthy();
    expect(view.queryByText('+2.50%')).toBeNull();
    expect(view.getByRole('combobox', { name: '조회 주차' })).toHaveProperty('value', '2026-11-30');
  });

  it('선택한 투자자의 지표를 별도 패널에 보여주고 닫기·Esc·배경 클릭으로 닫는다', async () => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue(ranking);
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    const trigger = await view.findByRole('button', { name: '차분한초보 상세 지표' });
    expect(document.title).toBe('주간 리그 | DAYNOMY');
    expect(document.activeElement).toBe(view.getByRole('heading', { name: '주간 리그' }));
    fireEvent.click(trigger);
    const dialog = view.getByRole('dialog', { name: '차분한초보 상세 지표' });
    expect(within(dialog).getByText('투자 기록 4개')).toBeTruthy();
    expect(
      within(dialog).getByRole('link', { name: '공개 프로필 보기' }).getAttribute('href'),
    ).toBe('/league/investor-1');
    expect(within(dialog).getAllByRole('link')).toHaveLength(1);
    expect(trigger.closest('li')?.querySelector('dl')).toBeNull();
    fireEvent.click(within(dialog).getByRole('button', { name: '상세 지표 닫기' }));
    expect(view.queryByRole('dialog')).toBeNull();
    fireEvent.click(trigger);
    fireEvent(dialog, new Event('cancel'));
    expect(view.queryByRole('dialog')).toBeNull();
    fireEvent.click(trigger);
    fireEvent.click(dialog);
    expect(view.queryByRole('dialog')).toBeNull();
  });
  it('팔로잉에는 순위와 성과 없이 투자자 이름과 프로필 링크만 표시한다', async () => {
    vi.mocked(leagueApi.getFollowSummary).mockResolvedValue([
      {
        publicId: 'up',
        displayName: '상승 사용자',
        rank: 8,
        previousRank: 10,
        rankChange: 2,
        weeklyReturnRate: 1,
        newReviewCount: 0,
      },
      {
        publicId: 'down',
        displayName: '하락 사용자',
        rank: 10,
        previousRank: 8,
        rankChange: -2,
        weeklyReturnRate: 1,
        newReviewCount: 0,
      },
      {
        publicId: 'same',
        displayName: '동일 사용자',
        rank: 5,
        previousRank: 5,
        rankChange: 0,
        weeklyReturnRate: 1,
        newReviewCount: 0,
      },
      {
        publicId: 'new',
        displayName: '신규 사용자',
        rank: null,
        previousRank: null,
        rankChange: null,
        weeklyReturnRate: null,
        newReviewCount: 0,
      },
    ]);
    const view = render(
      <MemoryRouter>
        <FollowingPage />
      </MemoryRouter>,
    );
    await view.findByRole('link', { name: '상승 사용자' });
    expect(document.title).toBe('팔로잉 | DAYNOMY');
    expect(document.activeElement).toBe(view.getByRole('heading', { name: '팔로잉' }));
    const list = view.getByRole('list', { name: '팔로우한 투자자' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(4);
    for (const [id, name] of [
      ['up', '상승 사용자'],
      ['down', '하락 사용자'],
      ['same', '동일 사용자'],
      ['new', '신규 사용자'],
    ]) {
      const link = within(list).getByRole('link', { name });
      expect(link.getAttribute('href')).toBe(`/league/${id}`);
      expect(link.textContent?.trim()).toBe(name);
    }
    expect(list.textContent).not.toMatch(/위|수익률|복기|집계|비교 기록/);
  });

  it('최대 종목 비중의 30%·50% 경계와 데이터 부족을 명세대로 구분한다', () => {
    expect([29.99, 30, 49.99, 50, null].map(concentrationLabel)).toEqual([
      '분산',
      '보통',
      '보통',
      '집중 주의',
      '데이터 부족',
    ]);
  });
  it('최근 3주에서 누락 주를 유지하고 이번 주 수익률과 순위만 표시한다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
      detailAvailable: false,
      performance: {
        ...investor.performance,
        trackingStartedOn: '2026-10-12',
        eligibleFrom: '2026-10-19',
        volatilityRate: null,
        consecutiveRecordedWeeks: 2,
        weeklyRank: 3,
      },
      history: Array.from({ length: 8 }, (_, index) => ({
        weekStart: new Date(Date.UTC(2026, 9, 12 + index * 7)).toISOString().slice(0, 10),
        weeklyReturnRate: index >= 6 ? 2.5 : null,
        maxDrawdownRate: null,
        status:
          index === 5
            ? ('PENDING' as const)
            : index < 6
              ? ('NOT_PARTICIPATING' as const)
              : ('CALCULATED' as const),
        confirmed: index === 6,
        weeklyRank: index >= 6 ? 3 : null,
      })),
    });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByRole('heading', { name: investor.displayName });
    fireEvent.click(view.getByRole('button', { name: '주간' }));
    const chart = within(view.getByLabelText('최근 3주 주간 수익률'));
    expect(chart.getAllByRole('button')).toHaveLength(3);
    expect(chart.getAllByText('3위')).toHaveLength(2);
    expect(view.getByText('이번 주 순위')).toBeTruthy();
    expect(
      within(view.getByRole('region', { name: '성과와 위험 지표' })).getByText('3위'),
    ).toBeTruthy();
    expect(chart.queryByText('미참여')).toBeNull();
    expect(chart.getByText('집계 대기')).toBeTruthy();
    expect(view.queryByText('전체 누적 수익률')).toBeNull();
    expect(view.queryByText('위험·기록')).toBeNull();
  });

  it('주간 리그만 제공하고 20개 단위로 순위를 조회한다', async () => {
    vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue(
      Array.from({ length: 8 }, (_, index) => ({
        weekStart: new Date(Date.UTC(2026, 11, 7 - index * 7)).toISOString().slice(0, 10),
        weekEnd: new Date(Date.UTC(2026, 11, 13 - index * 7)).toISOString().slice(0, 10),
        confirmed: index > 0,
      })).reverse(),
    );
    vi.mocked(leagueApi.getRankings).mockImplementation(async (leagueType) => ({
      ...ranking,
      leagueType,
      totalCount: 45,
    }));
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByRole('button', { name: '2페이지' });
    expect(view.queryByRole('radio')).toBeNull();
    expect(view.getByRole('combobox', { name: '조회 주차' })).toHaveProperty('value', '2026-12-07');
    expect(view.getAllByRole('option').map((option) => option.textContent)).toEqual([
      '이번 주 · 12-07 ~ 12-13',
      '지난주 · 11-30 ~ 12-06',
      '지지난주 · 11-23 ~ 11-29',
    ]);
    fireEvent.click(view.getByRole('button', { name: '2페이지' }));
    await waitFor(() =>
      expect(leagueApi.getRankings).toHaveBeenLastCalledWith(
        'WEEKLY_RETURN',
        '2026-12-07',
        expect.any(AbortSignal),
        1,
      ),
    );
    const pageStatus = await view.findByRole('status', { name: '전체 3페이지 중 2페이지' });
    await waitFor(() => expect(document.activeElement).toBe(pageStatus));
    expect(view.getByRole('list').getAttribute('start')).toBe('21');
    fireEvent.change(view.getByRole('combobox', { name: '조회 주차' }), {
      target: { value: '2026-11-30' },
    });
    await waitFor(() =>
      expect(leagueApi.getRankings).toHaveBeenLastCalledWith(
        'WEEKLY_RETURN',
        '2026-11-30',
        expect.any(AbortSignal),
        0,
      ),
    );
    expect(view.queryByText('안정형')).toBeNull();
    expect(view.queryByText('초보리그')).toBeNull();
    expect(view.queryByRole('complementary', { name: '순위 갱신 안내' })).toBeNull();
  });

  it('순위 번호를 6개씩 표시하고 번호와 화살표를 서버 페이지에 연결한다', async () => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue({ ...ranking, totalCount: 145 });
    const view = await act(async () =>
      render(
        <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
          <MemoryRouter>
            <LeaguePage />
          </MemoryRouter>
        </AuthContext.Provider>,
      ),
    );
    const pagination = () => within(view.getByRole('navigation', { name: '순위 페이지' }));
    expect(pagination().getAllByRole('button')).toHaveLength(8);
    expect(pagination().getByRole('button', { name: '이전 방향 1페이지로 이동' })).toHaveProperty(
      'disabled',
      true,
    );
    const calls = vi.mocked(leagueApi.getRankings).mock.calls.length;
    fireEvent.click(pagination().getByRole('button', { name: '1페이지' }));
    expect(leagueApi.getRankings).toHaveBeenCalledTimes(calls);

    fireEvent.click(pagination().getByRole('button', { name: '6페이지' }));
    await waitFor(() =>
      expect(
        pagination().getByRole('button', { name: '6페이지' }).getAttribute('aria-current'),
      ).toBe('page'),
    );
    expect(leagueApi.getRankings).toHaveBeenLastCalledWith(
      'WEEKLY_RETURN',
      '2026-12-07',
      expect.any(AbortSignal),
      5,
    );
    expect(trackEvent).toHaveBeenCalledWith('league_action', {
      surface: 'ranking',
      action: 'change_page',
      status: 'clicked',
      page: 5,
    });
    fireEvent.click(pagination().getByRole('button', { name: '다음 방향 7페이지로 이동' }));
    await view.findByRole('button', { name: '7페이지' });
    expect(pagination().queryByRole('button', { name: '6페이지' })).toBeNull();
    fireEvent.click(pagination().getByRole('button', { name: '8페이지' }));
    await waitFor(() =>
      expect(
        pagination().getByRole('button', { name: '8페이지' }).getAttribute('aria-current'),
      ).toBe('page'),
    );
    expect(leagueApi.getRankings).toHaveBeenLastCalledWith(
      'WEEKLY_RETURN',
      '2026-12-07',
      expect.any(AbortSignal),
      7,
    );
    expect(pagination().getByRole('button', { name: '다음 방향 8페이지로 이동' })).toHaveProperty(
      'disabled',
      true,
    );
    fireEvent.click(pagination().getByRole('button', { name: '이전 방향 7페이지로 이동' }));
    await waitFor(() =>
      expect(
        pagination().getByRole('button', { name: '7페이지' }).getAttribute('aria-current'),
      ).toBe('page'),
    );
    fireEvent.click(pagination().getByRole('button', { name: '이전 방향 6페이지로 이동' }));
    await view.findByRole('button', { name: '6페이지' });
    expect(leagueApi.getRankings).toHaveBeenLastCalledWith(
      'WEEKLY_RETURN',
      '2026-12-07',
      expect.any(AbortSignal),
      5,
    );
  });

  it.each([0, 1, 20])('%i명 순위는 한 페이지이므로 페이지네이션을 숨긴다', async (count) => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue({
      ...ranking,
      totalCount: count,
      rankings: count ? ranking.rankings : [],
    });
    const view = await act(async () =>
      render(
        <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
          <MemoryRouter>
            <LeaguePage />
          </MemoryRouter>
        </AuthContext.Provider>,
      ),
    );
    expect(view.queryByRole('navigation', { name: '순위 페이지' })).toBeNull();
  });

  it.each([
    ['COLLECTION_PENDING', '종가 수집을 기다리고 있어요'],
    ['CALENDAR_PENDING', '거래일 정보를 확인하고 있어요'],
    ['NO_TRADING_DAYS', '이 주에는 거래일이 없어요'],
  ] as const)('빈 순위의 %s 상태를 구분하고 재조회할 수 있다', async (collectionStatus, title) => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue({
      ...ranking,
      confirmed: false,
      asOfDate: null,
      rankings: [],
      totalCount: 0,
      collectionStatus,
    });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByText(title);
    expect(view.queryByText('아직 이 주차에 집계된 투자자가 없습니다')).toBeNull();
    expect(view.queryByRole('link', { name: '참여 시작하기' })).toBeNull();
    if (collectionStatus === 'NO_TRADING_DAYS') {
      expect(view.queryByRole('button', { name: '순위 다시 확인' })).toBeNull();
    } else {
      vi.mocked(leagueApi.getRankings).mockResolvedValue({
        ...ranking,
        rankings: [],
        totalCount: 0,
        collectionStatus: 'NO_TRADING_DAYS',
      });
      fireEvent.click(view.getByRole('button', { name: '순위 다시 확인' }));
      expect(await view.findByText('이 주에는 거래일이 없어요')).toBeTruthy();
    }
  });

  it.each([
    [false, null, '투자리그 참여하기', '/login?returnTo=%2Fleague%2Fportfolio%23select'],
    [true, null, '투자리그 참여하기', '/league/portfolio#select'],
    [true, false, '투자리그 참여하기', '/league/portfolio#select'],
    [true, true, '종목 관리하기', '/league/portfolio'],
  ] as const)(
    '로그인 %s·참여 %s 상태에서 %s 하나만 제공한다',
    async (isLoggedIn, leagueEnabled, label, href) => {
      vi.mocked(leagueApi.getMyInvestorProfile).mockResolvedValue(
        leagueEnabled === null
          ? null
          : {
              ...investor,
              profilePublic: leagueEnabled,
              leagueEnabled,
              allocationPublic: true,
              detailPublic: false,
              leagueEnabledAt: leagueEnabled ? '2026-10-13T01:00:00Z' : null,
            },
      );
      vi.mocked(leagueApi.getRankings).mockResolvedValue({
        ...ranking,
        rankings: [],
        totalCount: 0,
      });
      const view = render(
        <AuthContext.Provider value={{ isLoggedIn, loading: false, role: null }}>
          <MemoryRouter>
            <LeaguePage />
          </MemoryRouter>
        </AuthContext.Provider>,
      );
      expect((await view.findByRole('link', { name: label })).getAttribute('href')).toBe(href);
      await view.findByRole('status', { name: '순위 조회 결과' });
      expect(view.getAllByRole('link', { name: /투자리그 참여하기|종목 관리하기/ })).toHaveLength(
        1,
      );
      expect(
        within(view.getByRole('status', { name: '순위 조회 결과' })).queryByRole('link'),
      ).toBeNull();
      expect(view.queryByText('내 종목 준비하기')).toBeNull();
      if (!isLoggedIn) expect(leagueApi.getMyInvestorProfile).not.toHaveBeenCalled();
      fireEvent.change(view.getByRole('combobox', { name: '조회 주차' }), {
        target: { value: '2026-11-30' },
      });
      await view.findByText('이 주차에 표시할 순위가 없어요. 다른 주차를 선택해 보세요.');
      expect(view.getAllByRole('link', { name: label })).toHaveLength(1);
    },
  );

  it('참여 상태 조회 실패를 미참여로 표시하지 않고 재시도한다', async () => {
    vi.mocked(leagueApi.getMyInvestorProfile)
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValue(null);
    vi.mocked(leagueApi.getRankings).mockResolvedValue(ranking);
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    expect(view.getByText('참여 상태 확인 중')).toBeTruthy();
    expect(view.queryByRole('link', { name: '투자리그 참여하기' })).toBeNull();
    const retry = await view.findByRole('button', { name: '참여 상태 다시 확인' });
    expect(view.queryByRole('link', { name: '투자리그 참여하기' })).toBeNull();
    fireEvent.click(retry);
    expect(await view.findByRole('link', { name: '투자리그 참여하기' })).toBeTruthy();
  });

  it.each([0, 1, 2, 8])(
    '%i주 기록에 맞춰 빈 상태·요약·그래프를 표시하고 실제 기록만 선택한다',
    async (count) => {
      const history = Array.from({ length: count }, (_, index) => ({
        weekStart: new Date(Date.UTC(2026, 9, 12 + index * 7)).toISOString().slice(0, 10),
        weeklyReturnRate: index % 2 ? -1 : 2.5,
        maxDrawdownRate: -1.1,
      }));
      vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue(
        history.map((point) => ({
          weekStart: point.weekStart,
          weekEnd: point.weekStart,
          confirmed: true,
        })),
      );
      vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
        ...investor,
        history,
        detailAvailable: false,
      });
      const view = render(
        <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
          <MemoryRouter initialEntries={['/league/investor-1']}>
            <Routes>
              <Route path="/league/:publicId" element={<InvestorProfilePage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>,
      );
      await view.findByRole('heading', { name: '일별 수익률' });
      expect(
        [...view.container.querySelectorAll('.league-chart-switch button')].map(
          (button) => button.textContent,
        ),
      ).toEqual(['일별', '주간']);
      expect(view.getByRole('button', { name: '일별' }).getAttribute('aria-pressed')).toBe('true');
      fireEvent.click(view.getByRole('button', { name: '주간' }));
      await view.findByRole('heading', { name: '주간 수익률' });
      expect(view.queryAllByRole('button', { name: /주간.*일별 기록 보기/ })).toHaveLength(
        Math.min(count, 3),
      );
      expect(view.queryByLabelText('최근 3주 주간 수익률') !== null).toBe(count >= 2);
      expect(view.getByRole('button', { name: '일별' }).getAttribute('aria-pressed')).toBe('false');
      if (count === 0) {
        expect(view.getByText('아직 집계된 주간 기록이 없어요.')).toBeTruthy();
      } else {
        if (count === 1) expect(view.getByText('첫 주 기록')).toBeTruthy();
        fireEvent.click(view.getAllByRole('button', { name: /주간.*일별 기록 보기/ })[0]);
        await waitFor(() =>
          expect(leagueApi.getDailyHistory).toHaveBeenLastCalledWith(
            'investor-1',
            history.slice(-3)[0].weekStart,
            expect.any(AbortSignal),
          ),
        );
        expect(view.getByRole('button', { name: '일별' }).getAttribute('aria-pressed')).toBe(
          'true',
        );
        expect(await view.findByRole('group', { name: '일별 수익률 그래프' })).toBeTruthy();
      }
    },
  );

  it('3주 그래프와 주차 선택에서 일별·주간 누적을 조회하고 누락일은 대기로 표시한다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
      detailAvailable: false,
    });
    vi.mocked(leagueApi.getDailyHistory)
      .mockResolvedValueOnce(daily)
      .mockResolvedValue({
        ...daily,
        weekStart: '2026-11-30',
        weekEnd: '2026-12-06',
        asOfDate: '2026-12-02',
        weeklyReturnRate: null,
        confirmed: false,
        days: [
          {
            baseDate: '2026-11-30',
            dailyReturnRate: 10,
            cumulativeReturnRate: 10,
            status: 'CALCULATED',
            reason: null,
          },
          {
            baseDate: '2026-12-01',
            dailyReturnRate: null,
            cumulativeReturnRate: null,
            status: 'EXCLUDED',
            reason: 'MISSING_PRICE',
          },
          {
            baseDate: '2026-12-02',
            dailyReturnRate: 5,
            cumulativeReturnRate: null,
            status: 'CALCULATED',
            reason: null,
          },
        ],
      });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByRole('group', { name: '일별 수익률 그래프' });
    fireEvent.change(view.getByRole('combobox', { name: '조회 주차' }), {
      target: { value: '2026-11-30' },
    });
    const chart = within(await view.findByRole('group', { name: '일별 수익률 그래프' }));
    await view.findByText('종가 누락 · 집계 대기');
    expect(view.queryByText('주간 누적 집계 대기')).toBeNull();
    expect(leagueApi.getDailyHistory).toHaveBeenLastCalledWith(
      'investor-1',
      '2026-11-30',
      expect.any(AbortSignal),
    );
    expect(chart.getByText('+5.00%')).toBeTruthy();
    expect(view.queryByRole('group', { name: '선택한 날짜의 성과' })).toBeNull();
    expect(view.queryByRole('table')).toBeNull();
    const requestsBeforeToggle = vi.mocked(leagueApi.getDailyHistory).mock.calls.length;
    fireEvent.click(view.getByRole('button', { name: '주간' }));
    vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
    fireEvent(document, new Event('visibilitychange'));
    expect(leagueApi.getDailyHistory).toHaveBeenCalledTimes(requestsBeforeToggle);
    fireEvent.click(view.getByRole('button', { name: '일별' }));
    await view.findByRole('group', { name: '일별 수익률 그래프' });
    expect(leagueApi.getDailyHistory).toHaveBeenCalledTimes(requestsBeforeToggle);
    expect(view.getByRole('combobox', { name: '조회 주차' })).toHaveProperty('value', '2026-11-30');
    fireEvent.click(view.getByRole('button', { name: '주간' }));
    fireEvent.click(view.getByRole('button', { name: /2026-12-07 주간.*일별 기록 보기/ }));
    await waitFor(() =>
      expect(leagueApi.getDailyHistory).toHaveBeenLastCalledWith(
        'investor-1',
        '2026-12-07',
        expect.any(AbortSignal),
      ),
    );
  });

  it('일별 조회 실패는 재시도하고 미집계 성과를 0%로 표시하지 않는다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
      detailAvailable: false,
      history: [],
      performance: {
        weeklyReturnRate: null,
        threeWeekReturnRate: null,
        maxDrawdownRate: null,
        volatilityRate: null,
        maxHoldingWeight: null,
      },
    });
    vi.mocked(leagueApi.getDailyHistory)
      .mockRejectedValueOnce(new Error('연결 실패'))
      .mockResolvedValueOnce({
        ...daily,
        asOfDate: null,
        weeklyReturnRate: null,
        days: [],
        confirmed: false,
      });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByText('일별 수익률을 불러오지 못했습니다.');
    expect(
      within(view.getByRole('region', { name: '성과와 위험 지표' })).getAllByText('집계 대기'),
    ).toHaveLength(2);
    expect(view.queryByRole('region', { name: '기록과 위험 지표' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '일별 기록 다시 불러오기' }));
    await view.findByText('거래일 기록 없음');
    expect(leagueApi.getDailyHistory).toHaveBeenCalledTimes(2);
  });
  it('동일 기준으로 계산된 순위와 위험 지표를 보여준다', async () => {
    vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue([
      { weekStart: '2026-12-07', weekEnd: '2026-12-13', confirmed: true },
    ]);
    vi.mocked(leagueApi.getRankings).mockResolvedValue(ranking);

    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );

    expect(await view.findByText('차분한초보')).toBeTruthy();
    expect(view.getByText('+2.50%')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '차분한초보 상세 지표' }));
    expect(within(view.getByRole('dialog')).getByText('최대 하락률 · 3주')).toBeTruthy();
    expect(view.getByRole('link', { name: '투자리그 참여하기' })).toBeTruthy();
  });

  it('순위에서 공통 메뉴로 이동하고 공개 프로필에 바로 접근한다', async () => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue(ranking);
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
        <MemoryRouter initialEntries={['/league']}>
          <LeagueNavigation />
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    await view.findByRole('link', { name: /차분한초보.*중립형/ });
    expect(view.getByRole('link', { name: '내 프로필' }).getAttribute('href')).toBe(
      '/league/profile',
    );
    expect(view.getByRole('link', { name: '팔로잉' }).getAttribute('href')).toBe(
      '/league/following',
    );
    expect(view.queryByRole('link', { name: '리그 설정' })).toBeNull();
    expect(
      view.getByRole('link', { name: '차분한초보 공개 프로필 보기' }).getAttribute('href'),
    ).toBe('/league/investor-1');
    expect(
      (await view.findByRole('link', { name: '투자리그 참여하기' })).getAttribute('href'),
    ).toBe('/league/portfolio#select');
    expect(view.queryByText('순위·지표 기준')).toBeNull();
  });

  it('비회원도 공개 종목을 바로 조회하고 결제 안내는 보이지 않는다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue(investor);
    vi.mocked(leagueApi.getInvestorDetail).mockResolvedValue({
      publicId: investor.publicId,
      asOfDate: '2026-12-15',
      decisions: [],
      holdings: [
        {
          assetName: '종가 대기 종목',
          category: 'STOCK',
          weight: null,
          weeklyContributionRate: null,
        },
      ],
    });

    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
        <MemoryRouter initialEntries={['/league/investor-1?week=2026-12-07#investment-details']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );

    expect(await view.findByText('손실 조건부터 기록합니다.')).toBeTruthy();
    expect(await view.findByText('종가 대기 종목')).toBeTruthy();
    expect(view.getByText('비중 집계 대기')).toBeTruthy();
    expect(view.queryByText('기여도 집계 대기')).toBeNull();
    expect(view.queryByRole('link', { name: '멤버십 알아보기' })).toBeNull();
    const loginHref = view.getByRole('link', { name: '로그인하고 팔로우' }).getAttribute('href')!;
    expect(new URL(loginHref, window.location.origin).searchParams.get('returnTo')).toBe(
      '/league/investor-1?week=2026-12-07#investment-details',
    );
    expect(view.queryByRole('heading', { name: '자산군 비중' })).toBeNull();
    expect(leagueApi.getInvestorDetail).toHaveBeenCalledWith('investor-1', expect.any(AbortSignal));
  });

  it.each(['ranking', 'following'])(
    '%s에서 진입한 상세의 해시 이동은 조회 이벤트를 중복 전송하지 않는다',
    async (source) => {
      vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue(investor);
      vi.mocked(leagueApi.getInvestorDetail).mockResolvedValue({
        publicId: investor.publicId,
        asOfDate: '2026-12-05',
        holdings: [],
        decisions: [],
      });
      window.history.replaceState(
        { usr: { source, rankBucket: 'top_3' } },
        '',
        '/league/investor-1',
      );
      const pushState = window.history.pushState.bind(window.history);
      // jsdom에서도 브라우저의 history state 직렬화로 생기는 새 객체를 재현한다.
      vi.spyOn(window.history, 'pushState').mockImplementation((data, unused, url) => {
        pushState(structuredClone(data), unused, url);
      });
      const view = render(
        <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
          <BrowserRouter>
            <Routes>
              <Route path="/league/:publicId" element={<InvestorProfilePage />} />
            </Routes>
          </BrowserRouter>
        </AuthContext.Provider>,
      );
      await view.findByRole('heading', { name: '차분한초보' });
      expect(view.queryByRole('heading', { name: '자산군 비중' })).toBeNull();
      act(() => {
        window.history.pushState(
          { usr: { source, rankBucket: 'top_3' } },
          '',
          '/league/investor-1#investment-decisions',
        );
        window.dispatchEvent(new PopStateEvent('popstate'));
      });
      const views = () =>
        vi.mocked(trackEvent).mock.calls.filter(([name]) => name === 'view_investor_profile');
      expect(views()).toHaveLength(1);
      expect(trackEvent).toHaveBeenCalledWith('view_investor_profile', {
        source,
        rank_bucket: 'top_3',
      });

      act(() => {
        window.history.pushState({ usr: { source: 'direct' } }, '', '/league/investor-1');
        window.dispatchEvent(new PopStateEvent('popstate'));
      });
      expect(views()).toHaveLength(2);
      expect(views().at(-1)).toEqual([
        'view_investor_profile',
        {
          source: 'direct',
          rank_bucket: 'unknown',
        },
      ]);
    },
  );

  it('공개 판단의 복기에 작성일과 한국 시간 기준 수정일을 표시한다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue(investor);
    vi.mocked(leagueApi.getInvestorDetail).mockResolvedValue({
      publicId: 'investor-1',
      asOfDate: '2026-12-05',
      holdings: [
        {
          assetName: '삼성전자',
          category: 'STOCK',
          weight: 60,
          weeklyContributionRate: 1.2,
        },
      ],
      decisions: [
        {
          transactionId: 10,
          assetName: '삼성전자',
          category: 'STOCK',
          transactionType: 'BUY',
          tradedOn: '2026-11-29',
          reason: '실적 성장 기대',
          expectedHoldingPeriod: 'OVER_SIX_MONTHS',
          expectedChange: '매출 성장',
          invalidationCondition: '실적 역성장',
          maximumAcceptableLossRate: 10,
          writtenAfterTrade: false,
          reviews: [
            {
              id: 1,
              actualResult: '수정한 복기 결과',
              differenceFromExpectation: '수정한 차이',
              nextAction: '다음 실적 확인',
              createdAt: '2026-10-06T00:00:00Z',
              updatedAt: '2026-10-06T15:30:00Z',
            },
            {
              id: 2,
              actualResult: '처음 작성한 복기',
              differenceFromExpectation: '기존 차이',
              nextAction: '기록 유지',
              createdAt: '2026-10-06T00:00:00Z',
              updatedAt: '2026-10-06T00:00:00Z',
            },
          ],
        },
      ],
    });

    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );

    expect(await view.findByText('실적 성장 기대')).toBeTruthy();
    expect(view.getByText('· 수정됨 · 2026-10-07')).toBeTruthy();
    expect(view.getByText('· 작성 · 2026-10-06')).toBeTruthy();
    expect(view.queryByText('+1.20% 기여')).toBeNull();
    expect(view.queryByText('결과가 아니라 판단 과정을 더 깊게 보세요')).toBeNull();
  });

  it('공개 상세 조회 실패를 빈 기록으로 숨기지 않고 재시도한다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue(investor);
    vi.mocked(leagueApi.getInvestorDetail)
      .mockRejectedValueOnce(new Error('연결 실패'))
      .mockResolvedValueOnce({
        publicId: investor.publicId,
        asOfDate: '2026-12-05',
        holdings: [
          {
            assetName: '삼성전자',
            category: 'STOCK',
            weight: 100,
            weeklyContributionRate: 1,
            reason: '공시를 확인한 판단',
          },
        ],
        decisions: [],
      });
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    expect(await view.findByRole('alert')).toHaveProperty(
      'textContent',
      expect.stringContaining('상세 정보를 불러오지 못했습니다'),
    );
    expect(view.queryByText('결과가 아니라 판단 과정을 더 깊게 보세요')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '상세 다시 불러오기' }));
    expect(await view.findByText('보유 이유: 공시를 확인한 판단')).toBeTruthy();
    await waitFor(() => expect(leagueApi.getInvestorDetail).toHaveBeenCalledTimes(2));
  });

  it.each([investor.allocation, null])(
    '상세 비공개에서는 성과만 표시하고 종목·판단 API를 호출하지 않는다 (%j)',
    async (allocation) => {
      vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
        ...investor,
        detailAvailable: false,
        allocation,
      });
      const view = render(
        <AuthContext.Provider value={{ isLoggedIn: false, loading: false, role: null }}>
          <MemoryRouter initialEntries={['/league/investor-1#investment-decisions']}>
            <Routes>
              <Route path="/league/:publicId" element={<InvestorProfilePage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>,
      );
      expect(await view.findByText('종목·투자 기록 비공개')).toBeTruthy();
      expect(view.queryByRole('navigation', { name: '투자자 상세 탐색' })).toBeNull();
      expect(view.queryByRole('link', { name: '투자 기록' })).toBeNull();
      expect(leagueApi.getInvestorDetail).not.toHaveBeenCalled();
      expect(view.queryByRole('link', { name: '멤버십 알아보기' })).toBeNull();
      expect(view.queryByRole('heading', { name: '자산군 비중' })).toBeNull();
    },
  );
});
