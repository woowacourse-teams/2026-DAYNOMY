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
    dailyProfitLoss: null,
    dailyReturnRate: null,
  };
});

for (const viewport of [
  { name: 'desktop', width: 1440, height: 1050 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`비회원 빈 자산 분석 카드에 과도한 여백이 없다: ${viewport.name}`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize(viewport);
    await page.route('**/api/**', (route) =>
      route.fulfill({ status: 401, contentType: 'application/json', body: '{}' }),
    );
    await page.goto('/');
    const trend = page.locator('.portfolio-return-dashboard');
    const composition = page.locator('.portfolio-composition');
    await expect(trend.getByRole('link', { name: '로그인하기' })).toBeVisible();
    await expect(composition.getByText('표시할 자산이 없습니다')).toBeVisible();
    for (const card of [trend, composition]) {
      const height = await card.evaluate((element) => parseFloat(getComputedStyle(element).height));
      expect(height).toBeLessThanOrEqual(340);
      expect(height).toBeGreaterThanOrEqual(250);
    }
    await page.locator('.portfolio-dashboard-grid').screenshot({
      path: testInfo.outputPath('empty-asset-analysis.png'),
    });
  });

  test(`비회원 잠금 안내와 종목 상세 진입: ${viewport.name}`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport);
    const guestHolding = {
      assetId: stocks[0].assetId,
      assetCode: stocks[0].assetCode,
      name: stocks[0].name,
      category: stocks[0].category,
      market: stocks[0].market,
      quantity: 10,
      averagePurchasePrice: 70000,
    };
    await page.addInitScript((holding) => {
      if (!sessionStorage.getItem('daynomy:guest-portfolio-holdings:v1'))
        sessionStorage.setItem('daynomy:guest-portfolio-holdings:v1', JSON.stringify([holding]));
    }, guestHolding);
    const memberRequests: string[] = [];
    await page.route('**/api/**', async (route) => {
      const path = new URL(route.request().url()).pathname;
      const fulfill = (body: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      const contentStock = stocks.find((stock) => path === `/api/assets/${stock.assetId}/contents`);
      if (contentStock)
        return fulfill({
          assetId: contentStock.assetId,
          assetCode: contentStock.assetCode,
          assetName: contentStock.name,
          contents: [],
        });
      if (/users\/me\/portfolio|portfolio\/(histories|transactions)/.test(path)) {
        memberRequests.push(path);
        return fulfill({}, 401);
      }
      if (path === '/api/stocks/1/price')
        return fulfill({ ...stocks[0], baseDate: today, closePrice: stocks[0].closePrice });
      if (path === '/api/auth/csrf')
        return fulfill({ token: 'preview', headerName: 'X-CSRF-TOKEN' });
      if (path === '/api/portfolio/calculate')
        return fulfill({
          baseDate: today,
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: 1,
          unpricedHoldingCount: 0,
          totalPurchaseAmount: 700000,
          totalEvaluationAmount: 752000,
          dailyProfitLoss: null,
          dailyReturnRate: null,
          totalProfitLoss: 52000,
          totalReturnRate: 7.43,
          holdings: [
            {
              ...guestHolding,
              priceAvailable: true,
              baseDate: today,
              closePrice: 75200,
              purchaseAmount: 700000,
              evaluationAmount: 752000,
              profitLoss: 52000,
              returnRate: 7.43,
              weight: 100,
            },
          ],
          marketAllocations: [{ market: 'KOSPI', evaluationAmount: 752000, weight: 100 }],
        });
      return fulfill({}, 401);
    });
    await page.goto('/');
    await expect(page.getByRole('button', { name: '삼성전자 보유 상세' })).toBeVisible();
    const newsTab = page.getByRole('button', { name: '소식', exact: true });
    const newsPanel = page.getByRole('complementary', { name: '보유 자산 소식' });
    await expect(newsPanel).toBeVisible();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByRole('dialog', { name: '보유 자산 소식' })).toHaveCount(0);
    expect(await page.locator('body').evaluate((body) => body.style.overflow)).not.toBe('hidden');
    const mainWithNews = (await page.locator('.portfolio-dashboard-main').boundingBox())!;
    const panelBox = (await newsPanel.boundingBox())!;
    if (viewport.name === 'desktop') {
      expect(panelBox.x).toBeGreaterThanOrEqual(mainWithNews.x + mainWithNews.width);
      const canvasScale = await page
        .locator('.portfolio-canvas')
        .evaluate((canvas) => canvas.getBoundingClientRect().width / canvas.clientWidth);
      expect((panelBox.y - mainWithNews.y) / canvasScale).toBeCloseTo(32, 0);
    } else {
      expect(panelBox.y).toBeGreaterThanOrEqual(mainWithNews.y + mainWithNews.height);
    }
    await newsPanel.evaluate(async (panel) => {
      await Promise.all(panel.getAnimations().map((animation) => animation.finished));
    });
    await page.screenshot({ path: testInfo.outputPath('news-sidebar-expanded.png') });
    await newsPanel.getByRole('button', { name: '소식 접기' }).click();
    await expect(newsPanel).toBeHidden();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'false');
    await expect(newsTab).toBeFocused();
    await page.locator('.portfolio-dashboard-layout').evaluate(async (layout) => {
      await Promise.all(
        layout.getAnimations({ subtree: true }).map((animation) => animation.finished),
      );
    });
    const mainWithoutNews = (await page.locator('.portfolio-dashboard-main').boundingBox())!;
    if (viewport.name === 'desktop')
      expect(mainWithoutNews.width).toBeGreaterThan(mainWithNews.width);
    await newsTab.press('Enter');
    await expect(newsPanel).toBeVisible();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'true');
    await newsTab.press('Space');
    await expect(newsPanel).toBeHidden();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'false');
    const summary = page.getByLabel('자산 요약');
    await expect(summary.getByText('누적 실현손익', { exact: true })).toBeVisible();
    await expect(summary.getByText('로그인 후 확인할 수 있어요', { exact: true })).toBeVisible();
    await expect(summary.getByText('현재 보유 수량 기준', { exact: true })).toBeVisible();
    await expect(summary.getByText('매도로 확정된 손익', { exact: true })).toBeVisible();
    const trend = page.getByRole('region', { name: '자산 추이', exact: true });
    await expect(trend.getByText('잠금', { exact: true })).toBeVisible();
    await expect(trend.getByText('로그인 후 자산 추이를 확인할 수 있어요')).toBeVisible();
    await expect(trend.getByRole('link', { name: '로그인하기' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2F',
    );
    await expect(trend.getByRole('img')).toHaveCount(0);
    await expect(trend.getByRole('button', { name: '자산 추이 새로고침' })).toHaveCount(0);
    for (const width of [320, 390, 620, 768, 1000, 1440]) {
      await page.setViewportSize({ width, height: 700 });
      await newsTab.click();
      await expect(newsPanel).toBeVisible();
      await page.locator('.portfolio-dashboard-layout').evaluate(async (layout) => {
        await Promise.all(
          layout.getAnimations({ subtree: true }).map((animation) => animation.finished),
        );
      });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      await newsTab.click();
      await expect(newsPanel).toBeHidden();
      await page.locator('.portfolio-dashboard-layout').evaluate(async (layout) => {
        await Promise.all(
          layout.getAnimations({ subtree: true }).map((animation) => animation.finished),
        );
      });
      expect(
        await page.evaluate(
          () =>
            document.documentElement.scrollWidth <= innerWidth &&
            [
              ...document.querySelectorAll(
                '.portfolio-overview dd, .portfolio-trend-locked, .portfolio-composition, .portfolio-holdings',
              ),
            ].every((element) => element.scrollWidth <= element.clientWidth + 1),
        ),
        `비회원 포트폴리오가 ${width}px에서 잘리지 않아야 합니다.`,
      ).toBe(true);
      const headerSize = await page.locator('.service-header').evaluate((header) => {
        const box = header.getBoundingClientRect();
        return {
          height: box.height,
          overflow: header.scrollWidth > header.clientWidth,
          itemsFit: [
            ...header.querySelectorAll(
              '.service-brand, .service-header-tab, .service-header-actions',
            ),
          ].every((item) => {
            const bounds = item.getBoundingClientRect();
            return (
              bounds.top >= box.top &&
              bounds.bottom <= box.bottom + 1 &&
              bounds.left >= box.left &&
              bounds.right <= box.right + 1
            );
          }),
        };
      });
      expect(headerSize.height).toBe(64);
      expect(headerSize.overflow).toBe(false);
      expect(headerSize.itemsFit).toBe(true);
    }
    await page.setViewportSize(viewport);
    const summaryGaps = await summary.locator(':scope > div').evaluateAll((items) =>
      items.map((item) => {
        const label = item.querySelector('dt')!.getBoundingClientRect();
        const value = (item.querySelector('.portfolio-balance-value') ??
          item.querySelector(':scope > dd'))!.getBoundingClientRect();
        return value.top - label.bottom;
      }),
    );
    for (const gap of summaryGaps) {
      expect(gap).toBeGreaterThanOrEqual(0);
      expect(gap).toBeLessThanOrEqual(24);
    }
    await expect(summary.locator('.portfolio-summary-locked')).toHaveCSS('font-size', '14px');
    await summary.screenshot({
      path: testInfo.outputPath('guest-summary-restored.png'),
      animations: 'disabled',
    });
    await page.screenshot({ path: testInfo.outputPath('guest-portfolio.png'), fullPage: true });
    await page.screenshot({
      path: testInfo.outputPath('guest-notice.png'),
      clip: { x: 0, y: 0, width: viewport.width, height: 340 },
    });
    await page
      .locator('.service-header')
      .screenshot({ path: testInfo.outputPath('guest-header.png') });
    await page.getByRole('button', { name: '거래 내역' }).click();
    const history = page.getByRole('region', { name: '거래 내역', exact: true });
    await expect(history.getByText('이 기간에 표시할 내역이 없어요')).toBeVisible();
    await history.screenshot({ path: testInfo.outputPath('guest-history.png') });
    await page.getByRole('button', { name: /^보유 자산/ }).click();
    const holdingTrigger = page.getByRole('button', { name: '삼성전자 보유 상세' });
    await holdingTrigger.click();
    const detail = page.getByRole('dialog', { name: '삼성전자 보유 상세' });
    await expect(detail).toBeVisible();
    await expect(detail.getByRole('complementary', { name: '소식' })).toHaveCount(0);
    await expect(detail.getByRole('link', { name: /종목 정보 보기/ })).toHaveAttribute(
      'href',
      '/stocks/1',
    );
    await expect(detail.getByRole('button', { name: '매수', exact: true })).toBeVisible();
    await expect(detail.getByRole('button', { name: '매도', exact: true })).toBeVisible();
    await expect(detail.getByRole('heading', { name: '거래 내역', exact: true })).toBeVisible();
    await expect(detail.getByText('이 기간에 표시할 내역이 없어요')).toBeVisible();
    await detail.getByRole('region', { name: '거래 내역', exact: true }).screenshot({
      path: testInfo.outputPath('guest-history-refined.png'),
      animations: 'disabled',
    });
    await detail.screenshot({
      path: testInfo.outputPath('guest-detail.png'),
      animations: 'disabled',
    });
    await page.screenshot({
      path: testInfo.outputPath('guest-detail-centered.png'),
      animations: 'disabled',
    });
    expect(
      await detail.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return (
          Math.abs(
            bounds.left -
              (document.documentElement.getBoundingClientRect().width - bounds.width) / 2,
          ) <= 1 && Math.abs(bounds.top - (innerHeight - bounds.height) / 2) <= 1
        );
      }),
    ).toBe(true);
    await detail.getByRole('button', { name: '매수', exact: true }).click();
    const trade = page.getByRole('dialog', { name: '거래 기록' });
    await expect(trade.getByLabel('거래일', { exact: true })).toBeFocused();
    await expect(trade.getByLabel('거래 단가 (원)')).toHaveValue('75200');
    await trade.getByLabel('수량 (주)').fill('2');
    await trade.getByLabel('거래 단가 (원)').fill('76000');
    await trade.getByRole('button', { name: '매수 기록하기' }).click();
    await expect(detail.getByText('12주', { exact: true })).toBeVisible();
    await expect(
      detail.locator('.portfolio-history-amount').getByText('152,000원', { exact: true }),
    ).toBeVisible();
    await detail.getByRole('button', { name: '매도', exact: true }).click();
    await expect(trade.getByLabel('거래 단가 (원)')).toHaveValue('75200');
    await trade.getByLabel('수량 (주)').fill('3');
    await trade.getByLabel('거래 단가 (원)').fill('78000');
    await trade.getByRole('button', { name: '매도 기록하기' }).click();
    await expect(detail.getByText('9주', { exact: true })).toBeVisible();
    await expect(detail.getByRole('button', { name: '매수', exact: true })).toBeEnabled();
    await page.screenshot({
      path: testInfo.outputPath('guest-trades.png'),
      animations: 'disabled',
    });
    await page.keyboard.press('Escape');
    await expect(holdingTrigger).toBeFocused();
    const assetTrigger = page.getByRole('button', { name: '거래 기록', exact: true });
    await assetTrigger.click();
    const assetEditor = page.getByRole('dialog', { name: '거래 기록' });
    await expect(assetEditor).toBeVisible();
    await expect(assetEditor.getByRole('searchbox')).toBeFocused();
    expect(
      await assetEditor.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return (
          Math.abs(
            bounds.left -
              (document.documentElement.getBoundingClientRect().width - bounds.width) / 2,
          ) <= 1 &&
          Math.abs(bounds.top - (innerHeight - bounds.height) / 2) <= 1 &&
          element.scrollWidth <= element.clientWidth
        );
      }),
    ).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('guest-asset-add-centered.png') });
    await page.keyboard.press('Escape');
    await expect(assetTrigger).toBeFocused();
    await page.reload();
    await page.getByRole('button', { name: '거래 내역' }).click();
    await expect(
      history.locator('.portfolio-history-amount').getByText('152,000원', { exact: true }),
    ).toBeVisible();
    await expect(
      history.locator('.portfolio-history-amount').getByText('234,000원', { exact: true }),
    ).toBeVisible();
    expect(memberRequests).toEqual([]);
  });

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
    let largeChart = false;
    let chartState: 'normal' | 'single' | 'empty' | 'error' = 'normal';
    let pendingPerformance: Promise<void> | null = null;
    let pendingStockSearch: Promise<void> | null = null;
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await page.route('**/api/**', async (route) => {
      const url = new URL(route.request().url());
      const path = url.pathname;
      const fulfill = (body: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
      const contentStock = stocks.find((stock) => path === `/api/assets/${stock.assetId}/contents`);
      if (contentStock)
        return fulfill({
          assetId: contentStock.assetId,
          assetCode: contentStock.assetCode,
          assetName: contentStock.name,
          contents: Array.from({ length: 15 }, (_, index) => ({
            id: index + 1,
            assetId: contentStock.assetId,
            sourceType: 'OTHER',
            title: `보유 종목과 연결된 산업 동향과 주요 소식 ${index + 1}`,
            url: `https://example.test/news/${index + 1}`,
            imageUrl: null,
            createdAt: `${today}T01:00:00Z`,
          })),
        });
      if (path === '/api/users/me')
        return fulfill({
          id: 1,
          email: 'preview@example.test',
          nickname: '미리보기',
          role: 'USER',
        });
      if (path === '/api/stocks/1/price')
        return fulfill({ ...stocks[0], baseDate: today, closePrice: stocks[0].closePrice });
      if (path === '/api/auth/csrf')
        return fulfill({ token: 'preview', headerName: 'X-CSRF-TOKEN' });
      if (path === '/api/users/me/portfolio/analysis')
        return fulfill({
          status: 'EMPTY',
          lastCheckOutcome: null,
          analysisId: null,
          matchesCurrentPortfolio: false,
          lastSucceededAt: null,
          lastFailedAt: null,
          failureCode: null,
          changeReason: null,
          analysis: null,
        });
      if (path === '/api/users/me/portfolio')
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
      if (path === '/api/users/me/portfolio/performance') {
        await pendingPerformance;
        if (chartState === 'error') return fulfill({ message: '추이 조회 실패' }, 500);
        return fulfill({
          status: 'READY',
          reason: null,
          baseDate: points.at(-1)!.baseDate,
          previousBaseDate: points.at(-2)!.baseDate,
          points:
            chartState === 'empty' || chartState === 'single'
              ? []
              : largeChart
                ? points.map((point) => ({
                    ...point,
                    totalPurchaseAmount: 123456789012,
                    totalEvaluationAmount: 98765432109876,
                    totalProfitLoss: 98641975320864,
                    totalReturnRate: 79876.123,
                  }))
                : points,
          currentPoint:
            chartState === 'empty'
              ? null
              : {
                  baseDate: today,
                  priceBaseDate: today,
                  totalPurchaseAmount: sum('averagePurchasePrice'),
                  totalEvaluationAmount: sum('closePrice'),
                  totalProfitLoss: sum('closePrice') - sum('averagePurchasePrice'),
                  totalReturnRate: 5.68,
                  dailyProfitLoss: null,
                  dailyReturnRate: null,
                },
        });
      }
      if (path === '/api/portfolio/histories') return fulfill(history);
      if (path === '/api/stocks') {
        if (pendingStockSearch) await pendingStockSearch;
        return fulfill({ stocks });
      }
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
    const newsTab = page.getByRole('button', { name: '소식', exact: true });
    const newsPanel = page.getByRole('complementary', { name: '보유 자산 소식' });
    await expect(newsPanel).toBeVisible();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByRole('dialog', { name: '보유 자산 소식' })).toHaveCount(0);
    expect(await page.locator('body').evaluate((body) => body.style.overflow)).not.toBe('hidden');
    const mainWithNews = (await page.locator('.portfolio-dashboard-main').boundingBox())!;
    const panelBox = (await newsPanel.boundingBox())!;
    if (viewport.name === 'desktop') {
      expect(panelBox.x).toBeGreaterThanOrEqual(mainWithNews.x + mainWithNews.width);
      const canvasScale = await page
        .locator('.portfolio-canvas')
        .evaluate((canvas) => canvas.getBoundingClientRect().width / canvas.clientWidth);
      expect((panelBox.y - mainWithNews.y) / canvasScale).toBeCloseTo(32, 0);
    } else {
      expect(panelBox.y).toBeGreaterThanOrEqual(mainWithNews.y + mainWithNews.height);
    }
    await newsPanel.evaluate(async (panel) => {
      await Promise.all(panel.getAnimations().map((animation) => animation.finished));
    });
    await page.screenshot({ path: testInfo.outputPath('news-sidebar-expanded.png') });
    await newsPanel.getByRole('button', { name: '소식 접기' }).click();
    await expect(newsPanel).toBeHidden();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'false');
    await expect(newsTab).toBeFocused();
    await page.locator('.portfolio-dashboard-layout').evaluate(async (layout) => {
      await Promise.all(
        layout.getAnimations({ subtree: true }).map((animation) => animation.finished),
      );
    });
    const mainWithoutNews = (await page.locator('.portfolio-dashboard-main').boundingBox())!;
    if (viewport.name === 'desktop')
      expect(mainWithoutNews.width).toBeGreaterThan(mainWithNews.width);
    await newsTab.press('Enter');
    await expect(newsPanel).toBeVisible();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'true');
    await newsTab.press('Space');
    await expect(newsPanel).toBeHidden();
    await expect(newsTab).toHaveAttribute('aria-expanded', 'false');
    // Laptop widths scale the same desktop canvas, including the news sidebar.
    let desktopProportions: number[] | undefined;
    for (const size of [
      { width: 1440, height: 900 },
      { width: 1024, height: 768 },
      { width: 1280, height: 800 },
      { width: 1470, height: 956 },
      { width: 1920, height: 1080 },
    ]) {
      await page.setViewportSize(size);
      await newsTab.click();
      await expect(newsPanel).toBeVisible();
      await page.locator('.portfolio-dashboard-layout').evaluate(async (layout) => {
        await Promise.all(
          layout.getAnimations({ subtree: true }).map((animation) => animation.finished),
        );
      });
      const desktopLayout = await page.locator('.portfolio-dashboard-main').evaluate((main) => {
        const cards = [...main.querySelectorAll('.portfolio-dashboard-grid > section')].map(
          (card) => card.getBoundingClientRect(),
        );
        const summaryItems = [...main.querySelectorAll('.portfolio-overview > div')].map((item) =>
          item.getBoundingClientRect(),
        );
        const table = main.querySelector('.portfolio-table-wrap table')!;
        const row = main.querySelector('.portfolio-holding-row')!;
        const canvas = main.closest('.portfolio-canvas')!;
        const scale = canvas.getBoundingClientRect().width / canvas.clientWidth;
        const donut = main.querySelector('.portfolio-donut-wrap')!.getBoundingClientRect();
        const breakdown = main
          .querySelector('.portfolio-composition-breakdown')!
          .getBoundingClientRect();
        return {
          compositionFits:
            donut.width / scale >= 135 &&
            Math.abs(donut.width - donut.height) < 1 &&
            breakdown.left >= donut.right + 12 * scale &&
            [...main.querySelectorAll('.portfolio-category-list > div > div')].every(
              (item) => item.scrollWidth <= item.clientWidth + 1,
            ),
          cardsSideBySide:
            cards.length === 2 &&
            Math.abs(cards[0].top - cards[1].top) < 1 &&
            cards[1].left >= cards[0].right,
          summaryInOneRow: summaryItems.every(
            (item) => Math.abs(item.top - summaryItems[0].top) < 1,
          ),
          tableVisible:
            getComputedStyle(table.querySelector('thead')!).position !== 'absolute' &&
            getComputedStyle(row).display === 'table-row',
          pageFits: document.documentElement.scrollWidth <= innerWidth,
          controlsFit: [
            ...main.querySelectorAll(
              '.portfolio-trend-controls, .portfolio-composition-content, .portfolio-overview dd',
            ),
          ].every((item) => item.scrollWidth <= item.clientWidth + 1),
        };
      });
      expect(
        desktopLayout,
        `소식 패널이 열린 ${size.width}px 노트북 화면도 데스크톱 배치를 유지해야 합니다.`,
      ).toEqual({
        compositionFits: true,
        cardsSideBySide: true,
        summaryInOneRow: true,
        tableVisible: true,
        pageFits: true,
        controlsFit: true,
      });
      const proportions = await page.locator('.portfolio-canvas').evaluate((canvas) => {
        const bounds = canvas.getBoundingClientRect();
        return [
          ...canvas.querySelectorAll(
            '.portfolio-overview, .portfolio-dashboard-grid > section, .portfolio-donut-wrap, .portfolio-table-wrap',
          ),
        ].flatMap((element) => {
          const rect = element.getBoundingClientRect();
          return [
            (rect.left - bounds.left) / bounds.width,
            (rect.top - bounds.top) / bounds.width,
            rect.width / bounds.width,
            rect.height / bounds.width,
          ];
        });
      });
      const bodyWidth = await page
        .locator('.portfolio-canvas')
        .evaluate((canvas) => canvas.clientWidth);
      if (bodyWidth <= 1325) {
        if (!desktopProportions) desktopProportions = proportions;
        for (const [index, value] of proportions.entries()) {
          expect(
            value,
            `${size.width}px에서도 카드와 도넛의 배치·비율을 유지해야 합니다.`,
          ).toBeCloseTo(desktopProportions[index], 2);
        }
      }
      if (size.width === 1024) {
        await page.getByRole('button', { name: '자산 추이 크게 보기', exact: true }).click();
        const detail = page.getByRole('dialog', { name: '자산 추이 상세' });
        await expect(detail).toBeVisible();
        const bounds = (await detail.boundingBox())!;
        expect(bounds.x).toBeGreaterThanOrEqual(0);
        expect(bounds.x + bounds.width).toBeLessThanOrEqual(size.width);
        expect(bounds.y).toBeGreaterThanOrEqual(0);
        expect(bounds.y + bounds.height).toBeLessThanOrEqual(size.height);
        await detail.getByRole('button', { name: '자산 추이 상세 닫기' }).click();
      }
      if (viewport.name === 'desktop' && size.width === 1280) {
        await page.screenshot({ path: testInfo.outputPath('laptop-1280.png'), fullPage: true });
        await page.locator('.portfolio-analysis').screenshot({
          path: testInfo.outputPath('laptop-asset-composition.png'),
        });
      }
      await newsPanel.getByRole('button', { name: '소식 접기' }).click();
      await expect(newsPanel).toBeHidden();
      const cards = await page
        .locator('.portfolio-dashboard-grid > section')
        .evaluateAll((elements) => elements.map((element) => element.getBoundingClientRect().top));
      expect(Math.abs(cards[0] - cards[1])).toBeLessThan(1);
    }
    await page.setViewportSize(viewport);
    if (viewport.name === 'mobile') {
      const mobileLayout = await page.locator('.portfolio-canvas').evaluate((canvas) => {
        const cards = [...canvas.querySelectorAll('.portfolio-dashboard-grid > section')].map(
          (card) => card.getBoundingClientRect(),
        );
        const donut = canvas.querySelector('.portfolio-donut-wrap')!.getBoundingClientRect();
        return {
          cardsStacked: cards[1].top >= cards[0].bottom,
          fullSizeDonut: donut.width >= 135,
          centeredDonut:
            Math.abs(donut.left + donut.width / 2 - (cards[1].left + cards[1].width / 2)) < 1,
          tableReflows:
            getComputedStyle(canvas.querySelector('.portfolio-holding-row')!).display === 'grid',
          pageFits: document.documentElement.scrollWidth <= innerWidth,
        };
      });
      expect(mobileLayout).toEqual({
        cardsStacked: true,
        fullSizeDonut: true,
        centeredDonut: true,
        tableReflows: true,
        pageFits: true,
      });
      await page.locator('.portfolio-page').screenshot({
        path: testInfo.outputPath('mobile-responsive.png'),
      });
    }
    const summary = page.getByLabel('자산 요약');
    await expect(summary.getByText('현재 보유 수량 기준', { exact: true })).toBeVisible();
    await expect(summary.getByText('매도로 확정된 손익', { exact: true })).toBeVisible();
    await summary.screenshot({
      path: testInfo.outputPath('member-summary-restored.png'),
      animations: 'disabled',
    });
    await expect(page.getByRole('img', { name: /포트폴리오 자산 추이/ })).toBeVisible();
    const navigation = page.getByRole('navigation', { name: '주요 메뉴' });
    const selectedMenu = navigation.getByRole('link', { name: '포트폴리오', exact: true });
    expect(
      await selectedMenu.evaluate((element) => getComputedStyle(element).borderBottomWidth),
    ).toBe('0px');
    expect(await selectedMenu.evaluate((element) => getComputedStyle(element).fontWeight)).toBe(
      '700',
    );
    expect(
      await selectedMenu.evaluate((element) => getComputedStyle(element).fontFamily),
    ).toContain('Noto Sans KR');
    const otherMenu = navigation.getByRole('link', { name: '소식', exact: true });
    expect(await otherMenu.evaluate((element) => getComputedStyle(element).fontWeight)).toBe('600');
    await expect(page.getByRole('link', { name: '마이페이지', exact: true })).toHaveAttribute(
      'href',
      '/mypage',
    );
    const compactChart = page.locator('.portfolio-return-dashboard.is-compact');
    expect(
      await compactChart
        .locator('.evaluation.is-gain')
        .first()
        .evaluate((element) => getComputedStyle(element).stroke),
    ).toBe('rgb(217, 73, 89)');
    expect(
      await compactChart
        .locator('.evaluation.is-loss')
        .first()
        .evaluate((element) => getComputedStyle(element).stroke),
    ).toBe('rgb(49, 116, 205)');

    const controlHeights = await compactChart
      .locator('.portfolio-trend-controls select, .portfolio-trend-controls button')
      .evaluateAll((controls) =>
        controls.map((control) => Number.parseFloat(getComputedStyle(control).height)),
      );
    for (const height of controlHeights) expect(height).toBeCloseTo(44, 1);
    expect(new Set(controlHeights).size).toBe(1);
    const cardHeights = () =>
      page
        .locator('.portfolio-dashboard-grid > section')
        .evaluateAll((cards) => cards.map((card) => card.getBoundingClientRect().height));
    const initialHeights = await cardHeights();
    const initialPlotHeight = (await compactChart.locator('.portfolio-return-chart').boundingBox())!
      .height;
    for (const nextState of ['single', 'empty', 'error', 'normal'] as const) {
      chartState = nextState;
      let releasePerformance!: () => void;
      pendingPerformance = new Promise<void>((resolve) => {
        releasePerformance = resolve;
      });
      await compactChart.getByRole('button', { name: '자산 추이 새로고침' }).click();
      await expect(compactChart.getByRole('status')).toContainText('불러오고');
      expect(await cardHeights()).toEqual(initialHeights);
      releasePerformance();
      pendingPerformance = null;
      await expect(compactChart.getByRole('button', { name: '자산 추이 새로고침' })).toBeEnabled();
      if (nextState === 'single' || nextState === 'normal') {
        await expect(compactChart.getByRole('img')).toBeVisible();
        expect((await compactChart.locator('.portfolio-return-chart').boundingBox())!.height).toBe(
          initialPlotHeight,
        );
      } else if (nextState === 'empty') {
        await expect(compactChart.getByRole('status')).toContainText('표시할 추이가 없어요');
      } else {
        await expect(compactChart.getByRole('alert')).toBeVisible();
      }
      expect(await cardHeights()).toEqual(initialHeights);
    }
    await compactChart.getByLabel('자산 추이 기간', { exact: true }).selectOption('1M');
    await expect(compactChart.getByRole('img')).toBeVisible();
    await expect(compactChart.getByRole('button', { name: '자산 추이 새로고침' })).toBeEnabled();
    expect(await cardHeights()).toEqual(initialHeights);
    await compactChart.getByLabel('자산 추이 기간', { exact: true }).selectOption('YTD');
    await expect(compactChart.getByRole('button', { name: '자산 추이 새로고침' })).toBeEnabled();
    await page
      .locator('.portfolio-analysis')
      .screenshot({ path: testInfo.outputPath('asset-analysis.png') });
    const trendTitle = compactChart.getByRole('button', { name: '자산 추이', exact: true });
    await trendTitle.click();
    const trendDetail = page.getByRole('dialog', { name: '자산 추이 상세' });
    await expect(trendDetail).toBeVisible();
    await trendDetail.getByRole('button', { name: '평가손익', exact: true }).click();
    await expect(trendDetail.getByRole('img', { name: /평가손익/ })).toBeVisible();
    await trendDetail.getByRole('button', { name: '평가수익률', exact: true }).click();
    await expect(trendDetail.getByRole('img', { name: /평가수익률/ })).toBeVisible();
    await trendDetail.getByLabel('자산 추이 조회 날짜').fill(points[0].baseDate);
    await trendDetail.getByLabel('자산 추이 조회 날짜').fill(today);
    await expect(trendDetail.getByLabel('자산 추이 조회 날짜')).toHaveValue(today);
    await expect(trendDetail.locator('.portfolio-trend-day-trades')).toHaveCount(0);
    await expect(trendDetail.locator('.portfolio-trend-trade-marker')).toHaveCount(0);
    await expect(trendDetail.getByText('매수·매도', { exact: true })).toHaveCount(0);
    await trendDetail.getByRole('button', { name: '평가금액', exact: true }).click();
    const trendSvg = trendDetail.locator('.portfolio-return-chart');
    const chartBox = (await trendSvg.boundingBox())!;
    const chartWidth = await trendSvg.evaluate(
      (svg) => (svg as SVGSVGElement).viewBox.baseVal.width,
    );
    for (const point of [points[0], points[20], points[44]]) {
      const dateRatio =
        (Date.parse(point.baseDate) - Date.parse(points[0].baseDate)) /
        (Date.parse(today) - Date.parse(points[0].baseDate));
      await page.mouse.move(
        chartBox.x + ((58 + dateRatio * (chartWidth - 70)) / chartWidth) * chartBox.width,
        chartBox.y + chartBox.height / 2,
      );
      const tooltip = trendDetail.getByRole('tooltip');
      await expect(tooltip).toBeVisible();
      await expect(tooltip.locator('time')).toHaveAttribute('datetime', point.baseDate);
      await expect(trendDetail.getByLabel('자산 추이 조회 날짜')).toHaveValue(today);
      await expect(trendDetail.locator('.portfolio-trend-date-values')).toContainText(
        `${sum('closePrice').toLocaleString('ko-KR')}원`,
      );
    }
    await trendDetail.screenshot({ path: testInfo.outputPath('asset-trend-date-tooltip.png') });
    await page.mouse.move(chartBox.x, chartBox.y - 10);
    await expect(trendDetail.getByRole('tooltip')).toHaveCount(0);
    const trendGeometry = () =>
      trendDetail.evaluate((dialog) => {
        const bounds = (element: Element) => {
          const { x, y, width, height } = element.getBoundingClientRect();
          return [x, y, width, height].map((value) => Math.round(value * 10) / 10);
        };
        return {
          dialog: bounds(dialog),
          chart: bounds(dialog.querySelector('.portfolio-return-chart')!),
        };
      });
    const stableGeometry = await trendGeometry();
    // 날짜를 반복 선택해도 거래 내역 없이 자산 값만 바뀌며 팝업과 그래프는 움직이지 않는다.
    for (let repeat = 0; repeat < 3; repeat++) {
      for (const point of [points[0], points[20], points[44], { baseDate: today }]) {
        const dateRatio =
          (Date.parse(point.baseDate) - Date.parse(points[0].baseDate)) /
          (Date.parse(today) - Date.parse(points[0].baseDate));
        await page.mouse.click(
          chartBox.x + ((58 + dateRatio * (chartWidth - 70)) / chartWidth) * chartBox.width,
          chartBox.y + chartBox.height / 2,
        );
        await expect(trendDetail.getByLabel('자산 추이 조회 날짜')).toHaveValue(point.baseDate);
        expect(await trendGeometry()).toEqual(stableGeometry);
      }
    }
    await expect(trendDetail.locator('.portfolio-trend-day-trades')).toHaveCount(0);
    await expect(trendDetail.locator('.portfolio-trend-trade-marker')).toHaveCount(0);
    await expect(trendDetail.getByText(/평가금액은 종가 기준/)).toHaveCount(0);
    await expect(trendDetail.getByText('이 날짜의 매수·매도 내역이 없어요.')).toHaveCount(0);
    await expect(trendDetail.getByLabel('거래일 바로 보기')).toHaveCount(0);
    await trendDetail.screenshot({
      path: testInfo.outputPath('asset-trend-detail.png'),
      animations: 'disabled',
    });
    for (const width of [320, 390, 620, 768, 1440]) {
      await page.setViewportSize({ width, height: viewport.height });
      expect(
        await trendDetail.evaluate(
          (dialog) =>
            dialog.scrollWidth <= dialog.clientWidth &&
            [
              ...dialog.querySelectorAll(
                '.portfolio-trend-controls, .portfolio-trend-date-values, .portfolio-trend-detail-heading',
              ),
            ].every((element) => element.scrollWidth <= element.clientWidth + 1),
        ),
        `자산 추이 상세가 ${width}px에서 잘리지 않아야 합니다.`,
      ).toBe(true);
    }
    await page.setViewportSize(viewport);
    await page.keyboard.press('Escape');
    await expect(trendDetail).toHaveCount(0);
    await expect(trendTitle).toBeFocused();

    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.screenshot({
      path: testInfo.outputPath('portfolio.png'),
      fullPage: true,
    });
    await page.locator('.service-header').screenshot({ path: testInfo.outputPath('header.png') });
    for (const width of [320, 390, 620, 621, 768, 1000, 1024, 1100, 1440]) {
      await page.setViewportSize({ width, height: 600 });
      expect(
        await page.evaluate(() => {
          const dashboard = document.querySelector('.portfolio-dashboard-grid')!;
          const graph = dashboard.getBoundingClientRect();
          return (
            document.documentElement.scrollWidth <= innerWidth &&
            dashboard.scrollWidth <= dashboard.clientWidth &&
            [
              ...document.querySelectorAll(
                '.portfolio-trend-controls, .portfolio-composition, .portfolio-holdings',
              ),
            ].every((element) => element.scrollWidth <= element.clientWidth + 1) &&
            graph.right <= innerWidth &&
            [...document.querySelectorAll('.portfolio-overview dd')].every(
              (amount) => amount.scrollWidth <= amount.clientWidth + 1,
            )
          );
        }),
        `포트폴리오 본문이 ${width}px에서 잘리지 않아야 합니다.`,
      ).toBe(true);
      const headerSize = await page.locator('.service-header').evaluate((header) => {
        const box = header.getBoundingClientRect();
        return {
          height: box.height,
          overflow: header.scrollWidth > header.clientWidth,
          itemsFit: [
            ...header.querySelectorAll(
              '.service-brand, .service-header-tab, .service-header-actions',
            ),
          ].every((item) => {
            const bounds = item.getBoundingClientRect();
            return (
              bounds.top >= box.top &&
              bounds.bottom <= box.bottom + 1 &&
              bounds.left >= box.left &&
              bounds.right <= box.right + 1
            );
          }),
        };
      });
      expect(headerSize.height).toBe(64);
      expect(headerSize.overflow).toBe(false);
      expect(headerSize.itemsFit).toBe(true);
    }
    await page.setViewportSize({ width: 1440, height: 1050 });
    for (const [mainWidth, compositionWidth] of [
      [760, '100%'],
      [1200, '280px'],
    ] as const) {
      const constrainedDashboard = await page.addStyleTag({
        content: `.portfolio-dashboard-main { width: ${mainWidth}px; max-width: 100%; } .portfolio-composition { width: ${compositionWidth}; max-width: 100%; }`,
      });
      const composition = page.locator('.portfolio-composition');
      expect(
        await composition.evaluate((element) => {
          const content = element.querySelector('.portfolio-composition-content')!;
          const box = content.getBoundingClientRect();
          return (
            content.scrollWidth <= content.clientWidth + 1 &&
            [...element.querySelectorAll('.portfolio-donut-wrap, dt, dd')].every((item) => {
              const rect = item.getBoundingClientRect();
              return rect.left >= box.left && rect.right <= box.right + 1;
            })
          );
        }),
        '창이 넓어도 본문 공간이 좁아지면 자산 구성의 차트와 비율이 카드 안에 있어야 합니다.',
      ).toBe(true);
      await constrainedDashboard.evaluate((element) => element.parentNode?.removeChild(element));
    }
    await page.setViewportSize(viewport);
    const holdingTrigger = page.getByRole('button', { name: '삼성전자 보유 상세' });
    const holdingsRegion = page.getByRole('region', { name: '보유 자산', exact: true });
    await expect(holdingsRegion.getByRole('heading')).toHaveCount(0);
    const holdingRow = page.locator('.portfolio-holding-row').first();
    await holdingRow.hover();
    expect(await holdingRow.evaluate((element) => getComputedStyle(element).cursor)).toBe(
      'pointer',
    );
    expect(
      await holdingTrigger.evaluate((element) => getComputedStyle(element).textDecorationLine),
    ).toBe('none');
    const triggerStyle = await holdingTrigger.evaluate((element) => {
      const text = getComputedStyle(element);
      const arrow = getComputedStyle(element.querySelector('svg')!);
      return { display: text.display, color: text.color, arrowColor: arrow.stroke };
    });
    expect(triggerStyle.display).toBe('inline-flex');
    expect(triggerStyle.arrowColor).toBe(triggerStyle.color);
    await holdingRow.locator('td').nth(1).click();
    const detail = page.getByRole('dialog', { name: '삼성전자 보유 상세' });
    await expect(detail).toBeVisible();
    await expect(detail.getByRole('heading', { name: '거래 내역' })).toBeVisible();
    await expect(detail.locator('.portfolio-history-row')).toHaveCount(1);
    const filters = detail.locator('.portfolio-history-filters');
    await expect(filters).not.toHaveAttribute('open');
    await filters.locator('summary').click();
    await expect(detail.getByLabel('시작일')).toBeVisible();
    await detail.getByLabel('내역 유형').selectOption('SELL');
    await expect(detail.locator('.portfolio-history-row')).toHaveCount(0);
    await detail.getByLabel('내역 유형').selectOption('ALL');
    await expect(detail.locator('.portfolio-history-row')).toHaveCount(1);
    await filters.locator('summary').click();
    await detail.locator('summary[aria-label="보유정보 편집"]').click();
    await expect(detail.getByRole('button', { name: '보유정보 정정' })).toBeVisible();
    await expect(detail.getByRole('button', { name: '보유자산 제거' })).toBeVisible();
    await detail.locator('summary[aria-label="보유정보 편집"]').click();

    await page.screenshot({
      path: testInfo.outputPath('holding-detail.png'),
      animations: 'disabled',
      fullPage: false,
    });
    expect(await detail.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(
      true,
    );
    expect(
      await detail.evaluate((element) => {
        const dialog = element.getBoundingClientRect();
        const footer = element.querySelector('.portfolio-detail-footer')!.getBoundingClientRect();
        return dialog.bottom - footer.bottom <= 1;
      }),
      '내용이 적을 때 하단 버튼 아래에 빈 공간이 생기지 않아야 합니다.',
    ).toBe(true);
    await expect(detail.getByRole('complementary', { name: '소식' })).toHaveCount(0);
    for (const width of [320, 390, 620, 621, 768, 1000, 1440, 390]) {
      await page.setViewportSize({ width, height: 600 });
      expect(
        await detail.evaluate((element) => {
          const bounds = element.getBoundingClientRect();
          const scroll = element.querySelector('.portfolio-detail-scroll')!;
          const footer = element.querySelector('.portfolio-detail-footer')!.getBoundingClientRect();
          return (
            Math.abs(
              bounds.left -
                (document.documentElement.getBoundingClientRect().width - bounds.width) / 2,
            ) <= 1 &&
            Math.abs(bounds.top - (innerHeight - bounds.height) / 2) <= 1 &&
            bounds.width <= 720 &&
            bounds.height <= 760 &&
            element.scrollWidth <= element.clientWidth &&
            scroll.scrollWidth <= scroll.clientWidth &&
            footer.top >= 0 &&
            footer.bottom <= innerHeight
          );
        }),
        `종목 상세가 ${width}px에서 넘치지 않아야 합니다.`,
      ).toBe(true);
    }
    await page.setViewportSize(viewport);
    await detail.getByRole('button', { name: '매수', exact: true }).click();
    const nestedEditor = page.getByRole('dialog', { name: '거래 기록' });
    await expect(nestedEditor).toBeVisible();
    await expect(nestedEditor.getByLabel('거래일', { exact: true })).toBeFocused();
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
    await holdingTrigger.press('Enter');
    await expect(detail).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(holdingTrigger).toBeFocused();
    await holdingTrigger.press('Space');
    await expect(detail).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(holdingTrigger).toBeFocused();
    const trigger = page.getByRole('button', { name: '거래 기록', exact: true });
    await trigger.click();
    await expect(
      page.getByRole('dialog', { name: '거래 기록' }).getByRole('searchbox'),
    ).toBeFocused();
    await expect(
      page.getByRole('heading', { name: '기존 보유자산 등록', exact: true }),
    ).toHaveCount(0);
    await expect(
      page.getByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' }),
    ).toHaveCount(0);
    await expect(page.locator('dialog[open]')).toHaveCount(1);
    const dialog = page.getByRole('dialog', { name: '거래 기록' });
    await expect(dialog).toBeVisible();
    expect(
      await dialog.evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return (
          Math.abs(
            bounds.left -
              (document.documentElement.getBoundingClientRect().width - bounds.width) / 2,
          ) <= 1 &&
          Math.abs(bounds.top - (innerHeight - bounds.height) / 2) <= 1 &&
          element.scrollWidth <= element.clientWidth
        );
      }),
    ).toBe(true);
    const searchBounds = await dialog.boundingBox();
    expect(searchBounds!.height).toBeLessThanOrEqual(500);
    let finishStockSearch!: () => void;
    pendingStockSearch = new Promise<void>((resolve) => {
      finishStockSearch = resolve;
    });
    await dialog.getByRole('searchbox').fill('삼성');
    const stockLoading = dialog.getByRole('status');
    await expect(stockLoading).toHaveText('종목 검색 중');
    await expect(stockLoading.locator('.portfolio-stock-search-spinner')).toBeVisible();
    expect(await dialog.boundingBox()).toEqual(searchBounds);
    await dialog.screenshot({ path: testInfo.outputPath('stock-search-loading.png') });
    finishStockSearch();
    pendingStockSearch = null;
    await expect(dialog.getByRole('button', { name: /삼성전자/ })).toBeVisible();
    expect(await dialog.boundingBox()).toEqual(searchBounds);
    pendingStockSearch = new Promise<void>((resolve) => {
      finishStockSearch = resolve;
    });
    await dialog.getByRole('searchbox').fill('하이닉스');
    await expect(stockLoading).toHaveText('종목 검색 중');
    expect(await dialog.boundingBox()).toEqual(searchBounds);
    finishStockSearch();
    pendingStockSearch = null;
    await dialog.getByRole('button', { name: /삼성전자/ }).click();
    await expect(dialog.getByLabel('거래일', { exact: true })).toBeFocused();
    await expect(dialog.getByLabel('거래 단가 (원)')).toHaveValue('75200');
    await dialog.getByLabel('수량 (주)').fill('5');
    await dialog.getByLabel('거래 단가 (원)').fill('75200');
    await expect(dialog).toHaveCSS('scrollbar-width', 'none');
    const originalViewport = page.viewportSize()!;
    await page.setViewportSize({ width: originalViewport.width, height: 600 });
    await dialog.getByText('수수료·세금', { exact: false }).click();
    await dialog.getByLabel('세금 (원)', { exact: true }).fill('0');
    const submitTrade = dialog.getByRole('button', { name: '매수 기록하기' });
    await submitTrade.scrollIntoViewIfNeeded();
    await expect(submitTrade).toBeInViewport();
    expect(await dialog.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
    await dialog.getByText('수수료·세금', { exact: false }).click();
    await page.setViewportSize(originalViewport);
    await dialog.getByLabel('거래일', { exact: true }).focus();
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
    await page.getByRole('button', { name: '거래 내역' }).click();
    await expect(page.locator('.portfolio-history-row')).toHaveCount(2);
    await page.screenshot({
      path: testInfo.outputPath('history.png'),
      fullPage: true,
    });
    // 새 거래는 같은 거래일의 기존 기록보다 먼저 표시된다.
    await expect(page.locator('.portfolio-history-row').first().locator('time')).toContainText(
      today.replaceAll('-', '.'),
    );
    const historyList = page.locator('.portfolio-history-dashboard');
    await expect(historyList.locator('details')).toHaveCount(0);
    for (const row of await historyList.locator('.portfolio-history-row').all()) {
      expect(await row.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(
        true,
      );
      await expect(row.locator('time')).toBeVisible();
      await expect(row.locator('.portfolio-history-name')).toBeVisible();
      await expect(row.locator('.portfolio-history-amount > small')).toBeVisible();
    }
    const historyRow = page.locator('.portfolio-history-edit').first();
    await historyRow.focus();
    await page.keyboard.press('Enter');
    const edit = page.getByRole('dialog', { name: '거래 수정' });
    await expect(edit).toBeVisible();
    await expect(edit.getByLabel('수량 (주)')).toBeFocused();
    await expect(edit.getByLabel('수량 (주)')).toHaveValue('5');
    await expect(edit.getByRole('button', { name: '수정하기' })).toBeVisible();
    expect(await edit.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('transaction-edit.png') });
    await page.keyboard.press('Escape');
    await expect(edit).toHaveCount(0);
    await expect(historyRow).toBeFocused();
    await historyRow.click();
    await page.getByRole('button', { name: '거래 삭제' }).last().click();
    const confirm = page.getByRole('dialog', { name: '거래 삭제' });
    await expect(confirm).toBeVisible();
    await expect(confirm.getByRole('button', { name: '닫기' })).toBeFocused();
    expect(
      await confirm.evaluate((element) => {
        const box = element.getBoundingClientRect();
        return (
          box.width <= 460 &&
          element.scrollWidth <= element.clientWidth &&
          box.height < innerHeight &&
          Math.abs(
            box.left + box.width / 2 - document.documentElement.getBoundingClientRect().width / 2,
          ) <= 1 &&
          Math.abs(box.top + box.height / 2 - innerHeight / 2) <= 1
        );
      }),
      '삭제 확인창은 화면 중앙에 작게 표시되어야 합니다.',
    ).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('delete-confirm.png') });
    await confirm.getByRole('button', { name: '거래 삭제' }).click();
    await expect(confirm).toHaveCount(0);
    await expect(page.locator('.portfolio-history-row')).toHaveCount(1);
    await trigger.click();
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
    largeChart = true;
    await page.reload();
    const chart = page.getByLabel('자산 추이 그래프. 좌우 방향키로 날짜를 이동할 수 있습니다.');
    await chart.focus();
    for (const [key, count] of [
      ['ArrowLeft', 1],
      ['ArrowLeft', 45],
      ['ArrowRight', 22],
    ] as const) {
      for (let index = 0; index < count; index++) await chart.press(key);
      const tooltip = page.locator('.portfolio-return-tooltip');
      await expect(tooltip).toBeVisible();
      expect(
        await tooltip.evaluate((element) => {
          const box = element.getBoundingClientRect();
          return (
            [...element.querySelectorAll('dd, small')].every((value) => {
              const rect = value.getBoundingClientRect();
              return (
                rect.left >= box.left && rect.right <= box.right + 1 && rect.bottom <= box.bottom
              );
            }) &&
            element.scrollWidth <= element.clientWidth &&
            box.left >= 0 &&
            box.right <= innerWidth
          );
        }),
      ).toBe(true);
    }
    await page.screenshot({
      path: testInfo.outputPath('large-chart-tooltip.png'),
      animations: 'disabled',
    });
    expect(errors).toEqual([]);
  });
}
