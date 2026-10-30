import { expect, test } from '@playwright/test';

const stock = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
};
const second = {
  assetId: 2,
  assetCode: '000660',
  name: 'SK하이닉스',
  category: 'STOCK',
  market: 'KOSPI',
};
const third = { ...stock, assetId: 3, assetCode: '035420', name: 'NAVER' };
const apple = { ...stock, assetId: 4, assetCode: 'AAPL.US', name: '애플', market: 'NASDAQ' };
for (const viewport of [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`종목 추가 세 경로와 일괄 편집·CSV 중복 방지: ${viewport.name}`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize(viewport);
    await page.addInitScript(
      ({ stock, second }) => {
        localStorage.setItem('daynomy:service-started:v1', '1');
        if (!sessionStorage.getItem('daynomy:guest-portfolio-holdings:v1'))
          sessionStorage.setItem(
            'daynomy:guest-portfolio-holdings:v1',
            JSON.stringify([
              { ...stock, quantity: 2, averagePurchasePrice: 70000 },
              { ...second, quantity: 1, averagePurchasePrice: 180000 },
            ]),
          );
      },
      { stock, second },
    );
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      const fulfill = (body: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      if (url.pathname === '/api/stocks')
        return fulfill({
          stocks: [stock, second, third, apple].filter(
            (item) =>
              item.assetCode.includes(url.searchParams.get('q') ?? '') ||
              item.name.includes(url.searchParams.get('q') ?? ''),
          ),
        });
      if (url.pathname === '/api/exchange-rates/usd-krw')
        return fulfill({ baseDate: '2026-10-28', rate: 1400 });
      const quote = url.pathname.match(/^\/api\/stocks\/(\d+)\/price$/);
      if (quote) {
        const item = [stock, second, third, apple].find(
          (stock) => stock.assetId === Number(quote[1]),
        )!;
        return fulfill({
          ...item,
          baseDate: '2026-10-28',
          closePrice: item.assetId === 4 ? 250 : 75000,
        });
      }
      if (url.pathname === '/api/auth/csrf')
        return fulfill({ token: 'test', headerName: 'X-CSRF-TOKEN' });
      if (url.pathname === '/api/portfolio/calculate') {
        const body = route.request().postDataJSON() as {
          holdings: { assetId: number; quantity: number; averagePurchasePrice: number }[];
        };
        const holdings = body.holdings.map((item) => {
          const closePrice = item.assetId === 4 ? 250 : item.assetId === 1 ? 75000 : 190000;
          return {
            ...[stock, second, third, apple].find((stock) => stock.assetId === item.assetId)!,
            ...item,
            priceAvailable: true,
            baseDate: '2026-10-28',
            closePrice,
            purchaseAmount: item.quantity * item.averagePurchasePrice,
            evaluationAmount: item.quantity * closePrice,
            profitLoss: item.quantity * (closePrice - item.averagePurchasePrice),
            returnRate: 5,
            weight: 50,
          };
        });
        return fulfill({
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: holdings.length,
          unpricedHoldingCount: 0,
          baseDate: '2026-10-28',
          totalPurchaseAmount: holdings.reduce((sum, item) => sum + item.purchaseAmount, 0),
          totalEvaluationAmount: holdings.reduce((sum, item) => sum + item.evaluationAmount, 0),
          dailyProfitLoss: null,
          dailyReturnRate: null,
          totalProfitLoss: 10000,
          totalReturnRate: 5,
          holdings,
          marketAllocations: [],
        });
      }
      return fulfill({}, 401);
    });
    await page.goto('/');
    await expect(page.getByRole('button', { name: '일괄 편집', exact: true })).toBeEnabled();
    const add = page.getByRole('button', { name: '종목 추가', exact: true });
    await add.click();
    for (const name of ['한 종목 추가', '여러 종목 일괄 추가', 'CSV 파일 업로드'])
      await expect(page.getByRole('button', { name, exact: true })).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath('add-menu.png') });
    await page.getByRole('button', { name: '여러 종목 일괄 추가', exact: true }).click();
    let dialog = page.getByRole('dialog', { name: '여러 종목 일괄 추가', exact: true });
    for (const name of ['매매', '거래일', '수수료', '세금'])
      await expect(dialog.getByRole('columnheader', { name, exact: true })).toHaveCount(0);
    // Open the first row with later sticky rows underneath the search panel.
    await dialog.getByRole('button', { name: '새 자산 추가' }).click();
    await dialog.getByRole('button', { name: '새 자산 추가' }).click();
    await dialog.getByRole('button', { name: '1행 종목 검색' }).click();
    const search = dialog.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' });
    await expect(search).toBeFocused();
    await expect
      .poll(() =>
        search.evaluate((input) => {
          const box = input.getBoundingClientRect();
          return document.elementFromPoint(box.left + 8, box.top + box.height / 2) === input;
        }),
      )
      .toBe(true);
    await search.fill('삼성');
    await expect(dialog.getByRole('option', { name: /삼성전자.*보유 중/ })).toBeDisabled();
    await page.screenshot({ path: testInfo.outputPath('stock-search-owned.png') });
    await search.fill('NAVER');
    await expect(dialog.getByRole('option', { name: /NAVER.*035420/ })).toBeEnabled();
    await search.press('ArrowDown');
    await search.press('Enter');
    const removeRow = dialog.getByRole('button', { name: '3행 삭제' });
    await expect(removeRow).toBeInViewport();
    const removeBox = (await removeRow.boundingBox())!;
    const searchBox = (await dialog.getByRole('button', { name: '3행 종목 검색' }).boundingBox())!;
    expect(removeBox.x).toBeLessThan(searchBox.x);
    await removeRow.hover();
    await page.screenshot({ path: testInfo.outputPath('row-remove.png') });
    await removeRow.click();
    await dialog.getByRole('button', { name: '2행 종목 검색' }).click();
    const secondSearch = dialog.getByRole('combobox', { name: '2행 종목명 또는 코드 검색' });
    await secondSearch.fill('NAVER');
    await expect(dialog.getByRole('option', { name: /NAVER.*추가됨/ })).toBeDisabled();
    await secondSearch.press('Escape');
    await expect(dialog.getByRole('button', { name: '2행 종목 검색' })).toBeFocused();
    await dialog.getByRole('button', { name: '2행 삭제' }).click();
    await dialog.getByRole('textbox', { name: '1행 수량', exact: true }).fill('1');
    await dialog.getByRole('textbox', { name: '1행 평균 매입가' }).fill('70000');
    await page.screenshot({ path: testInfo.outputPath('bulk-add.png') });
    await dialog.getByRole('button', { name: '1건 등록' }).click();
    await expect(dialog).toHaveCount(0);
    await expect(add).toBeFocused();
    await page.getByRole('button', { name: '일괄 편집', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '포트폴리오 일괄편집', exact: true });
    await expect(dialog.getByRole('textbox', { name: '삼성전자 수량' })).toHaveValue('2');
    await dialog.getByRole('textbox', { name: '삼성전자 수량' }).fill('4');
    await expect(dialog.getByRole('textbox')).toHaveCount(6);
    await expect(dialog.getByRole('button', { name: '변경사항 저장' })).toBeInViewport();
    const box = (await dialog.boundingBox())!;
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.width).toBeLessThanOrEqual(viewport.width);
    await page.screenshot({ path: testInfo.outputPath('bulk-edit.png') });
    await dialog.getByRole('button', { name: '변경사항 저장' }).click();
    await expect(dialog).toHaveCount(0);
    await add.click();
    await page.getByRole('button', { name: '여러 종목 일괄 추가', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '여러 종목 일괄 추가', exact: true });
    await dialog.getByRole('button', { name: '1행 종목 검색' }).click();
    await dialog.getByRole('combobox', { name: '1행 종목명 또는 코드 검색' }).fill('애플');
    await dialog.getByRole('option', { name: /애플.*AAPL.US/ }).click();
    await expect(dialog.getByRole('cell', { name: 'USD', exact: true })).toBeVisible();
    await expect(dialog.getByRole('textbox', { name: '1행 적용 환율' })).toHaveCount(0);
    await expect(dialog.getByRole('columnheader', { name: /환율/ })).toHaveCount(0);
    await dialog.getByRole('textbox', { name: '1행 수량', exact: true }).fill('1');
    await dialog.getByRole('textbox', { name: '1행 평균 매입가' }).fill('250');
    await expect(dialog.getByRole('button', { name: '1건 등록' })).toBeEnabled();
    await page.screenshot({ path: testInfo.outputPath('native-currency-add.png') });
    await dialog.getByRole('button', { name: '1건 등록' }).click();
    await expect(dialog).toHaveCount(0);
    const csv =
      'assetCode,market,type,tradeDate,quantity,unitPrice,fee,tax,currency,exchangeRate\n005930,KOSPI,BUY,' +
      new Intl.DateTimeFormat('en-CA', {
        timeZone: 'Asia/Seoul',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
      }).format(new Date()) +
      ',1,70000,0,0,KRW,';
    for (let iteration = 0; iteration < 2; iteration++) {
      await add.click();
      await page.getByRole('button', { name: 'CSV 파일 업로드', exact: true }).click();
      dialog = page.getByRole('dialog', { name: 'CSV 파일 업로드', exact: true });
      await dialog.getByRole('button', { name: '거래내역', exact: true }).click();
      await expect(
        dialog.getByRole('button', { name: 'CSV 파일 선택', exact: true }),
      ).toBeInViewport();
      await expect(
        dialog.getByRole('button', { name: '예제 다운로드', exact: true }),
      ).toBeInViewport();
      if (!iteration) {
        await page.screenshot({ path: testInfo.outputPath('csv-actions.png') });
        const downloadEvent = page.waitForEvent('download');
        await dialog.getByRole('button', { name: '예제 다운로드', exact: true }).click();
        expect((await downloadEvent).suggestedFilename()).toBe('daynomy-transactions.csv');
      }
      const chooserEvent = page.waitForEvent('filechooser');
      await dialog.getByRole('button', { name: 'CSV 파일 선택', exact: true }).click();
      await (
        await chooserEvent
      ).setFiles({ name: 'trades.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
      await expect(dialog.getByRole('button', { name: '1건 가져오기' })).toBeEnabled();
      if (!iteration) await page.screenshot({ path: testInfo.outputPath('csv-preview.png') });
      await dialog.getByRole('button', { name: '1건 가져오기' }).click();
      await expect(dialog).toHaveCount(0);
    }
    await expect(page.getByText('이미 가져온 CSV 거래예요. 중복 등록하지 않았어요.')).toBeVisible();
    const ledger = await page.evaluate(() =>
      JSON.parse(sessionStorage.getItem('daynomy:guest-portfolio-holdings:v1')!),
    );
    expect(
      ledger.histories.filter((item: { changeType: string }) => item.changeType === 'BUY'),
    ).toHaveLength(1);
    expect(
      ledger.histories.filter(
        (item: { changeType: string }) => item.changeType === 'OPENING_BALANCE',
      ),
    ).toHaveLength(2);
    expect(
      ledger.histories.find((item: { assetId: number }) => item.assetId === 4).averagePurchasePrice,
    ).toBe(350000);
    expect(
      ledger.histories.find((item: { assetId: number }) => item.assetId === 4)
        .nativeAveragePurchasePrice,
    ).toBe(250);
    await page.reload();
    await page.getByRole('button', { name: '일괄 편집', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '포트폴리오 일괄편집', exact: true });
    await expect(dialog.getByRole('textbox', { name: '애플 평균 매입가' })).toHaveValue('250');
    await dialog.getByRole('textbox', { name: '애플 수량' }).fill('2');
    await dialog.getByRole('textbox', { name: '애플 평균 매입가' }).fill('220');
    await expect(dialog.getByText('$ 60', { exact: true })).toBeVisible();
    await page.screenshot({ path: testInfo.outputPath('native-currency-edit.png') });
    await dialog.getByRole('button', { name: '변경사항 저장' }).click();
    await expect(dialog).toHaveCount(0);
    await page.reload();
    await page.getByRole('button', { name: '일괄 편집', exact: true }).click();
    dialog = page.getByRole('dialog', { name: '포트폴리오 일괄편집', exact: true });
    await expect(dialog.getByRole('textbox', { name: '애플 평균 매입가' })).toHaveValue('220');
  });
}
