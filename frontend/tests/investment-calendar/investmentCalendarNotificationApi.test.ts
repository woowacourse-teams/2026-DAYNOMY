import assert from 'node:assert/strict';
import test from 'node:test';
import { isInvestmentCalendarNotificationSetting } from '../../src/features/investment-calendar/notificationApi.ts';

test('투자 캘린더 알림 설정 응답을 검증한다', () => {
  assert.equal(
    isInvestmentCalendarNotificationSetting({
      email: 'user@example.com',
      emailVerified: true,
      scopes: ['PORTFOLIO', 'MAJOR_ECONOMIC'],
      timings: ['ONE_DAY_BEFORE', 'SAME_DAY_MORNING'],
      sendBeforeAnnouncement: true,
      sendAfterAnnouncement: true,
    }),
    true,
  );
});

test('알 수 없는 알림 범위가 포함된 응답은 거부한다', () => {
  assert.equal(
    isInvestmentCalendarNotificationSetting({
      email: 'user@example.com',
      emailVerified: true,
      scopes: ['EVERYTHING'],
      timings: ['ONE_DAY_BEFORE'],
      sendBeforeAnnouncement: true,
      sendAfterAnnouncement: false,
    }),
    false,
  );
});
