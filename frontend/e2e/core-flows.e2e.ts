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
  await expect(page.getByRole('heading', { name: 'DAYNOMY 관리자 로그인' })).toBeVisible();
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

test('종목 상세에서 리그와 마이페이지로 이동하고 관리자 메뉴는 분리된다', async ({ page }) => {
  await page.route('**/api/users/me', (route) =>
    route.fulfill({
      json: { id: 1, email: 'admin@example.invalid', nickname: '관리자', role: 'ADMIN' },
    }),
  );
  await page.route('**/api/assets/1/contents', (route) =>
    route.fulfill({
      json: { assetId: 1, assetCode: '005930', assetName: '삼성전자', contents: [] },
    }),
  );
  await page.route('**/api/league/weeks', (route) =>
    route.fulfill({
      json: { weeks: [{ weekStart: '2026-10-05', weekEnd: '2026-10-11', confirmed: false }] },
    }),
  );
  await page.route('**/api/league/rankings**', (route) =>
    route.fulfill({
      json: {
        weekStart: '2026-10-05',
        weekEnd: '2026-10-11',
        leagueType: 'WEEKLY_RETURN',
        confirmed: false,
        totalCount: 0,
        asOfDate: null,
        rankings: [],
      },
    }),
  );

  await page.goto('/stocks/1');
  await expect(page.getByRole('heading', { name: '삼성전자', level: 1 })).toBeVisible();
  await page
    .getByRole('navigation', { name: '주요 메뉴' })
    .getByRole('link', { name: '투자 리그' })
    .click();
  await expect(page).toHaveURL('/league');
  await expect(page.getByRole('heading', { name: '투자 리그' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toBeVisible();
  await page.getByRole('link', { name: '마이페이지' }).click();
  await expect(page).toHaveURL('/mypage');
  await expect(page.getByRole('heading', { name: '마이페이지' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toBeVisible();

  await page.goto('/admin/stock-contents');
  await expect(page.getByRole('heading', { name: '종목 관련 링크' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '관리자 메뉴' })).toBeVisible();
  await expect(page.getByRole('navigation', { name: '주요 메뉴' })).toHaveCount(0);
});

for (const width of [1440, 390]) {
  test(`${width}px: 비회원도 지표 패널과 주간 그래프에서 투자 기록을 확인한다`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 1000 });
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
          history: [2.5, -1, 0, null, 3, -2, 4, 1].map((rate, index) => ({
            weekStart: new Date(Date.UTC(2026, 7, 10 + index * 7)).toISOString().slice(0, 10),
            weeklyReturnRate: rate,
            maxDrawdownRate: rate == null ? null : -1.1,
            weeklyRank: rate == null ? null : index + 1,
            confirmed: rate !== null,
            status: rate == null ? 'PENDING' : 'CALCULATED',
          })),
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
    await expect(page.getByRole('link', { name: /차분한초보.*중립형/ })).toBeVisible();
    const trigger = page.getByRole('button', { name: '차분한초보 상세 지표' });
    const row = page.locator('.league-board li').first();
    const before = await row.boundingBox();
    await trigger.click();
    const dialog = page.getByRole('dialog', { name: '차분한초보 상세 지표' });
    await expect(dialog).toBeVisible();
    const after = await row.boundingBox();
    expect(after?.y).toBe(before?.y);
    expect(after?.height).toBe(before?.height);
    const viewportWidth = await page.evaluate(() => document.documentElement.clientWidth);
    const panel = (await dialog.boundingBox())!;
    expect(panel.width).toBeCloseTo(Math.min(440, viewportWidth - 12), 0);
    expect(panel.x + panel.width).toBeCloseTo(viewportWidth, 0);
    await expect(dialog.getByText('최대 하락률 · 8주', { exact: true })).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(dialog).toHaveCount(0);
    await expect(trigger).toBeFocused();
    await trigger.click();
    await dialog.getByRole('link', { name: '성과 보기' }).click();

    await expect(page.getByRole('heading', { name: '차분한초보', level: 1 })).toBeVisible();
    const chart = page.getByLabel('최근 8주 주간 수익률', { exact: true });
    await expect(chart.getByRole('button')).toHaveCount(8);
    await expect(chart.locator('.league-week-bar')).toHaveCount(7);
    if (width === 390) {
      const latest = await chart.getByRole('button').last().boundingBox();
      expect(latest?.x).toBeGreaterThanOrEqual(0);
      expect((latest?.x ?? 0) + (latest?.width ?? 0)).toBeLessThanOrEqual(width);
    }
    const barsFit = await chart.locator('.league-week-bar').evaluateAll((bars) =>
      bars.every((bar) => {
        const rect = bar.getBoundingClientRect();
        const track = bar.parentElement!.getBoundingClientRect();
        return (
          track.height >= 100 &&
          rect.height >= 2 &&
          rect.top >= track.top - 1 &&
          rect.bottom <= track.bottom + 1
        );
      }),
    );
    expect(barsFit).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page
      .getByRole('navigation', { name: '투자자 상세 탐색' })
      .getByRole('link', { name: '공개 종목', exact: true })
      .click();
    await expect(page.getByText('보유 이유: 공시를 확인한 보유 판단')).toBeVisible();
    await page
      .getByRole('navigation', { name: '투자자 상세 탐색' })
      .getByRole('link', { name: '투자 기록', exact: true })
      .click();
    await expect(page.getByText('당일 작성한 판단 근거')).toBeVisible();
    await expect(page.getByText('실적 악화', { exact: true })).toBeVisible();
    await expect(page.locator('main details')).toHaveCount(0);
    await page
      .getByRole('navigation', { name: '투자자 상세 탐색' })
      .getByRole('link', { name: '성과', exact: true })
      .click();
    await page.getByRole('button', { name: '일별', exact: true }).click();
    await expect(page.getByRole('heading', { name: '일별 수익률 · 주간 누적' })).toBeVisible();
    await expect(page.getByText('주간 누적 -1.00%', { exact: true })).toBeVisible();
    await expect(page.getByRole('table')).toContainText('+10.00%');
    await page.getByRole('button', { name: '주간', exact: true }).click();
    await page.getByRole('button', { name: /2026-09-28 주간.*일별 기록 보기/ }).click();
    await expect(page.getByRole('combobox', { name: '조회 주차' })).toHaveValue('2026-09-28');
    await expect(page.getByRole('link', { name: '멤버십 알아보기' })).toHaveCount(0);
    await page
      .getByRole('navigation', { name: '투자자 상세 탐색' })
      .getByRole('link', { name: '공개 종목', exact: true })
      .click();
    await expect(page.getByText('금액 비공개', { exact: true })).toBeVisible();
    await page.getByRole('link', { name: '← 투자 리그' }).click();
    await page.getByRole('link', { name: '차분한초보 투자 기록', exact: true }).click();
    await expect(page.getByRole('heading', { name: '투자 기록', exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: '주간 수익률', exact: true })).toBeHidden();
    await page.reload();
    await expect(page.getByRole('heading', { name: '투자 기록', exact: true })).toBeVisible();
  });
}
