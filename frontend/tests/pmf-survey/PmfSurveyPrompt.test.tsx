/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthContext, type AuthContextValue } from '../../src/auth/AuthContext';
import { PmfSurveyPrompt } from '../../src/features/pmf-survey/PmfSurveyPrompt';
import {
  getCurrentPmfSurvey,
  submitPmfAnswer,
  submitPmfReason,
} from '../../src/features/pmf-survey/api';

vi.mock('../../src/features/pmf-survey/api', () => ({
  getCurrentPmfSurvey: vi.fn(),
  submitPmfAnswer: vi.fn(),
  submitPmfReason: vi.fn(),
}));

const guest: AuthContextValue = { isLoggedIn: false, memberId: null, loading: false, role: null };

beforeEach(() => {
  sessionStorage.clear();
  vi.mocked(getCurrentPmfSurvey).mockResolvedValue({
    surveyId: 805,
    eligible: true,
    responseId: null,
  });
  vi.mocked(submitPmfAnswer).mockResolvedValue(21);
  vi.mocked(submitPmfReason).mockResolvedValue(undefined);
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
});

function setup(auth = guest, path = '/') {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[path]}>
        <PmfSurveyPrompt />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
}

it('서버가 허용한 일반 사용자에게만 노출하고 응답 ID로 추가 의견을 연결한다', async () => {
  const view = setup();
  fireEvent.click(await view.findByRole('radio', { name: '매우 실망' }));
  fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
  const input = await view.findByRole('textbox');
  expect(submitPmfAnswer).toHaveBeenCalledWith(805, 'VERY_DISAPPOINTED', expect.any(AbortSignal));
  fireEvent.change(input, { target: { value: ' 관련 소식을 볼 수 있어서요. ' } });
  fireEvent.click(view.getByRole('button', { name: '의견 보내기' }));
  await view.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
  expect(submitPmfReason).toHaveBeenCalledWith(
    805,
    21,
    '관련 소식을 볼 수 있어서요.',
    expect.any(AbortSignal),
  );
});

it('대상이 아니거나 이미 응답했거나 API가 없거나 장애이면 팝업을 띄우지 않는다', async () => {
  for (const status of [
    null,
    { surveyId: 805, eligible: false, responseId: null },
    { surveyId: 805, eligible: true, responseId: 21 },
  ]) {
    vi.mocked(getCurrentPmfSurvey).mockResolvedValueOnce(status);
    const view = setup();
    await act(async () => {});
    expect(view.queryByRole('dialog')).toBeNull();
    view.unmount();
  }
  vi.mocked(getCurrentPmfSurvey).mockRejectedValueOnce(new Error('unavailable'));
  const view = setup();
  await act(async () => {});
  expect(view.queryByRole('dialog')).toBeNull();
});

it('관리자·로그인 확인 중·다른 경로에서는 대상 API를 호출하지 않는다', async () => {
  for (const [auth, path] of [
    [{ ...guest, isLoggedIn: true, memberId: 1, role: 'ADMIN' }, '/'],
    [{ ...guest, loading: true }, '/'],
    [guest, '/login'],
    [guest, '/welcome'],
    [guest, '/admin/news'],
    [guest, '/news'],
  ] as Array<[AuthContextValue, string]>) {
    const view = setup(auth, path);
    await act(async () => {});
    expect(view.queryByRole('dialog')).toBeNull();
    view.unmount();
  }
  expect(getCurrentPmfSurvey).not.toHaveBeenCalled();
});

it('다른 팝업이 열려 있으면 닫힐 때까지 기다린다', async () => {
  const otherDialog = document.createElement('dialog');
  document.body.append(otherDialog);
  otherDialog.showModal();
  const view = setup();
  await act(async () => {});
  expect(view.queryByRole('heading')).toBeNull();
  await act(async () => otherDialog.close());
  expect(await view.findByRole('radio', { name: '매우 실망' })).toBeTruthy();
  otherDialog.remove();
});

it('나중에를 누르면 같은 방문에서 다시 띄우지 않으며 다른 계정에는 적용하지 않는다', async () => {
  const view = setup();
  fireEvent.click(await view.findByRole('button', { name: '나중에' }));
  expect(view.queryByRole('dialog')).toBeNull();
  view.unmount();
  const again = setup();
  await act(async () => {});
  expect(again.queryByRole('dialog')).toBeNull();
  again.unmount();
  const member = setup({ ...guest, isLoggedIn: true, memberId: 5, role: 'USER' });
  expect(await member.findByRole('dialog')).toBeTruthy();
});

it('첫 응답 후 추가 의견을 건너뛰어도 새로 방문했을 때 응답을 다시 요구하지 않는다', async () => {
  const view = setup();
  fireEvent.click(await view.findByRole('radio', { name: '약간 실망' }));
  fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
  fireEvent.click(await view.findByRole('button', { name: '건너뛰기' }));
  view.unmount();
  const again = setup();
  await act(async () => {});
  expect(again.queryByRole('dialog')).toBeNull();
  expect(submitPmfAnswer).toHaveBeenCalledOnce();
  expect(submitPmfReason).not.toHaveBeenCalled();
});

it('대상 조회를 중단한 뒤 도착한 응답으로 팝업을 열지 않는다', async () => {
  let resolve!: (status: { surveyId: number; eligible: boolean; responseId: null }) => void;
  vi.mocked(getCurrentPmfSurvey).mockImplementationOnce(
    () => new Promise((done) => (resolve = done)),
  );
  const view = setup();
  const signal = vi.mocked(getCurrentPmfSurvey).mock.calls[0]?.[0];
  view.unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => resolve({ surveyId: 805, eligible: true, responseId: null }));
  expect(document.querySelector('.pmf-dialog')).toBeNull();
});

it('계정을 전환하면 이전 계정의 저장 요청을 중단하고 늦은 응답을 새 화면에 반영하지 않는다', async () => {
  let resolve!: (id: number) => void;
  vi.mocked(submitPmfAnswer).mockImplementationOnce(() => new Promise((done) => (resolve = done)));
  const view = setup();
  fireEvent.click(await view.findByRole('radio', { name: '약간 실망' }));
  fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
  const signal = vi.mocked(submitPmfAnswer).mock.calls[0]?.[2];

  view.rerender(
    <AuthContext.Provider value={{ ...guest, isLoggedIn: true, memberId: 5, role: 'USER' }}>
      <MemoryRouter>
        <PmfSurveyPrompt />
      </MemoryRouter>
    </AuthContext.Provider>,
  );
  expect(signal?.aborted).toBe(true);
  await act(async () => resolve(21));
  expect(await view.findByRole('radio', { name: '약간 실망' })).toBeTruthy();
  expect(view.queryByRole('textbox')).toBeNull();
  expect(sessionStorage.getItem('daynomy:pmf-survey:v1:805:guest:dismissed')).toBeNull();
});

it('추가 의견 저장 중 화면을 떠나면 요청을 중단하고 첫 응답의 저장 표시는 유지한다', async () => {
  let resolve!: () => void;
  vi.mocked(submitPmfReason).mockImplementationOnce(() => new Promise((done) => (resolve = done)));
  const view = setup();
  fireEvent.click(await view.findByRole('radio', { name: '매우 실망' }));
  fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
  const input = await view.findByRole('textbox');
  fireEvent.change(input, { target: { value: '내 자산에 관련된 소식을 볼 수 있어서요.' } });
  fireEvent.click(view.getByRole('button', { name: '의견 보내기' }));
  const signal = vi.mocked(submitPmfReason).mock.calls[0]?.[3];
  view.unmount();
  expect(signal?.aborted).toBe(true);
  await act(async () => resolve());
  expect(sessionStorage.getItem('daynomy:pmf-survey:v1:805:guest:dismissed')).toBe('true');
  expect(submitPmfAnswer).toHaveBeenCalledOnce();
});
