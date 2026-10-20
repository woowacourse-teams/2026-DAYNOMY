import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fillTrendDates, getTrendPeriodStart } from '../../src/features/portfolio/trendDates';

test('월말·윤년 조회 시작일은 해당 월의 마지막 날로 보정한다', () => {
  assert.equal(getTrendPeriodStart('2026-03-31', '1M'), '2026-02-28');
  assert.equal(getTrendPeriodStart('2024-03-31', '1M'), '2024-02-29');
  assert.equal(getTrendPeriodStart('2024-02-29', '1Y'), '2023-02-28');
  assert.equal(getTrendPeriodStart('2026-10-19', '6M'), '2026-04-19');
  assert.equal(getTrendPeriodStart('2026-10-19', '5Y'), '2021-10-19');
  assert.equal(getTrendPeriodStart('2026-10-19', 'YTD'), '2026-01-01');
});

test('빈 날짜는 직전 기록을 유지하고 다음 기록이 있는 날부터 실제 값을 사용한다', () => {
  const point = {
    baseDate: '2026-10-16',
    totalPurchaseAmount: 100,
    totalEvaluationAmount: 110,
    totalProfitLoss: 10,
    totalReturnRate: 10,
    dailyProfitLoss: 5,
    dailyReturnRate: 5,
  };
  const result = fillTrendDates([
    point,
    { ...point, baseDate: '2026-10-19', totalEvaluationAmount: 120 },
  ]);
  assert.deepEqual(
    result.map((item) => item.baseDate),
    ['2026-10-16', '2026-10-17', '2026-10-18', '2026-10-19'],
  );
  assert.deepEqual(
    result.map((item) => item.totalEvaluationAmount),
    [110, 110, 110, 120],
  );
  assert.equal(result[2].carriedFrom, '2026-10-16');
  assert.equal(result[2].dailyProfitLoss, null);
  assert.equal(result[3].carriedFrom, undefined);
  assert.deepEqual(fillTrendDates([]), []);
  assert.deepEqual(fillTrendDates([point]), [point]);
});
