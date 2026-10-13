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
      if (/users\/me\/portfolio|portfolio\/(histories|transactions)/.test(path)) {
        memberRequests.push(path);
        return fulfill({}, 401);
      }
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
    await page.getByRole('button', { name: '거래·변경 내역' }).click();
    const history = page.getByRole('region', { name: '거래·변경 내역', exact: true });
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
    await trade.getByLabel('수량 (주)').fill('2');
    await trade.getByLabel('거래 단가 (원)').fill('76000');
    await trade.getByRole('button', { name: '매수 기록하기' }).click();
    await expect(detail.getByText('12주', { exact: true })).toBeVisible();
    await expect(
      detail.locator('.portfolio-history-amount').getByText('152,000원', { exact: true }),
    ).toBeVisible();
    await detail.getByRole('button', { name: '매도', exact: true }).click();
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
    await page.reload();
    await page.getByRole('button', { name: '거래·변경 내역' }).click();
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
      if (path === '/api/users/me/portfolio/performance')
        return fulfill({
          status: 'READY',
          reason: null,
          baseDate: points.at(-1)!.baseDate,
          previousBaseDate: points.at(-2)!.baseDate,
          points: largeChart
            ? points.map((point) => ({
                ...point,
                totalPurchaseAmount: 123456789012,
                totalEvaluationAmount: 98765432109876,
                totalProfitLoss: 98641975320864,
                totalReturnRate: 79876.123,
              }))
            : points,
          currentPoint: {
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
    const otherMenu = navigation.getByRole('link', { name: '이슈', exact: true });
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
    await page.screenshot({
      path: testInfo.outputPath('holding-detail.png'),
      animations: 'disabled',
      fullPage: false,
    });
    expect(await detail.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(
      true,
    );
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
    const trigger = page.getByRole('button', { name: '자산 추가', exact: true });
    await trigger.click();
    await expect(
      page.getByRole('dialog', { name: '거래 기록' }).getByRole('searchbox'),
    ).toBeFocused();
    await expect(page.getByRole('button', { name: '기존 보유자산 등록', exact: true })).toHaveCount(
      0,
    );
    await page.getByRole('button', { name: '거래일을 모르면 현재 보유분으로 등록' }).click();
    await expect(page.locator('dialog[open]')).toHaveCount(1);
    await expect(page.getByRole('dialog', { name: '기존 보유자산 등록' })).toBeVisible();
    await expect(
      page.getByRole('dialog', { name: '기존 보유자산 등록' }).getByRole('searchbox'),
    ).toBeFocused();
    await page.getByRole('button', { name: '거래 기록으로 돌아가기' }).click();
    await expect(page.locator('dialog[open]')).toHaveCount(1);
    const dialog = page.getByRole('dialog', { name: '거래 기록' });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('searchbox').fill('삼성');
    await dialog.getByRole('button', { name: /삼성전자/ }).click();
    await expect(dialog.getByLabel('거래일', { exact: true })).toBeFocused();
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
