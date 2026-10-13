import assert from 'node:assert/strict';
import { test } from 'node:test';
import { getPublishedDate } from '../../src/features/stock-content/publishedDate';

test('원문 날짜를 시간대 변환 없이 보존하고 불명확한 날짜는 비운다', () => {
  assert.equal(getPublishedDate('2020-04-05T23:30:00-07:00'), '2020-04-05');
  assert.equal(getPublishedDate('2020-02-29'), '2020-02-29');
  for (const value of [
    null,
    undefined,
    '',
    '2025',
    '2025-02-29',
    '2020-13-05',
    '2020-04-05 unknown',
  ]) {
    assert.equal(getPublishedDate(value), null);
  }
});
