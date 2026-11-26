/** @vitest-environment jsdom */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../src/api/client';
import { createTransactionSubmission } from '../../src/features/portfolio/transactionSubmission';
import type {
  PortfolioTransactionRequest,
  PortfolioTransactionResponse,
} from '../../src/features/portfolio/types';

type Send = (
  request: PortfolioTransactionRequest,
  key: string,
) => Promise<PortfolioTransactionResponse>;

const trade: PortfolioTransactionRequest = {
  assetId: 1,
  type: 'BUY',
  tradeDate: '2026-11-24',
  quantity: 1,
  unitPrice: 100,
  fee: 0,
  tax: 0,
};
const response: PortfolioTransactionResponse = {
  transactionId: 42,
  holding: null,
  totalRealizedProfitLoss: 0,
};
beforeEach(() => sessionStorage.clear());

describe('회원 단건 거래의 제출 의도', () => {
  it('응답 대기 중 같은 요청을 합치고 성공 후 같은 내용의 새 매수에는 새 키를 쓴다', async () => {
    let finish!: (result: PortfolioTransactionResponse) => void;
    const send = vi.fn<Send>(
      () => new Promise<PortfolioTransactionResponse>((resolve) => (finish = resolve)),
    );
    const submission = createTransactionSubmission(1);
    const a = submission.submit(trade, send);
    const b = submission.submit({ ...trade }, send);
    expect(send).toHaveBeenCalledTimes(1);
    expect(b).toBe(a);
    const firstKey = send.mock.calls[0]![1];
    finish(response);
    await a;
    expect(submission.getPending()).toBeNull();
    const again = vi.fn<Send>(async () => response);
    await submission.submit(trade, again);
    expect(again.mock.calls[0]![1]).not.toBe(firstKey);
  });

  it('응답 유실 뒤 새 인스턴스에서도 같은 키와 본문으로 결과를 확인한다', async () => {
    const send = vi.fn<Send>(async () => {
      throw new TypeError('network disconnected');
    });
    await expect(createTransactionSubmission(7).submit(trade, send)).rejects.toThrow();
    const [originalBody, key] = send.mock.calls[0]!;
    const restored = createTransactionSubmission(7);
    expect(restored.getPending()).toEqual({ key, request: trade });
    const retry = vi.fn(async () => response);
    await restored.submit(restored.getPending()!.request, retry);
    expect(retry).toHaveBeenCalledWith(originalBody, key);
    expect(createTransactionSubmission(7).getPending()).toBeNull();
  });

  it('미확정 거래의 내용을 바꾸거나 다른 거래를 보내는 것을 막는다', async () => {
    const submission = createTransactionSubmission(1);
    await expect(
      submission.submit(trade, async () => {
        throw new TypeError();
      }),
    ).rejects.toThrow();
    const send = vi.fn(async () => response);
    await expect(submission.submit({ ...trade, quantity: 2 }, send)).rejects.toThrow('이전 거래');
    expect(send).not.toHaveBeenCalled();
    expect(submission.getPending()!.request.quantity).toBe(1);
  });

  it('회원별 미확정 거래를 분리한다', async () => {
    await expect(
      createTransactionSubmission(1).submit(trade, async () => {
        throw new TypeError();
      }),
    ).rejects.toThrow();
    expect(createTransactionSubmission(2).getPending()).toBeNull();
    expect(createTransactionSubmission(1).getPending()).not.toBeNull();
  });

  it.each([401, 403, 408, 409, 429, 500, 503])('%i 응답은 기존 키를 유지한다', async (status) => {
    const submission = createTransactionSubmission(1);
    await expect(
      submission.submit(trade, async () => {
        throw new ApiError(status);
      }),
    ).rejects.toThrow();
    expect(submission.getPending()).not.toBeNull();
  });

  it('확정적인 입력 거절 뒤에는 수정한 입력을 새 키로 제출한다', async () => {
    const submission = createTransactionSubmission(1);
    let originalKey = '';
    await expect(
      submission.submit(trade, async (_body, key) => {
        originalKey = key;
        throw new ApiError(400, 'INSUFFICIENT_HOLDING_QUANTITY');
      }),
    ).rejects.toThrow();
    expect(submission.getPending()).toBeNull();
    await submission.submit({ ...trade, quantity: 2 }, async (_body, key) => {
      expect(key).not.toBe(originalKey);
      return response;
    });
  });

  it('저장소 기록 실패 시 요청을 보내지 않는다', async () => {
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    try {
      const send = vi.fn(async () => response);
      await expect(createTransactionSubmission(1).submit(trade, send)).rejects.toThrow('보관');
      expect(send).not.toHaveBeenCalled();
    } finally {
      spy.mockRestore();
    }
  });
});
