/** @vitest-environment jsdom */
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { AggregationNotice } from '../../src/features/league/AggregationNotice';

afterEach(cleanup);

const pending = {
  baseDate: '2026-10-19',
  expectedAt: '2026-10-22T08:20:00Z',
  firstReturnExpectedAt: null,
  reason: 'SNAPSHOT_PENDING',
  delayed: false,
};

it('누락된 날짜와 한국 시간의 다음 집계를 안내하고 새로고침할 수 있다', () => {
  const refresh = vi.fn();
  const view = render(<AggregationNotice aggregation={pending} onRefresh={refresh} />);
  expect(view.getByRole('heading', { name: '이번 주 성과를 준비하고 있어요' })).toBeTruthy();
  expect(view.getByText('10월 19일 보유 데이터 확인 중')).toBeTruthy();
  expect(view.getByText(/10월 22일.*17:20/).getAttribute('datetime')).toBe(pending.expectedAt);
  fireEvent.click(view.getByRole('button', { name: '집계 현황 새로고침' }));
  expect(refresh).toHaveBeenCalledOnce();
  expect(view.queryByText('0.00%')).toBeNull();
});

it.each(['private-internal-error', '__proto__', 'constructor'])(
  '일정을 모르면 날짜나 알 수 없는 사유 %s를 노출하지 않는다',
  (reason) => {
    const view = render(
      <AggregationNotice
        aggregation={{ ...pending, expectedAt: null, reason }}
        onRefresh={vi.fn()}
      />,
    );
    expect(view.getByText('일정 확인 중')).toBeTruthy();
    expect(view.container.querySelector('time')).toBeNull();
    expect(view.getByRole('heading', { name: '다음 집계를 기다리고 있어요' })).toBeTruthy();
    expect(view.container.textContent).not.toContain(reason);
  },
);

it('첫 기준과 첫 수익률 변화의 예상 일정을 각각 표시한다', () => {
  const view = render(
    <AggregationNotice
      aggregation={{
        ...pending,
        reason: 'MARKET_CLOSE_PENDING',
        expectedAt: '2026-10-22T05:30:00Z',
        firstReturnExpectedAt: '2026-10-23T05:30:00Z',
      }}
      onRefresh={vi.fn()}
    />,
  );
  expect(view.getByText('다음 집계 예정')).toBeTruthy();
  expect(view.getByText('첫 수익률 변화 예상')).toBeTruthy();
  expect([...view.container.querySelectorAll('time')].map((time) => time.dateTime)).toEqual([
    '2026-10-22T05:30:00Z',
    '2026-10-23T05:30:00Z',
  ]);
});

it('제공 지연을 오류 대신 대기 상태로 구분한다', () => {
  const view = render(
    <AggregationNotice
      aggregation={{ ...pending, reason: 'PRICE_COLLECTION_DELAYED', delayed: true }}
      onRefresh={vi.fn()}
    />,
  );
  expect(view.getByText('제공 지연')).toBeTruthy();
  expect(view.getByRole('heading', { name: '종가 데이터 제공이 늦어지고 있어요' })).toBeTruthy();
  expect(view.queryByRole('alert')).toBeNull();
});
