import { expect, test } from '@playwright/test';
import { accessibilityArticle, installAccessibilityFixtures } from './fixtures/accessibility';

test('뉴스 첫 진입은 관리자 화면 코드를 받지 않고, 지연된 화면을 로드한 뒤 표시한다', async ({
  page,
}) => {
  await installAccessibilityFixtures(page);
  const scripts: string[] = [];
  page.on('request', (request) => {
    if (request.resourceType() === 'script') scripts.push(request.url());
  });
  let release!: () => void;
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/assets/NewsDetailPage-*.js', async (route) => {
    await pending;
    await route.continue();
  });
  await page.goto('/news/7', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('status')).toHaveText('화면을 불러오는 중…');
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toBeVisible();
  expect((await page.locator('.daynomy-footer').boundingBox())?.y).toBeGreaterThanOrEqual(
    page.viewportSize()!.height,
  );
  release();
  await expect(
    page.getByRole('heading', { name: accessibilityArticle.title, level: 1 }),
  ).toBeVisible();
  expect(scripts.some((url) => /\/Admin[^/]+\.js/.test(url))).toBe(false);
});

test('분할된 파일을 받지 못하면 빈 화면 대신 복구 안내를 제공한다', async ({ page }) => {
  await installAccessibilityFixtures(page);
  await page.route('**/assets/NewsDetailPage-*.js', (route) => route.abort());
  await page.goto('/news/7');
  await expect(page.getByRole('alert')).toContainText('문제가 발생했습니다.');
  await expect(page.getByRole('link', { name: '홈으로 이동' })).toHaveAttribute('href', '/');
});
