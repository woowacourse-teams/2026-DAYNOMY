import { expect, test } from '@playwright/test';
import { mockLeagueApi } from './league-api';

for (const width of [320, 390, 1440]) {
  test(`${width}px: 순위 번호형 페이지네이션 디자인과 페이지 이동`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript(() => localStorage.setItem('daynomy:service-started:v1', '1'));
    await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
    await page.route('**/api/users/me', (route) => route.fulfill({ status: 401 }));
    await mockLeagueApi(page);
    const requestedPages: number[] = [];
    await page.route('**/api/league/rankings?**', (route) => {
      const query = new URL(route.request().url()).searchParams;
      const index = Number(query.get('page'));
      requestedPages.push(index);
      expect(query.get('size')).toBe('20');
      return route.fulfill({
        json: {
          weekStart: '2026-10-12',
          weekEnd: '2026-10-18',
          leagueType: 'WEEKLY_RETURN',
          confirmed: false,
          asOfDate: '2026-10-13',
          totalCount: 145,
          aggregation: {
            baseDate: '2026-10-14',
            expectedAt: '2026-10-15T05:30:00Z',
            firstReturnExpectedAt: null,
            reason: 'PRICE_COLLECTION_PENDING',
            delayed: false,
          },
          rankings: Array.from({ length: Math.min(20, 145 - index * 20) }, (_, offset) => ({
            rank: index * 20 + offset + 1,
            publicId: `investor-${index * 20 + offset + 1}`,
            displayName: `투자자 ${index * 20 + offset + 1}`,
            experienceLevel: 'BEGINNER',
            riskProfile: 'BALANCED',
            weeklyReturnRate: 2.5,
            threeWeekReturnRate: null,
            maxDrawdownRate: null,
            volatilityRate: null,
            maxHoldingWeight: 100,
            decisionCount: 1,
            reviewCompletionRate: 0,
            followed: false,
          })),
        },
      });
    });
    await page.goto('/league');
    await expect(page.getByText('다음 집계 예정')).toBeVisible();
    await expect(
      page.getByText('집계된 종가 기준의 잠정 순위예요. 최종 집계 후 달라질 수 있어요.'),
    ).toHaveCount(0);
    await page.locator('.league-aggregation').screenshot({
      path: testInfo.outputPath(`aggregation-${width}.png`),
    });
    const navigation = page.getByRole('navigation', { name: '순위 페이지' });
    await expect(navigation.getByRole('button')).toHaveCount(8);
    const current = navigation.getByRole('button', { name: '1페이지', exact: true });
    await expect(current).toHaveAttribute('aria-current', 'page');
    await expect(current).toHaveCSS('background-color', 'rgb(10, 10, 10)');
    await expect(current).toHaveCSS('color', 'rgb(255, 255, 255)');
    await expect(current).toHaveCSS('border-radius', '4px');
    await expect(navigation.getByRole('button', { name: '2페이지', exact: true })).toHaveCSS(
      'color',
      'rgb(16, 35, 75)',
    );
    await expect(
      navigation.getByRole('button', { name: '이전 방향 1페이지로 이동' }),
    ).toBeDisabled();
    await navigation.screenshot({ path: testInfo.outputPath(`pagination-${width}.png`) });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      await current.evaluate((element) => element.getBoundingClientRect().height),
    ).toBeGreaterThanOrEqual(44);

    await navigation.getByRole('button', { name: '6페이지', exact: true }).click();
    await expect(navigation.getByRole('button', { name: '6페이지', exact: true })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await expect(page.getByRole('status', { name: '전체 8페이지 중 6페이지' })).toBeFocused();
    await navigation.getByRole('button', { name: '다음 방향 7페이지로 이동' }).click();
    await expect(navigation.getByRole('button', { name: '7페이지', exact: true })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await navigation.getByRole('button', { name: '8페이지', exact: true }).click();
    await expect(
      navigation.getByRole('button', { name: '다음 방향 8페이지로 이동' }),
    ).toBeDisabled();
    expect(requestedPages).toEqual([0, 5, 6, 7]);
  });
}
