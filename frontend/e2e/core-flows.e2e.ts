import { expect, test, type Page } from '@playwright/test';

const article = {
  id: 7,
  title: '기준금리 동결 가능성 확대',
  category: 'ECONOMY',
  imageUrl: null,
  publishedAt: '2026-09-01T10:00:00Z',
};

const newsPage = {
  items: [article],
  page: 1,
  size: 6,
  totalPages: 1,
  totalElements: 1,
  hasNext: false,
};

async function mockNewsApi(page: Page) {
  await page.route('**/api/news**', (route) => {
    const path = new URL(route.request().url()).pathname;

    if (path === '/api/news' || path === '/api/news/today') {
      return route.fulfill({ json: newsPage });
    }
    if (path === '/api/news/7') {
      return route.fulfill({
        json: {
          ...article,
          content: '기준금리는 당분간 동결될 전망입니다.',
          sources: [],
        },
      });
    }
    if (path === '/api/news/7/keywords') {
      return route.fulfill({ json: { keywords: [] } });
    }
    if (path === '/api/news/7/market-analysis') {
      return route.fulfill({ json: { summary: '시장 분석 테스트' } });
    }
    return route.fulfill({ status: 404 });
  });
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
  await page.route('**/api/auth/csrf', (route) =>
    route.fulfill({ json: { token: 'test-token', headerName: 'X-CSRF-TOKEN' } }),
  );
  await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/users/me', (route) => route.fulfill({ status: 401 }));
});

test('관리자 로그인 시작 후 인증된 관리자 화면으로 이동한다', async ({ page }) => {
  let loggedIn = false;
  await page.route('**/api/users/me', (route) =>
    route.fulfill(
      loggedIn
        ? {
            json: { id: 1, email: 'admin@example.invalid', nickname: '관리자', role: 'ADMIN' },
          }
        : { status: 401 },
    ),
  );
  await page.route('**/api/auth/google', (route) => {
    loggedIn = true;
    return route.fulfill({
      status: 302,
      headers: { location: 'http://127.0.0.1:4173/' },
    });
  });
  await page.route('**/api/admin/news**', (route) =>
    route.fulfill({
      json: { items: [], page: 1, size: 15, totalPages: 0, totalElements: 0, hasNext: false },
    }),
  );

  await page.goto('/admin/news');
  await expect(page.getByRole('heading', { name: 'DAYNOMY 로그인' })).toBeVisible();
  await page.getByRole('link', { name: 'Google로 시작하기' }).click();

  await expect(page).toHaveURL(/\/admin\/news$/);
  await expect(page.getByRole('heading', { name: '뉴스 관리' })).toBeVisible();
});

test('관리자 권한이 없는 계정은 관리자 화면에 접근하지 못한다', async ({ page }) => {
  await page.route('**/api/users/me', (route) =>
    route.fulfill({
      json: { id: 2, email: 'user@example.invalid', nickname: '팀원', role: 'USER' },
    }),
  );

  await page.goto('/admin/news');

  await expect(page.getByRole('heading', { name: '관리자 권한이 필요합니다.' })).toBeVisible();
  await expect(page.getByRole('link', { name: '서비스 홈으로' })).toBeVisible();
});

test('검색어 입력에서 뉴스 결과와 상세 화면까지 이동한다', async ({ page }) => {
  await mockNewsApi(page);
  await page.route('**/api/search/news**', (route) =>
    route.fulfill({
      json: { content: [article], page: 1, size: 10, totalPages: 1, totalElements: 1 },
    }),
  );

  await page.goto('/news');
  await page.getByRole('button', { name: '검색 열기' }).click();
  await page.getByRole('dialog', { name: '통합 검색' }).getByRole('textbox').fill('금리');
  await page.getByRole('dialog', { name: '통합 검색' }).getByRole('textbox').press('Enter');

  await expect(page).toHaveURL(/\/search\?q=/);
  await page
    .getByRole('region', { name: '검색된 뉴스 목록' })
    .getByRole('link', { name: /기준금리 동결 가능성 확대/ })
    .click();
  await expect(page).toHaveURL(/\/news\/7$/);
  await expect(page.getByRole('heading', { name: article.title, level: 1 })).toBeVisible();
});

test('뉴스 목록에서 상세 본문을 읽고 목록으로 돌아온다', async ({ page }) => {
  await mockNewsApi(page);

  await page.goto('/news');
  await page
    .getByRole('region', { name: '이슈 목록' })
    .getByRole('link', { name: /기준금리 동결 가능성 확대/ })
    .click();

  await expect(page.getByRole('heading', { name: article.title, level: 1 })).toBeVisible();
  await expect(page.getByRole('region', { name: '뉴스 본문' })).toContainText(
    '기준금리는 당분간 동결될 전망입니다.',
  );
  await page.getByRole('button', { name: '전 페이지로 돌아가기' }).click();
  await expect(page).toHaveURL('/news');
  await expect(page.getByRole('region', { name: '이슈 목록' })).toContainText(article.title);
});

test('비회원도 공개 투자 리그에서 종목과 판단을 바로 확인한다', async ({ page }) => {
  await page.route('**/api/league/weeks', (route) =>
    route.fulfill({
      json: {
        weeks: [{ weekStart: '2026-09-28', weekEnd: '2026-10-04', confirmed: true }],
      },
    }),
  );
  await page.route('**/api/league/rankings**', (route) =>
    route.fulfill({
      json: {
        weekStart: '2026-09-28',
        weekEnd: '2026-10-04',
        leagueType: 'WEEKLY_RETURN',
        confirmed: true,
        totalCount: 1,
        asOfDate: '2026-10-02',
        rankings: [
          {
            rank: 1,
            publicId: 'investor-1',
            displayName: '차분한초보',
            experienceLevel: 'BEGINNER',
            riskProfile: 'BALANCED',
            weeklyReturnRate: 2.5,
            eightWeekReturnRate: 4.2,
            maxDrawdownRate: -1.1,
            volatilityRate: 0.7,
            maxHoldingWeight: 55,
            decisionCount: 4,
            reviewCompletionRate: 75,
            followed: false,
          },
        ],
      },
    }),
  );
  await page.route('**/api/league/investors/investor-1', (route) =>
    route.fulfill({
      json: {
        publicId: 'investor-1',
        displayName: '차분한초보',
        bio: '손실 조건부터 기록합니다.',
        experienceLevel: 'BEGINNER',
        riskProfile: 'BALANCED',
        performance: {
          weeklyReturnRate: 2.5,
          eightWeekReturnRate: 4.2,
          maxDrawdownRate: -1.1,
          volatilityRate: 0.7,
          maxHoldingWeight: 55,
        },
        allocation: { stockWeight: 60, etfWeight: 40 },
        history: [{ weekStart: '2026-09-28', weeklyReturnRate: 2.5, maxDrawdownRate: -1.1 }],
        decisionCount: 4,
        reviewCompletionRate: 75,
        followed: false,
        detailAvailable: true,
      },
    }),
  );

  await page.route('**/api/league/investors/investor-1/details', (route) =>
    route.fulfill({
      json: {
        publicId: 'investor-1',
        asOfDate: '2026-10-06',
        holdings: [
          {
            assetName: '삼성전자',
            category: 'STOCK',
            weight: 60,
            weeklyContributionRate: 1.2,
            reason: '공시를 확인한 보유 판단',
          },
        ],
        decisions: [
          {
            transactionId: 1,
            assetName: '삼성전자',
            category: 'STOCK',
            transactionType: 'HOLD',
            tradedOn: '2026-10-06',
            reason: '당일 작성한 판단 근거',
            expectedHoldingPeriod: 'OVER_SIX_MONTHS',
            expectedChange: '매출 성장',
            invalidationCondition: '실적 악화',
            maximumAcceptableLossRate: 10,
            writtenAfterTrade: false,
            reviews: [],
          },
        ],
      },
    }),
  );

  await page.route('**/api/league/investors/investor-1/daily-history**', (route) =>
    route.fulfill({
      json: {
        weekStart: '2026-09-28',
        weekEnd: '2026-10-04',
        asOfDate: '2026-09-29',
        eligibleFrom: '2026-08-03',
        confirmed: true,
        weeklyReturnRate: -1,
        days: [
          {
            baseDate: '2026-09-28',
            dailyReturnRate: 10,
            cumulativeReturnRate: 10,
            status: 'CALCULATED',
            reason: null,
          },
          {
            baseDate: '2026-09-29',
            dailyReturnRate: -10,
            cumulativeReturnRate: -1,
            status: 'CALCULATED',
            reason: null,
          },
        ],
      },
    }),
  );

  await page.goto('/league');
  await expect(page.getByRole('link', { name: /차분한초보/ })).toBeVisible();
  await page.getByRole('link', { name: /차분한초보/ }).click();

  await expect(page.getByRole('heading', { name: '차분한초보', level: 1 })).toBeVisible();
  await expect(page.getByText('판단 근거: 공시를 확인한 보유 판단')).toBeVisible();
  await expect(page.getByText('당일 작성한 판단 근거')).toBeVisible();
  await expect(page.getByRole('heading', { name: '일별 수익률 · 주간 누적' })).toBeVisible();
  await expect(page.getByText('주간 누적 -1.00%', { exact: true })).toBeVisible();
  await expect(page.getByRole('table')).toContainText('+10.00%');
  await page.getByRole('button', { name: /2026-09-28 주간.*일별 기록 보기/ }).click();
  await expect(page.getByRole('combobox', { name: '조회 주차' })).toHaveValue('2026-09-28');
  await expect(page.getByRole('link', { name: '멤버십 알아보기' })).toHaveCount(0);
  await expect(page.getByText(/실제 투자금액은 공개하지 않습니다/)).toBeVisible();
});
