import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getTransactionValidationError,
  validateTransaction,
} from '../../src/features/portfolio/transactionForm.ts';
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
test('거래 오류는 수정해야 할 필드와 기존 안내 문구를 함께 반환한다', () => {
  for (const [field, value] of Object.entries({
    tradeDate: '9999-01-01',
    quantity: '0',
    unitPrice: '0',
    fee: '-1',
    tax: '-1',
  })) {
    const fields = { ...valid, [field]: value };
    assert.equal(getTransactionValidationError(fields)?.field, field);
    assert.equal(getTransactionValidationError(fields)?.message, validateTransaction(fields));
  }
});
test('미래일·존재하지 않는 날짜·소수 수량·음수 비용을 거부한다', () => {
  for (const change of [
    { tradeDate: '9999-01-01' },
    { tradeDate: '2026-02-30' },
    { quantity: '1.5' },
    { quantity: '0' },
    { quantity: '9007199254740992' },
    { fee: '-1' },
    { unitPrice: '1.23456' },
    { unitPrice: '0' },
    { unitPrice: '1000000000000000' },
  ]) {
    assert.notEqual(validateTransaction({ ...valid, ...change }), null, JSON.stringify(change));
  }
});

test('미국 거래는 소수점 네 자리 단가와 여섯 자리 환율을 허용하고 환율 누락을 거부한다', () => {
  assert.equal(
    validateTransaction({ ...valid, unitPrice: '100.1234', exchangeRate: '1300.123456' }, 'USD'),
    null,
  );
  for (const exchangeRate of [undefined, '0', '-1', '1300.1234567', 'NaN']) {
    assert.equal(
      getTransactionValidationError({ ...valid, exchangeRate }, 'USD')?.field,
      'exchangeRate',
    );
  }
});

test('거래 시각은 한국 시간 HH:mm 형식만 허용하고 기존 시각 누락도 허용한다', () => {
  for (const tradeTime of ['00:00', '09:35', '23:59', undefined]) {
    assert.equal(validateTransaction({ ...valid, tradeTime }), null);
  }
  for (const tradeTime of ['', '24:00', '09:60', '9:35', '09:35:00']) {
    assert.equal(getTransactionValidationError({ ...valid, tradeTime })?.field, 'tradeTime');
  }
});
