import assert from 'node:assert/strict';
import test from 'node:test';
import {
  CSV_HEADERS,
  balanceHolding,
  holdingError,
  parseDelimited,
  parseTransactionCsv,
} from '../../src/features/portfolio/bulkEditorModel.ts';

const header = CSV_HEADERS.join(',');
const domestic = '005930,KOSPI,BUY,2026-01-02,2,70000,0,0,KRW,';
test('셀 붙여넣기는 빈 행 위치와 따옴표 속 탭을 보존한다', () => {
  assert.deepEqual(parseDelimited('1\t2\r\n\t\r\n3\t"a\tb"\r\n', '\t', true), [
    { cells: ['1', '2'], line: 1 },
    { cells: ['', ''], line: 2 },
    { cells: ['3', 'a\tb'], line: 3 },
  ]);
});
test('CSV는 BOM, CRLF, 따옴표 속 쉼표와 빈 줄을 해석하고 원본 행 번호를 보존한다', () => {
  assert.deepEqual(parseDelimited('\uFEFFa,b\r\n\r\n"a,b","c""d"\r\n'), [
    { cells: ['a', 'b'], line: 1 },
    { cells: ['a,b', 'c"d'], line: 3 },
  ]);
  assert.equal(parseTransactionCsv(`\uFEFF${header}\r\n\r\n${domestic}`)[0].line, 3);
});
test('CSV 거래는 통화, 매매, 날짜, 수량과 정밀도를 검증한다', () => {
  assert.equal(parseTransactionCsv(`${header}\n${domestic}`)[0].assetCode, '005930');
  for (const row of [
    domestic.replace('KRW', 'USD'),
    domestic.replace('BUY', 'HOLD'),
    domestic.replace('2026-01-02', '2026-02-30'),
    domestic.replace(',2,70000', ',1.5,70000'),
    domestic.replace(',2,70000', ',2,=SUM(1)'),
    domestic.replace('KRW,', 'KRW,1300'),
  ]) {
    assert.throws(() => parseTransactionCsv(`${header}\n${row}`), /2행/);
  }
  assert.throws(
    () => parseTransactionCsv(`${header}\nAAPL.US,NASDAQ,BUY,2026-01-02,1,100.1234,0,0,USD,`),
    /환율/,
  );
  assert.equal(
    parseTransactionCsv(
      `${header}\nAAPL.US,NASDAQ,BUY,2026-01-02,1,100.1234,0,0,USD,1300.123456`,
    )[0].currency,
    'USD',
  );
});
test('CSV는 헤더 중복, 열 개수 불일치, 미완성 따옴표, 초과 건수를 거부한다', () => {
  assert.throws(() => parseTransactionCsv(header.replace('assetCode', 'market')), /열 이름/);
  assert.throws(() => parseTransactionCsv(`${header}\n${domestic},extra`), /열 개수/);
  assert.throws(() => parseDelimited('a,"b'), /따옴표/);
  assert.throws(
    () => parseTransactionCsv(`${header}\n${Array(201).fill(domestic).join('\n')}`),
    /200/,
  );
});
test('일괄 보유정보는 수량과 평균 매입가 필드별 오류를 구분한다', () => {
  assert.equal(holdingError({ quantity: '2', averagePurchasePrice: '1234.56' }), null);
  assert.equal(holdingError({ quantity: '1.5', averagePurchasePrice: '1234' })?.field, 'quantity');
  assert.equal(
    holdingError({ quantity: '1', averagePurchasePrice: '1234.567' })?.field,
    'averagePurchasePrice',
  );
});

test('현재 보유잔고는 외화 평단가를 정확히 원화로 환산하고 0원·초과 원가를 거부한다', () => {
  const stock = {
    assetId: 3,
    assetCode: 'AAPL.US',
    name: '애플',
    category: 'STOCK' as const,
    market: 'NASDAQ' as const,
  };
  assert.equal(
    balanceHolding(stock, { quantity: '2', unitPrice: '250', exchangeRate: '1450' })
      .averagePurchasePrice,
    362500,
  );
  assert.equal(
    balanceHolding(stock, { quantity: '1', unitPrice: '1.005', exchangeRate: '1' })
      .averagePurchasePrice,
    1.01,
  );
  assert.throws(
    () => balanceHolding(stock, { quantity: '1', unitPrice: '0.0001', exchangeRate: '0.01' }),
    /원화 환산/,
  );
  assert.throws(
    () =>
      balanceHolding(stock, {
        quantity: '1',
        unitPrice: '999999999999999',
        exchangeRate: '9999999999',
      }),
    /원화 환산/,
  );
});

test('외화 평단가는 원화 참고 원가와 별도로 소수 넷째 자리까지 보존한다', () => {
  const stock = {
    assetId: 3,
    assetCode: 'AAPL.US',
    name: '애플',
    category: 'STOCK' as const,
    market: 'NASDAQ' as const,
  };
  const holding = balanceHolding(stock, {
    quantity: '2',
    unitPrice: '123.4567',
    exchangeRate: '1400',
  });
  assert.equal(holding.nativeAveragePurchasePrice, 123.4567);
  assert.equal(holding.averagePurchasePrice, 172839.38);
  assert.equal(holdingError({ quantity: '2', averagePurchasePrice: '123.4567' }, 'USD'), null);
});
