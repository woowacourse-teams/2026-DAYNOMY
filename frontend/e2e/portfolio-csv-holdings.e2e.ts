import { expect, test } from '@playwright/test';
const stocks = [
  { assetId: 1, assetCode: '005930', name: '삼성전자', category: 'STOCK', market: 'KOSPI' },
  { assetId: 2, assetCode: 'AAPL.US', name: '애플', category: 'STOCK', market: 'NASDAQ' },
];
for (const viewport of [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`자유 형식 CSV 열 연결부터 보유잔고 저장·중복 방지: ${viewport.name}`, async ({
    page,
  }, testInfo) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.setViewportSize(viewport);
    await page.addInitScript(() => {
      localStorage.setItem('daynomy:service-started:v1', '1');
    });
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      const fulfill = (body: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      if (url.pathname === '/api/auth/csrf')
        return fulfill({ token: 'test', headerName: 'X-CSRF-TOKEN' });
      if (url.pathname === '/api/stocks') return fulfill({ stocks });
      if (url.pathname === '/api/exchange-rates/usd-krw')
        return fulfill({ baseDate: '2026-10-29', rate: 1400 });
      const quote = url.pathname.match(/^\/api\/stocks\/(\d+)\/price$/);
      if (quote)
        return fulfill({
          ...stocks[Number(quote[1]) - 1],
          closePrice: quote[1] === '1' ? 75000 : 250,
          baseDate: '2026-10-29',
        });
      if (url.pathname === '/api/portfolio/calculate') {
        const { holdings: inputs } = route.request().postDataJSON() as {
          holdings: { assetId: number; quantity: number; averagePurchasePrice: number }[];
        };
        const holdings = inputs.map((row) => {
          const closePrice = row.assetId === 1 ? 75000 : 250;
          const purchaseAmount = row.quantity * row.averagePurchasePrice;
          const evaluationAmount = closePrice * row.quantity * (row.assetId === 1 ? 1 : 1400);
          return {
            ...stocks[row.assetId - 1],
            ...row,
            priceAvailable: true,
            baseDate: '2026-10-29',
            closePrice,
            purchaseAmount,
            evaluationAmount,
            profitLoss: evaluationAmount - purchaseAmount,
            returnRate: 5,
            weight: 50,
          };
        });
        return fulfill({
          baseDate: '2026-10-29',
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: holdings.length,
          unpricedHoldingCount: 0,
          totalPurchaseAmount: holdings.reduce((sum, row) => sum + row.purchaseAmount, 0),
          totalEvaluationAmount: holdings.reduce((sum, row) => sum + row.evaluationAmount, 0),
          totalProfitLoss: 1,
          totalReturnRate: 5,
          dailyProfitLoss: null,
          dailyReturnRate: null,
          holdings,
          marketAllocations: [],
        });
      }
      return fulfill({}, 401);
    });
    await page.goto('/');
    const csv =
      '나의 엑셀 보유현황\r\n종목;보유분;총매입금액\r\nAAPL;5;$1,000\r\n삼성전자;10;700000원\r\n합계;15;';
    for (let iteration = 0; iteration < 2; iteration++) {
      await page.getByRole('button', { name: '종목 추가', exact: true }).click();
      await page.getByRole('button', { name: 'CSV 파일 업로드', exact: true }).click();
      const dialog = page.getByRole('dialog', { name: 'CSV 파일 업로드', exact: true });
      await expect(dialog.getByRole('button', { name: '보유현황', exact: true })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
      const chooserEvent = page.waitForEvent('filechooser');
      await dialog.getByRole('button', { name: 'CSV 파일 선택' }).click();
      await (
        await chooserEvent
      ).setFiles({
        name: '내보유종목.csv',
        mimeType: 'text/csv',
        buffer: Buffer.from(iteration ? csv.replace('보유분', '개수') : csv),
      });
      if (!iteration) {
        await expect(dialog.getByRole('combobox', { name: 'CSV 구분자' })).toHaveValue(';');
        await expect(dialog.getByRole('combobox', { name: 'CSV 헤더 행' })).toHaveValue('1');
        await expect(dialog.getByRole('textbox', { name: '1행 수량' })).toHaveCount(0);
        if (viewport.name === 'mobile') {
          await page.setViewportSize({ width: 390, height: 640 });
          await expect(dialog.getByRole('combobox', { name: 'CSV 보유 수량 열' })).toBeAttached();
          await page.setViewportSize(viewport);
        }
        await page.screenshot({ path: testInfo.outputPath('csv-holdings-mapping.png') });
        await dialog.getByRole('combobox', { name: 'CSV 보유 수량 열' }).selectOption('1');
      }
      await expect(dialog.getByRole('button', { name: '미리보기', exact: true })).toHaveCount(0);
      const save = dialog.getByRole('button', { name: '2건 가져오기', exact: true });
      await expect(dialog.getByRole('textbox', { name: '1행 평균 매입가' })).toHaveValue('200');
      await expect(dialog.getByRole('textbox', { name: '2행 수량' })).toHaveValue('10');
      await expect(
        dialog.getByRole('columnheader', { name: /환율|거래일|세금|수수료/ }),
      ).toHaveCount(0);
      await expect(dialog.getByText('합계·소계 1개 행은 제외했습니다.')).toBeVisible();
      if (iteration) {
        await expect(save).toBeDisabled();
        break;
      }
      await expect(save).toBeEnabled();
      await page.screenshot({ path: testInfo.outputPath('csv-holdings-preview.png') });
      await save.click();
      await expect(dialog).toHaveCount(0);
      await page.reload();
      const ledger = await page.evaluate(() =>
        JSON.parse(sessionStorage.getItem('daynomy:guest-portfolio-holdings:v1')!),
      );
      expect(
        ledger.histories.filter(
          (row: { changeType: string }) => row.changeType === 'OPENING_BALANCE',
        ),
      ).toHaveLength(2);
      expect(
        ledger.histories.filter((row: { changeType: string }) => row.changeType === 'BUY'),
      ).toHaveLength(0);
      expect(
        ledger.histories.find((row: { assetId: number }) => row.assetId === 2)
          .nativeAveragePurchasePrice,
      ).toBe(200);
    }
    expect(errors).toEqual([]);
  });
}
