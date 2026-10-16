import { expect, test, type Page } from '@playwright/test';
import { mockLeagueApi } from './league-api';

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
  await page.route('**/api/people', (route) => route.fulfill({ json: { people: [] } }));
  await page.route('**/api/contents?**', (route) =>
    route.fulfill({ json: { items: [], page: 1, totalPages: 0 } }),
  );
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
  await page.route('**/api/search/contents**', (route) =>
    route.fulfill({
      json: {
        items: [
          {
            kind: 'NEWS',
            id: article.id,
            source: 'DAYNOMY',
            title: article.title,
            date: article.publishedAt,
            category: article.category,
            imageUrl: article.imageUrl,
          },
        ],
        page: 1,
        size: 12,
        totalPages: 1,
        totalElements: 1,
      },
    }),
  );

  await page.goto('/news');
  await page.getByRole('button', { name: '검색 열기' }).click();
  await page.getByRole('dialog', { name: '통합 검색' }).getByRole('textbox').fill('금리');
  await page.getByRole('dialog', { name: '통합 검색' }).getByRole('textbox').press('Enter');

  await expect(page).toHaveURL(/\/search\?q=/);
  await page
    .getByRole('region', { name: '검색된 소식' })
    .getByRole('link', { name: /기준금리 동결 가능성 확대/ })
    .click();
  await expect(page).toHaveURL(/\/news\/7$/);
  await expect(page.getByRole('heading', { name: article.title, level: 1 })).toBeVisible();
});

test('오늘의 이슈에서 상세 본문을 읽고 소식 홈으로 돌아온다', async ({ page }) => {
  await page.clock.setFixedTime(new Date(article.publishedAt));
  await mockNewsApi(page);

  await page.goto('/news');
  await page
    .getByRole('region', { name: '오늘의 이슈' })
    .getByRole('link', { name: /기준금리 동결 가능성 확대/ })
    .click();

  await expect(page.getByRole('heading', { name: article.title, level: 1 })).toBeVisible();
  await expect(page.getByRole('region', { name: '뉴스 본문' })).toContainText(
    '기준금리는 당분간 동결될 전망입니다.',
  );
  await page.getByRole('button', { name: '전 페이지로 돌아가기' }).click();
  await expect(page).toHaveURL('/news');
  await expect(page.getByRole('region', { name: '오늘의 이슈' })).toContainText(article.title);
});

test('종목 상세에서 리그와 마이페이지로 이동하고 관리자 메뉴는 분리된다', async ({ page }) => {
  await mockLeagueApi(page);
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

  await page.goto('/stocks/1');
  await expect(page.getByRole('heading', { name: '삼성전자', level: 1 })).toBeVisible();
  await page
    .getByRole('navigation', { name: '주요 메뉴' })
    .getByRole('link', { name: '투자 리그' })
    .click();
  await expect(page).toHaveURL('/league');
  await expect(page.getByRole('heading', { name: '주간 리그', exact: true })).toBeVisible();
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
  test(
    width + 'px: 공개 리그·일별 성과를 조회하고 개인 경로는 로그인으로 보호한다',
    async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await mockLeagueApi(page);
      const privateRequests: string[] = [];
      page.on('request', (request) => {
        const path = new URL(request.url()).pathname;
        if (/^\/api\/users\/me\/(shared-portfolio|portfolio|investor-profile|league)/.test(path))
          privateRequests.push(path);
      });
      for (const path of ['/league', '/membership']) {
        await page.goto(path);
        await expect(page).toHaveURL('/league');
        await expect(page.getByRole('heading', { name: '주간 순위', exact: true })).toBeVisible();
        await expect(page.getByRole('link', { name: '투자리그 참여하기' })).toHaveAttribute(
          'href',
          '/login?returnTo=%2Fleague%2Fportfolio%23select',
        );
        await expect(page.getByRole('link', { name: '투자 리그', exact: true })).toHaveAttribute(
          'aria-current',
          'page',
        );
      }
      await page.getByRole('link', { name: '리그 투자자 공개 프로필 보기' }).click();
      await expect(page.getByRole('heading', { name: '리그 투자자', exact: true })).toBeVisible();
      await expect(page.locator('.league-chart-switch button')).toHaveText(['일별', '주간']);
      await expect(page.getByRole('button', { name: '일별', exact: true })).toHaveAttribute(
        'aria-pressed',
        'true',
      );
      const dailyChart = page.getByRole('group', { name: '일별 수익률 그래프', exact: true });
      await expect(dailyChart).toBeVisible();
      await expect(dailyChart.locator('.league-return-line circle')).toHaveCount(1);
      await expect(page.getByText('2026-10-13 종가 기준 · 집계 중')).toBeVisible();
      await expect(dailyChart).toContainText('성과 시작 종가 · 0%');
      await expect(dailyChart).toContainText('종가 수집 대기');
      await expect(page.getByRole('group', { name: '선택한 날짜의 성과' })).toHaveCount(0);
      await expect(page.getByRole('table')).toHaveCount(0);
      await page.getByRole('button', { name: '주간', exact: true }).click();
      const weeklyChart = page.getByRole('group', { name: '이번 주 주간 수익률 그래프' });
      await expect(weeklyChart).toBeVisible();
      await expect(weeklyChart.locator('.league-return-line circle')).toHaveCount(1);
      await weeklyChart.getByRole('button', { name: /주간 0.00% 일별 기록 보기/ }).click();
      await expect(dailyChart).toBeVisible();
      const back = page.getByRole('link', { name: '← 주간 리그' });
      await back.focus();
      await expect(back).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(page).toHaveURL('/league');

      for (const [path, returnTo] of [
        ['/league/profile', '/league/profile'],
        [
          '/league/portfolio?week=2026-10-12#holdings',
          '/league/portfolio?week=2026-10-12#holdings',
        ],
        ['/league/following', '/league/following'],
        ['/league/reviews', '/league/reviews'],
        ['/portfolio/publication?join=true', '/portfolio/publication?join=true'],
        ['/portfolio/records', '/league/portfolio'],
      ]) {
        await page.goto(path);
        await expect(page).toHaveURL('/login?returnTo=' + encodeURIComponent(returnTo));
        await expect(page.getByRole('heading', { name: 'DAYNOMY 로그인' })).toBeVisible();
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
          true,
        );
      }
      expect(privateRequests).toEqual([]);
    },
  );
}
