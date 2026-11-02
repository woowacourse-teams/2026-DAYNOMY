import { expect, test } from '@playwright/test';
import { mockLeagueApi } from './league-api';

test('소식·포트폴리오·주간 리그 본문을 같은 너비로 배치한다', async ({ page }, testInfo) => {
  test.slow();
  await page.addInitScript(() => localStorage.setItem('daynomy:portfolio-tour:v1', 'done'));
  await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
  await page.route('**/api/users/me', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 401 }));
  await mockLeagueApi(page);
  for (const width of [390, 768, 1024, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 1000 });
    let reference: { left: number; width: number } | undefined;
    for (const [url, main] of [
      ['/news', '.issue-page'],
      ['/', '.portfolio-page'],
    ] as const) {
      await page.goto(url);
      if (url === '/news') {
        await expect(
          page.getByRole('heading', { name: '소식', level: 1, includeHidden: true }),
        ).toBeAttached();
      } else {
        await expect(page.locator('.portfolio-page-title h1')).toBeVisible();
      }
      const layout = await page.locator(main).evaluate((element) => {
        const bounds = element.getBoundingClientRect();
        return {
          left: bounds.left,
          width: bounds.width,
        };
      });
      if (!reference) reference = layout;
      expect(layout.left, `${width}px ${url} 좌측 정렬`).toBeCloseTo(reference.left, 0);
      expect(layout.width, `${width}px ${url} 본문 너비`).toBeCloseTo(reference.width, 0);
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
    await page.goto('/league');
    await expect(page.getByRole('heading', { name: '주간 리그', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: '주간 순위', exact: true })).toBeVisible();
    await expect(page.getByText('집계 기준 안내', { exact: true })).toHaveCount(0);
    const leagueLayout = await page.locator('main.league-page').evaluate((element) => {
      const bounds = element.getBoundingClientRect();
      return { left: bounds.left, width: bounds.width };
    });
    expect(leagueLayout.left, `${width}px 리그 좌측 정렬`).toBeCloseTo(reference!.left, 0);
    expect(leagueLayout.width, `${width}px 리그 본문 너비`).toBeCloseTo(reference!.width, 0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    if (width === 390 || width === 1280) {
      await page.screenshot({
        path: testInfo.outputPath(`league-${width}.png`),
        fullPage: false,
      });
    }
  }
});
