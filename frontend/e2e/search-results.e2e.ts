import { expect, test } from '@playwright/test';

const articles = Array.from({ length: 14 }, (_, index) => ({
  kind: 'NEWS',
  id: index + 1,
  source: 'DAYNOMY',
  title: `금리 기사 ${index + 1}`,
  date: '2026-10-21T00:00:00Z',
  category: 'ECONOMY',
  imageUrl: '/test-search-image.svg',
}));
const video = {
  kind: 'ASSET_CONTENT',
  id: 31,
  source: 'YOUTUBE',
  title: '금리 인기 영상',
  date: '2026-10-19T00:00:00Z',
  url: 'https://youtu.be/abcdefghijk',
  assetId: 5,
  imageUrl: null,
  publishedDate: null,
};
const post = {
  kind: 'PERSON_POST',
  id: 40,
  source: 'X',
  title: '금리 전망 글',
  date: '2026-10-20T00:00:00Z',
  url: 'https://x.com/example/status/123',
  personId: 3,
  personName: '경제 연구원',
};
const image =
  '<svg xmlns="http://www.w3.org/2000/svg" width="180" height="100"><rect width="180" height="100" fill="#ccd9ec"/></svg>';

for (const width of [390, 1440]) {
  test(`${width}px: 검색 썸네일과 인기순·출처·페이지 조건을 유지한다`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript(() => localStorage.setItem('daynomy:service-started:v1', '1'));
    await page.route('**/test-search-image.svg', (route) =>
      route.fulfill({ contentType: 'image/svg+xml', body: image }),
    );
    await page.route('https://i.ytimg.com/**', (route) =>
      route.fulfill({ contentType: 'image/svg+xml', body: image }),
    );
    const requests: URL[] = [];
    await page.route('**/api/**', (route) => {
      const url = new URL(route.request().url());
      if (url.pathname !== '/api/search/contents') return route.fulfill({ status: 401, json: {} });
      requests.push(url);
      const sorted =
        url.searchParams.get('sort') === 'POPULAR'
          ? [video, post, ...articles]
          : [...articles, video, post];
      const source = url.searchParams.get('source');
      const matched = source ? sorted.filter((item) => item.source === source) : sorted;
      const currentPage = Number(url.searchParams.get('page'));
      const size = Number(url.searchParams.get('size'));
      return route.fulfill({
        json: {
          items: matched.slice((currentPage - 1) * size, currentPage * size),
          page: currentPage,
          size,
          totalElements: matched.length,
          totalPages: Math.ceil(matched.length / size),
        },
      });
    });

    await page.goto('/search?q=금리&source=ALL&page=2');
    await expect(page.getByRole('button', { name: '최신순', exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(page.locator('.issue-row-title').first()).toContainText('금리 기사 13');
    await expect(page.locator('.issue-feed-thumbnail')).toHaveCount(4);

    await page.getByRole('button', { name: '인기순', exact: true }).click();
    await expect(page).toHaveURL(/sort=popular/);
    await expect(page.locator('.issue-pagination [aria-current="page"]')).toHaveText('1');
    await expect(page.locator('.issue-row-title').first()).toContainText(video.title);
    await expect(page.locator('.issue-feed-thumbnail > img').first()).toHaveJSProperty(
      'complete',
      true,
    );
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: testInfo.outputPath('popular-search.png') });

    await page.getByRole('button', { name: '2페이지', exact: true }).click();
    await expect(page.locator('.issue-pagination [aria-current="page"]')).toHaveText('2');
    expect(requests.at(-1)?.searchParams.get('sort')).toBe('POPULAR');
    await page.getByRole('button', { name: 'YouTube', exact: true }).click();
    await expect(page.locator('.issue-feed-row')).toHaveCount(1);
    expect(new URL(page.url()).searchParams.get('page')).toBe('1');
    expect(new URL(page.url()).searchParams.get('sort')).toBe('popular');
    await expect(page.getByRole('navigation', { name: '콘텐츠 페이지' })).toHaveCount(0);

    await page.goBack();
    await expect(page.locator('.issue-pagination [aria-current="page"]')).toHaveText('2');
    await page.reload();
    await expect(page.getByRole('button', { name: '인기순', exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(page.locator('.issue-pagination [aria-current="page"]')).toHaveText('2');
    await page.getByRole('button', { name: '최신순', exact: true }).click();
    await expect(page.locator('.issue-row-title').first()).toContainText('금리 기사 1');
    expect(new URL(page.url()).searchParams.has('sort')).toBe(false);
    expect(new URL(page.url()).searchParams.get('q')).toBe('금리');
  });
}
