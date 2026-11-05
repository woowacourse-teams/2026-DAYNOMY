/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { Link, MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../../src/auth/AuthContext';
import { PmfSurveyPrompt } from '../../src/features/pmf-survey/PmfSurveyPrompt';
import {
  getCurrentPmfSurvey,
  hidePmfSurveyToday,
  submitPmfResponse,
} from '../../src/features/pmf-survey/api';
import {
  nextKoreanMidnight,
  readSurveyPreference,
  surveyStorageKey,
} from '../../src/features/pmf-survey/storage';
vi.mock('../../src/features/pmf-survey/api', () => ({
  getCurrentPmfSurvey: vi.fn(),
  hidePmfSurveyToday: vi.fn(),
  submitPmfResponse: vi.fn(),
}));
const guest: AuthContextValue = { isLoggedIn: false, memberId: null, loading: false, role: null };
const status = {
  surveyId: 805,
  eligible: true,
  responseId: null,
  isFirstVisit: false,
  hiddenUntil: null,
};
beforeEach(() => {
  localStorage.clear();
  sessionStorage.clear();
  vi.mocked(getCurrentPmfSurvey).mockResolvedValue(status);
  vi.mocked(hidePmfSurveyToday).mockResolvedValue(undefined);
  vi.mocked(submitPmfResponse).mockResolvedValue(21);
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
    configurable: true,
    value() {
      this.setAttribute('open', '');
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, 'close', {
    configurable: true,
    value() {
      this.removeAttribute('open');
      this.dispatchEvent(new Event('close'));
    },
  });
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
  vi.restoreAllMocks();
  vi.useRealTimers();
});
function setup(auth = guest, path = '/') {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <nav>
          <Link to="/">대시보드로</Link>
          <Link to="/news">소식으로</Link>
        </nav>
        <PmfSurveyPrompt />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}
it('응답과 선택 이유를 한 요청으로 저장하고 완료 후 재노출하지 않는다', async () => {
  const v = setup();
  fireEvent.click(await v.findByRole('radio', { name: '매우 실망' }));
  fireEvent.change(v.getByRole('textbox'), { target: { value: ' 이유입니다. ' } });
  fireEvent.click(v.getByRole('button', { name: '응답 보내기' }));
  await v.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
  expect(submitPmfResponse).toHaveBeenCalledExactlyOnceWith(
    805,
    'VERY_DISAPPOINTED',
    '이유입니다.',
    expect.any(AbortSignal),
  );
  fireEvent.click(v.getByRole('button', { name: '닫기' }));
  v.unmount();
  const again = setup();
  await act(async () => {});
  expect(again.queryByRole('dialog')).toBeNull();
});
it('닫기는 현재 화면에서만 유지하며 데이터 재렌더링에는 열지 않고 대시보드 재진입 시 다시 연다', async () => {
  const v = setup();
  fireEvent.click(await v.findByRole('button', { name: '닫기' }));
  expect(v.queryByRole('dialog')).toBeNull();
  fireEvent(document, new Event('visibilitychange'));
  await act(async () => {});
  expect(v.queryByRole('dialog')).toBeNull();
  fireEvent.click(v.getByRole('link', { name: '소식으로' }));
  expect(v.queryByRole('dialog')).toBeNull();
  fireEvent.click(v.getByRole('link', { name: '대시보드로' }));
  expect(await v.findByRole('dialog')).toBeTruthy();
});
it('닫은 뒤 새로 불러오면 같은 날에도 노출한다', async () => {
  const v = setup();
  fireEvent.click(await v.findByRole('button', { name: '설문 닫기' }));
  v.unmount();
  const again = setup();
  expect(await again.findByRole('dialog')).toBeTruthy();
  expect(hidePmfSurveyToday).not.toHaveBeenCalled();
});
it('오늘 숨김은 새 탭·재진입에도 유지하고 KST 자정부터 새 진입에서 다시 표시한다', async () => {
  const now = Date.parse('2026-11-05T23:59:00+09:00');
  vi.spyOn(Date, 'now').mockReturnValue(now);
  const v = setup();
  fireEvent.click(await v.findByRole('button', { name: '오늘 하루 그만 보기' }));
  await act(async () => {});
  expect(v.queryByRole('dialog')).toBeNull();
  expect(readSurveyPreference(surveyStorageKey(805, null)).hiddenUntil).toBe(
    Date.parse('2026-11-06T00:00:00+09:00'),
  );
  v.unmount();
  const sameDay = setup();
  await act(async () => {});
  expect(sameDay.queryByRole('dialog')).toBeNull();
  vi.mocked(Date.now).mockReturnValue(nextKoreanMidnight(now));
  fireEvent(document, new Event('visibilitychange'));
  await act(async () => {});
  expect(sameDay.queryByRole('dialog')).toBeNull();
  sameDay.unmount();
  const nextDay = setup();
  expect(await nextDay.findByRole('dialog')).toBeTruthy();
  vi.restoreAllMocks();
});
it('첫 방문자는 최초 진입을 보류하고 실제 서비스 화면을 방문한 뒤 복귀하면 표시한다', async () => {
  vi.mocked(getCurrentPmfSurvey).mockResolvedValue({ ...status, isFirstVisit: true });
  const v = setup();
  await act(async () => {});
  expect(v.queryByRole('dialog')).toBeNull();
  fireEvent.click(v.getByRole('link', { name: '소식으로' }));
  fireEvent.click(v.getByRole('link', { name: '대시보드로' }));
  expect(await v.findByRole('dialog')).toBeTruthy();
});
it('서버 숨김·기응답·비대상·장애에는 표시하지 않는다', async () => {
  for (const current of [
    null,
    { ...status, eligible: false },
    { ...status, responseId: 21 },
    { ...status, hiddenUntil: new Date(Date.now() + 3600000).toISOString() },
  ]) {
    vi.mocked(getCurrentPmfSurvey).mockResolvedValueOnce(current);
    const v = setup();
    await act(async () => {});
    expect(v.queryByRole('dialog')).toBeNull();
    v.unmount();
  }
  vi.mocked(getCurrentPmfSurvey).mockRejectedValueOnce(new Error('offline'));
  const v = setup();
  await act(async () => {});
  expect(v.queryByRole('dialog')).toBeNull();
});
it('관리자·인증 확인 중·다른 경로에서는 조회하지 않는다', async () => {
  for (const [auth, path] of [
    [{ ...guest, role: 'ADMIN' }, '/'],
    [{ ...guest, loading: true }, '/'],
    [guest, '/login'],
    [guest, '/news'],
  ] as Array<[AuthContextValue, string]>) {
    const v = setup(auth, path);
    await act(async () => {});
    expect(v.queryByRole('dialog')).toBeNull();
    v.unmount();
  }
  expect(getCurrentPmfSurvey).not.toHaveBeenCalled();
});
it('다른 모달이 닫힐 때까지 기다린다', async () => {
  const other = document.createElement('dialog');
  document.body.append(other);
  other.showModal();
  const v = setup();
  await act(async () => {});
  expect(v.queryByRole('heading')).toBeNull();
  await act(async () => other.close());
  expect(await v.findByRole('radio', { name: '매우 실망' })).toBeTruthy();
  other.remove();
});
it('계정 전환 시 이전 저장을 중단하고 늦은 응답을 반영하지 않는다', async () => {
  let resolve!: (id: number) => void;
  vi.mocked(submitPmfResponse).mockImplementationOnce(
    () => new Promise((done) => (resolve = done)),
  );
  const v = setup();
  fireEvent.click(await v.findByRole('radio', { name: '약간 실망' }));
  fireEvent.click(v.getByRole('button', { name: '응답 보내기' }));
  const signal = vi.mocked(submitPmfResponse).mock.calls[0]?.[3];
  v.rerender(
    <AuthContext.Provider value={{ ...guest, isLoggedIn: true, memberId: 5, role: 'USER' }}>
      <MemoryRouter>
        <PmfSurveyPrompt />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
  expect(signal?.aborted).toBe(true);
  await act(async () => resolve(21));
  expect(await v.findByRole('radio', { name: '약간 실망' })).toBeTruthy();
  expect(readSurveyPreference(surveyStorageKey(805, null)).answered).toBe(false);
});
it('다른 탭에서 오늘 숨김을 설정하면 열린 팝업도 닫고 계정별로 분리한다', async () => {
  const v = setup();
  await v.findByRole('dialog');
  const key = surveyStorageKey(805, null);
  localStorage.setItem(key, JSON.stringify({ hiddenUntil: nextKoreanMidnight() }));
  fireEvent(window, new StorageEvent('storage', { key }));
  expect(v.queryByRole('dialog')).toBeNull();
  v.unmount();
  const member = setup({ ...guest, isLoggedIn: true, memberId: 5, role: 'USER' });
  expect(await member.findByRole('dialog')).toBeTruthy();
});
it('오늘 숨김 서버 저장 실패를 안내하며 브라우저 숨김은 유지한다', async () => {
  vi.mocked(hidePmfSurveyToday).mockRejectedValueOnce(new Error('offline'));
  const v = setup();
  fireEvent.click(await v.findByRole('button', { name: '오늘 하루 그만 보기' }));
  expect((await v.findByRole('alert')).textContent).toContain('이 브라우저에서는 오늘 숨겼어요');
  fireEvent.click(v.getByRole('button', { name: '닫기' }));
  v.unmount();
  const again = setup();
  await act(async () => {});
  expect(again.queryByRole('dialog')).toBeNull();
});
