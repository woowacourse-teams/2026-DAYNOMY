import assert from 'node:assert/strict';
import test from 'node:test';
import {
  inspectHoldingCsv,
  guessCsvColumns,
  mapHoldingCsv,
  csvMappingError,
  csvAveragePrice,
  normalizeCsvNumber,
  normalizeCsvCode,
  matchCsvStock,
  readCsvText,
} from '../../src/features/portfolio/csvHoldingsImport.ts';
const parse = (text: string) => {
  const table = inspectHoldingCsv(text);
  return mapHoldingCsv(
    table,
    table.headerIndex,
    guessCsvColumns(table.rows[table.headerIndex].cells),
  );
};
const samsung = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  market: 'KOSPI',
  category: 'STOCK',
} as const;
const apple = {
  ...samsung,
  assetId: 2,
  assetCode: 'AAPL.US',
  name: '애플',
  market: 'NASDAQ',
} as const;
test('보유현황은 한영 열 이름·구분자·앞의 제목·여분 열·합계 행을 처리한다', () => {
  for (const delimiter of [',', '\t', ';', '|']) {
    const text = [
      '내 보유 현황',
      ['메모', '종목', '개수', '평단가'].join(delimiter),
      ['장기', '삼성전자', '10주', '70000원'].join(delimiter),
      ['합계 메모', '합계', '10', ''].join(delimiter),
    ].join('\r\n');
    const table = inspectHoldingCsv(text);
    assert.equal(table.delimiter, delimiter);
    assert.equal(table.headerIndex, 1);
    const result = parse(text);
    assert.equal(result.rows[0].line, 3);
    assert.equal(result.rows[0].quantity, '10');
    assert.equal(result.rows[0].unitPrice, '70000');
    assert.equal(result.rows[0].currency, 'KRW');
    assert.equal(result.skipped, 1);
  }
  assert.equal(parse('Ticker,SHARES,Average Price ($)\nAAPL,5,200.50').rows[0].currency, 'USD');
});
test('동일 의미 열이 여러 개면 추측하지 않고 수동 연결을 요구한다', () => {
  const mapping = guessCsvColumns(['종목명', '수량', '개수', '평단가', '현재가', '평가금액']);
  assert.equal(mapping.quantity, -1);
  assert.equal(mapping.unitPrice, 3);
  assert.match(csvMappingError(mapping)!, /수량/);
  assert.equal(csvMappingError({ ...mapping, quantity: 1 }), null);
  assert.match(csvMappingError({ ...mapping, quantity: 3 })!, /한 항목/);
  assert.match(
    csvMappingError(guessCsvColumns(['종목', '수량', '현재가', '평가금액']))!,
    /평균 매입가/,
  );
});
test('열 이름이 없는 CSV는 사용자가 지정한 순서를 사용하고 첫 데이터도 유지한다', () => {
  const table = inspectHoldingCsv('005930,10,70000\nAAPL,5,200');
  const mapping = { ...guessCsvColumns([]), assetCode: 0, quantity: 1, unitPrice: 2 };
  const result = mapHoldingCsv(table, -1, mapping);
  assert.equal(result.rows.length, 2);
  assert.equal(result.rows[0].assetCode, '005930');
});
test('쉼표·통화·수량 단위를 정리하되 소수 쉼표·수식·잘못된 단위는 임의로 숫자로 만들지 않는다', () => {
  for (const [raw, expected] of [
    ['₩ 70,000', '70000'],
    ['$200.50', '200.50'],
    ['10 개', '10'],
    ['2 shares', '2'],
    ['1 000', '1000'],
    ['(1,000원)', '-1000'],
    ['1e3', '1000'],
    ['1,5', '1,5'],
    ['1.234,56', '1.234,56'],
    ['1주2', '1주2'],
    ['=SUM(1)', '=SUM(1)'],
  ])
    assert.equal(normalizeCsvNumber(raw), expected);
  assert.equal(parse('종목,수량,평단가\n삼성전자,10,"70,000원"').rows[0].unitPrice, '70000');
});
test('국내 코드 앞자리·Excel 문자열과 종목명에 포함된 코드를 보존한다', () => {
  for (const value of [
    '5930',
    '005930',
    'A005930',
    "'005930",
    '="005930"',
    '005930.KS',
    '5.93E+03',
    '5930.0',
  ])
    assert.equal(normalizeCsvCode(value), '005930');
  const row = parse('종목,수량,평단가\n삼성전자 (005930),1,70000').rows[0];
  assert.equal(row.assetCode, '005930');
  assert.equal(matchCsvStock(row, [samsung]), samsung);
});
test('이름 또는 티커의 유일한 일치만 자동 연결하고 시장·통화 불일치·동명 종목은 남겨둔다', () => {
  const row = parse('종목,수량,평단가\nAAPL,5,200').rows[0];
  assert.equal(matchCsvStock(row, [apple, apple]), apple);
  assert.equal(matchCsvStock({ ...row, currency: 'KRW' }, [apple]), undefined);
  assert.equal(matchCsvStock({ ...row, market: 'KOSPI' }, [apple]), undefined);
  assert.equal(matchCsvStock(row, [apple, { ...apple, assetId: 3 }]), undefined);
  assert.equal(matchCsvStock({ ...row, name: '애플 일부' }, [apple]), undefined);
  assert.equal(matchCsvStock({ ...row, assetCode: 'MSFT', name: '애플' }, [apple]), undefined);
});
test('총 매입금액만 있으면 평단가를 계산하며 가격이 있으면 원래 평단가를 사용한다', () => {
  const row = parse('종목코드,보유수량,총매입금액\nAAPL,3,1000').rows[0];
  assert.equal(csvAveragePrice(row, 'USD'), '333.3333');
  assert.equal(csvAveragePrice(row, 'KRW'), '333.33');
  assert.equal(csvAveragePrice({ ...row, unitPrice: '200' }, 'USD'), '200');
  assert.equal(csvAveragePrice({ ...row, purchaseAmount: '1.005', quantity: '1' }, 'KRW'), '1.01');
  assert.equal(
    csvAveragePrice({ ...row, purchaseAmount: '1234567890123456', quantity: '10' }, 'KRW'),
    '123456789012345.6',
  );
  assert.equal(csvAveragePrice({ ...row, quantity: '0' }, 'USD'), '');
  assert.equal(csvAveragePrice({ ...row, purchaseAmount: '잘못된 금액' }, 'USD'), '');
});
test('통화 열과 값이 다르면 중단하며 일반적인 원금 헤더를 KRW 통화로 추측하지 않는다', () => {
  assert.throws(() => parse('종목,수량,평단가,통화\nAAPL,1,$200,KRW'), /통화/);
  assert.equal(parse('종목,수량,매입원금\nAAPL,1,200').rows[0].currency, '');
});
test('거래내역 파일을 현재 보유현황으로 오인해 등록하지 않는다', () => {
  assert.throws(
    () => parse('assetCode,type,tradeDate,quantity,unitPrice\nAAPL,SELL,2026-01-02,1,200'),
    /거래내역 파일/,
  );
});
test('잘못된 행·크기·종목 수를 거부하고 누락된 셀은 빈 값으로 남겨 미리보기에서 정정한다', () => {
  assert.throws(() => parse('종목,수량,평단가\n삼성전자,1,70,000'), /2행/);
  assert.throws(
    () => parse('종목,수량,평단가\n' + Array(201).fill('AAPL,1,200').join('\n')),
    /200/,
  );
  assert.throws(() => inspectHoldingCsv('a'.repeat(500001)), /500KB/);
  assert.equal(parse('종목,수량,평단가\n삼성전자,1').rows[0].unitPrice, '');
});
test('UTF-8·UTF-16·EUC-KR 파일을 읽고 지원하지 않는 바이너리는 오류로 반환한다', async () => {
  const file = (bytes: Uint8Array) => ({ arrayBuffer: async () => bytes.buffer }) as File;
  assert.equal(await readCsvText(file(new TextEncoder().encode('종목,개수'))), '종목,개수');
  assert.equal(
    await readCsvText(file(new Uint8Array([0xff, 0xfe, 0x41, 0, 0x2c, 0, 0x42, 0]))),
    'A,B',
  );
  assert.equal(await readCsvText(file(new Uint8Array([0xb0, 0xa1]))), '가');
  await assert.rejects(() => readCsvText(file(new Uint8Array([0xff, 0xff]))));
});
