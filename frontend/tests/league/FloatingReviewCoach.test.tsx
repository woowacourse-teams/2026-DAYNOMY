/** @vitest-environment jsdom */
import { act, cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { StrictMode } from 'react';
import { FloatingReviewCoach } from '../../src/features/league/FloatingReviewCoach';
import * as api from '../../src/features/league/api';
import type { InvestmentDecisionRecord, ReviewGuide } from '../../src/features/league/types';

vi.mock('../../src/analytics', () => ({ trackEvent: vi.fn() }));
vi.mock('../../src/features/league/api', () => ({
  getReviewGuide: vi.fn(),
  getInvestmentDecisions: vi.fn(),
  createReview: vi.fn(),
  updateReview: vi.fn(),
  updateDecisionReason: vi.fn(),
}));
const record: InvestmentDecisionRecord = {
  id: 7,
  assetId: 1,
  assetCode: '005930',
  assetName: '삼성전자',
  category: 'STOCK',
  transactionType: 'HOLD',
  quantity: 1,
  unitPrice: 1,
  fee: 0,
  tradedOn: '2026-10-23',
  reason: '실적 개선 기대',
  expectedHoldingPeriod: null,
  expectedChange: '영업이익 개선',
  invalidationCondition: '',
  maximumAcceptableLossRate: null,
  writtenAfterTrade: false,
  reviews: [],
};
const review = {
  id: 2,
  actualResult: '결과',
  differenceFromExpectation: '차이',
  nextAction: '기준',
  createdAt: '2026-10-29T00:00:00Z',
};
const guide: ReviewGuide = {
  transactionId: 7,
  retrievalStatus: 'FOUND',
  basis: null,
  evidence: [
    {
      id: 'NEWS:900001',
      title: '가상 참고 기사',
      publishedAt: '2026-10-28T00:00:00Z',
      excerpt: '가상 자료',
      href: '/news/900001',
    },
  ],
  questions: {
    actualResult: { text: '어떤 결과를 확인했나요?', evidenceIds: ['NEWS:900001'] },
    differenceFromExpectation: { text: '예상과 어떤 차이가 있었나요?', evidenceIds: [] },
    nextAction: { text: '다음에 무엇을 확인할까요?', evidenceIds: [] },
  },
};
beforeEach(() => {
  vi.mocked(api.getReviewGuide).mockResolvedValue(guide);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it('고양이에서 기존 InvestmentRecords 작성 폼 하나를 열고 검증 후 기존 API로 저장한다', async () => {
  vi.mocked(api.createReview).mockResolvedValue(review);
  const onSaved = vi.fn();
  const view = render(<FloatingReviewCoach record={record} reminderCount={1} onSaved={onSaved} />);
  expect(view.queryByRole('dialog')).toBeNull();
  expect(api.getReviewGuide).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '바로 작성하기' }));
  expect(view.getAllByRole('textbox')).toHaveLength(3);
  expect(view.getByRole('dialog').querySelectorAll('form.shared-edit-form')).toHaveLength(1);
  expect(document.activeElement).toBe(view.getByRole('heading', { name: '삼성전자 복기' }));
  const form = view.getByRole('textbox', { name: '실제 결과' }).closest('form')!;
  fireEvent.submit(form);
  expect(view.getByRole('alert').textContent).toContain('모두 작성');
  expect(document.activeElement).toBe(view.getByRole('textbox', { name: '실제 결과' }));
  expect(api.createReview).not.toHaveBeenCalled();
  for (const [label, value] of [
    ['실제 결과', '결과'],
    ['예상과 달랐던 점', '차이'],
    ['다음 행동', '기준'],
  ])
    fireEvent.change(view.getByRole('textbox', { name: label! }), { target: { value } });
  fireEvent.submit(form);
  await view.findByText('복기를 남겼어요.');
  expect(api.createReview).toHaveBeenCalledWith(7, {
    actualResult: '결과',
    differenceFromExpectation: '차이',
    nextAction: '기준',
  });
  expect(onSaved).toHaveBeenCalledWith({ ...record, reviews: [review] });
  expect(api.getReviewGuide).toHaveBeenCalledOnce();
});

it('AI 조회·저장 실패와 기사 없음에서도 기존 폼과 초안을 유지해 재시도한다', async () => {
  const noEvidence = {
    ...guide,
    retrievalStatus: 'NO_EVIDENCE' as const,
    evidence: [],
    questions: {
      actualResult: { text: '결과는 무엇인가요?', evidenceIds: [] },
      differenceFromExpectation: { text: '차이는 무엇인가요?', evidenceIds: [] },
      nextAction: { text: '다음 기준은 무엇인가요?', evidenceIds: [] },
    },
  };
  vi.mocked(api.getReviewGuide)
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValue(noEvidence);
  vi.mocked(api.createReview)
    .mockRejectedValueOnce(new Error('저장 실패'))
    .mockResolvedValue(review);
  const view = render(<FloatingReviewCoach record={record} onSaved={vi.fn()} />);
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  fireEvent.change(view.getByRole('textbox', { name: '실제 결과' }), { target: { value: '결과' } });
  expect((await view.findByRole('alert')).textContent).toContain('직접 복기를 작성');
  expect(view.getByRole('textbox', { name: '실제 결과' })).toHaveProperty('value', '결과');
  fireEvent.click(view.getByRole('button', { name: '근거 다시 찾기' }));
  await view.findByText(/관련 발행 기사가 없습니다/);
  expect(view.getAllByRole('textbox')).toHaveLength(3);
  for (const [label, value] of [
    ['예상과 달랐던 점', '차이'],
    ['다음 행동', '기준'],
  ])
    fireEvent.change(view.getByRole('textbox', { name: label! }), { target: { value } });
  fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
  expect((await view.findByRole('alert')).textContent).toBe('저장 실패');
  expect(view.getByRole('textbox', { name: '다음 행동' })).toHaveProperty('value', '기준');
  fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
  await view.findByText('복기를 남겼어요.');
  expect(api.createReview).toHaveBeenCalledTimes(2);
});

it('접기와 숨기기는 작성 폼을 보존하고 비동기 AI 결과가 입력값을 덮어쓰지 않는다', async () => {
  let finish!: (value: ReviewGuide) => void;
  vi.mocked(api.getReviewGuide).mockImplementation(
    () =>
      new Promise((resolve) => {
        finish = resolve;
      }),
  );
  const view = render(<FloatingReviewCoach record={record} onSaved={vi.fn()} />);
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  fireEvent.change(view.getByRole('textbox', { name: '실제 결과' }), {
    target: { value: '내 초안' },
  });
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 접기' }));
  expect(view.queryByRole('dialog')).toBeNull();
  expect(document.activeElement).toBe(view.getByRole('button', { name: '복기 도우미 열기' }));
  await act(async () => finish(guide));
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  expect(view.getByRole('textbox', { name: '실제 결과' })).toHaveProperty('value', '내 초안');
  expect(view.getByRole('link', { name: '가상 참고 기사' })).toBeTruthy();
  fireEvent.click(view.getByLabelText('복기 도우미 메뉴'));
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 숨기기' }));
  expect(view.queryByRole('complementary', { name: '투자 복기 도우미' })).toBeNull();
  expect(document.activeElement).toBe(view.getByRole('button', { name: '복기 이어서 작성' }));
  fireEvent.click(view.getByRole('button', { name: '복기 이어서 작성' }));
  expect(view.getByRole('textbox', { name: '실제 결과' })).toHaveProperty('value', '내 초안');
  expect(api.getReviewGuide).toHaveBeenCalledTimes(1);
});

it('판단과 기사 내용을 펼쳐 확인하고 나중에 이어 써도 초안과 AI 근거를 유지한다', async () => {
  const view = render(<FloatingReviewCoach record={record} onSaved={vi.fn()} />);
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  const evidenceHeading = await view.findByText('AI가 찾은 참고 기사 · 1개');
  const evidence = evidenceHeading.closest('details')!;
  const context = view.getByText('당시의 판단').closest('details')!;
  expect(evidence.open).toBe(false);
  expect(context.open).toBe(false);
  fireEvent.click(context.querySelector('summary')!);
  expect(context.open).toBe(true);
  expect(view.getByText('영업이익 개선')).toBeTruthy();
  expect(view.getByText('0/3 작성')).toBeTruthy();
  fireEvent.change(view.getByRole('textbox', { name: '실제 결과' }), {
    target: { value: '  ' },
  });
  expect(view.getByText('0/3 작성')).toBeTruthy();
  fireEvent.change(view.getByRole('textbox', { name: '실제 결과' }), {
    target: { value: '확인한 결과' },
  });
  expect(view.getByText('1/3 작성')).toBeTruthy();
  const jump = new MouseEvent('click', { bubbles: true, cancelable: true });
  fireEvent(view.getByRole('link', { name: '근거 1 보기' }), jump);
  expect(jump.defaultPrevented).toBe(true);
  expect(evidence.open).toBe(true);
  expect(view.getByRole('link', { name: '가상 참고 기사' }).getAttribute('href')).toBe(
    '/news/900001',
  );
  fireEvent.click(view.getByRole('button', { name: '나중에' }));
  expect(view.queryByRole('dialog')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  expect(view.getByRole('textbox', { name: '실제 결과' })).toHaveProperty('value', '확인한 결과');
  expect(view.getByText('1/3 작성')).toBeTruthy();
  expect(evidence.open).toBe(true);
  expect(api.getReviewGuide).toHaveBeenCalledOnce();
});

it('StrictMode에서도 고양이를 열 때 한 번만 근거를 찾고 다시 열어도 재사용한다', async () => {
  const view = render(
    <StrictMode>
      <FloatingReviewCoach record={record} onSaved={vi.fn()} />
    </StrictMode>,
  );
  expect(api.getReviewGuide).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  await view.findByRole('link', { name: '가상 참고 기사' });
  expect(api.getReviewGuide).toHaveBeenCalledOnce();
  expect(view.queryByRole('button', { name: '근거 찾아 복기하기' })).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 접기' }));
  fireEvent.click(view.getByRole('button', { name: '복기 도우미 열기' }));
  expect(view.getByRole('link', { name: '가상 참고 기사' })).toBeTruthy();
  expect(api.getReviewGuide).toHaveBeenCalledOnce();
});
