import { expect, test } from '@playwright/test';

const holdings = [
  {
    assetId: 1,
    assetCode: '000660',
    name: 'SK하이닉스',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 1,
    averagePurchasePrice: 1287000,
  },
  {
    assetId: 2,
    assetCode: 'QQQ.US',
    name: 'Invesco QQQ Trust',
    category: 'ETF',
    market: 'NASDAQ',
    quantity: 112,
    averagePurchasePrice: 1015588,
    nativeAveragePurchasePrice: 725.42,
  },
  {
    assetId: 3,
    assetCode: 'QQQA.US',
    name: 'ProShares Nasdaq-100',
    category: 'ETF',
    market: 'NASDAQ',
    quantity: 1,
    averagePurchasePrice: 986160,
    nativeAveragePurchasePrice: 704.4,
  },
  {
    assetId: 4,
    assetCode: 'TEST.US',
    name: '외화 원가 미확인',
    category: 'STOCK',
    market: 'NASDAQ',
    quantity: 1,
    averagePurchasePrice: 140000,
  },
];
for (const viewport of [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`대시보드 원복과 일괄편집에만 통화·수익률 표시: ${viewport.name}`, async ({
    page,
  }, testInfo) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.setViewportSize(viewport);
    await page.addInitScript((holdings) => {
      localStorage.setItem('daynomy:service-started:v1', '1');
      sessionStorage.setItem('daynomy:guest-portfolio-holdings:v1', JSON.stringify(holdings));
    }, holdings);
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      const fulfill = (body: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      if (url.pathname === '/api/auth/csrf')
        return fulfill({ token: 'test', headerName: 'X-CSRF-TOKEN' });
      if (url.pathname === '/api/portfolio/calculate') {
        const rows = holdings.map((item, index) => {
          const closePrice = [1526000, 725.61, 68.09, 110][index];
          const purchaseAmount = item.averagePurchasePrice * item.quantity;
          const evaluationAmount = closePrice * item.quantity * (index === 0 ? 1 : 1400);
          return {
            ...item,
            priceAvailable: true,
            baseDate: '2026-10-29',
            closePrice,
            purchaseAmount,
            evaluationAmount,
            profitLoss: evaluationAmount - purchaseAmount,
            returnRate: ((evaluationAmount - purchaseAmount) / purchaseAmount) * 100,
            weight: 25,
          };
        });
        const totalPurchaseAmount = rows.reduce((sum, row) => sum + row.purchaseAmount, 0);
        const totalEvaluationAmount = rows.reduce((sum, row) => sum + row.evaluationAmount, 0);
        return fulfill({
          baseDate: '2026-10-29',
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: rows.length,
          unpricedHoldingCount: 0,
          totalPurchaseAmount,
          totalEvaluationAmount,
          totalProfitLoss: totalEvaluationAmount - totalPurchaseAmount,
          totalReturnRate: 0,
          dailyProfitLoss: null,
          dailyReturnRate: null,
          holdings: rows,
          marketAllocations: [],
        });
      }
      if (/^\/api\/assets\/\d+\/contents$/.test(url.pathname)) {
        const item = holdings.find((holding) => url.pathname.includes(`/${holding.assetId}/`))!;
        return fulfill({
          assetId: item.assetId,
          assetCode: item.assetCode,
          assetName: item.name,
          contents: [],
        });
      }
      return fulfill({}, 401);
    });
    await page.goto('/');
    const table = page.getByRole('region', { name: /보유 자산 표/ });
    // The dashboard keeps its existing buttons, labels, and layout.
    await expect(table.getByRole('button', { name: '평균 매수가 정렬' })).toBeAttached();
    await expect(table.getByRole('button', { name: '최근 종가 정렬' })).toBeAttached();
    await expect(table.getByRole('button', { name: '평가금액 정렬' })).toBeAttached();
    await expect(page.locator('.portfolio-holdings-caption')).toHaveCount(0);
    await page.getByRole('button', { name: '일괄 편집', exact: true }).click();
    const bulk = page.getByRole('dialog', { name: '포트폴리오 일괄편집', exact: true });
    await expect(bulk.getByRole('textbox', { name: 'Invesco QQQ Trust 평균 매입가' })).toHaveValue(
      '725.42',
    );
    await expect(bulk.getByText('$ 81,268.32', { exact: true })).toBeVisible();
    await expect(bulk.getByText('(−90.33%)', { exact: true })).toBeVisible();
    await expect(bulk.getByText('(+18.57%)', { exact: true })).toBeVisible();
    await expect(bulk.getByRole('columnheader', { name: /환율/ })).toHaveCount(0);
    await expect(bulk.getByRole('textbox', { name: '외화 원가 미확인 평균 매입가' })).toHaveValue(
      '',
    );
    await page.screenshot({ path: testInfo.outputPath('bulk-only-native-returns.png') });
    if (viewport.name === 'desktop') {
      await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
      const start = bulk.getByRole('textbox', { name: 'SK하이닉스 수량' });
      const end = bulk.getByRole('textbox', { name: 'Invesco QQQ Trust 평균 매입가' });
      const from = (await start.boundingBox())!;
      const to = (await end.boundingBox())!;
      await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2);
      await page.mouse.down();
      await page.mouse.move(to.x + to.width / 2, to.y + to.height / 2, { steps: 12 });
      await page.mouse.up();
      await expect(bulk.locator('[data-bulk-selected]')).toHaveCount(4);
      await page.screenshot({ path: testInfo.outputPath('bulk-drag-selection.png') });
      await page.keyboard.press('ControlOrMeta+c');
      await expect
        .poll(() => page.evaluate(() => navigator.clipboard.readText()))
        .toBe('1\t1287000\n112\t725.42');
      await bulk.getByRole('textbox', { name: 'ProShares Nasdaq-100 수량' }).click();
      await page.keyboard.press('ControlOrMeta+v');
      await expect(bulk.getByRole('textbox', { name: '외화 원가 미확인 수량' })).toHaveValue('112');
      await expect(bulk.getByRole('textbox', { name: '외화 원가 미확인 평균 매입가' })).toHaveValue(
        '725.42',
      );
      await page.keyboard.press('ControlOrMeta+z');
      await expect(bulk.getByRole('textbox', { name: '외화 원가 미확인 수량' })).toHaveValue('1');
      await expect(bulk.getByRole('textbox', { name: '외화 원가 미확인 평균 매입가' })).toHaveValue(
        '',
      );
      await page.keyboard.press('ControlOrMeta+Shift+z');
      await expect(bulk.getByRole('textbox', { name: '외화 원가 미확인 수량' })).toHaveValue('112');
      await bulk.getByRole('button', { name: '되돌리기' }).click();
      // Drag to the edge of a narrower window: selection follows horizontal scrolling.
      await page.setViewportSize({ width: 900, height: 1000 });
      const region = bulk.getByRole('region', { name: /일괄 편집 표/ });
      const currency = bulk.locator('tbody tr').first().locator('td').nth(2);
      const origin = (await currency.boundingBox())!;
      const scrollBox = (await region.boundingBox())!;
      await page.mouse.move(origin.x + origin.width / 2, origin.y + origin.height / 2);
      await page.mouse.down();
      await page.mouse.move(scrollBox.x + scrollBox.width - 4, origin.y + origin.height / 2, {
        steps: 10,
      });
      await expect.poll(() => region.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0);
      await page.mouse.up();
      await expect(bulk.locator('[data-bulk-selected]')).not.toHaveCount(1);
      await page.setViewportSize(viewport);
      await region.evaluate((element) => {
        element.scrollLeft = 0;
      });
    }
    await bulk.getByRole('button', { name: '일괄 편집 닫기' }).click();
    await page.screenshot({ path: testInfo.outputPath('dashboard-restored.png') });
    await page.getByRole('button', { name: '금액 숨기기' }).click();
    await page.getByRole('button', { name: '일괄 편집', exact: true }).click();
    await expect(bulk.getByText('$ 81,268.32', { exact: true })).toHaveCount(0);
    await expect(bulk.getByText('(−90.33%)', { exact: true })).toHaveCount(0);
    expect(errors).toEqual([]);
  });
}
