import { expect, test } from '@playwright/test';

const holdings = [
  {
    assetId: 1,
    assetCode: '005930',
    name: '삼성전자',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 1,
    averagePurchasePrice: 268500,
    closePrice: 215500,
  },
  {
    assetId: 2,
    assetCode: '000660',
    name: 'SK하이닉스',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 2,
    averagePurchasePrice: 911500,
    closePrice: 1272000,
  },
];

test('보유 종목의 금액과 날짜는 가로 스크롤에서도 한 줄로 온전히 보인다', async ({
  page,
}, testInfo) => {
  await page.addInitScript(() => {
    localStorage.setItem('daynomy:service-started:v1', '1');
    localStorage.setItem('daynomy:portfolio-tour:v1', 'done');
  });
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (!path.startsWith('/api/')) return route.continue();
    const json = (value: unknown) => route.fulfill({ json: value });
    if (path === '/api/users/me')
      return json({
        id: 1,
        nickname: '레이아웃 확인',
        email: 'layout@example.invalid',
        role: 'USER',
      });
    if (path === '/api/users/me/portfolio') return json({ holdings, totalRealizedProfitLoss: 0 });
    if (path === '/api/auth/csrf') return json({ token: 'test', headerName: 'X-CSRF-TOKEN' });
    if (path === '/api/portfolio/calculate')
      return json({
        baseDate: '2026-10-16',
        pricingStatus: 'COMPLETE',
        pricedHoldingCount: 2,
        unpricedHoldingCount: 0,
        totalPurchaseAmount: 2091500,
        totalEvaluationAmount: 2759500,
        totalProfitLoss: 668000,
        totalReturnRate: 31.94,
        dailyProfitLoss: null,
        dailyReturnRate: null,
        holdings: holdings.map((h) => ({
          ...h,
          priceAvailable: true,
          baseDate: '2026-10-16',
          purchaseAmount: h.quantity * h.averagePurchasePrice,
          evaluationAmount: h.quantity * h.closePrice,
          profitLoss: h.quantity * (h.closePrice - h.averagePurchasePrice),
          returnRate: (h.closePrice / h.averagePurchasePrice - 1) * 100,
          weight: ((h.quantity * h.closePrice) / 2759500) * 100,
        })),
        marketAllocations: [{ market: 'KOSPI', evaluationAmount: 2759500, weight: 100 }],
      });
    return route.fulfill({ status: 404 });
  });
  await page.goto('/');
  const rows = page.locator('.portfolio-holding-row');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(1).locator('[data-label="평가금액"]')).toHaveText('2,544,000원');
  for (const width of [320, 390, 768, 1024, 1280, 1440, 1920, 2560]) {
    await page.setViewportSize({ width, height: 1000 });
    await expect(page.locator('.portfolio-table-wrap thead')).toBeVisible();
    expect(await rows.first().evaluate((row) => getComputedStyle(row).display)).toBe('table-row');
    const defects = await rows.evaluateAll((elements) =>
      elements.flatMap((row) => {
        const failures: string[] = [];
        for (const cell of row.querySelectorAll('td[data-label]')) {
          const bounds = cell.getBoundingClientRect();
          const walker = document.createTreeWalker(cell, NodeFilter.SHOW_TEXT);
          while (walker.nextNode()) {
            const node = walker.currentNode;
            if (!node.textContent?.trim()) continue;
            const range = document.createRange();
            range.selectNodeContents(node);
            const rects = [...range.getClientRects()].filter((rect) => rect.width > 0);
            if (
              rects.length !== 1 ||
              rects.some((rect) => rect.left < bounds.left - 1 || rect.right > bounds.right + 1)
            ) {
              failures.push(`${cell.getAttribute('data-label')}: ${node.textContent}`);
            }
          }
        }
        return failures;
      }),
    );
    expect(defects, `${width}px 금액·날짜 줄바꿈 또는 셀 넘침`).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    const wrap = page.locator('.portfolio-table-wrap');
    await wrap.evaluate((element) => {
      element.scrollLeft = element.scrollWidth;
    });
    const lastCellVisible = await rows
      .first()
      .locator('td:last-child')
      .evaluate((cell) => {
        const bounds = cell.getBoundingClientRect();
        const viewport = cell.closest('.portfolio-table-wrap')!.getBoundingClientRect();
        return bounds.right <= viewport.right + 1 && bounds.left >= viewport.left - 1;
      });
    expect(lastCellVisible, `${width}px에서 마지막 손익 열까지 스크롤 가능`).toBe(true);
    await wrap.screenshot({ path: testInfo.outputPath(`holdings-${width}-right.png`) });
    await wrap.evaluate((element) => {
      element.scrollLeft = 0;
    });
    await page
      .locator('.portfolio-table-wrap')
      .screenshot({ path: testInfo.outputPath(`holdings-${width}.png`) });
  }
});
