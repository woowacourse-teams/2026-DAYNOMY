/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import {
  PortfolioSharingSettings,
  PublicationSettingsPage,
} from '../../src/features/league/PublicationSettingsPage';
import * as api from '../../src/features/league/api';
import * as sharedApi from '../../src/features/league/sharedPortfolioApi';
import { AuthContext } from '../../src/auth/AuthContext';
vi.mock('../../src/features/league/api', () => ({
  getMyInvestorProfile: vi.fn(),
  getTransactions: vi.fn(),
  saveInvestorProfile: vi.fn(),
  savePublication: vi.fn(),
}));
vi.mock('../../src/features/league/sharedPortfolioApi', () => ({ getSharedPortfolio: vi.fn() }));
const profile = {
  publicId: 'test-profile',
  displayName: '초보',
  bio: '',
  experienceLevel: 'BEGINNER' as const,
  riskProfile: 'BALANCED' as const,
  profilePublic: false,
  leagueEnabled: false,
  allocationPublic: true,
  detailPublic: false,
  leagueEnabledAt: null,
};
const updateNickname = vi.fn();
const onSaved = vi.fn();
function renderSettings({
  saved = profile as typeof profile | null,
  ready = true,
  failed = false,
  missing = 0,
  onRetry = vi.fn(),
} = {}) {
  return render(
    <MemoryRouter>
      <AuthContext.Provider
        value={{ isLoggedIn: true, loading: false, role: 'USER', nickname: '초보', updateNickname }}
      >
        <PortfolioSharingSettings
          profile={saved}
          loading={false}
          loadFailed={failed}
          onRetry={onRetry}
          onSaved={onSaved}
          recordsReady={ready}
          missingCount={missing}
        />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
it('독립 설정 화면에서 숨긴 종목의 필수 기록을 확인하고 조회 실패는 재시도한다', async () => {
  vi.mocked(api.getMyInvestorProfile).mockResolvedValue(profile);
  vi.mocked(sharedApi.getSharedPortfolio).mockResolvedValue({
    holdings: [
      {
        assetId: 1,
        assetCode: '005930',
        assetName: '삼성전자',
        category: 'STOCK',
        market: 'KOSPI',
        quantity: 1,
        averagePurchasePrice: 100,
        hidden: true,
        reason: '',
        baseDate: null,
        closePrice: null,
        evaluationAmount: null,
        returnRate: null,
      },
    ],
    totalPurchaseAmount: 100,
    totalEvaluationAmount: null,
    totalReturnRate: null,
    pricesComplete: false,
  });
  vi.mocked(api.getTransactions)
    .mockRejectedValueOnce(new Error('기록 조회 실패'))
    .mockResolvedValue([]);
  const view = render(
    <MemoryRouter initialEntries={['/portfolio/publication']}>
      <AuthContext.Provider
        value={{ isLoggedIn: true, loading: false, role: 'USER', nickname: '초보' }}
      >
        <PublicationSettingsPage />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  expect(view.getByRole('heading', { name: '리그 설정' })).toBeTruthy();
  fireEvent.click(await view.findByRole('button', { name: '공유 범위 다시 불러오기' }));
  const join = await view.findByRole('checkbox', { name: '주간 순위에 참여하기' });
  expect(join).toHaveProperty('disabled', true);
  expect(view.getByText('보유 이유 1개 미작성')).toBeTruthy();
  expect(view.getByRole('link', { name: '내 리그에서 작성' }).getAttribute('href')).toBe(
    '/league/portfolio',
  );
  expect(api.savePublication).not.toHaveBeenCalled();
});
it('공유 범위를 못 불러오면 편집을 막고 다시 불러온다', async () => {
  const onRetry = vi.fn();
  const view = renderSettings({ failed: true, onRetry });
  expect(view.getByRole('alert')).toBeTruthy();
  expect(view.queryByRole('radio', { name: /나만 보기/ })).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '공유 범위 다시 불러오기' }));
  expect(onRetry).toHaveBeenCalledOnce();
  expect(api.savePublication).not.toHaveBeenCalled();
});
it('나만 보기를 선택하면 종목 공개와 주간 참여를 함께 끄고 저장한다', async () => {
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.savePublication).mockResolvedValue(profile);
  const view = renderSettings();
  fireEvent.click(await view.findByRole('radio', { name: /종목과 보유 이유도 공유/ }));
  fireEvent.click(view.getByRole('checkbox', { name: '주간 순위에 참여하기' }));
  fireEvent.click(view.getByRole('radio', { name: /나만 보기/ }));
  fireEvent.click(view.getByRole('button', { name: '저장' }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith({
      profilePublic: false,
      leagueEnabled: false,
      detailPublic: false,
      allocationPublic: true,
    }),
  );
  expect(await view.findByText('저장했습니다.')).toBeTruthy();
  expect(updateNickname).toHaveBeenCalledWith(profile.displayName);
});
it('보유 이유가 빠졌으면 참여를 막고 성과 공유만 저장할 수 있다', async () => {
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.savePublication).mockResolvedValue({ ...profile, profilePublic: true });
  const view = renderSettings({ ready: false, missing: 1 });
  expect(await view.findByRole('checkbox', { name: '주간 순위에 참여하기' })).toHaveProperty(
    'disabled',
    true,
  );
  expect(view.getByText('보유 이유 1개 미작성')).toBeTruthy();
  fireEvent.click(view.getByRole('radio', { name: /성과만 공유/ }));
  fireEvent.click(view.getByRole('button', { name: '저장' }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith(
      expect.objectContaining({ profilePublic: true, leagueEnabled: false, detailPublic: false }),
    ),
  );
});
it('리그 참여 선택은 성과 공개 동의를 포함하고 종목 공개는 강제하지 않는다', async () => {
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.savePublication).mockResolvedValue({
    ...profile,
    profilePublic: true,
    leagueEnabled: true,
  });
  const view = renderSettings();
  fireEvent.click(await view.findByRole('checkbox', { name: '주간 순위에 참여하기' }));
  expect(view.getByRole('radio', { name: /성과만 공유/ })).toHaveProperty('checked', true);
  fireEvent.click(view.getByRole('button', { name: '저장' }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith(
      expect.objectContaining({ profilePublic: true, leagueEnabled: true, detailPublic: false }),
    ),
  );
});
it('새 프로필은 계정 닉네임으로 시작하고 저장 실패 후 선택과 입력을 유지한다', async () => {
  const saved = { ...profile, displayName: '새공개닉네임' };
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(saved);
  vi.mocked(api.savePublication)
    .mockRejectedValueOnce(new Error('저장 실패'))
    .mockResolvedValueOnce(saved);
  const view = renderSettings({ saved: null });
  expect(view.getByLabelText('공개 닉네임')).toHaveProperty('value', '초보');
  fireEvent.change(view.getByLabelText('공개 닉네임'), { target: { value: saved.displayName } });
  fireEvent.click(view.getByRole('radio', { name: '투자 3년 이상' }));
  fireEvent.click(view.getByRole('radio', { name: '공격형' }));
  fireEvent.click(view.getByRole('radio', { name: /종목과 보유 이유도 공유/ }));
  fireEvent.click(view.getByRole('button', { name: '저장' }));
  await view.findByRole('alert');
  expect(view.getByLabelText('공개 닉네임')).toHaveProperty('value', saved.displayName);
  expect(view.getByRole('radio', { name: /종목과 보유 이유도 공유/ })).toHaveProperty(
    'checked',
    true,
  );
  expect(view.getByRole('radio', { name: '투자 3년 이상' })).toHaveProperty('checked', true);
  expect(view.getByRole('radio', { name: '공격형' })).toHaveProperty('checked', true);
  expect(api.saveInvestorProfile).toHaveBeenCalledWith(
    expect.objectContaining({ experienceLevel: 'OVER_THREE_YEARS', riskProfile: 'AGGRESSIVE' }),
  );
  expect(onSaved).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '저장' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
  expect(updateNickname).toHaveBeenCalledWith(saved.displayName);
});
