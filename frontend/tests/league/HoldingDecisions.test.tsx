/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { InvestmentReviewsPage } from '../../src/features/league/InvestmentReviewsPage';
import { HoldingDecisions } from '../../src/features/league/HoldingDecisions';
import * as api from '../../src/features/league/api';
import type { InvestmentDecisionRecord } from '../../src/features/league/types';

vi.mock('../../src/features/league/api', () => ({
  getInvestmentDecisions: vi.fn(),
  recordDecision: vi.fn(),
  createReview: vi.fn(),
  updateReview: vi.fn(),
}));
const holding = {
  assetId: 1,
  assetCode: '005930',
  assetName: '삼성전자',
  category: 'STOCK' as const,
  market: 'KOSPI' as const,
  quantity: 2,
  averagePurchasePrice: 70000,
  hidden: false,
  reason: '',
  baseDate: null,
  closePrice: null,
  evaluationAmount: null,
  returnRate: null,
};
const saved: InvestmentDecisionRecord = {
  id: 1,
  assetId: 1,
  assetCode: '005930',
  assetName: '삼성전자',
  category: 'STOCK',
  transactionType: 'HOLD',
  quantity: 2,
  unitPrice: 70000,
  fee: 0,
  tradedOn: '2026-10-06',
  reason: '실적을 확인했습니다',
  expectedHoldingPeriod: 'OVER_SIX_MONTHS',
  expectedChange: '매출 성장',
  invalidationCondition: '실적 악화',
  maximumAcceptableLossRate: 10,
  writtenAfterTrade: true,
  reviews: [],
};
beforeEach(() => {
  vi.mocked(api.getInvestmentDecisions).mockResolvedValue([]);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

function renderRecords(holdings: (typeof holding)[]) {
  return render(
    <HoldingDecisions
      holdings={holdings}
      portfolioBusy={false}
      publicationStatus="공개 후보"
      onVisibilityChange={vi.fn()}
      onRemove={vi.fn().mockResolvedValue(true)}
      onRecordsChange={vi.fn()}
      refreshKey={0}
    />,
  );
}

function fillDecision(view: ReturnType<typeof render>) {
  fireEvent.change(view.getByLabelText('보유하는 이유'), { target: { value: saved.reason } });
}

describe('종목별 기록과 바로 작성하는 복기', () => {
  it('저장 실패 시 입력과 요청 키를 유지하고 같은 종목에서 바로 복기를 작성한다', async () => {
    vi.mocked(api.recordDecision)
      .mockRejectedValueOnce(new Error('저장 실패'))
      .mockResolvedValueOnce(saved);
    vi.mocked(api.createReview).mockResolvedValue({
      id: 2,
      actualResult: '예상과 다릅니다',
      differenceFromExpectation: '주가 하락',
      nextAction: '다음 실적 확인',
      createdAt: '2026-10-06T00:00:00Z',
    });
    const view = renderRecords([holding]);
    fireEvent.click(await view.findByRole('button', { name: '삼성전자 보유 이유 작성' }));
    fillDecision(view);
    fireEvent.click(view.getByRole('button', { name: '보유 이유 저장' }));
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '저장 실패');
    await waitFor(() => expect(document.activeElement).toBe(view.getByRole('alert')));
    expect(view.getByLabelText('보유하는 이유')).toHaveProperty('value', saved.reason);
    const first = vi.mocked(api.recordDecision).mock.calls[0][0];
    fireEvent.click(view.getByRole('button', { name: '보유 이유 저장' }));
    expect(await view.findByRole('button', { name: '삼성전자 투자 기록 추가' })).toBeTruthy();
    expect(document.activeElement).toBe(
      view.getByRole('button', { name: '삼성전자 투자 기록 추가' }),
    );
    expect(vi.mocked(api.recordDecision).mock.calls[1][0]).toEqual(first);
    expect(first.decision.expectedHoldingPeriod).toBeNull();
    expect(first).not.toHaveProperty('quantity');
    expect(first).not.toHaveProperty('unitPrice');
    expect(holding.quantity).toBe(2);
    const reviews = view;
    expect(api.getInvestmentDecisions).toHaveBeenCalledTimes(1);
    const reviewTrigger = await reviews.findByRole('button', {
      name: '삼성전자 2026-10-06 보유 기록 복기하기',
    });
    fireEvent.click(reviewTrigger);
    fireEvent.change(reviews.getByLabelText('실제 결과'), { target: { value: '예상과 다릅니다' } });
    fireEvent.change(reviews.getByLabelText('예상과 달랐던 점'), {
      target: { value: '주가 하락' },
    });
    fireEvent.change(reviews.getByLabelText('다음 행동'), { target: { value: '다음 실적 확인' } });
    fireEvent.click(reviews.getByRole('button', { name: '복기 저장' }));
    expect(await reviews.findByText('예상과 다릅니다')).toBeTruthy();
    await waitFor(() => expect(document.activeElement).toBe(reviewTrigger));
    expect(reviews.getByText(saved.reason)).toBeTruthy();
    expect(api.createReview).toHaveBeenCalledWith(1, {
      actualResult: '예상과 다릅니다',
      differenceFromExpectation: '주가 하락',
      nextAction: '다음 실적 확인',
    });
  });

  it('조회 실패를 빈 기록으로 숨기지 않고 재시도하며 자산 없이 작성하지 않는다', async () => {
    vi.mocked(api.getInvestmentDecisions)
      .mockRejectedValueOnce(new Error('연결 실패'))
      .mockResolvedValueOnce([]);
    const view = renderRecords([]);
    expect(await view.findByRole('alert')).toHaveProperty(
      'textContent',
      expect.stringContaining('기록이 없는 것은 아니에요'),
    );
    expect(view.queryByRole('textbox', { name: '보유하는 이유' })).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '판단 기록 다시 불러오기' }));
    await waitFor(() =>
      expect(view.queryByRole('button', { name: '삼성전자 보유 이유 작성' })).toBeNull(),
    );
    expect(api.recordDecision).not.toHaveBeenCalled();
  });

  it('지난 종목의 기록에서도 복기를 작성하고 저장 실패 시 초안을 보존한다', async () => {
    vi.mocked(api.getInvestmentDecisions).mockResolvedValue([saved]);
    vi.mocked(api.createReview).mockRejectedValue(new Error('복기 실패'));
    const view = renderRecords([]);
    fireEvent.click(await view.findByText('지난 종목의 기록 1개'));
    fireEvent.click(
      await view.findByRole('button', { name: '삼성전자 2026-10-06 보유 기록 복기하기' }),
    );
    fireEvent.change(view.getByLabelText('실제 결과'), { target: { value: '결과' } });
    fireEvent.change(view.getByLabelText('예상과 달랐던 점'), { target: { value: '차이' } });
    fireEvent.change(view.getByLabelText('다음 행동'), { target: { value: '행동' } });
    fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '복기 실패');
    await waitFor(() => expect(document.activeElement).toBe(view.getByRole('alert')));
    expect(view.getByLabelText('다음 행동')).toHaveProperty('value', '행동');
    expect(view.getByText(saved.reason)).toBeTruthy();
  });

  it('기존 복기를 수정하고 작성 시각과 판단은 유지한다', async () => {
    const original = {
      id: 2,
      actualResult: '기존 결과',
      differenceFromExpectation: '기존 차이',
      nextAction: '기존 행동',
      createdAt: '2026-10-06T00:00:00Z',
    };
    vi.mocked(api.getInvestmentDecisions).mockResolvedValue([{ ...saved, reviews: [original] }]);
    vi.mocked(api.updateReview).mockResolvedValue({
      ...original,
      actualResult: '수정한 결과',
      updatedAt: '2026-10-07T00:00:00Z',
    });
    const view = renderRecords([holding]);
    const trigger = await view.findByRole('button', {
      name: '삼성전자 2026-10-06 보유 기록 복기 수정',
    });
    fireEvent.click(trigger);
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    fireEvent.click(view.getByRole('button', { name: '취소' }));
    expect(document.activeElement).toBe(trigger);
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    fireEvent.click(trigger);
    expect(view.getByLabelText('다음 행동')).toHaveProperty('value', '기존 행동');
    fireEvent.change(view.getByLabelText('실제 결과'), { target: { value: '수정한 결과' } });
    fireEvent.click(view.getByRole('button', { name: '수정 저장' }));
    expect(await view.findByText('수정한 결과')).toBeTruthy();
    expect(view.getByText(saved.reason)).toBeTruthy();
    expect(view.getByText('작성 2026-10-06 · 마지막 저장 2026-10-07')).toBeTruthy();
    await waitFor(() => expect(document.activeElement).toBe(trigger));
    expect(api.updateReview).toHaveBeenCalledWith(1, 2, {
      actualResult: '수정한 결과',
      differenceFromExpectation: '기존 차이',
      nextAction: '기존 행동',
    });
    expect(api.createReview).not.toHaveBeenCalled();
  });
  it('보유 이유만 저장하고 선택 항목을 임의의 기간이나 하락률로 채우지 않는다', async () => {
    vi.mocked(api.recordDecision).mockResolvedValue({
      ...saved,
      expectedHoldingPeriod: null,
      expectedChange: '',
      invalidationCondition: '',
      maximumAcceptableLossRate: null,
    });
    const view = renderRecords([holding]);
    fireEvent.click(await view.findByRole('button', { name: '삼성전자 보유 이유 작성' }));
    fireEvent.change(view.getByLabelText('보유하는 이유'), { target: { value: '장기 실적 성장' } });
    fireEvent.click(view.getByRole('button', { name: '보유 이유 저장' }));
    await view.findByText('장기 실적 성장');
    expect(api.recordDecision).toHaveBeenCalledWith(
      expect.objectContaining({
        decision: {
          reason: '장기 실적 성장',
          expectedHoldingPeriod: null,
          expectedChange: '',
          invalidationCondition: '',
          maximumAcceptableLossRate: null,
        },
      }),
    );
    await view.findByRole('button', { name: '삼성전자 투자 기록 추가' });
    expect(view.queryByText('보유 이유 미작성')).toBeNull();
  });
  it('입력하지 않은 선택 정보는 생략하되 허용 하락률 0%는 표시한다', async () => {
    vi.mocked(api.getInvestmentDecisions).mockResolvedValue([
      {
        ...saved,
        expectedHoldingPeriod: null,
        expectedChange: '',
        invalidationCondition: '',
        maximumAcceptableLossRate: 0,
      },
    ]);
    const view = render(
      <MemoryRouter>
        <InvestmentReviewsPage />
      </MemoryRouter>,
    );
    expect(await view.findByText('0%')).toBeTruthy();
    expect(view.queryByText('예상 보유 기간')).toBeNull();
    expect(view.queryByText('미작성')).toBeNull();
    expect(view.container.querySelector('details')).toBeNull();
  });
});

it('공백뿐인 보유 이유는 오류와 연결하고 취소하면 작성 버튼으로 돌아간다', async () => {
  const view = renderRecords([holding]);
  fireEvent.click(await view.findByRole('button', { name: '삼성전자 보유 이유 작성' }));
  const input = view.getByLabelText('보유하는 이유');
  fireEvent.change(input, { target: { value: '   ' } });
  fireEvent.click(view.getByRole('button', { name: '보유 이유 저장' }));
  const error = view.getByRole('alert');
  expect(input.getAttribute('aria-invalid')).toBe('true');
  expect(input.getAttribute('aria-describedby')).toBe(error.id);
  expect(document.activeElement).toBe(input);
  expect(api.recordDecision).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '취소' }));
  expect(document.activeElement).toBe(
    view.getByRole('button', { name: '삼성전자 보유 이유 작성' }),
  );
});

it('삭제 확인은 취소에 초점을 두고 취소 후 해당 종목 삭제 버튼으로 돌아간다', async () => {
  const view = renderRecords([holding]);
  await waitFor(() => expect(view.queryByRole('status')).toBeNull());
  const trigger = view.getByRole('button', { name: '삼성전자 리그에서 삭제' });
  fireEvent.click(trigger);
  const cancel = view.getByRole('button', { name: '취소' });
  expect(document.activeElement).toBe(cancel);
  fireEvent.click(cancel);
  expect(document.activeElement).toBe(trigger);
});

it('복기 오류는 첫 미작성 항목으로 이동하고 취소 후 원래 기록으로 돌아간다', async () => {
  vi.mocked(api.getInvestmentDecisions).mockResolvedValue([saved]);
  const view = render(
    <MemoryRouter>
      <InvestmentReviewsPage />
    </MemoryRouter>,
  );
  const trigger = await view.findByRole('button', {
    name: '삼성전자 2026-10-06 보유 기록 복기하기',
  });
  fireEvent.click(trigger);
  fireEvent.change(view.getByLabelText('실제 결과'), { target: { value: '결과' } });
  for (const label of ['예상과 달랐던 점', '다음 행동']) {
    fireEvent.change(view.getByLabelText(label), { target: { value: '   ' } });
  }
  fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
  const invalidInput = view.getByLabelText('예상과 달랐던 점');
  expect(document.activeElement).toBe(invalidInput);
  expect(invalidInput.getAttribute('aria-invalid')).toBe('true');
  expect(invalidInput.getAttribute('aria-describedby')).toBe(view.getByRole('alert').id);
  expect(view.getByLabelText('실제 결과').hasAttribute('aria-invalid')).toBe(false);
  expect(api.createReview).not.toHaveBeenCalled();
  fireEvent.click(view.getByRole('button', { name: '취소' }));
  expect(document.activeElement).toBe(trigger);
});

it('복기 조회 실패를 빈 기록과 구분하고 재시도 후 첫 기록 작성으로 안내한다', async () => {
  vi.mocked(api.getInvestmentDecisions)
    .mockRejectedValueOnce(new Error('연결 실패'))
    .mockResolvedValueOnce([]);
  const view = render(
    <MemoryRouter>
      <InvestmentReviewsPage />
    </MemoryRouter>,
  );
  expect(await view.findByRole('alert')).toHaveProperty(
    'textContent',
    expect.stringContaining('불러오지 못했습니다'),
  );
  expect(view.queryByText('아직 투자 기록이 없습니다')).toBeNull();
  fireEvent.click(view.getByRole('button', { name: '다시 불러오기' }));
  expect(await view.findByRole('link', { name: '보유 이유 작성하기' })).toHaveProperty(
    'href',
    expect.stringContaining('/league/portfolio'),
  );
  expect(api.createReview).not.toHaveBeenCalled();
});

it('최신 기록을 먼저 보여주고 이전 기록에만 복기를 연결하며 다른 기록을 보존한다', async () => {
  const newer = { ...saved, id: 3, tradedOn: '2026-10-10', reason: '새 판단' };
  const removed = { ...saved, id: 4, assetId: 2, assetName: '다른 종목', reason: '지난 판단' };
  vi.mocked(api.getInvestmentDecisions).mockResolvedValue([saved, removed, newer]);
  vi.mocked(api.createReview).mockResolvedValue({
    id: 10,
    actualResult: '지난 결과',
    differenceFromExpectation: '지난 차이',
    nextAction: '확인',
    createdAt: '2026-10-19T00:00:00Z',
  });
  const view = renderRecords([holding]);
  const latest = await view.findByRole('button', {
    name: '삼성전자 2026-10-10 보유 기록 복기하기',
  });
  expect(latest).toBeTruthy();
  expect(view.getByText('이전 기록 1개').closest('details')?.hasAttribute('open')).toBe(false);
  fireEvent.click(view.getByText('이전 기록 1개'));
  const previous = view.getByRole('button', { name: '삼성전자 2026-10-06 보유 기록 복기하기' });
  fireEvent.click(previous);
  expect(view.getByRole('button', { name: '삼성전자 리그에서 삭제' })).toHaveProperty(
    'disabled',
    true,
  );
  expect(latest).toHaveProperty('disabled', true);
  fireEvent.change(view.getByLabelText('실제 결과'), { target: { value: '지난 결과' } });
  fireEvent.change(view.getByLabelText('예상과 달랐던 점'), { target: { value: '지난 차이' } });
  fireEvent.change(view.getByLabelText('다음 행동'), { target: { value: '확인' } });
  fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
  expect(await view.findByText('지난 결과')).toBeTruthy();
  expect(api.createReview).toHaveBeenCalledWith(1, expect.any(Object));
  expect(view.getByText('새 판단')).toBeTruthy();
  fireEvent.click(view.getByText('지난 종목의 기록 1개'));
  expect(
    view.getByRole('button', { name: '다른 종목 2026-10-06 보유 기록 복기하기' }),
  ).toBeTruthy();
});
