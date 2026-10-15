import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    (route) => route.fulfill({ status: 401 }),
  );
});

for (const width of [1440, 390]) {
  test(`${width}px: 첫 방문 소개에서 시작하고 재방문과 소개 다시 보기를 지원한다`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/');
    await expect(page).toHaveURL('/welcome');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('매일의 경제 이슈를');
    await expect(page.locator('iframe')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.getByRole('link', { name: 'DAYNOMY 시작하기', exact: true }).first().click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('daynomy:service-started:v1'))).toBe('1');
    await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toHaveCSS(
      'font-size',
      width === 390 ? '22px' : '24px',
    );
    await page.reload();
    await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeVisible();
    await expect(page.locator('.landing-page')).toHaveCount(0);
    await page.getByRole('link', { name: '서비스 소개', exact: true }).click();
    await expect(page).toHaveURL('/welcome');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('매일의 경제 이슈를');
  });
}

test('공유 뉴스 상세는 소개를 거치지 않고 원래 주소로 열린다', async ({ page }) => {
  await page.route('**/api/news/7', (route) =>
    route.fulfill({
      json: {
        id: 7,
        title: '공유 뉴스 테스트',
        category: 'ECONOMY',
        imageUrl: null,
        publishedAt: '2026-10-14T10:00:00Z',
        content: '공유한 뉴스 본문입니다.',
        sources: [],
      },
    }),
  );
  await page.route('**/api/news/7/keywords', (route) => route.fulfill({ json: { keywords: [] } }));
  await page.route('**/api/news/7/market-analysis', (route) =>
    route.fulfill({ json: { summary: '시장 분석' } }),
  );
  await page.goto('/news/7?source=share#analysis');
  await expect(page.getByRole('heading', { name: '공유 뉴스 테스트' })).toBeVisible();
  await expect(page).toHaveURL('/news/7?source=share#analysis');
  await expect(page.locator('.landing-page')).toHaveCount(0);
  await page.getByRole('link', { name: '포트폴리오', exact: true }).click();
  await expect(page).toHaveURL('/');
  await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeVisible();
});
