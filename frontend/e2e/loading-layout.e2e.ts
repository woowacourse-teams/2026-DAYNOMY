import { expect, test } from '@playwright/test';
import { accessibilityArticle, installAccessibilityFixtures } from './fixtures/accessibility';

declare global {
  interface Window {
    loadingLayoutShifts: { startTime: number; value: number }[];
  }
}

for (const viewport of [
  { width: 1440, height: 880 },
  { width: 390, height: 844 },
]) {
  for (const surface of ['news', 'portfolio'] as const) {
    test(`${surface} 로딩 후 푸터와 필터가 밀리지 않는다 (${viewport.width}px)`, async ({
      page,
    }, testInfo) => {
      await page.setViewportSize(viewport);
      await page.addInitScript(() => {
        localStorage.setItem('daynomy:service-started:v1', '1');
        window.loadingLayoutShifts = [];
        new PerformanceObserver((list) => {
          for (const entry of list.getEntries()) {
            const shift = entry as PerformanceEntry & { value: number; hadRecentInput: boolean };
            if (!shift.hadRecentInput)
              window.loadingLayoutShifts.push({ startTime: shift.startTime, value: shift.value });
          }
        }).observe({ type: 'layout-shift', buffered: true });
      });
      await installAccessibilityFixtures(page);
      await page.route('**/api/users/me', (route) =>
        route.fulfill({
          json: { id: 1, nickname: '레이아웃 확인', email: 'layout@example.invalid', role: 'USER' },
        }),
      );

      let releaseData!: () => void;
      const pending = new Promise<void>((resolve) => {
        releaseData = resolve;
      });
      if (surface === 'news') {
        await page.route('**/api/news/7', async (route) => {
          await pending;
          await route.fulfill({
            json: { ...accessibilityArticle, content: accessibilityArticle.content.repeat(20) },
          });
        });
      } else {
        await page.route('**/api/users/me/portfolio', async (route) => {
          await pending;
          await route.fulfill({
            json: {
              holdings: [
                {
                  assetId: 1,
                  assetCode: '005930',
                  name: '삼성전자',
                  category: 'STOCK',
                  market: 'KOSPI',
                  quantity: 1,
                  averagePurchasePrice: 70000,
                },
              ],
              totalRealizedProfitLoss: 0,
            },
          });
        });
        await page.route('**/api/assets/1/contents', (route) =>
          route.fulfill({
            json: {
              assetId: 1,
              assetCode: '005930',
              assetName: '삼성전자',
              contents: Array.from({ length: 12 }, (_, index) => ({
                id: index + 1,
                assetId: 1,
                sourceType: 'INTERNAL_NEWS',
                title: `삼성전자 소식 ${index + 1}`,
                url: `/news/${index + 1}`,
                imageUrl: null,
                createdAt: '2026-10-29T00:00:00Z',
              })),
            },
          }),
        );
      }

      await page.goto(surface === 'news' ? '/news/7' : '/news/contents?scope=portfolio&asset=1');
      const status = page.getByRole('status');
      await expect(status).toContainText('불러오는 중');
      await page.evaluate(() => document.fonts.ready);
      const footerBefore = await page.locator('.daynomy-footer').boundingBox();
      const toolbar = page.getByRole('group', { name: '콘텐츠 출처' });
      const toolbarBefore = (await toolbar.count()) ? await toolbar.boundingBox() : null;
      const controlsDisabled =
        surface === 'portfolio'
          ? await toolbar
              .getByRole('button')
              .evaluateAll(
                (buttons) =>
                  buttons.length > 0 &&
                  buttons.every((button) => (button as HTMLButtonElement).disabled),
              )
          : null;
      await page.screenshot({ path: testInfo.outputPath('loading.png') });
      releaseData();
      if (surface === 'news') {
        await expect(page.getByRole('heading', { name: accessibilityArticle.title })).toBeVisible();
      } else {
        await expect(page.locator('.issue-feed > li')).toHaveCount(12);
      }
      await page.evaluate(
        () => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))),
      );
      await page.screenshot({ path: testInfo.outputPath('loaded.png') });
      const cls = await page.evaluate(() => {
        let maximum = 0;
        let session = 0;
        let first = 0;
        let previous = 0;
        for (const entry of window.loadingLayoutShifts) {
          if (
            session === 0 ||
            entry.startTime - previous >= 1000 ||
            entry.startTime - first >= 5000
          ) {
            session = 0;
            first = entry.startTime;
          }
          session += entry.value;
          maximum = Math.max(maximum, session);
          previous = entry.startTime;
        }
        return maximum;
      });
      const toolbarAfter = surface === 'portfolio' ? await toolbar.boundingBox() : null;
      await testInfo.attach('loading-layout.json', {
        body: JSON.stringify({ viewport, surface, cls, footerBefore, toolbarBefore, toolbarAfter }),
        contentType: 'application/json',
      });

      expect(
        footerBefore!.y,
        '로딩 중 푸터는 본문을 위해 확보한 영역 뒤에 있어야 한다',
      ).toBeGreaterThanOrEqual(viewport.height);
      expect(cls, '첫 화면 로딩 CLS는 0.1 이하여야 한다').toBeLessThanOrEqual(0.1);
      if (surface === 'portfolio') {
        expect(controlsDisabled, '보유 종목 조회 중에도 비활성 필터가 공간을 유지한다').toBe(true);
        expect(toolbarBefore!.y).toBeCloseTo(toolbarAfter!.y, 0);
        await expect(toolbar.getByRole('button', { name: '전체', exact: true })).toBeEnabled();
      }
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      await page.locator('.daynomy-footer').scrollIntoViewIfNeeded();
      await expect(page.locator('.daynomy-footer')).toBeInViewport();
    });
  }
}
