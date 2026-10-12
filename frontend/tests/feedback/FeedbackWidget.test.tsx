/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FeedbackWidget } from '../../src/features/feedback/FeedbackWidget';
import { createFeedback } from '../../src/features/feedback/api';

vi.mock('../../src/features/feedback/api', () => ({ createFeedback: vi.fn() }));

beforeEach(() => {
  vi.mocked(createFeedback).mockReset();
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

describe('피드백 위젯', () => {
  it('현재 화면과 입력 내용을 서버에 저장하고 완료 메시지를 표시한다', async () => {
    vi.mocked(createFeedback).mockResolvedValue(undefined);

    const view = render(
      <MemoryRouter initialEntries={['/news/3?from=home']}>
        <FeedbackWidget />
      </MemoryRouter>,
    );

    fireEvent.click(view.getByRole('button', { name: '피드백 보내기' }));
    fireEvent.change(view.getByLabelText('피드백 내용'), {
      target: { value: ' 기사 글씨를 조금 키워주세요. ' },
    });
    fireEvent.submit(view.getByLabelText('피드백 내용').closest('form')!);

    expect(await view.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' })).toBeTruthy();
    expect(createFeedback).toHaveBeenCalledWith('기사 글씨를 조금 키워주세요.', '/news/3');
  });

  it('공백만 입력하면 요청하지 않고 입력 안내를 표시한다', async () => {
    const view = render(
      <MemoryRouter>
        <FeedbackWidget />
      </MemoryRouter>,
    );

    fireEvent.click(view.getByRole('button', { name: '피드백 보내기' }));
    fireEvent.change(view.getByLabelText('피드백 내용'), { target: { value: '   ' } });
    fireEvent.submit(view.getByLabelText('피드백 내용').closest('form')!);

    await waitFor(() => expect(view.getByRole('alert').textContent).toContain('입력해 주세요'));
    expect(createFeedback).not.toHaveBeenCalled();
  });
});
