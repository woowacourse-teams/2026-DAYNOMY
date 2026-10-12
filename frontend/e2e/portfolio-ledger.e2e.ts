import { expect, test } from '@playwright/test';

const stocks = [
  {
    assetId: 1,
    assetCode: '005930',
    name: '삼성전자',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 120,
    averagePurchasePrice: 68250,
    closePrice: 75200,
  },
  {
    assetId: 2,
    assetCode: '000660',
    name: 'SK하이닉스',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 32,
    averagePurchasePrice: 178500,
    closePrice: 193400,
  },
  {
    assetId: 3,
    assetCode: '069500',
    name: 'KODEX 200',
    category: 'ETF',
    market: 'KOSPI',
    quantity: 85,
    averagePurchasePrice: 35680,
    closePrice: 34120,
  },
  {
    assetId: 4,
    assetCode: '247540',
    name: '에코프로비엠',
    category: 'STOCK',
    market: 'KOSDAQ',
    quantity: 18,
    averagePurchasePrice: 154800,
    closePrice: 148900,
  },
];
const sum = (key: 'averagePurchasePrice' | 'closePrice') =>
  stocks.reduce((total, stock) => total + stock.quantity * stock[key], 0);
const today = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}).format(new Date());
const start = new Date(`${today}T12:00:00Z`);
start.setUTCDate(start.getUTCDate() - 60);
const points = Array.from({ length: 45 }, (_, index) => {
  const date = new Date(start);
  date.setUTCDate(date.getUTCDate() + index);
  const totalPurchaseAmount = sum('averagePurchasePrice');
  const totalEvaluationAmount =
    sum('closePrice') - 1200000 + index * 26500 + Math.sin(index * 0.8) * 180000;
  return {
    baseDate: date.toISOString().slice(0, 10),
    totalPurchaseAmount,
    totalEvaluationAmount,
    totalProfitLoss: totalEvaluationAmount - totalPurchaseAmount,
    totalReturnRate: 5.68,
  };
});

for (const viewport of [
  { name: 'desktop', width: 1440, height: 1050 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`포트폴리오 거래 화면과 키보드 접근성: ${viewport.name}`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport);
    let history = [
      {
        historyId: 11,
        ...stocks[0],
        changeType: 'BUY',
        effectiveDate: today,
        transactionQuantity: 20,
        unitPrice: 73200,
        fee: 220,
        tax: 0,
        occurredAt: `${today}T01:30:00Z`,
      },
    ];
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      const path = url.pathname;
      const fulfill = (body: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      if (path === '/api/users/me')
        return fulfill({
          id: 1,
          email: 'preview@example.test',
          nickname: '미리보기',
          role: 'USER',
        });
      if (path === '/api/auth/csrf')
        return fulfill({ token: 'preview', headerName: 'X-CSRF-TOKEN' });
      if (path === '/api/portfolio')
        return fulfill({ holdings: stocks, totalRealizedProfitLoss: 483250 });
      if (path === '/api/portfolio/calculate')
        return fulfill({
          baseDate: today,
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: stocks.length,
          unpricedHoldingCount: 0,
          totalPurchaseAmount: sum('averagePurchasePrice'),
          totalEvaluationAmount: sum('closePrice'),
          totalProfitLoss: sum('closePrice') - sum('averagePurchasePrice'),
          totalReturnRate: (sum('closePrice') / sum('averagePurchasePrice') - 1) * 100,
          dailyProfitLoss: 186420,
          dailyReturnRate: 0.91,
          holdings: stocks.map((stock) => ({
            ...stock,
            priceAvailable: true,
            baseDate: today,
            purchaseAmount: stock.quantity * stock.averagePurchasePrice,
            evaluationAmount: stock.quantity * stock.closePrice,
            profitLoss: stock.quantity * (stock.closePrice - stock.averagePurchasePrice),
            returnRate: (stock.closePrice / stock.averagePurchasePrice - 1) * 100,
            weight: ((stock.quantity * stock.closePrice) / sum('closePrice')) * 100,
          })),
          marketAllocations: [
            { market: 'KOSPI', evaluationAmount: sum('closePrice'), weight: 100 },
          ],
        });
      if (path === '/api/portfolio/performance')
        return fulfill({
          status: 'READY',
          reason: null,
          baseDate: points.at(-1)!.baseDate,
          previousBaseDate: points.at(-2)!.baseDate,
          points,
          currentPoint: {
            baseDate: today,
            priceBaseDate: today,
            totalPurchaseAmount: sum('averagePurchasePrice'),
            totalEvaluationAmount: sum('closePrice'),
            totalProfitLoss: sum('closePrice') - sum('averagePurchasePrice'),
            totalReturnRate: 5.68,
          },
        });
      if (path === '/api/portfolio/histories') return fulfill(history);
      if (path === '/api/stocks') return fulfill({ stocks });
      if (path === '/api/portfolio/transactions' && route.request().method() === 'POST') {
        const body = route.request().postDataJSON();
        history = [
          ...history,
          {
            ...history[0],
            historyId: 12,
            transactionQuantity: body.quantity,
            unitPrice: body.unitPrice,
            effectiveDate: body.tradeDate,
          },
        ];
        return fulfill(
          { transactionId: 12, holding: stocks[0], totalRealizedProfitLoss: 483250 },
          201,
        );
      }
      if (path === '/api/portfolio/transactions/12' && route.request().method() === 'DELETE') {
        history = history.filter((item) => item.historyId !== 12);
        return fulfill({ transactionId: 12, holding: stocks[0], totalRealizedProfitLoss: 483250 });
      }
      return fulfill({}, 404);
    });
    await page.goto('/');
    await expect(
      page.getByText(`${sum('closePrice').toLocaleString('ko-KR')}원`).first(),
    ).toBeVisible();
    await expect(page.getByRole('img', { name: /포트폴리오 자산 추이/ })).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.screenshot({
      path: testInfo.outputPath('portfolio.png'),
      fullPage: true,
    });
    const holdingTrigger = page.getByRole('button', { name: '삼성전자 보유 상세' });
    await holdingTrigger.click();
    const detail = page.getByRole('dialog', { name: '삼성전자 보유 상세' });
    await expect(detail).toBeVisible();
    await expect(detail.getByRole('heading', { name: '거래 내역' })).toBeVisible();
    await expect(detail.locator('.portfolio-history-row')).toHaveCount(1);
    await page.screenshot({
      path: testInfo.outputPath('holding-detail.png'),
      animations: 'disabled',
      fullPage: false,
    });
    expect(await detail.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(
      true,
    );
    await detail.getByRole('button', { name: '매수', exact: true }).click();
    const nestedEditor = page.getByRole('dialog', { name: '거래 기록' });
    await expect(nestedEditor).toBeVisible();
    await expect(page.locator('dialog[open]')).toHaveCount(1);
    await expect(detail).toHaveCount(0);
    await expect(nestedEditor.getByRole('searchbox')).toHaveCount(0);
    await expect(
      nestedEditor.locator('.portfolio-trade-stock').getByText('삼성전자'),
    ).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(nestedEditor).toHaveCount(0);
    await expect(detail).toBeVisible();
    await expect(detail.getByRole('button', { name: '매수', exact: true })).toBeFocused();
    await page.keyboard.press('Escape');
    await expect(detail).toHaveCount(0);
    await expect(holdingTrigger).toBeFocused();
    const trigger = page.getByRole('button', { name: '자산 추가', exact: true });
    await trigger.click();
    await expect(page.getByRole('button', { name: '기존 보유자산 등록', exact: true })).toHaveCount(
      0,
    );
    await page.getByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' }).click();
    await expect(page.locator('dialog[open]')).toHaveCount(1);
    await expect(page.getByRole('dialog', { name: '기존 보유자산 등록' })).toBeVisible();
    await page.getByRole('button', { name: '거래 기록으로 돌아가기' }).click();
    await expect(page.locator('dialog[open]')).toHaveCount(1);
    const dialog = page.getByRole('dialog', { name: '거래 기록' });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('searchbox').fill('삼성');
    await dialog.getByRole('button', { name: /삼성전자/ }).click();
    await dialog.getByLabel('수량 (주)').fill('5');
    await dialog.getByLabel('거래 단가 (원)').fill('75200');
    await page.screenshot({
      path: testInfo.outputPath('transaction.png'),
      fullPage: true,
    });
    for (let i = 0; i < 12; i++) {
      await page.keyboard.press('Tab');
      expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBe(
        true,
      );
    }
    await dialog.getByRole('button', { name: '매수 기록하기' }).click();
    await expect(dialog).toHaveCount(0);
    await page.getByRole('button', { name: '거래·변경 내역' }).click();
    await expect(page.locator('.portfolio-history-row')).toHaveCount(2);
    await page.screenshot({
      path: testInfo.outputPath('history.png'),
      fullPage: true,
    });
    await page.locator('.portfolio-history-row').last().locator('summary').click();
    await page.getByRole('button', { name: '거래 삭제' }).last().click();
    const confirm = page.getByRole('dialog', { name: '거래 삭제' });
    await expect(confirm).toBeVisible();
    await confirm.getByRole('button', { name: '거래 삭제' }).click();
    await expect(confirm).toHaveCount(0);
    await expect(page.locator('.portfolio-history-row')).toHaveCount(1);
    await trigger.click();
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
    expect(errors).toEqual([]);
  });
}
