import assert from 'node:assert/strict';
import test from 'node:test';
import { reviewDateLabel } from '../../src/features/league/labels';

for (const [description, updatedAt, expected] of [
  ['수정 시각이 없으면 작성일을 표시한다', undefined, '작성 · 2026-10-06'],
  ['수정 시각이 null이면 작성일을 표시한다', null, '작성 · 2026-10-06'],
  ['생성 시각과 같으면 수정됨을 표시하지 않는다', '2026-10-06T00:00:00Z', '작성 · 2026-10-06'],
  [
    '같은 시각의 다른 시간대 표현도 작성으로 표시한다',
    '2026-10-06T09:00:00+09:00',
    '작성 · 2026-10-06',
  ],
  ['같은 날 수정도 수정됨을 표시한다', '2026-10-06T01:00:00Z', '수정됨 · 2026-10-06'],
  ['한국 시간 자정 이후는 다음 날짜로 표시한다', '2026-10-06T15:00:00Z', '수정됨 · 2026-10-07'],
  ['재수정은 가장 최근 수정 날짜를 표시한다', '2026-10-08T16:30:00Z', '수정됨 · 2026-10-09'],
  ['잘못된 수정 시각은 작성일로 표시한다', 'invalid', '작성 · 2026-10-06'],
  ['생성보다 이른 수정 시각은 작성일로 표시한다', '2026-10-05T12:00:00Z', '작성 · 2026-10-06'],
] as const) {
  test(description, () => {
    assert.equal(reviewDateLabel({ createdAt: '2026-10-06T00:00:00Z', updatedAt }), expected);
  });
}

test('생성 시각은 한국 시간으로 표시하고 밀리초 미만 정밀도 차이는 수정으로 보지 않는다', () => {
  assert.equal(
    reviewDateLabel({
      createdAt: '2026-10-06T15:00:00.123456Z',
      updatedAt: '2026-10-06T15:00:00.123789Z',
    }),
    '작성 · 2026-10-07',
  );
});

test('같은 밀리초 내에서 형식만 다른 생성 시각은 작성으로 표시한다', () => {
  assert.equal(
    reviewDateLabel({
      createdAt: '2026-10-06T00:00:00.000000Z',
      updatedAt: '2026-10-06T00:00:00Z',
    }),
    '작성 · 2026-10-06',
  );
});

test('생성 시각이 잘못되어도 화면에 예외를 발생시키지 않는다', () => {
  assert.equal(reviewDateLabel({ createdAt: 'invalid', updatedAt: null }), '작성일 미제공');
});
