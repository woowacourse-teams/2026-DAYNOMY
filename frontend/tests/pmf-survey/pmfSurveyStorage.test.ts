import assert from 'node:assert/strict';
import test from 'node:test';
import { nextKoreanMidnight, surveyStorageKey } from '../../src/features/pmf-survey/storage.ts';
test('KST 자정 경계는 24시간 후가 아니며 UTC·월말·연말도 처리한다', () => {
  for (const [input, expected] of [
    ['2026-11-05T23:59:59+09:00', '2026-11-06T00:00:00+09:00'],
    ['2026-11-06T00:00:00+09:00', '2026-11-07T00:00:00+09:00'],
    ['2026-12-31T14:59:59Z', '2027-01-01T00:00:00+09:00'],
    ['2026-11-30T00:00:00+09:00', '2026-12-01T00:00:00+09:00'],
  ])
    assert.equal(nextKoreanMidnight(Date.parse(input)), Date.parse(expected));
});
test('설문·계정·비회원의 숨김 키를 분리한다', () => {
  assert.notEqual(surveyStorageKey(1, 1), surveyStorageKey(1, 2));
  assert.notEqual(surveyStorageKey(1, 1), surveyStorageKey(2, 1));
  assert.notEqual(surveyStorageKey(1, null), surveyStorageKey(1, 1));
});
