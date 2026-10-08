/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HoldingDecisions } from '../../src/features/league/HoldingDecisions';
import * as api from '../../src/features/league/api';
import type { InvestmentTransaction } from '../../src/features/league/types';

vi.mock('../../src/features/league/api', () => ({
  getTransactions: vi.fn(),
  recordDecision: vi.fn(),
  createReview: vi.fn(),
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
const saved: InvestmentTransaction = {
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
  vi.mocked(api.getTransactions).mockResolvedValue([]);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

function fillDecision(view: ReturnType<typeof render>) {
  fireEvent.change(view.getByLabelText('보유하는 이유'), { target: { value: saved.reason } });
  fireEvent.change(view.getByLabelText('기대하는 변화'), {
    target: { value: saved.expectedChange },
  });
  fireEvent.change(view.getByLabelText('판단을 다시 확인할 조건'), {
    target: { value: saved.invalidationCondition },
  });
}

describe('공유 자산의 보유 판단과 결과 복기', () => {
  it('실패해도 입력과 요청 키를 유지하고 저장 후 최초 판단에 복기를 추가한다', async () => {
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
    const view = render(<HoldingDecisions holdings={[holding]} />);
    fireEvent.click(await view.findByRole('button', { name: '보유 판단 남기기' }));
    fillDecision(view);
    fireEvent.click(view.getByRole('button', { name: '판단 기록 저장' }));
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '저장 실패');
    expect(view.getByLabelText('보유하는 이유')).toHaveProperty('value', saved.reason);
    const first = vi.mocked(api.recordDecision).mock.calls[0][0];
    fireEvent.click(view.getByRole('button', { name: '판단 기록 저장' }));
    expect(await view.findByRole('heading', { name: '삼성전자 · 보유 판단' })).toBeTruthy();
    expect(vi.mocked(api.recordDecision).mock.calls[1][0]).toEqual(first);
    expect(first).not.toHaveProperty('quantity');
    expect(first).not.toHaveProperty('unitPrice');
    expect(holding.quantity).toBe(2);
    fireEvent.click(view.getByRole('button', { name: '결과 복기 추가' }));
    fireEvent.change(view.getByLabelText('실제 결과'), { target: { value: '예상과 다릅니다' } });
    fireEvent.change(view.getByLabelText('예상과 달랐던 점'), { target: { value: '주가 하락' } });
    fireEvent.change(view.getByLabelText('다음 행동'), { target: { value: '다음 실적 확인' } });
    fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
    expect(await view.findByText('예상과 다릅니다')).toBeTruthy();
    expect(view.getByText(saved.reason)).toBeTruthy();
    expect(api.createReview).toHaveBeenCalledWith(1, {
      actualResult: '예상과 다릅니다',
      differenceFromExpectation: '주가 하락',
      nextAction: '다음 실적 확인',
    });
  });

  it('조회 실패를 빈 기록으로 숨기지 않고 재시도하며 자산 없이 작성하지 않는다', async () => {
    vi.mocked(api.getTransactions)
      .mockRejectedValueOnce(new Error('연결 실패'))
      .mockResolvedValueOnce([]);
    const view = render(<HoldingDecisions holdings={[]} />);
    expect(await view.findByRole('alert')).toHaveProperty(
      'textContent',
      expect.stringContaining('기록이 없는 것은 아니에요'),
    );
    expect(view.queryByText('아직 판단 기록이 없어요. 첫 보유 판단을 남겨 보세요.')).toBeNull();
    fireEvent.click(view.getByRole('button', { name: '판단 기록 다시 불러오기' }));
    await waitFor(() =>
      expect(view.getByRole('button', { name: '보유 판단 남기기' })).toHaveProperty(
        'disabled',
        true,
      ),
    );
    expect(api.recordDecision).not.toHaveBeenCalled();
  });

  it('복기 저장 실패 시 초안을 유지하고 삭제한 종목의 이전 기록은 비공개로 표시한다', async () => {
    vi.mocked(api.getTransactions).mockResolvedValue([saved]);
    vi.mocked(api.createReview).mockRejectedValue(new Error('복기 실패'));
    const view = render(<HoldingDecisions holdings={[]} />);
    fireEvent.click(await view.findByRole('button', { name: '결과 복기 추가' }));
    fireEvent.change(view.getByLabelText('실제 결과'), { target: { value: '결과' } });
    fireEvent.change(view.getByLabelText('예상과 달랐던 점'), { target: { value: '차이' } });
    fireEvent.change(view.getByLabelText('다음 행동'), { target: { value: '행동' } });
    fireEvent.click(view.getByRole('button', { name: '복기 저장' }));
    expect(await view.findByRole('alert')).toHaveProperty('textContent', '복기 실패');
    expect(view.getByLabelText('다음 행동')).toHaveProperty('value', '행동');
    expect(view.getByText(/종목 숨김·삭제로 비공개/)).toBeTruthy();
  });
});
