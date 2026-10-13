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
  await expect(page.getByRole('heading', { name: '투자 리그를 준비하고 있어요' })).toBeVisible();
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
    width + 'px: 리그 경로는 준비 중 화면만 보여주고 리그 데이터를 요청하지 않는다',
    async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      const leagueRequests: string[] = [];
      page.on('request', (request) => {
        const pathname = new URL(request.url()).pathname;
        if (
          /\/api\/(league|users\/me\/(shared-portfolio|portfolio\/publication|investor-profile))/.test(
            pathname,
          )
        )
          leagueRequests.push(pathname);
      });

      for (const path of [
        '/league',
        '/league/profile',
        '/league/portfolio?week=2026-09-28#holdings',
        '/league/following',
        '/league/reviews',
        '/league/investor-1#investment-details',
        '/portfolio/publication?join=true',
        '/portfolio/records',
        '/membership',
      ]) {
        await page.goto(path);
        await expect(
          page.getByRole('heading', { name: '투자 리그를 준비하고 있어요' }),
        ).toBeVisible();
        await expect(page.getByRole('link', { name: '투자 리그' })).toHaveAttribute(
          'aria-current',
          'page',
        );
        await expect(page.getByRole('link', { name: '← 주간 리그' })).toHaveCount(0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
          true,
        );
      }

      expect(leagueRequests).toEqual([]);
      const returnLink = page.getByRole('link', { name: '내 포트폴리오로 돌아가기' });
      await returnLink.focus();
      await expect(returnLink).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(page).toHaveURL('/');
    },
  );
}
