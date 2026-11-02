/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { LeagueReviewReminder } from '../../src/features/league/LeagueReviewReminder';
import * as api from '../../src/features/league/api';
import type { InvestmentDecisionRecord } from '../../src/features/league/types';

vi.mock('../../src/features/league/api', () => ({
  getInvestmentDecisions: vi.fn(),
  createReview: vi.fn(),
  getReviewGuide: vi.fn(),
}));
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
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it.each([{ records: [] }, { records: [{ ...record, reviews: [review] }] }])(
  '미복기 기록이 없으면 고양이나 AI 요청을 만들지 않는다',
  async ({ records }) => {
    vi.mocked(api.getInvestmentDecisions).mockResolvedValue(records);
    const view = render(<LeagueReviewReminder />);
    await waitFor(() => expect(api.getInvestmentDecisions).toHaveBeenCalledOnce());
    expect(view.queryByRole('button', { name: '복기 도우미 열기' })).toBeNull();
    expect(api.getReviewGuide).not.toHaveBeenCalled();
  },
);

it('미복기 기록만 안내하고 한 번 눌러 작성·저장한 뒤 고양이를 없앤다', async () => {
  vi.mocked(api.getInvestmentDecisions).mockResolvedValue([
    { ...record, id: 6, reviews: [review] },
    record,
  ]);
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
  vi.mocked(api.getInvestmentDecisions).mockRejectedValue(new Error('offline'));
  const view = render(<LeagueReviewReminder />);
  await waitFor(() => expect(api.getInvestmentDecisions).toHaveBeenCalledOnce());
  expect(view.queryByRole('button', { name: '복기 도우미 열기' })).toBeNull();
});

it('기존 화면의 기록을 받으면 중복 조회 없이 미복기 기록만 사용한다', () => {
  const view = render(<LeagueReviewReminder records={null} />);
  expect(view.queryByRole('button', { name: '복기 도우미 열기' })).toBeNull();
  view.rerender(<LeagueReviewReminder records={[record]} />);
  expect(view.getByRole('button', { name: '복기 도우미 열기' })).toBeTruthy();
  view.rerender(<LeagueReviewReminder records={[{ ...record, reviews: [review] }]} />);
  expect(view.queryByRole('button', { name: '복기 도우미 열기' })).toBeNull();
  expect(api.getInvestmentDecisions).not.toHaveBeenCalled();
  expect(api.getReviewGuide).not.toHaveBeenCalled();
});
