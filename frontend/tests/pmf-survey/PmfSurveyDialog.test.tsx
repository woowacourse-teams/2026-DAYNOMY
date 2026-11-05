/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { PmfSurveyDialog } from '../../src/features/pmf-survey/PmfSurveyDialog';
import { PMF_QUESTION } from '../../src/features/pmf-survey/types';
beforeEach(() => {
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
  vi.restoreAllMocks();
});
function setup() {
  const onSubmit = vi
    .fn<(answer: string, reason: string | null) => Promise<void>>()
    .mockResolvedValue(undefined);
  const onHideToday = vi.fn<() => Promise<'saved' | 'local-only'>>().mockResolvedValue('saved');
  const onDismiss = vi.fn();
  return {
    onSubmit,
    onHideToday,
    onDismiss,
    ...render(
      <PmfSurveyDialog onSubmit={onSubmit} onHideToday={onHideToday} onDismiss={onDismiss} />,
    ),
  };
}
it('고정 문항과 순서를 유지하며 기본 선택과 두 번째 단계가 없다', () => {
  const v = setup();
  expect(v.getByRole('heading', { name: PMF_QUESTION })).toBe(document.activeElement);
  expect(v.getAllByRole('radio').map((r) => r.parentElement?.textContent)).toEqual([
    '매우 실망',
    '약간 실망',
    '실망하지 않음',
    '더 이상 쓰지 않음',
  ]);
  expect(v.getAllByRole('radio').some((r) => (r as HTMLInputElement).checked)).toBe(false);
  expect(v.queryByRole('textbox')).toBeNull();
  expect((v.getByRole('button', { name: '응답 보내기' }) as HTMLButtonElement).disabled).toBe(true);
});
it('의견 없이 한 번 제출하고 저장 중 중복 요청과 닫기를 막는다', async () => {
  const v = setup();
  let resolve!: () => void;
  v.onSubmit.mockImplementationOnce(() => new Promise((done) => (resolve = done)));
  fireEvent.click(v.getByRole('radio', { name: '약간 실망' }));
  const form = v.getByRole('button', { name: '응답 보내기' }).closest('form')!;
  fireEvent.submit(form);
  fireEvent.submit(form);
  fireEvent(v.getByRole('dialog'), new Event('cancel', { cancelable: true }));
  expect(v.onSubmit).toHaveBeenCalledExactlyOnceWith('SOMEWHAT_DISAPPOINTED', null);
  expect(v.onDismiss).not.toHaveBeenCalled();
  await act(async () => resolve());
  await v.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
  expect(v.queryByRole('textbox')).toBeNull();
});
it('선택 이유를 접어도 유지하고 응답과 함께 저장하며 실패 시 두 입력 모두 보존한다', async () => {
  const v = setup();
  const toggle = v.getByRole('button', { name: /그렇게 답하신/ });
  fireEvent.click(toggle);
  const input = v.getByRole('textbox');
  expect(input.getAttribute('maxlength')).toBe('500');
  fireEvent.change(input, { target: { value: '  내 자산의 소식을 볼 수 있어요.  ' } });
  fireEvent.click(toggle);
  expect(v.queryByRole('textbox')).toBeNull();
  fireEvent.click(toggle);
  expect((v.getByRole('textbox') as HTMLTextAreaElement).value).toContain('내 자산');
  fireEvent.click(v.getByRole('radio', { name: '매우 실망' }));
  v.onSubmit.mockRejectedValueOnce(new Error('private-server-message'));
  fireEvent.click(v.getByRole('button', { name: '응답 보내기' }));
  expect((await v.findByRole('alert')).textContent).toContain('응답을 저장하지 못했어요');
  expect(v.queryByText('private-server-message')).toBeNull();
  expect((v.getByRole('radio', { name: '매우 실망' }) as HTMLInputElement).checked).toBe(true);
  expect((v.getByRole('textbox') as HTMLTextAreaElement).value).toContain('내 자산');
  fireEvent.click(v.getByRole('button', { name: '응답 보내기' }));
  await v.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
  expect(v.onSubmit).toHaveBeenLastCalledWith(
    'VERY_DISAPPOINTED',
    '내 자산의 소식을 볼 수 있어요.',
  );
});
it('선택 이유가 공백이면 null로 보내며 닫기는 아무 응답도 보내지 않는다', async () => {
  const v = setup();
  fireEvent.click(v.getByRole('button', { name: /그렇게 답하신/ }));
  fireEvent.change(v.getByRole('textbox'), { target: { value: '   ' } });
  fireEvent.click(v.getByRole('radio', { name: '실망하지 않음' }));
  fireEvent.click(v.getByRole('button', { name: '응답 보내기' }));
  await v.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
  expect(v.onSubmit).toHaveBeenCalledWith('NOT_DISAPPOINTED', null);
  v.unmount();
  const closed = setup();
  fireEvent.click(closed.getByRole('button', { name: '닫기' }));
  expect(closed.onDismiss).toHaveBeenCalledOnce();
  expect(closed.onSubmit).not.toHaveBeenCalled();
  expect(closed.onHideToday).not.toHaveBeenCalled();
});
it('오늘 숨김은 제출과 구분하고 저장 실패를 안내하며 재시도할 수 있다', async () => {
  const v = setup();
  v.onHideToday.mockResolvedValueOnce('local-only');
  fireEvent.click(v.getByRole('button', { name: '오늘 하루 그만 보기' }));
  expect((await v.findByRole('alert')).textContent).toContain('이 브라우저에서는 오늘 숨겼어요');
  expect(v.onSubmit).not.toHaveBeenCalled();
  fireEvent.click(v.getByRole('button', { name: '오늘 하루 그만 보기' }));
  await act(async () => {});
  expect(v.onHideToday).toHaveBeenCalledTimes(2);
});
it('포커스 순환·Escape·스크롤 복원을 유지하고 배경 단축키는 차단한다', () => {
  const trigger = document.createElement('button');
  document.body.append(trigger);
  trigger.focus();
  document.body.style.overflow = 'auto';
  const v = setup(),
    listener = vi.fn();
  document.addEventListener('keydown', listener);
  fireEvent.keyDown(v.getByRole('heading'), { key: '/' });
  expect(listener).not.toHaveBeenCalled();
  const close = v.getByRole('button', { name: '닫기' }),
    x = v.getByRole('button', { name: '설문 닫기' });
  close.focus();
  fireEvent.keyDown(close, { key: 'Tab' });
  expect(document.activeElement).toBe(x);
  fireEvent.keyDown(x, { key: 'Tab', shiftKey: true });
  expect(document.activeElement).toBe(close);
  fireEvent(v.getByRole('dialog'), new Event('cancel', { cancelable: true }));
  expect(v.onDismiss).toHaveBeenCalledOnce();
  v.unmount();
  expect(document.activeElement).toBe(trigger);
  expect(document.body.style.overflow).toBe('auto');
  document.removeEventListener('keydown', listener);
  trigger.remove();
  document.body.style.overflow = '';
});
