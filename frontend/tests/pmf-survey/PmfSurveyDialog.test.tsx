/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  const onSubmitAnswer = vi.fn<(answer: string) => Promise<void>>().mockResolvedValue(undefined);
  const onSubmitReason = vi.fn<(reason: string) => Promise<void>>().mockResolvedValue(undefined);
  const onDismiss = vi.fn();
  return {
    onSubmitAnswer,
    onSubmitReason,
    onDismiss,
    ...render(
      <PmfSurveyDialog
        onSubmitAnswer={onSubmitAnswer}
        onSubmitReason={onSubmitReason}
        onDismiss={onDismiss}
      />,
    ),
  };
}

describe('PMF 설문 팝업', () => {
  it('문항·선택지 순서를 유지하고 어떤 응답도 미리 선택하지 않는다', () => {
    const view = setup();
    expect(view.getByRole('heading', { name: PMF_QUESTION })).toBe(document.activeElement);
    expect(view.getAllByRole('radio').map((radio) => radio.parentElement?.textContent)).toEqual([
      '매우 실망',
      '약간 실망',
      '실망하지 않음',
      '더 이상 쓰지 않음',
    ]);
    expect(view.getAllByRole('radio').every((radio) => !(radio as HTMLInputElement).checked)).toBe(
      true,
    );
    expect((view.getByRole('button', { name: '응답 제출' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it('첫 응답이 저장되기 전에는 다음 단계로 이동하거나 중복 제출하지 않는다', async () => {
    const view = setup();
    let resolve!: () => void;
    view.onSubmitAnswer.mockImplementationOnce(() => new Promise((done) => (resolve = done)));
    fireEvent.click(view.getByRole('radio', { name: '약간 실망' }));
    const form = view.getByRole('button', { name: '응답 제출' }).closest('form')!;
    fireEvent.submit(form);
    fireEvent.submit(form);
    fireEvent(view.getByRole('dialog'), new Event('cancel', { cancelable: true }));
    expect(view.onSubmitAnswer).toHaveBeenCalledTimes(1);
    expect(view.onSubmitAnswer).toHaveBeenCalledWith('SOMEWHAT_DISAPPOINTED');
    expect(view.onDismiss).not.toHaveBeenCalled();
    expect(view.queryByRole('textbox')).toBeNull();
    await act(async () => resolve());
    expect(await view.findByText('응답이 저장되었어요')).toBeTruthy();
    expect(view.getByRole('textbox').getAttribute('maxlength')).toBe('500');
  });

  it('첫 저장 실패 시 선택을 유지하고 재시도한다', async () => {
    const view = setup();
    view.onSubmitAnswer.mockRejectedValueOnce(new Error('private-server-error'));
    fireEvent.click(view.getByRole('radio', { name: '매우 실망' }));
    fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
    expect((await view.findByRole('alert')).textContent).toContain('응답을 저장하지 못했어요');
    expect(view.queryByText('응답이 저장되었어요')).toBeNull();
    expect(view.queryByText('private-server-error')).toBeNull();
    expect((view.getByRole('radio', { name: '매우 실망' }) as HTMLInputElement).checked).toBe(true);
    fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
    await view.findByRole('textbox');
    expect(view.onSubmitAnswer).toHaveBeenCalledTimes(2);
  });

  it('추가 의견을 건너뛰어도 첫 응답을 취소하거나 다시 제출하지 않는다', async () => {
    const view = setup();
    fireEvent.click(view.getByRole('radio', { name: '더 이상 쓰지 않음' }));
    fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
    fireEvent.click(await view.findByRole('button', { name: '건너뛰기' }));
    expect(view.onDismiss).toHaveBeenCalledTimes(1);
    expect(view.onSubmitAnswer).toHaveBeenCalledTimes(1);
    expect(view.onSubmitReason).not.toHaveBeenCalled();
  });

  it('공백 의견은 제출하지 않고 의견 저장에 실패해도 첫 응답과 입력을 유지한다', async () => {
    const view = setup();
    fireEvent.click(view.getByRole('radio', { name: '실망하지 않음' }));
    fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
    const input = await view.findByRole('textbox');
    fireEvent.change(input, { target: { value: '   ' } });
    fireEvent.submit(input.closest('form')!);
    expect(view.onSubmitReason).not.toHaveBeenCalled();
    view.onSubmitReason.mockRejectedValueOnce(new Error('private-reason-error'));
    fireEvent.change(input, { target: { value: '  분석 결과를 자주 확인해요.  ' } });
    fireEvent.click(view.getByRole('button', { name: '의견 보내기' }));
    expect((await view.findByRole('alert')).textContent).toContain('먼저 제출한 응답은 저장');
    expect((view.getByRole('textbox') as HTMLTextAreaElement).value).toBe(
      '  분석 결과를 자주 확인해요.  ',
    );
    fireEvent.click(view.getByRole('button', { name: '의견 보내기' }));
    await view.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
    expect(view.onSubmitReason).toHaveBeenLastCalledWith('분석 결과를 자주 확인해요.');
    expect(view.onSubmitAnswer).toHaveBeenCalledTimes(1);
  });

  it('닫으면 스크롤과 기존 포커스를 복원하고 배경의 검색 단축키로 이벤트를 보내지 않는다', () => {
    const trigger = document.createElement('button');
    document.body.append(trigger);
    document.body.style.overflow = 'auto';
    trigger.focus();
    const view = setup();
    const onKey = vi.fn();
    document.addEventListener('keydown', onKey);
    fireEvent.keyDown(view.getByRole('heading'), { key: '/' });
    expect(onKey).not.toHaveBeenCalled();
    const skip = view.getByRole('button', { name: '나중에' });
    const close = view.getByRole('button', { name: '설문 닫기' });
    skip.focus();
    fireEvent.keyDown(skip, { key: 'Tab' });
    expect(document.activeElement).toBe(close);
    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(skip);
    fireEvent(view.getByRole('dialog'), new Event('cancel', { cancelable: true }));
    expect(view.onDismiss).toHaveBeenCalledOnce();
    view.unmount();
    expect(document.activeElement).toBe(trigger);
    expect(document.body.style.overflow).toBe('auto');
    document.removeEventListener('keydown', onKey);
    trigger.remove();
    document.body.style.overflow = '';
  });

  it('추가 의견 저장 중 닫기와 건너뛰기를 막고 같은 의견의 중복 전송을 막는다', async () => {
    const view = setup();
    fireEvent.click(view.getByRole('radio', { name: '매우 실망' }));
    fireEvent.click(view.getByRole('button', { name: '응답 제출' }));
    const input = await view.findByRole('textbox');
    let resolve!: () => void;
    view.onSubmitReason.mockImplementationOnce(() => new Promise((done) => (resolve = done)));
    fireEvent.change(input, { target: { value: '내 자산과 소식을 함께 볼 수 있어서요.' } });
    fireEvent.submit(input.closest('form')!);
    fireEvent.submit(input.closest('form')!);
    fireEvent(view.getByRole('dialog'), new Event('cancel', { cancelable: true }));
    expect(view.onSubmitReason).toHaveBeenCalledOnce();
    expect(view.onDismiss).not.toHaveBeenCalled();
    expect((view.getByRole('button', { name: '건너뛰기' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
    await act(async () => resolve());
    await waitFor(() => expect(view.getByRole('button', { name: '닫기' })).toBeTruthy());
  });
});
