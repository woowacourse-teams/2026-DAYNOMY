import assert from 'node:assert/strict';
import test from 'node:test';
import { isInvestmentCalendar } from '../../src/features/investment-calendar/api.ts';
import { getMockInvestmentCalendar } from '../../src/features/investment-calendar/mockData.ts';

test('투자 캘린더 목데이터는 백엔드 응답 형식을 따른다', () => {
  const calendar = getMockInvestmentCalendar(2026, 10);

  assert.equal(isInvestmentCalendar(calendar), true);
  assert.equal(calendar.events.length, 3);
  assert.equal(calendar.events[0]?.type, 'US_CPI');
});

test('투자 캘린더 응답에 필수 분석값이 없으면 거부한다', () => {
  const calendar = getMockInvestmentCalendar(2026, 10);
  const invalid = {
    ...calendar,
    events: calendar.events.map((event, index) =>
      index === 0
        ? { ...event, portfolioAnalysis: { ...event.portfolioAnalysis, status: undefined } }
        : event,
    ),
  };

  assert.equal(isInvestmentCalendar(invalid), false);
});

test('목데이터가 없는 월은 빈 일정으로 반환한다', () => {
  assert.deepEqual(getMockInvestmentCalendar(2026, 11), {
    year: 2026,
    month: 11,
    events: [],
  });
});
