/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { LeagueProfilePage } from '../../src/features/league/LeagueProfilePage';
import { AuthContext } from '../../src/auth/AuthContext';
import * as api from '../../src/features/league/api';
vi.mock('../../src/features/league/api', () => ({
  getMyInvestorProfile: vi.fn(),
  getPublicInvestor: vi.fn(),
  getInvestorDetail: vi.fn(),
  getDailyHistory: vi.fn(),
}));
const profile = {
  publicId: 'investor',
  displayName: '기록하는투자자',
  bio: '',
  experienceLevel: 'BEGINNER' as const,
  riskProfile: 'BALANCED' as const,
  leagueEnabled: true,
  profilePublic: true,
  detailPublic: false,
  allocationPublic: false,
  leagueEnabledAt: null,
};
beforeEach(() => {
  vi.mocked(api.getMyInvestorProfile).mockResolvedValue(null);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
const renderPage = () =>
  render(
    <AuthContext.Provider
      value={{ isLoggedIn: true, loading: false, role: 'USER', nickname: '투자자' }}
    >
      <MemoryRouter>
        <LeagueProfilePage />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
it.each([null, { ...profile, profilePublic: false, leagueEnabled: false }])(
  '비공개 프로필은 설정만 제공하고 종목 관리를 표시하지 않는다',
  async (saved) => {
    vi.mocked(api.getMyInvestorProfile).mockResolvedValue(saved);
    const view = renderPage();
    expect(await view.findByText('현재 비공개입니다.')).toBeTruthy();
    expect(view.getByRole('link', { name: '프로필 설정' })).toHaveProperty(
      'href',
      expect.stringContaining('/portfolio/publication'),
    );
    expect(view.queryByRole('link', { name: '종목 관리' })).toBeNull();
    expect(api.getPublicInvestor).not.toHaveBeenCalled();
  },
);
it('내 프로필은 공개 프로필 본문을 표시하고 팔로우 대신 설정을 제공한다', async () => {
  vi.mocked(api.getMyInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.getPublicInvestor).mockResolvedValue({
    ...profile,
    performance: {
      weeklyReturnRate: 2,
      threeWeekReturnRate: null,
      maxDrawdownRate: null,
      volatilityRate: null,
      maxHoldingWeight: null,
    },
    history: [],
    allocation: null,
    decisionCount: 0,
    reviewCompletionRate: 0,
    detailAvailable: false,
    followed: false,
  });
  const view = renderPage();
  expect(await view.findByRole('heading', { name: profile.displayName })).toBeTruthy();
  expect(view.getByText('이번 주 수익률')).toBeTruthy();
  expect(view.getByRole('link', { name: '프로필 설정' })).toBeTruthy();
  expect(view.queryByRole('button', { name: '팔로우' })).toBeNull();
  expect(view.queryByRole('link', { name: '종목 관리' })).toBeNull();
  expect(api.getPublicInvestor).toHaveBeenCalledWith(profile.publicId, expect.any(AbortSignal));
});
it('조회 실패는 비공개로 오인하지 않고 재시도한다', async () => {
  vi.mocked(api.getMyInvestorProfile).mockRejectedValueOnce(new Error('연결 실패'));
  const view = renderPage();
  await view.findByRole('alert');
  expect(view.queryByText('현재 비공개입니다.')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '다시 불러오기' }));
  expect(await view.findByText('현재 비공개입니다.')).toBeTruthy();
});
