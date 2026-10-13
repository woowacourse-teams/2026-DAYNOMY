import { expect, test } from '@playwright/test';

test('이슈·포트폴리오·리그의 본문 너비와 제목 위치·크기를 맞춘다', async ({ page }, testInfo) => {
  await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
  await page.route('**/api/users/me', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 401 }));
  for (const width of [390, 768, 1024, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 1000 });
    let reference: { left: number; width: number; top: number; fontSize: string } | undefined;
    for (const [url, main, heading] of [
      ['/news', '.issue-page', '#issue-today-heading'],
      ['/', '.portfolio-page', '.portfolio-page-title h1'],
      ['/league', '.league-page', '.league-hero h1'],
    ]) {
      await page.goto(url);
      await expect(page.locator(heading)).toBeVisible();
      const layout = await page.locator(main).evaluate((element, selector) => {
        const bounds = element.getBoundingClientRect();
        const title = element.querySelector(selector)!;
        return {
          left: bounds.left,
          width: bounds.width,
          top: title.getBoundingClientRect().top,
          fontSize: getComputedStyle(title).fontSize,
        };
      }, heading);
      if (!reference) reference = layout;
      expect(layout.left, `${width}px ${url} 좌측 정렬`).toBeCloseTo(reference.left, 0);
      expect(layout.width, `${width}px ${url} 본문 너비`).toBeCloseTo(reference.width, 0);
      expect(layout.top, `${width}px ${url} 제목 시작 위치`).toBeCloseTo(reference.top, 0);
      expect(layout.fontSize, `${width}px ${url} 제목 크기`).toBe(reference.fontSize);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      if (width === 1280) {
        await page.screenshot({
          path: testInfo.outputPath(`${main.slice(1)}-1280.png`),
          fullPage: false,
        });
      }
    }
  }
});
