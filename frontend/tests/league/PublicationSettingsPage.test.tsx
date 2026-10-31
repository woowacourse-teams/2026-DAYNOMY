/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import {
  PortfolioSharingSettings,
  PublicationSettingsPage,
} from '../../src/features/league/PublicationSettingsPage';
import * as api from '../../src/features/league/api';
import * as sharedApi from '../../src/features/league/sharedPortfolioApi';
import * as portfolioApi from '../../src/features/portfolio/api';
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
import { AuthContext } from '../../src/auth/AuthContext';
vi.mock('../../src/features/league/api', () => ({
  getMyInvestorProfile: vi.fn(),
  getInvestmentDecisions: vi.fn(),
  saveInvestorProfile: vi.fn(),
  savePublication: vi.fn(),
}));
vi.mock('../../src/features/league/sharedPortfolioApi', () => ({ getSharedPortfolio: vi.fn() }));
vi.mock('../../src/features/portfolio/api', () => ({ getPortfolioPerformance: vi.fn() }));
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
  joining = false,
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
          joining={joining}
        />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
it.each([false, true])(
  '공개 상태 %s의 설정에는 프로필 이동 링크가 하나이며 저장 후 내 프로필로 돌아간다',
  async (published) => {
    vi.mocked(api.getMyInvestorProfile).mockResolvedValue({
      ...profile,
      profilePublic: published,
      leagueEnabled: published,
      detailPublic: published,
    });
    vi.mocked(sharedApi.getSharedPortfolio).mockResolvedValue({
      holdings: [],
      totalPurchaseAmount: 0,
      totalEvaluationAmount: 0,
      totalReturnRate: null,
      pricesComplete: true,
    });
    vi.mocked(api.getInvestmentDecisions).mockResolvedValue([]);
    vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
    vi.mocked(api.savePublication).mockResolvedValue({ ...profile, allocationPublic: false });
    const view = render(
      <MemoryRouter initialEntries={['/portfolio/publication']}>
        <AuthContext.Provider
          value={{ isLoggedIn: true, loading: false, role: 'USER', nickname: '초보' }}
        >
          <Routes>
            <Route path="/portfolio/publication" element={<PublicationSettingsPage />} />
            <Route path="/league/profile" element={<h1>내 프로필</h1>} />
          </Routes>
        </AuthContext.Provider>
      </MemoryRouter>,
    );
    await view.findByLabelText('공개 닉네임');
    expect(document.title).toBe('프로필 설정 | DAYNOMY');
    expect(document.activeElement).toBe(view.getByRole('heading', { name: '프로필 설정' }));
    expect(view.getByRole('link', { name: '← 내 프로필' })).toHaveProperty(
      'href',
      expect.stringContaining('/league/profile'),
    );
    expect(view.queryByRole('link', { name: '공개 프로필 보기' })).toBeNull();
    expect(
      view
        .getAllByRole('link')
        .filter((link) =>
          ['/league/profile', '/league/test-profile'].includes(link.getAttribute('href') ?? ''),
        ),
    ).toHaveLength(1);
    fireEvent.click(view.getByRole('radio', { name: /비공개/ }));
    fireEvent.click(view.getByRole('button', { name: '저장' }));
    expect(await view.findByRole('heading', { name: '내 프로필' })).toBeTruthy();
    expect(api.savePublication).toHaveBeenCalledWith({
      profilePublic: false,
      leagueEnabled: false,
      detailPublic: false,
      allocationPublic: false,
    });
  },
);
it('가입 화면에 기존 스냅샷 평가손익과 기준일을 리그 성과와 별도로 표시한다', async () => {
  vi.mocked(api.getMyInvestorProfile).mockResolvedValue(profile);
  vi.mocked(sharedApi.getSharedPortfolio).mockResolvedValue({
    holdings: [],
    totalPurchaseAmount: 0,
    totalEvaluationAmount: 0,
    totalReturnRate: null,
    pricesComplete: true,
  });
  vi.mocked(api.getInvestmentDecisions).mockResolvedValue([]);
  vi.mocked(portfolioApi.getPortfolioPerformance).mockResolvedValue({
    status: 'READY',
    reason: null,
    baseDate: '2026-10-26',
    previousBaseDate: '2026-10-23',
    points: [],
    currentPoint: {
      baseDate: '2026-10-28',
      priceBaseDate: '2026-10-27',
      totalPurchaseAmount: 100000,
      totalEvaluationAmount: 110000,
      totalProfitLoss: 10000,
      totalReturnRate: 10,
      dailyProfitLoss: null,
      dailyReturnRate: null,
    },
  });
  const view = render(
    <MemoryRouter initialEntries={['/portfolio/publication?join=true']}>
      <AuthContext.Provider
        value={{ isLoggedIn: true, loading: false, role: 'USER', nickname: '초보' }}
      >
        <PublicationSettingsPage />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  expect(await view.findByText(/10,000원 · 10.00% · 2026-10-27 종가 기준/)).toBeTruthy();
  expect(view.getByText('매수 금액 기준')).toBeTruthy();
  expect(view.getByText(/이번 주에 저장된 보유 기록/)).toBeTruthy();
  expect(view.getByText(/비공개 종목도 수익률에 포함/)).toBeTruthy();
  const guide = view.getByText('집계 기준').closest('details')!;
  expect(guide.open).toBe(false);
  fireEvent.click(view.getByText('집계 기준'));
  expect(guide.open).toBe(true);
});
it('비공개 종목은 보유 이유 없이 참여할 수 있고 조회 실패는 재시도한다', async () => {
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
        pendingReason: false,
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
  vi.mocked(api.getInvestmentDecisions)
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
  expect(view.getByRole('heading', { name: '프로필 설정' })).toBeTruthy();
  fireEvent.click(await view.findByRole('button', { name: '공유 범위 다시 불러오기' }));
  const join = await view.findByRole('radio', { name: /주간 리그 참여/ });
  expect(join).toHaveProperty('disabled', false);
  expect(view.queryByText('보유 이유 1개 미작성')).toBeNull();
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
  fireEvent.click(view.getByRole('radio', { name: /주간 리그 참여/ }));
  fireEvent.click(view.getByRole('radio', { name: /나만 보기/ }));
  fireEvent.click(view.getByRole('button', { name: /^(저장|공개에 동의하고 저장)$/ }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith({
      profilePublic: false,
      leagueEnabled: false,
      detailPublic: false,
      allocationPublic: false,
    }),
  );
  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(profile));
  expect(updateNickname).toHaveBeenCalledWith(profile.displayName);
});
it('보유 이유가 빠졌으면 순위 참여를 막고 비공개 설정은 저장할 수 있다', async () => {
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.savePublication).mockResolvedValue(profile);
  const view = renderSettings({ ready: false, missing: 1 });
  expect(await view.findByRole('radio', { name: /주간 리그 참여/ })).toHaveProperty(
    'disabled',
    true,
  );
  expect(view.getByText('보유 이유 1개 미작성')).toBeTruthy();
  expect(view.queryByRole('radio', { name: /프로필 공개/ })).toBeNull();
  fireEvent.click(view.getByRole('button', { name: /^(저장|공개에 동의하고 저장)$/ }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith(
      expect.objectContaining({ profilePublic: false, leagueEnabled: false, detailPublic: false }),
    ),
  );
});
it('리그 참여는 종목·비중·기록 공개를 함께 저장한다', async () => {
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.savePublication).mockResolvedValue({
    ...profile,
    profilePublic: true,
    leagueEnabled: true,
  });
  const view = renderSettings();
  fireEvent.click(await view.findByRole('radio', { name: /주간 리그 참여/ }));
  expect(view.getByRole('radio', { name: /주간 리그 참여/ })).toHaveProperty('checked', true);
  expect(view.queryByRole('checkbox')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: /^(저장|공개에 동의하고 저장)$/ }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith(
      expect.objectContaining({ profilePublic: true, leagueEnabled: true, detailPublic: true }),
    ),
  );
  await waitFor(() =>
    expect(trackEvent).toHaveBeenCalledWith(
      'league_enrollment',
      expect.objectContaining({ action: 'join', status: 'success', participating: true }),
    ),
  );
  expect(
    vi
      .mocked(trackEvent)
      .mock.calls.filter(
        ([name, params]) => name === 'league_enrollment' && params?.status === 'success',
      ),
  ).toHaveLength(1);
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
  expect(view.getByRole('textbox', { name: '공개 닉네임' })).toHaveProperty(
    'value',
    saved.displayName,
  );
  fireEvent.click(view.getByRole('radio', { name: '투자 3년 이상' }));
  fireEvent.click(view.getByRole('radio', { name: '공격형' }));
  fireEvent.click(view.getByRole('radio', { name: /주간 리그 참여/ }));
  fireEvent.click(view.getByRole('button', { name: /^(저장|공개에 동의하고 저장)$/ }));
  await view.findByRole('alert');
  await waitFor(() => expect(document.activeElement).toBe(view.getByRole('alert')));
  expect(view.getByLabelText('공개 닉네임')).toHaveProperty('value', saved.displayName);
  expect(view.queryByRole('checkbox')).toBeNull();
  expect(view.getByRole('radio', { name: '투자 3년 이상' })).toHaveProperty('checked', true);
  expect(view.getByRole('radio', { name: '공격형' })).toHaveProperty('checked', true);
  expect(api.saveInvestorProfile).toHaveBeenCalledWith(
    expect.objectContaining({ experienceLevel: 'OVER_THREE_YEARS', riskProfile: 'AGGRESSIVE' }),
  );
  expect(onSaved).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: /^(저장|공개에 동의하고 저장)$/ }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledWith(saved));
  expect(updateNickname).toHaveBeenCalledWith(saved.displayName);
});

it('닉네임은 기본 필수 입력과 공백 검증을 사용하며 저장 버튼을 탐색할 수 있다', () => {
  const view = renderSettings({ saved: null });
  const input = view.getByRole('textbox', { name: '공개 닉네임' }) as HTMLInputElement;
  for (const value of ['', '   ']) {
    fireEvent.change(input, { target: { value } });
    expect(input.checkValidity()).toBe(false);
    expect(view.getByRole('button', { name: '저장' })).toHaveProperty('disabled', false);
  }
  fireEvent.change(input, { target: { value: '새닉네임' } });
  expect(input.checkValidity()).toBe(true);
  expect(api.saveInvestorProfile).not.toHaveBeenCalled();
});

it('기존 프로필만 공개한 계정은 자동 가입 없이 비공개 선택으로 시작한다', async () => {
  const existing = { ...profile, profilePublic: true, detailPublic: true };
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(existing);
  vi.mocked(api.savePublication).mockResolvedValue(profile);
  const view = renderSettings({ saved: existing });
  expect(view.getByRole('radio', { name: /비공개/ })).toHaveProperty('checked', true);
  expect(view.queryByRole('radio', { name: /프로필 공개/ })).toBeNull();
  expect(api.savePublication).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '저장' }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith({
      profilePublic: false,
      leagueEnabled: false,
      detailPublic: false,
      allocationPublic: false,
    }),
  );
});

it('참여 화면은 공개 내용을 알리고 확인 버튼을 눌렀을 때만 참여를 저장한다', async () => {
  const existing = { ...profile, detailPublic: true, allocationPublic: false };
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(existing);
  vi.mocked(api.savePublication).mockResolvedValue({
    ...existing,
    profilePublic: true,
    leagueEnabled: true,
  });
  const view = renderSettings({ saved: existing, joining: true });
  expect(view.queryByRole('radio', { name: /주간 리그 참여/ })).toBeNull();
  expect(view.getByLabelText('공개 닉네임')).toHaveProperty('value', profile.displayName);
  expect(view.queryByText('추가 공개 설정')).toBeNull();
  expect(view.getByText('닉네임 · 수익률 · 종목 · 비중 · 보유 이유 · 복기')).toBeTruthy();
  expect(api.savePublication).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '공개에 동의하고 참여' }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith({
      profilePublic: true,
      leagueEnabled: true,
      detailPublic: true,
      allocationPublic: true,
    }),
  );
});

it('참여 주소로 바로 들어와도 종목 준비가 안 됐으면 참여를 막는다', () => {
  const view = renderSettings({ joining: true, ready: false, missing: 1 });
  expect(view.getByRole('button', { name: '공개에 동의하고 참여' })).toHaveProperty(
    'disabled',
    true,
  );
  expect(view.getByRole('link', { name: '종목 준비하기' })).toHaveProperty(
    'href',
    expect.stringContaining('/league/portfolio'),
  );
  expect(api.savePublication).not.toHaveBeenCalled();
});

it('참여 저장 실패 시 화면을 떠나지 않고 닉네임과 동의 선택을 유지한다', async () => {
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.savePublication)
    .mockRejectedValueOnce(new Error('저장 실패'))
    .mockResolvedValueOnce({
      ...profile,
      profilePublic: true,
      leagueEnabled: true,
      allocationPublic: false,
    });
  const view = renderSettings({ joining: true });
  fireEvent.change(view.getByLabelText('공개 닉네임'), { target: { value: '새닉네임' } });
  vi.mocked(api.saveInvestorProfile).mockResolvedValue({ ...profile, displayName: '새닉네임' });
  fireEvent.click(view.getByRole('button', { name: '공개에 동의하고 참여' }));
  await view.findByText('저장 실패');
  expect(onSaved).not.toHaveBeenCalled();
  expect(view.getByLabelText('공개 닉네임')).toHaveProperty('value', '새닉네임');
  expect(view.queryByRole('checkbox')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '공개에 동의하고 참여' }));
  await waitFor(() => expect(onSaved).toHaveBeenCalledOnce());
});
