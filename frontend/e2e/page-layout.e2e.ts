import { expect, test } from '@playwright/test';

test('이슈·포트폴리오 본문을 맞추고 리그 준비 중 화면을 같은 너비로 배치한다', async ({
  page,
}, testInfo) => {
  test.slow();
  await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
  await page.route('**/api/users/me', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 401 }));
  for (const width of [390, 768, 1024, 1280, 1440, 1920]) {
    await page.setViewportSize({ width, height: 1000 });
    let reference: { left: number; width: number; top: number; fontSize: string } | undefined;
    for (const [url, main, heading] of [
      ['/news', '.issue-page', '#issue-today-heading'],
      ['/', '.portfolio-page', '.portfolio-page-title h1'],
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
    await page.goto('/league');
    await expect(page.getByRole('heading', { name: '투자 리그를 준비하고 있어요' })).toBeVisible();
    const leagueLayout = await page.locator('.league-coming-soon').evaluate((element) => {
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
        path: testInfo.outputPath(`league-coming-soon-${width}.png`),
        fullPage: false,
      });
    }
  }
});
