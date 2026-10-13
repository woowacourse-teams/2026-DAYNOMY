import { expect, test } from '@playwright/test';

const keyword = '주택담보대출금리변동위험관리법';
const paragraphs = [
  '서울 아파트 가격이 오르면서 대출 조건에 관심이 모이고 있다.',
  `${keyword} 개념과 실제 거래량을 함께 살펴야 한다.`,
];

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/news/1') {
      return route.fulfill({
        json: {
          id: 1,
          title: '주택시장 흐름과 대출 여건',
          category: 'REAL_ESTATE',
          publishedAt: '2026-10-12',
          content: paragraphs,
          imageUrl: null,
          sources: [],
        },
      });
    }
    if (path.endsWith('/keywords')) {
      return route.fulfill({
        json: {
          keywords: [
            {
              keyword,
              category: 'TERM',
              definition: '대출금리 변동이 상환 부담에 미치는 영향을 확인하는 개념이에요. '
                .repeat(7)
                .slice(0, 200),
              context: '대출 조건의 변화와 가격 상승의 지속성을 함께 살펴야 해요.',
              watchPoint: '실제 거래량과 금리 추이를 함께 확인하세요.',
            },
          ],
        },
      });
    }
    if (path.endsWith('/market-analysis')) {
      return route.fulfill({
        json: {
          risk: '거래량과 금리 변화에 따라 주택 수요가 달라질 수 있어요. '.repeat(8).slice(0, 240),
          watchPoint: '거래량과 대출 규제의 시행 내용을 함께 확인하세요.',
        },
      });
    }
    return route.fulfill({ status: 401, json: {} });
  });
});

for (const width of [1440, 768]) {
  test(`${width}px: 긴 내용에서도 본문과 분석 배치를 유지하고 호버·키보드로 팝업을 제어한다`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/news/1');
    await expect(page.locator('.body-copy p')).toHaveText(paragraphs);
    await expect(page.locator('.market-analysis-item')).toHaveCount(2);
    await expect(page.locator('.market-analysis-item dd').first()).toHaveCSS('font-weight', '400');
    const trigger = page.getByRole('button', { name: keyword, exact: true });
    await expect(trigger).toHaveCSS('color', 'rgb(36, 99, 181)');
    await trigger.hover();
    const tooltip = page.getByRole('tooltip');
    await tooltip.hover();
    await expect(tooltip).toBeVisible();
    await page.mouse.move(0, 0);
    await expect(tooltip).toBeHidden();
    await trigger.focus();
    await expect(tooltip).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(tooltip).toBeHidden();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  });
}

for (const viewport of [
  { width: 320, height: 568 },
  { width: 390, height: 844 },
]) {
  test.describe(`${viewport.width}px 터치 화면`, () => {
    test.use({ viewport, isMobile: true, hasTouch: true });

    test('팝업 내부 터치는 유지하고 외부 터치는 닫는다', async ({ page }) => {
      await page.goto('/news/1');
      await page.getByRole('button', { name: keyword, exact: true }).tap();
      const tooltip = page.getByRole('tooltip');
      await expect(tooltip).toBeVisible();
      await tooltip.tap();
      await expect(tooltip).toBeVisible();
      const box = await tooltip.boundingBox();
      expect(box).not.toBeNull();
      expect(box!.x).toBeGreaterThanOrEqual(0);
      expect(box!.y).toBeGreaterThanOrEqual(0);
      expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width);
      expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
      await page.getByRole('button', { name: '검색 열기' }).tap();
      await expect(tooltip).toBeHidden();
      await expect(page.locator('.body-copy p')).toHaveText(paragraphs);
    });
  });
}
