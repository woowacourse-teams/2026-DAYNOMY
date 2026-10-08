/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../src/auth/AuthContext';
import { InvestorProfilePage } from '../../src/features/league/InvestorProfilePage';
import { LeaguePage } from '../../src/features/league/LeaguePage';
import * as leagueApi from '../../src/features/league/api';

vi.mock('../../src/features/league/api', () => ({
  followInvestor: vi.fn(),
  getLeagueWeeks: vi.fn(),
  getInvestorDetail: vi.fn(),
  getPublicInvestor: vi.fn(),
  getDailyHistory: vi.fn(),
  getRankings: vi.fn(),
  unfollowInvestor: vi.fn(),
}));

const ranking = {
  weekStart: '2026-09-28',
  weekEnd: '2026-10-02',
  leagueType: 'WEEKLY_RETURN' as const,
  confirmed: true,
  totalCount: 1,
  asOfDate: '2026-10-02',
  rankings: [
    {
      rank: 1,
      publicId: 'investor-1',
      displayName: '차분한초보',
      experienceLevel: 'BEGINNER' as const,
      riskProfile: 'BALANCED' as const,
      weeklyReturnRate: 2.5,
      eightWeekReturnRate: 4.2,
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
    eightWeekReturnRate: 4.2,
    maxDrawdownRate: -1.1,
    volatilityRate: 0.7,
    maxHoldingWeight: 55,
  },
  allocation: { stockWeight: 60, etfWeight: 40 },
  history: [{ weekStart: '2026-09-28', weeklyReturnRate: 2.5, maxDrawdownRate: -1.1 }],
  decisionCount: 4,
  reviewCompletionRate: 75,
  followed: false,
  detailAvailable: true,
};

const daily = {
  weekStart: '2026-09-28',
  weekEnd: '2026-10-04',
  asOfDate: '2026-09-28',
  eligibleFrom: '2026-08-03',
  confirmed: true,
  weeklyReturnRate: 2.5,
  days: [
    {
      baseDate: '2026-09-28',
      dailyReturnRate: 2.5,
      cumulativeReturnRate: 2.5,
      status: 'CALCULATED' as const,
      reason: null,
    },
  ],
};

beforeEach(() => {
  vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue([
    { weekStart: '2026-09-28', weekEnd: '2026-10-04', confirmed: true },
    { weekStart: '2026-09-21', weekEnd: '2026-09-27', confirmed: true },
  ]);
  vi.mocked(leagueApi.getDailyHistory).mockResolvedValue(daily);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('투자 리그 화면', () => {
  it.each([0, 1, 2, 8])(
    '%i주 기록에 맞춰 빈 상태·요약·그래프를 표시하고 실제 기록만 선택한다',
    async (count) => {
      const history = Array.from({ length: count }, (_, index) => ({
        weekStart: new Date(Date.UTC(2026, 7, 3 + index * 7)).toISOString().slice(0, 10),
        weeklyReturnRate: index % 2 ? -1 : 2.5,
        maxDrawdownRate: -1.1,
      }));
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
      await view.findByRole('heading', { name: '주간 수익률' });
      expect(view.queryAllByRole('button', { name: /주간.*일별 기록 보기/ })).toHaveLength(count);
      expect(view.queryByLabelText('최근 8주 주간 수익률') !== null).toBe(count >= 2);
      expect(view.getByText('일별 수익률 보기').closest('details')?.open).toBe(false);
      if (count === 0) {
        expect(view.getByText('아직 집계된 주간 기록이 없어요.')).toBeTruthy();
      } else {
        if (count === 1) expect(view.getByText('첫 주 기록')).toBeTruthy();
        fireEvent.click(view.getAllByRole('button', { name: /주간.*일별 기록 보기/ })[0]);
        await waitFor(() =>
          expect(leagueApi.getDailyHistory).toHaveBeenLastCalledWith(
            'investor-1',
            history[0].weekStart,
            expect.any(AbortSignal),
          ),
        );
        expect(view.getByText('일별 수익률 보기').closest('details')?.open).toBe(true);
        expect(view.queryByRole('img', { name: /주간 누적 수익률 그래프/ })).toBeNull();
      }
    },
  );

  it('8주 그래프와 주차 선택에서 일별·주간 누적을 조회하고 누락일은 대기로 표시한다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
      detailAvailable: false,
    });
    vi.mocked(leagueApi.getDailyHistory)
      .mockResolvedValueOnce(daily)
      .mockResolvedValue({
        ...daily,
        weekStart: '2026-09-21',
        weekEnd: '2026-09-27',
        asOfDate: '2026-09-23',
        weeklyReturnRate: null,
        confirmed: false,
        days: [
          {
            baseDate: '2026-09-21',
            dailyReturnRate: 10,
            cumulativeReturnRate: 10,
            status: 'CALCULATED',
            reason: null,
          },
          {
            baseDate: '2026-09-22',
            dailyReturnRate: null,
            cumulativeReturnRate: null,
            status: 'EXCLUDED',
            reason: 'MISSING_PRICE',
          },
          {
            baseDate: '2026-09-23',
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
    fireEvent.click(await view.findByText('일별 수익률 보기'));
    await view.findByText('주간 누적 +2.50%');
    fireEvent.change(view.getByRole('combobox', { name: '조회 주차' }), {
      target: { value: '2026-09-21' },
    });
    await view.findByText('주간 누적 집계 대기');
    expect(leagueApi.getDailyHistory).toHaveBeenLastCalledWith(
      'investor-1',
      '2026-09-21',
      expect.any(AbortSignal),
    );
    const table = within(view.getByRole('table'));
    expect(table.getByText('종가 누락 · 집계 대기')).toBeTruthy();
    expect(table.getByText('+5.00%')).toBeTruthy();
    expect(table.getAllByText('—')).toHaveLength(3);
    fireEvent.click(view.getByRole('button', { name: /2026-09-28 주간.*일별 기록 보기/ }));
    await waitFor(() =>
      expect(leagueApi.getDailyHistory).toHaveBeenLastCalledWith(
        'investor-1',
        '2026-09-28',
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
        eightWeekReturnRate: null,
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
    fireEvent.click(await view.findByText('일별 수익률 보기'));
    await view.findByText('일별 수익률을 불러오지 못했습니다.');
    expect(
      within(view.getByRole('region', { name: '성과와 위험 지표' })).getAllByText('집계 대기'),
    ).toHaveLength(5);
    fireEvent.click(view.getByRole('button', { name: '일별 기록 다시 불러오기' }));
    await view.findByText(
      '이 주차에는 아직 수집된 거래일 종가가 없습니다. 휴장일은 기록하지 않아요.',
    );
    expect(leagueApi.getDailyHistory).toHaveBeenCalledTimes(2);
  });
  it('동일 기준으로 계산된 순위와 위험 지표를 보여준다', async () => {
    vi.mocked(leagueApi.getLeagueWeeks).mockResolvedValue([
      { weekStart: '2026-09-28', weekEnd: '2026-10-02', confirmed: true },
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
    expect(view.getByText('최대 하락')).toBeTruthy();
    expect(view.getByRole('link', { name: '로그인하고 참여하기' })).toBeTruthy();
  });

  it('내 자산 공유를 첫 행동으로 안내하고 순위 뒤에 참여 순서를 보여준다', async () => {
    vi.mocked(leagueApi.getRankings).mockResolvedValue(ranking);
    const view = render(
      <AuthContext.Provider value={{ isLoggedIn: true, loading: false, role: 'USER' }}>
        <MemoryRouter>
          <LeaguePage />
        </MemoryRouter>
      </AuthContext.Provider>,
    );
    const investorLink = await view.findByRole('link', { name: /차분한초보/ });
    expect(view.getByRole('link', { name: '내 공유 관리' }).getAttribute('href')).toBe(
      '/league/portfolio',
    );
    expect(view.getByRole('link', { name: '관심 투자자' }).getAttribute('href')).toBe(
      '/league/following',
    );
    const guide = view.getByText('참여 방법').closest('details');
    expect(guide?.open).toBe(false);
    expect(investorLink.compareDocumentPosition(guide!) & Node.DOCUMENT_POSITION_FOLLOWING).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it('비회원도 공개 종목을 바로 조회하고 결제 안내는 보이지 않는다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue(investor);
    vi.mocked(leagueApi.getInvestorDetail).mockResolvedValue({
      publicId: investor.publicId,
      asOfDate: '2026-10-06',
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
        <MemoryRouter initialEntries={['/league/investor-1']}>
          <Routes>
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>,
    );

    expect(await view.findByText('손실 조건부터 기록합니다.')).toBeTruthy();
    expect(await view.findByText('종가 대기 종목')).toBeTruthy();
    expect(view.getByText('비중 집계 대기')).toBeTruthy();
    expect(view.getByText('기여도 집계 대기')).toBeTruthy();
    expect(view.queryByRole('link', { name: '멤버십 알아보기' })).toBeNull();
    expect(leagueApi.getInvestorDetail).toHaveBeenCalledWith('investor-1', expect.any(AbortSignal));
  });

  it('일반 회원도 서버가 공개한 판단과 기여도를 바로 보여준다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue(investor);
    vi.mocked(leagueApi.getInvestorDetail).mockResolvedValue({
      publicId: 'investor-1',
      asOfDate: '2026-09-26',
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
          tradedOn: '2026-09-20',
          reason: '실적 성장 기대',
          expectedHoldingPeriod: 'OVER_SIX_MONTHS',
          expectedChange: '매출 성장',
          invalidationCondition: '실적 역성장',
          maximumAcceptableLossRate: 10,
          writtenAfterTrade: false,
          reviews: [],
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
    expect(view.getByText('+1.20% 기여')).toBeTruthy();
    expect(view.queryByText('결과가 아니라 판단 과정을 더 깊게 보세요')).toBeNull();
  });

  it('공개 상세 조회 실패를 빈 기록으로 숨기지 않고 재시도한다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue(investor);
    vi.mocked(leagueApi.getInvestorDetail)
      .mockRejectedValueOnce(new Error('연결 실패'))
      .mockResolvedValueOnce({
        publicId: investor.publicId,
        asOfDate: '2026-09-26',
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
    expect(await view.findByText('판단 근거: 공시를 확인한 판단')).toBeTruthy();
    await waitFor(() => expect(leagueApi.getInvestorDetail).toHaveBeenCalledTimes(2));
  });

  it('상세 비공개 동의는 유지하고 종목·판단 API를 호출하지 않는다', async () => {
    vi.mocked(leagueApi.getPublicInvestor).mockResolvedValue({
      ...investor,
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
    expect(
      await view.findByText('이 투자자는 종목과 판단 기록을 비공개로 설정했습니다.'),
    ).toBeTruthy();
    expect(leagueApi.getInvestorDetail).not.toHaveBeenCalled();
    expect(view.queryByRole('link', { name: '멤버십 알아보기' })).toBeNull();
  });
});
