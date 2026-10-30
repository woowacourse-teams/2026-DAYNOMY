import assert from 'node:assert/strict';
import test from 'node:test';
import { holdingReturn } from '../../src/features/portfolio/holdingDisplay.ts';

test('일괄편집 손익 옆 수익률은 부호와 소수 둘째 자리를 표시하고 미확인은 생략한다', () => {
  assert.equal(holdingReturn(-90.3333), '(−90.33%)');
  assert.equal(holdingReturn(25), '(+25.00%)');
  assert.equal(holdingReturn(0), '(0.00%)');
  assert.equal(holdingReturn(null), '');
  assert.equal(holdingReturn(Number.NaN), '');
});
