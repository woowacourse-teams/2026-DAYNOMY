import assert from 'node:assert/strict';
import test from 'node:test';
import { validateTransaction } from '../../src/features/portfolio/transactionForm.ts';
const valid = {
  tradeDate: '2026-01-01',
  quantity: '10',
  unitPrice: '12.34',
  fee: '0',
  tax: '0.25',
};
test('거래 입력은 소수점 비용과 과거 거래일을 허용한다', () => {
  assert.equal(validateTransaction(valid), null);
});
test('미래일·존재하지 않는 날짜·소수 수량·음수 비용을 거부한다', () => {
  for (const change of [
    { tradeDate: '9999-01-01' },
    { tradeDate: '2026-02-30' },
    { quantity: '1.5' },
    { quantity: '0' },
    { quantity: '9007199254740992' },
    { fee: '-1' },
    { unitPrice: '1.234' },
    { unitPrice: '0' },
    { unitPrice: '1000000000000000' },
  ]) {
    assert.notEqual(validateTransaction({ ...valid, ...change }), null, JSON.stringify(change));
  }
});
