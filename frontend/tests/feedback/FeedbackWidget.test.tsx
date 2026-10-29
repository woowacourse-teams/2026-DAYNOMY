/** @vitest-environment jsdom */
import { trackEvent } from '../../src/analytics';
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { FeedbackWidget } from '../../src/features/feedback/FeedbackWidget';
import { createAdminFeedback, createFeedback } from '../../src/features/feedback/api';

vi.mock('../../src/features/feedback/api', () => ({
  createAdminFeedback: vi.fn(),
  createFeedback: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(createFeedback).mockReset();
  vi.mocked(createAdminFeedback).mockReset();
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
  vi.mocked(trackEvent).mockClear();
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

  it('관리자 화면에서는 유형과 쿼리를 포함한 현재 경로를 저장한다', async () => {
    vi.mocked(createAdminFeedback).mockResolvedValue(undefined);
    const view = render(
      <MemoryRouter initialEntries={['/admin/news?tab=draft']}>
        <FeedbackWidget variant="admin" />
      </MemoryRouter>,
    );

    fireEvent.click(view.getByRole('button', { name: '피드백 보내기' }));
    fireEvent.change(view.getByLabelText('피드백 유형'), { target: { value: 'BUG' } });
    fireEvent.change(view.getByLabelText('피드백 내용'), {
      target: { value: '저장 버튼이 동작하지 않아요.' },
    });
    fireEvent.submit(view.getByLabelText('피드백 내용').closest('form')!);

    await view.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
    expect(createAdminFeedback).toHaveBeenCalledWith(
      'BUG',
      '저장 버튼이 동작하지 않아요.',
      '/admin/news?tab=draft',
    );
    expect(createFeedback).not.toHaveBeenCalled();
  });
});

it('제출 실패는 상태만 기록하고 본문은 분석 도구로 보내지 않으며 재시도 성공을 기록한다', async () => {
  vi.mocked(createFeedback)
    .mockRejectedValueOnce(new Error('private-error'))
    .mockResolvedValueOnce(undefined);
  const view = render(
    <MemoryRouter>
      <FeedbackWidget />
    </MemoryRouter>,
  );
  fireEvent.click(view.getByRole('button', { name: '피드백 보내기' }));
  const input = view.getByLabelText('피드백 내용');
  fireEvent.change(input, { target: { value: 'private-feedback' } });
  fireEvent.submit(input.closest('form')!);
  await view.findByRole('alert');
  expect(trackEvent).toHaveBeenCalledWith('feedback_submit_failed', {
    error_type: 'network_or_unknown',
    status_code: undefined,
  });
  expect(JSON.stringify(vi.mocked(trackEvent).mock.calls)).not.toContain('private-');
  fireEvent.submit(input.closest('form')!);
  await view.findByRole('heading', { name: '의견을 보내주셔서 감사합니다' });
  expect(trackEvent).toHaveBeenCalledWith('submit_feedback');
});
