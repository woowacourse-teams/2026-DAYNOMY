import { expect, test, type Page } from '@playwright/test';
import { mockLeagueApi } from './league-api';

const stock = {
  assetId: 1,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK',
  market: 'KOSPI',
};

async function checkWidth(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
  await page.route('**/api/auth/csrf', (route) =>
    route.fulfill({ json: { token: 'ux-test-token', headerName: 'X-CSRF-TOKEN' } }),
  );
  await page.route('**/api/auth/refresh', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/users/me', (route) => route.fulfill({ status: 401 }));
  await page.route('**/api/users/me/investor-profile', (route) => route.fulfill({ status: 204 }));
  await page.route('**/api/users/me/portfolio/transactions', (route) =>
    route.fulfill({ json: { transactions: [] } }),
  );
  await page.route('**/api/stocks?**', (route) => route.fulfill({ json: { stocks: [stock] } }));
});

for (const viewport of [
  { name: '데스크톱', width: 1440, height: 1000 },
  { name: '모바일', width: 390, height: 844 },
]) {
  test(`${viewport.name}: 프로필에서 마이페이지로 이동하고 실패한 로그아웃을 재시도한다`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    let loggedIn = true;
    let attempts = 0;
    await page.route('**/api/users/me', (route) =>
      loggedIn
        ? route.fulfill({
            json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' },
          })
        : route.fulfill({ status: 401 }),
    );
    await page.route('**/api/auth/logout', (route) => {
      expect(route.request().method()).toBe('POST');
      expect(route.request().headers()['x-csrf-token']).toBe('ux-test-token');
      if (++attempts === 1) return route.fulfill({ status: 503, json: { message: '연결 실패' } });
      loggedIn = false;
      return route.fulfill({ status: 204 });
    });
    await page.goto('/league');
    await page.getByRole('link', { name: '마이페이지' }).click();
    await expect(page).toHaveURL('/mypage');
    await expect(page.getByText('ux@example.invalid', { exact: true })).toBeVisible();
    await checkWidth(page);
    await page.getByRole('button', { name: '로그아웃', exact: true }).click();
    await expect(page.getByRole('alert')).toContainText('로그아웃하지 못했습니다');
    await expect(page.getByRole('link', { name: '마이페이지' })).toBeVisible();
    await page.getByRole('button', { name: '로그아웃', exact: true }).click();
    await expect(page).toHaveURL('/news');
    await expect(page.getByRole('link', { name: '로그인', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: '마이페이지' })).toHaveCount(0);
    await page.reload();
    await expect(page.getByRole('link', { name: '로그인', exact: true })).toBeVisible();
  });

  test(`${viewport.name}: 비공개 계정 정보와 공개 닉네임을 구분하고 중복 오류 후 저장한다`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    const account = {
      id: 2,
      email: 'ux@example.invalid',
      name: 'Google 계정 이름',
      nickname: '초보',
      role: 'USER',
    };
    await page.route('**/api/users/me', (route) => {
      if (route.request().method() === 'PATCH') {
        expect(route.request().headers()['x-csrf-token']).toBe('ux-test-token');
        const { nickname } = route.request().postDataJSON();
        if (nickname === '사용중')
          return route.fulfill({
            status: 409,
            json: { code: 'NICKNAME_ALREADY_EXISTS', message: '이미 사용 중인 닉네임입니다.' },
          });
        account.nickname = nickname;
      }
      return route.fulfill({ json: account });
    });
    await page.goto('/mypage');
    await expect(page.getByText(account.email, { exact: true })).toBeVisible();
    await expect(page.getByText(account.name, { exact: true })).toBeVisible();
    await expect(page.getByText('비공개', { exact: true })).toHaveCount(2);
    await expect(page.getByText('공개용', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '닉네임 변경' }).click();
    await page.getByLabel('새 닉네임', { exact: true }).fill('사용중');
    await page.getByRole('button', { name: '닉네임 저장' }).click();
    await expect(page.getByRole('alert')).toContainText('이미 사용 중');
    await expect(page.getByLabel('새 닉네임')).toHaveValue('사용중');
    await page.getByLabel('새 닉네임').fill('새로운공개별명');
    await page.getByRole('button', { name: '닉네임 저장' }).click();
    await expect(page.getByRole('status')).toContainText('닉네임을 변경했어요');
    await expect(page.getByRole('link', { name: '마이페이지' })).toHaveText('새');
    await expect(page.getByText(account.name, { exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByText('새로운공개별명', { exact: true })).toBeVisible();
    await checkWidth(page);
  });

  test(
    viewport.name + ': 마이페이지에서 리그 프로필·팔로우·설정으로 이동한다',
    async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.route('**/api/users/me', (route) =>
        route.fulfill({
          json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' },
        }),
      );
      await mockLeagueApi(page);
      const leagueRequests: string[] = [];
      page.on('request', (request) => {
        const pathname = new URL(request.url()).pathname;
        if (
          /\/api\/(league|users\/me\/(shared-portfolio|portfolio\/publication|investor-profile|league))/.test(
            pathname,
          )
        )
          leagueRequests.push(pathname);
      });

      await page.goto('/mypage');
      for (const [name, href] of [
        [/리그 프로필/, '/league/profile'],
        [/팔로우한 투자자/, '/league/following'],
        [/리그 설정/, '/portfolio/publication'],
      ] as const) {
        await expect(page.getByRole('link', { name })).toHaveAttribute('href', href);
      }
      expect(leagueRequests).toEqual([]);
      await checkWidth(page);
      await page.getByRole('link', { name: /팔로우한 투자자/ }).click();
      await expect(page.getByRole('heading', { name: '팔로잉', exact: true })).toBeVisible();
      await expect(page.getByText('팔로우한 투자자가 없습니다')).toBeVisible();
      expect(leagueRequests).toContain('/api/users/me/league/follows/summary');
    },
  );
}

test('모바일: 이름이 없는 기존 계정은 재로그인을 안내하고 닉네임 취소는 저장하지 않는다', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/users/me', (route) => {
    expect(route.request().method()).toBe('GET');
    return route.fulfill({
      json: { id: 2, email: 'ux@example.invalid', name: null, nickname: '공개별명', role: 'USER' },
    });
  });
  await page.goto('/mypage');
  await expect(page.getByText('다시 Google 로그인하면 이름을 불러옵니다.')).toBeVisible();
  await page.getByRole('button', { name: '닉네임 변경' }).click();
  await page.getByLabel('새 닉네임').fill('');
  await page.getByRole('button', { name: '닉네임 저장' }).click();
  await expect(page.getByRole('alert')).toContainText('1~20자');
  await page.getByLabel('새 닉네임').fill('바꾸지않을닉네임');
  await page.getByRole('button', { name: '취소', exact: true }).click();
  await expect(page.getByText('공개별명', { exact: true })).toBeVisible();
  await checkWidth(page);
});

test('모바일: 삭제된 돈 관리의 기존 주소는 비회원 포트폴리오로 이동하고 저장 데이터는 보존한다', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(() => localStorage.setItem('daynomy:financial-plans:v1', '보존할 기록'));
  for (const path of [
    '/finance',
    '/finance/deposits',
    '/finance/investing',
    '/finance/guide',
    '/finance/guide/first-account',
    '/finance/monthly-plan',
  ]) {
    await page.goto(path);
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: '내 포트폴리오' })).toBeVisible();
    await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeVisible();
    await expect(page.getByRole('banner').getByRole('link', { name: '로그인' })).toHaveAttribute(
      'href',
      '/login?returnTo=%2F',
    );
    await expect(page.getByRole('link', { name: '초보 돈 관리', exact: true })).toHaveCount(0);
    await expect(page.getByRole('navigation', { name: '초보 돈 관리 메뉴' })).toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem('daynomy:financial-plans:v1'))).toBe(
      '보존할 기록',
    );
  }
  await checkWidth(page);
  await page.getByRole('banner').getByRole('link', { name: '로그인' }).click();
  await expect(page).toHaveURL('/login?returnTo=%2F');
  await expect(page.getByRole('heading', { name: 'DAYNOMY 로그인' })).toBeVisible();
});

for (const width of [390, 1440]) {
  test(`${width}px: 로그인한 사용자가 리그 하위 화면과 첫 일별 성과를 조회한다`, async ({
    page,
  }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await mockLeagueApi(page);
    await page.route('**/api/users/me', (route) =>
      route.fulfill({
        json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' },
      }),
    );
    await page.route('**/api/users/me/portfolio/transactions', (route) =>
      route.fulfill({
        json: {
          transactions: [
            {
              id: 1,
              assetId: 1,
              assetCode: '005930',
              assetName: '삼성전자',
              category: 'STOCK',
              transactionType: 'HOLD',
              quantity: 1,
              unitPrice: 100,
              fee: 0,
              tradedOn: '2026-10-13',
              reason: '실적의 변화와 산업의 흐름을 살펴보며 보유합니다.',
              expectedHoldingPeriod: null,
              expectedChange: '',
              invalidationCondition: '',
              maximumAcceptableLossRate: null,
              writtenAfterTrade: false,
              reviews: [],
            },
          ],
        },
      }),
    );
    for (const [path, heading] of [
      ['/league', '주간 리그'],
      ['/league/portfolio', '종목 관리'],
      ['/portfolio/publication?join=true', '주간 리그 참여'],
      ['/league/following', '팔로잉'],
      ['/league/reviews', '투자 복기'],
      ['/league/profile', '리그 투자자'],
      ['/league/investor-1', '리그 투자자'],
    ]) {
      await page.goto(path);
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
      await expect(page.getByRole('heading', { name: heading, exact: true })).toBeFocused();
      if (path === '/league') {
        await expect(page.getByRole('link', { name: '종목 관리하기' })).toBeVisible();
        await expect(page.getByRole('navigation', { name: '내 리그' })).toBeVisible();
        await expect(page.getByRole('heading', { name: '주간 순위', exact: true })).toBeVisible();
      } else {
        await expect(page.getByRole('link', { name: '← 주간 리그' })).toBeVisible();
      }
      await expect(page.getByRole('alert')).toHaveCount(0);
      if (path === '/portfolio/publication?join=true') {
        await expect(page.getByLabel('공개 닉네임', { exact: true })).toBeVisible();
      }
      if (path === '/league/following') {
        await expect(page.getByText('팔로우한 투자자가 없습니다', { exact: true })).toBeVisible();
      }
      if (path === '/league/portfolio') {
        await page.getByRole('button', { name: '삼성전자 투자 기록 추가' }).click();
        await expect(page.getByRole('textbox', { name: '보유 이유' })).toBeFocused();
      }
      if (path === '/league/reviews') {
        await page.getByRole('button', { name: /복기 작성/ }).click();
        await expect(page.getByRole('textbox', { name: '실제 결과' })).toBeFocused();
      }
      await checkWidth(page);
      const smallButtons = await page
        .locator('main button:visible')
        .evaluateAll((buttons) =>
          buttons
            .filter((button) => button.getBoundingClientRect().height < 44)
            .map((button) => button.textContent),
        );
      expect(smallButtons, `${path} 터치 영역`).toEqual([]);
      await page.screenshot({
        path: testInfo.outputPath(`${path.split('?')[0].replaceAll('/', '-')}-${width}.png`),
        fullPage: true,
      });
      if (path === '/league/portfolio' || path === '/league/reviews') {
        await page.getByRole('button', { name: '취소', exact: true }).click();
        await expect(page.locator('main textarea')).toHaveCount(0);
      }
      if (path === '/league/portfolio') {
        await page.getByRole('button', { name: /복기 작성/ }).click();
        await expect(page).toHaveURL('/league/portfolio');
        await expect(page.getByRole('textbox', { name: '실제 결과' })).toBeFocused();
        await checkWidth(page);
        await page.screenshot({
          path: testInfo.outputPath(`inline-review-${width}.png`),
          fullPage: true,
        });
        await page.getByRole('button', { name: '취소', exact: true }).click();
        await expect(page.getByRole('button', { name: /복기 작성/ })).toBeFocused();
      }
    }
    await expect(page.getByRole('button', { name: '일별', exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await expect(
      page.getByRole('group', { name: '일별 수익률 그래프', exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole('group', { name: '일별 수익률 그래프', exact: true }),
    ).toContainText('0.00%');
    const lastDay = page
      .getByRole('group', { name: '일별 수익률 그래프', exact: true })
      .locator('time')
      .last();
    await lastDay.scrollIntoViewIfNeeded();
    await expect(lastDay).toBeInViewport({ ratio: 1 });
    await expect(page.getByRole('table')).toHaveCount(0);
    await checkWidth(page);
  });
}

for (const width of [1440, 390]) {
  test(`${width}px: 관리자 화면에서 기간별 종가 복구와 리그 재집계를 실행한다`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.route('**/api/users/me', (route) =>
      route.fulfill({
        json: { id: 1, email: 'admin@example.invalid', nickname: '관리자', role: 'ADMIN' },
      }),
    );
    await page.route('**/api/admin/stocks/prices/backfill?**', (route) => {
      expect(route.request().method()).toBe('POST');
      expect(route.request().headers()['x-csrf-token']).toBe('ux-test-token');
      const query = new URL(route.request().url()).searchParams;
      expect(query.get('from')).toBe('2026-10-01');
      expect(query.get('to')).toBe('2026-10-02');
      return route.fulfill({
        json: {
          from: '2026-10-01',
          to: '2026-10-02',
          synchronizedDateCount: 1,
          receivedCount: 100,
          createdCount: 0,
          updatedCount: 100,
          skippedCount: 0,
        },
      });
    });
    await page.route('**/api/admin/league/recalculate', (route) => {
      expect(route.request().method()).toBe('POST');
      expect(route.request().headers()['x-csrf-token']).toBe('ux-test-token');
      return route.fulfill({ json: { calculatedCount: 10 } });
    });
    await page.goto('/admin/stocks');
    await expect(page.getByRole('heading', { name: '기간별 종가 재수집' })).toBeVisible();
    await page.getByLabel('시작일', { exact: true }).fill('2026-10-01');
    await page.getByLabel('종료일', { exact: true }).fill('2026-10-02');
    await page.getByRole('button', { name: '선택 기간 재수집' }).click();
    await expect(page.getByText('2026.10.01 ~ 2026.10.02 재수집 결과')).toBeVisible();
    await page.getByRole('button', { name: '리그 성과 재집계' }).click();
    await expect(page.getByText('일간 기록 10건 처리')).toBeVisible();
    await checkWidth(page);
  });
}

for (const width of [1440, 390]) {
  test(`${width}px: 비회원 임시 포트폴리오가 종목 자료 화면에서도 연결된다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(() => {
      sessionStorage.setItem(
        'daynomy:guest-portfolio-holdings:v1',
        JSON.stringify([
          {
            assetId: 1,
            assetCode: '005930',
            name: '삼성전자',
            category: 'STOCK',
            market: 'KOSPI',
            quantity: 1,
            averagePurchasePrice: 70000,
          },
        ]),
      );
    });
    await page.route('**/api/assets/1/contents', (route) =>
      route.fulfill({
        json: { assetId: 1, assetCode: '005930', assetName: '삼성전자', contents: [] },
      }),
    );
    const privateRequests: string[] = [];
    page.on('request', (request) => {
      if (new URL(request.url()).pathname === '/api/users/me/portfolio')
        privateRequests.push(request.url());
    });
    await page.goto('/stocks/1');
    await expect(page.getByRole('heading', { name: '삼성전자 소식' })).toBeVisible();
    if (width === 1440)
      await expect(page.getByRole('complementary', { name: '내 포트폴리오 종목' })).toContainText(
        '삼성전자',
      );
    else
      await expect(page.getByRole('combobox', { name: '종목 선택', exact: true })).toHaveValue('1');
    expect(privateRequests).toEqual([]);
    await checkWidth(page);
  });
}
