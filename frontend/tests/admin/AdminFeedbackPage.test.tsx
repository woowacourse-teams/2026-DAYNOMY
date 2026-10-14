/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AdminFeedbackPage } from '../../src/features/admin/AdminFeedbackPage';
import {
  deleteAdminFeedback,
  getAdminFeedbacks,
  updateAdminFeedbackStatus,
} from '../../src/features/feedback/api';

vi.mock('../../src/features/feedback/api', () => ({
  deleteAdminFeedback: vi.fn(),
  getAdminFeedbacks: vi.fn(),
  updateAdminFeedbackStatus: vi.fn(),
}));

const feedback = {
  id: 1,
  content: '기사 글씨를 조금 키워주세요.',
  pagePath: '/news/3',
  status: 'PENDING' as const,
  createdAt: '2026-10-12T09:00:00Z',
  updatedAt: '2026-10-12T09:00:00Z',
};

beforeEach(() => {
  vi.mocked(getAdminFeedbacks).mockReset();
  vi.mocked(updateAdminFeedbackStatus).mockReset();
  vi.mocked(deleteAdminFeedback).mockReset();
  vi.mocked(getAdminFeedbacks).mockResolvedValue({
    items: [feedback],
    page: 1,
    size: 15,
    totalPages: 1,
    totalElements: 1,
    hasNext: false,
  });
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('관리자 피드백 화면', () => {
  it('접수된 피드백을 표시하고 처리완료로 변경한다', async () => {
    vi.mocked(updateAdminFeedbackStatus).mockResolvedValue({
      ...feedback,
      status: 'RESOLVED',
    });
    const view = render(<AdminFeedbackPage />);

    expect(await view.findByText('기사 글씨를 조금 키워주세요.')).toBeTruthy();
    fireEvent.click(view.getByRole('button', { name: '완료 처리' }));

    await waitFor(() => expect(updateAdminFeedbackStatus).toHaveBeenCalledWith(1, 'RESOLVED'));
  });

  it('삭제 확인 후 피드백을 삭제한다', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(deleteAdminFeedback).mockResolvedValue(undefined);
    const view = render(<AdminFeedbackPage />);

    await view.findByText('기사 글씨를 조금 키워주세요.');
    fireEvent.click(view.getByRole('button', { name: '삭제' }));

    await waitFor(() => expect(deleteAdminFeedback).toHaveBeenCalledWith(1));
  });
});
