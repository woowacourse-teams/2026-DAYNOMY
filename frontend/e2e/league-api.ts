import type { Page } from '@playwright/test';

const profile = {
  publicId: 'investor-1',
  displayName: '리그 투자자',
  bio: '',
  experienceLevel: 'BEGINNER',
  riskProfile: 'BALANCED',
  profilePublic: true,
  leagueEnabled: true,
  allocationPublic: true,
  detailPublic: false,
  leagueEnabledAt: '2026-10-13T01:00:00Z',
  trackingStartedOn: '2026-10-13',
  eligibleFrom: '2026-10-13',
};

const performance = {
  weeklyReturnRate: 0,
  eightWeekReturnRate: 0,
  maxDrawdownRate: null,
  volatilityRate: null,
  maxHoldingWeight: 100,
};

export async function mockLeagueApi(page: Page) {
  await page.route('**/api/league/**', (route) => {
    const path = new URL(route.request().url()).pathname;
    const week = { weekStart: '2026-10-12', weekEnd: '2026-10-18', confirmed: false };
    if (path === '/api/league/weeks') return route.fulfill({ json: { weeks: [week] } });
    if (path === '/api/league/rankings')
      return route.fulfill({
        json: {
          ...week,
          leagueType: 'WEEKLY_RETURN',
          asOfDate: '2026-10-13',
          totalCount: 1,
          rankings: [
            {
              ...profile,
              ...performance,
              rank: 1,
              decisionCount: 1,
              reviewCompletionRate: 0,
              followed: false,
            },
          ],
        },
      });
    if (path === '/api/league/investors/investor-1')
      return route.fulfill({
        json: {
          ...profile,
          performance: { ...performance, weeklyRank: 1 },
          allocation: null,
          history: [{ weekStart: week.weekStart, weeklyReturnRate: 0, maxDrawdownRate: null }],
          decisionCount: 1,
          reviewCompletionRate: 0,
          followed: false,
          detailAvailable: false,
        },
      });
    if (path === '/api/league/investors/investor-1/daily-history')
      return route.fulfill({
        json: {
          ...week,
          asOfDate: '2026-10-13',
          eligibleFrom: profile.eligibleFrom,
          trackingStartedOn: profile.trackingStartedOn,
          weeklyReturnRate: 0,
          days: [
            {
              baseDate: '2026-10-12',
              dailyReturnRate: null,
              cumulativeReturnRate: null,
              status: 'NOT_PARTICIPATING',
              reason: null,
            },
            {
              baseDate: '2026-10-13',
              dailyReturnRate: 0,
              cumulativeReturnRate: 0,
              status: 'CALCULATED',
              reason: 'TRACKING_BASELINE',
            },
          ],
        },
      });
    return route.fulfill({ status: 404 });
  });
  await page.route('**/api/users/me/investor-profile', (route) => route.fulfill({ json: profile }));
  await page.route('**/api/users/me/shared-portfolio', (route) =>
    route.fulfill({
      json: {
        holdings: [
          {
            assetId: 1,
            assetCode: '005930',
            assetName: '삼성전자',
            category: 'STOCK',
            market: 'KOSPI',
            quantity: 1,
            averagePurchasePrice: 100,
            hidden: false,
            reason: '실적을 확인하며 보유',
            baseDate: '2026-10-13',
            closePrice: 100,
            evaluationAmount: 100,
            returnRate: 0,
          },
        ],
        totalPurchaseAmount: 100,
        totalEvaluationAmount: 100,
        totalReturnRate: 0,
        pricesComplete: true,
      },
    }),
  );
  await page.route('**/api/users/me/portfolio/transactions', (route) =>
    route.fulfill({ json: { transactions: [] } }),
  );
  await page.route('**/api/users/me/league/follows/summary', (route) =>
    route.fulfill({ json: { investors: [] } }),
  );
}
