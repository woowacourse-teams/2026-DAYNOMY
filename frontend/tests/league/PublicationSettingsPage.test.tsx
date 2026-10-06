/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { PublicationSettingsPage } from '../../src/features/league/PublicationSettingsPage';
import * as api from '../../src/features/league/api';
import { AuthContext } from '../../src/auth/AuthContext';

vi.mock('../../src/features/league/api', () => ({
  getMyInvestorProfile: vi.fn(),
  saveInvestorProfile: vi.fn(),
  savePublication: vi.fn(),
}));
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

function renderSettings() {
  return render(
    <MemoryRouter>
      <AuthContext.Provider
        value={{ isLoggedIn: true, loading: false, role: 'USER', nickname: '초보', updateNickname }}
      >
        <PublicationSettingsPage />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it('기존 설정을 못 불러오면 편집을 막고 다시 불러올 수 있게 한다', async () => {
  vi.mocked(api.getMyInvestorProfile)
    .mockRejectedValueOnce(new Error('일시적인 연결 오류'))
    .mockResolvedValue(profile);
  const view = renderSettings();
  expect(await view.findByRole('alert')).toBeTruthy();
  expect(view.queryByLabelText('공개 닉네임')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '다시 불러오기' }));
  expect(await view.findByLabelText('공개 닉네임')).toHaveProperty('value', '초보');
  expect(api.savePublication).not.toHaveBeenCalled();
});

it('비공개 미리보기와 잠긴 항목을 표시하며 공개를 끄면 리그 참여도 저장하지 않는다', async () => {
  vi.mocked(api.getMyInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(profile);
  vi.mocked(api.savePublication).mockResolvedValue(profile);
  const view = renderSettings();
  expect(await view.findByText('현재 선택: 비공개')).toBeTruthy();
  expect(view.getByRole('checkbox', { name: /주간 리그 참여/ })).toHaveProperty('disabled', true);
  fireEvent.click(view.getByRole('checkbox', { name: /프로필 공개/ }));
  fireEvent.click(view.getByRole('checkbox', { name: /주간 리그 참여/ }));
  expect(view.getByText(/주간 리그 참여 켜짐/)).toBeTruthy();
  fireEvent.click(view.getByRole('checkbox', { name: /프로필 공개/ }));
  fireEvent.click(view.getByRole('button', { name: '설정 저장' }));
  await waitFor(() =>
    expect(api.savePublication).toHaveBeenCalledWith(
      expect.objectContaining({
        profilePublic: false,
        leagueEnabled: false,
        detailPublic: false,
      }),
    ),
  );
  expect(await view.findByText('공개 설정을 저장했습니다.')).toBeTruthy();
  expect(updateNickname).toHaveBeenCalledWith(profile.displayName);
});

it('새 공개 프로필은 서비스 닉네임으로 시작하고 저장한 닉네임을 인증 상태에도 반영한다', async () => {
  vi.mocked(api.getMyInvestorProfile).mockResolvedValue(null);
  const saved = { ...profile, displayName: '새공개닉네임' };
  vi.mocked(api.saveInvestorProfile).mockResolvedValue(saved);
  vi.mocked(api.savePublication).mockResolvedValue(saved);
  const view = renderSettings();
  expect(await view.findByLabelText('공개 닉네임')).toHaveProperty('value', '초보');
  fireEvent.change(view.getByLabelText('공개 닉네임'), { target: { value: saved.displayName } });
  fireEvent.click(view.getByRole('button', { name: '프로필 만들기' }));
  await view.findByText('공개 설정을 저장했습니다.');
  expect(updateNickname).toHaveBeenCalledWith(saved.displayName);
  expect(api.getMyInvestorProfile).toHaveBeenCalledTimes(1);
});
