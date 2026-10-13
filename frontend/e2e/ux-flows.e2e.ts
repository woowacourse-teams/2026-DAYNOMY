import { expect, test, type Page } from '@playwright/test';

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

  test(viewport.name + ': 마이페이지 리그 링크는 준비 중 화면으로 연결된다', async ({ page }) => {
    await page.setViewportSize(viewport);
    await page.route('**/api/users/me', (route) =>
      route.fulfill({
        json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' },
      }),
    );
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

    await page.goto('/mypage');
    for (const name of [/리그 프로필/, /팔로우한 투자자/, /리그 설정/]) {
      const link = page.getByRole('link', { name });
      await expect(link).toContainText('투자 리그 준비 중');
      await link.click();
      await expect(
        page.getByRole('heading', { name: '투자 리그를 준비하고 있어요' }),
      ).toBeVisible();
      await page.goBack();
      await expect(page.getByRole('heading', { name: '마이페이지' })).toBeVisible();
    }
    expect(leagueRequests).toEqual([]);
    await checkWidth(page);
  });
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
    await expect(page.getByRole('button', { name: '자산 추가', exact: true })).toBeVisible();
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

test('모바일: 로그인한 사용자도 리그 참여·공개 설정에 접근하면 준비 중 안내를 본다', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/users/me', (route) =>
    route.fulfill({ json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' } }),
  );
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
    '/league/portfolio',
    '/portfolio/publication?join=true',
    '/league/profile',
    '/league/ux#investment-details',
  ]) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: '투자 리그를 준비하고 있어요' })).toBeVisible();
    await expect(page.locator('main').getByRole('button')).toHaveCount(0);
    await checkWidth(page);
  }
  expect(leagueRequests).toEqual([]);
});
