/** @vitest-environment jsdom */
import { StrictMode } from 'react';
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { trackEvent } from '../../src/analytics';
import { LeagueReviewReminder } from '../../src/features/league/LeagueReviewReminder';
import * as api from '../../src/features/league/api';
import type { InvestmentDecisionRecord } from '../../src/features/league/types';

vi.mock('../../src/features/league/api', () => ({
  claimReviewReminder: vi.fn(),
  snoozeReviewReminder: vi.fn(),
  createReview: vi.fn(),
  getReviewGuide: vi.fn(),
}));
vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
const record: InvestmentDecisionRecord = {
  id: 7,
  assetId: 1,
  assetCode: 'DEMO',
  assetName: '가상전자',
  category: 'STOCK',
  transactionType: 'HOLD',
  quantity: 1,
  unitPrice: 1,
  fee: 0,
  tradedOn: '2026-10-17',
  reason: '성장 기대',
  expectedHoldingPeriod: null,
  expectedChange: '',
  invalidationCondition: '',
  maximumAcceptableLossRate: null,
  writtenAfterTrade: false,
  reviews: [],
};
const review = {
  id: 2,
  actualResult: '결과',
  differenceFromExpectation: '차이',
  nextAction: '확인',
  createdAt: '2026-10-29T00:00:00Z',
};
const nextNotificationAt = '2026-11-09T00:00:00Z';
beforeEach(() => {
  vi.mocked(api.claimReviewReminder).mockImplementation(async (requestKey) => ({
    requestKey,
    transactions: [record],
    nextNotificationAt,
  }));
  vi.mocked(api.snoozeReviewReminder).mockResolvedValue(nextNotificationAt);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it('서버가 아직 알림 대상이 아니라고 하면 로컬 기록이 있어도 안내하지 않는다', async () => {
  vi.mocked(api.claimReviewReminder).mockResolvedValue({
    requestKey: null,
    transactions: [],
    nextNotificationAt,
  });
  const view = render(<LeagueReviewReminder records={[record]} />);
  await waitFor(() => expect(api.claimReviewReminder).toHaveBeenCalledOnce());
  expect(view.queryByRole('button', { name: '복기 도우미 열기' })).toBeNull();
  expect(api.getReviewGuide).not.toHaveBeenCalled();
  expect(trackEvent).not.toHaveBeenCalled();
});

it('미복기 기록만 안내하고 한 번 눌러 작성·저장한 뒤 고양이를 없앤다', async () => {
  vi.mocked(api.createReview).mockResolvedValue(review);
  vi.mocked(api.getReviewGuide).mockImplementation(() => new Promise(() => {}));
  const view = render(<LeagueReviewReminder />);
  fireEvent.click(await view.findByRole('button', { name: '바로 작성하기' }));
  expect(view.getByRole('textbox', { name: '실제 결과' })).toBeTruthy();
  expect(api.getReviewGuide).toHaveBeenCalledOnce();
  for (const [label, value] of [
    ['실제 결과', '결과'],
    ['예상과 달랐던 점', '차이'],
    ['다음 행동', '확인'],
  ]) {
    fireEvent.change(view.getByRole('textbox', { name: label }), { target: { value } });
  }
  fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
  await view.findByText('복기를 남겼어요.');
  expect(api.createReview).toHaveBeenCalledWith(7, {
    actualResult: '결과',
    differenceFromExpectation: '차이',
    nextAction: '확인',
  });
  fireEvent.click(view.getByRole('button', { name: '기록으로 돌아가기' }));
  expect(view.queryByRole('complementary', { name: '투자 복기 도우미' })).toBeNull();
});

it('조회 장애를 미복기 기록으로 오인해 알리지 않는다', async () => {
  vi.mocked(api.claimReviewReminder).mockRejectedValue(new Error('offline'));
  const view = render(<LeagueReviewReminder />);
  await waitFor(() => expect(api.claimReviewReminder).toHaveBeenCalledOnce());
  expect(view.queryByRole('button', { name: '복기 도우미 열기' })).toBeNull();
});

it('화면에서 별도로 복기한 기록은 알림에서 제외한다', async () => {
  const view = render(<LeagueReviewReminder records={[record]} />);
  await view.findByRole('button', { name: '바로 작성하기' });
  view.rerender(<LeagueReviewReminder records={[{ ...record, reviews: [review] }]} />);
  expect(view.queryByRole('button', { name: '복기 도우미 열기' })).toBeNull();
  expect(api.claimReviewReminder).toHaveBeenCalledOnce();
  expect(api.getReviewGuide).not.toHaveBeenCalled();
});

it('StrictMode의 중단된 재요청은 같은 요청 키를 사용하고 노출은 한 번만 집계한다', async () => {
  const view = render(
    <StrictMode>
      <LeagueReviewReminder entryPoint="holdings" />
    </StrictMode>,
  );
  await view.findByRole('button', { name: '바로 작성하기' });
  const calls = vi.mocked(api.claimReviewReminder).mock.calls;
  expect(calls).toHaveLength(2);
  expect(calls[0][0]).toBe(calls[1][0]);
  expect(calls[0][1]?.aborted).toBe(true);
  expect(trackEvent).toHaveBeenCalledExactlyOnceWith('view_league_review_reminder', {
    surface: 'review_coach',
    entry_point: 'holdings',
    result_count: 1,
  });
  expect(api.getReviewGuide).not.toHaveBeenCalled();
});

it('알림 닫기는 서버에 7일 미루기를 저장하고 고양이로 수동 작성은 가능하다', async () => {
  vi.mocked(api.getReviewGuide).mockImplementation(() => new Promise(() => {}));
  const view = render(<LeagueReviewReminder entryPoint="source_holdings" />);
  fireEvent.click(await view.findByRole('button', { name: '복기 알림 닫기' }));
  await waitFor(() => expect(view.queryByRole('button', { name: '바로 작성하기' })).toBeNull());
  expect(document.activeElement).toBe(view.getByRole('button', { name: '복기 도우미 열기' }));
  expect(api.snoozeReviewReminder).toHaveBeenCalledWith(
    vi.mocked(api.claimReviewReminder).mock.calls[0][0],
  );
  expect(trackEvent).toHaveBeenCalledWith('league_review_reminder_action', {
    surface: 'review_coach',
    entry_point: 'source_holdings',
    action: 'dismiss_reminder',
    target: 'bubble',
    status: 'success',
  });
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  expect(view.getByRole('textbox', { name: '실제 결과' })).toBeTruthy();
  expect(trackEvent).toHaveBeenCalledWith('league_review_reminder_action', {
    surface: 'review_coach',
    entry_point: 'source_holdings',
    action: 'open_review',
    target: 'cat',
    status: 'clicked',
  });
});

it('미루기 저장 실패에도 알림은 즉시 닫고 오류를 별도로 안내해 재시도한다', async () => {
  vi.mocked(api.snoozeReviewReminder)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValue(nextNotificationAt);
  const view = render(<LeagueReviewReminder />);
  fireEvent.click(await view.findByRole('button', { name: '복기 알림 닫기' }));
  expect(view.queryByRole('button', { name: '바로 작성하기' })).toBeNull();
  expect((await view.findByRole('alert')).textContent).toContain('저장하지 못했어요');
  expect(trackEvent).toHaveBeenCalledWith(
    'league_review_reminder_action',
    expect.objectContaining({ status: 'error' }),
  );
  expect(
    vi
      .mocked(trackEvent)
      .mock.calls.some(
        ([name, params]) =>
          name === 'league_review_reminder_action' && params?.status === 'success',
      ),
  ).toBe(false);
  fireEvent.click(view.getByRole('button', { name: '알림 미루기 다시 시도' }));
  await waitFor(() => expect(view.queryByRole('alert')).toBeNull());
  await waitFor(() =>
    expect(trackEvent).toHaveBeenCalledWith(
      'league_review_reminder_action',
      expect.objectContaining({ target: 'bubble', status: 'success' }),
    ),
  );
  expect(api.snoozeReviewReminder).toHaveBeenCalledTimes(2);
});

it('보이지 않는 탭에서는 알림 기회를 소비하지 않고 탭이 보일 때 요청한다', async () => {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('hidden');
  const view = render(<LeagueReviewReminder />);
  expect(api.claimReviewReminder).not.toHaveBeenCalled();
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue('visible');
  fireEvent(document, new Event('visibilitychange'));
  await view.findByRole('button', { name: '바로 작성하기' });
  expect(api.claimReviewReminder).toHaveBeenCalledOnce();
  vi.restoreAllMocks();
});

it('나중에 미루기를 저장한 뒤 접어도 입력 초안을 유지한다', async () => {
  vi.mocked(api.getReviewGuide).mockImplementation(() => new Promise(() => {}));
  const view = render(<LeagueReviewReminder />);
  fireEvent.click(await view.findByRole('button', { name: '바로 작성하기' }));
  fireEvent.change(view.getByRole('textbox', { name: '실제 결과' }), {
    target: { value: '작성 중인 초안' },
  });
  fireEvent.click(view.getByRole('button', { name: '나중에' }));
  await waitFor(() => expect(view.queryByRole('dialog')).toBeNull());
  expect(api.snoozeReviewReminder).toHaveBeenCalledOnce();
  expect(trackEvent).toHaveBeenCalledWith(
    'league_review_reminder_action',
    expect.objectContaining({ target: 'later', status: 'success' }),
  );
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  expect(view.getByRole('textbox', { name: '실제 결과' })).toHaveProperty(
    'value',
    '작성 중인 초안',
  );
  expect(api.getReviewGuide).toHaveBeenCalledOnce();
});

it('작성 중 목록이 다른 기기의 복기 완료 상태로 갱신돼도 현재 초안을 유지한다', async () => {
  vi.mocked(api.getReviewGuide).mockImplementation(() => new Promise(() => {}));
  const view = render(<LeagueReviewReminder records={[record]} />);
  fireEvent.click(await view.findByRole('button', { name: '바로 작성하기' }));
  fireEvent.change(view.getByRole('textbox', { name: '실제 결과' }), {
    target: { value: '목록 갱신 중에도 유지할 초안' },
  });
  view.rerender(<LeagueReviewReminder records={[{ ...record, reviews: [review] }]} />);
  expect(view.getByRole('textbox', { name: '실제 결과' })).toHaveProperty(
    'value',
    '목록 갱신 중에도 유지할 초안',
  );
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 접기' }));
  expect(view.queryByRole('dialog')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  expect(view.getByRole('textbox', { name: '실제 결과' })).toHaveProperty(
    'value',
    '목록 갱신 중에도 유지할 초안',
  );
  expect(api.getReviewGuide).toHaveBeenCalledOnce();
});
