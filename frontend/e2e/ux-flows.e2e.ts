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

  test(`${viewport.name}: 보유 판단과 복기를 저장하고 재방문해도 수량은 유지된다`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.route('**/api/users/me', (route) =>
      route.fulfill({
        json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' },
      }),
    );
    const holding = {
      assetId: 1,
      assetCode: '005930',
      assetName: '삼성전자',
      category: 'STOCK',
      market: 'KOSPI',
      quantity: 2,
      averagePurchasePrice: 70000,
      hidden: false,
      reason: '',
      baseDate: '2026-10-02',
      closePrice: 77000,
      evaluationAmount: 154000,
      returnRate: 10,
    };
    await page.route('**/api/users/me/shared-portfolio', (route) =>
      route.fulfill({
        json: {
          holdings: [holding],
          totalPurchaseAmount: 140000,
          totalEvaluationAmount: 154000,
          totalReturnRate: 10,
          pricesComplete: true,
        },
      }),
    );
    let records: Array<Record<string, unknown>> = [];
    await page.route('**/api/users/me/portfolio/transactions', (route) =>
      route.fulfill({ json: { transactions: records } }),
    );
    await page.route('**/api/users/me/portfolio/decisions', (route) => {
      expect(route.request().headers()['x-csrf-token']).toBe('ux-test-token');
      const input = route.request().postDataJSON();
      expect(input.quantity).toBeUndefined();
      expect(input.requestKey).toBeTruthy();
      expect(input.decision.expectedHoldingPeriod).toBeNull();
      expect(input.decision.maximumAcceptableLossRate).toBeNull();
      const saved = {
        id: 1,
        assetId: 1,
        assetCode: '005930',
        assetName: '삼성전자',
        category: 'STOCK',
        transactionType: 'HOLD',
        quantity: 2,
        unitPrice: 70000,
        fee: 0,
        tradedOn: '2026-10-06',
        ...input.decision,
        writtenAfterTrade: true,
        reviews: [],
      };
      records = [saved];
      return route.fulfill({ status: 201, json: saved });
    });
    await page.route('**/api/users/me/portfolio/decisions/1/reviews', (route) => {
      expect(route.request().headers()['x-csrf-token']).toBe('ux-test-token');
      const saved = { id: 1, ...route.request().postDataJSON(), createdAt: '2026-10-06T00:00:00Z' };
      records[0] = { ...records[0], reviews: [saved] };
      return route.fulfill({ status: 201, json: saved });
    });
    await page.goto('/league/portfolio');
    await page.route('**/api/users/me/portfolio', (route) =>
      route.fulfill({
        json: { holdings: [{ ...stock, quantity: 2, averagePurchasePrice: 70000 }] },
      }),
    );
    await expect(page.getByRole('region', { name: '내 자산 요약', exact: true })).toBeVisible();
    await expect(page.getByText('2주', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '삼성전자 보유 이유 작성' }).click();
    await expect(page.getByLabel('보유하는 이유', { exact: true })).toBeFocused();
    await expect(page.getByRole('radio', { name: '미정', exact: true })).toBeChecked();
    const decisionForm = page.getByRole('form', { name: '종목 투자 기록 작성' });
    await expect(decisionForm).toHaveCSS('border-top-width', '0px');
    for (const fieldset of await decisionForm.locator('fieldset').all()) {
      await expect(fieldset).toHaveCSS('border-top-width', '0px');
    }
    await page.getByLabel('보유하는 이유', { exact: true }).fill('실적을 확인했습니다');
    await page.getByRole('button', { name: '내 포트폴리오에서 가져오기' }).click();
    await expect(page.getByRole('heading', { name: '가져올 자산 선택' })).toBeVisible();
    await expect(page.getByRole('button', { name: '보유 이유 저장' })).toBeHidden();
    await page.getByRole('button', { name: '취소', exact: true }).click();
    await expect(page.getByRole('textbox', { name: '보유하는 이유', exact: true })).toHaveValue(
      '실적을 확인했습니다',
    );
    await page.getByRole('button', { name: '보유 이유 저장' }).click();
    await expect(page.getByRole('heading', { name: '삼성전자 · 보유 기록' })).toBeVisible();
    await page.getByRole('button', { name: '결과 복기 추가' }).click();
    await page.getByLabel('실제 결과', { exact: true }).fill('성장 가정을 점검했습니다');
    await page.getByLabel('예상과 달랐던 점', { exact: true }).fill('변동성은 더 컸습니다');
    await page.getByLabel('다음 행동', { exact: true }).fill('다음 실적 확인');
    await page.getByRole('button', { name: '복기 저장' }).click();
    await expect(
      page.getByRole('region', { name: '종목별 투자 기록' }).getByRole('status'),
    ).toContainText('복기를 추가했어요');
    await expect(page.getByText('성장 가정을 점검했습니다', { exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByText('성장 가정을 점검했습니다', { exact: true })).toBeVisible();
    await expect(page.getByText('2주', { exact: true })).toBeVisible();
    await checkWidth(page);
  });

  test(`${viewport.name}: 원본을 가져와 숨기고 다시 보여도 자산과 원본은 유지된다`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.route('**/api/users/me', (route) =>
      route.fulfill({
        json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' },
      }),
    );
    const original = [{ ...stock, quantity: 10, averagePurchasePrice: 70000 }];
    await page.route('**/api/users/me/portfolio', (route) => {
      expect(route.request().method()).toBe('GET');
      return route.fulfill({ json: { holdings: original } });
    });
    await page.addInitScript((value) => {
      if (!localStorage.getItem('daynomy:portfolio-holdings:v1'))
        localStorage.setItem('daynomy:portfolio-holdings:v1', JSON.stringify(value));
    }, original);
    let holdings: Array<Record<string, unknown>> = [];
    let importedRecords: Array<Record<string, unknown>> = [];
    await page.route('**/api/users/me/portfolio/transactions', (route) =>
      route.fulfill({ json: { transactions: importedRecords } }),
    );
    const response = () => ({
      holdings,
      totalPurchaseAmount: holdings.reduce(
        (sum, h) => sum + Number(h.quantity) * Number(h.averagePurchasePrice),
        0,
      ),
      totalEvaluationAmount: holdings.reduce((sum, h) => sum + Number(h.quantity) * 77000, 0),
      totalReturnRate: holdings.length ? 10 : null,
      pricesComplete: true,
    });
    await page.route('**/api/users/me/shared-portfolio**', async (route) => {
      const method = route.request().method();
      const url = route.request().url();
      if (method === 'POST') {
        const input = route.request().postDataJSON();
        importedRecords = input.holdings.map(
          (
            h: {
              assetId: number;
              quantity: number;
              averagePurchasePrice: number;
              decision: Record<string, unknown>;
            },
            index: number,
          ) => ({
            id: index + 1,
            assetId: h.assetId,
            assetCode: stock.assetCode,
            assetName: stock.name,
            category: stock.category,
            transactionType: 'HOLD',
            quantity: h.quantity,
            unitPrice: h.averagePurchasePrice,
            fee: 0,
            tradedOn: '2026-10-09',
            ...h.decision,
            writtenAfterTrade: true,
            reviews: [],
          }),
        );
        holdings = input.holdings.map((h: Record<string, unknown>) => ({
          ...h,
          assetCode: stock.assetCode,
          assetName: stock.name,
          category: stock.category,
          market: stock.market,
          hidden: false,
          reason: '',
          baseDate: '2026-10-02',
          closePrice: 77000,
          evaluationAmount: Number(h.quantity) * 77000,
          returnRate: 10,
        }));
      } else if (method === 'PATCH' && url.endsWith('/visibility')) {
        const input = route.request().postDataJSON();
        expect(Object.keys(input)).toEqual(['hidden']);
        holdings = holdings.map((h) => ({
          ...h,
          hidden: input.hidden,
        }));
      } else if (method !== 'GET') return route.fulfill({ status: 404 });
      return route.fulfill({ json: response() });
    });
    await page.goto('/portfolio/records');
    await expect(page).toHaveURL('/league/portfolio');
    await expect(page.getByRole('link', { name: '리그 설정', exact: true })).toHaveAttribute(
      'href',
      '/portfolio/publication',
    );
    await expect(page.getByRole('heading', { name: '공개 설정', exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: '내 포트폴리오에서 가져오기' }).click();
    await expect(page.getByRole('button', { name: '1종목 가져오기' })).toBeDisabled();
    await page
      .getByRole('textbox', { name: '삼성전자 보유 이유', exact: true })
      .fill('실적 성장을 기대합니다');
    await page.getByRole('button', { name: '1종목 가져오기' }).click();
    await expect(page.getByRole('heading', { name: '리그 종목 1개' })).toBeVisible();
    await expect(page.getByText('실적 성장을 기대합니다', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '종목 직접 추가' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '수정', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '공유용에서 삭제' })).toHaveCount(0);
    await expect(page.getByText('10주', { exact: true })).toBeVisible();
    await page.getByRole('checkbox', { name: '삼성전자 공개할 종목으로 선택' }).click();
    await expect(
      page.getByRole('checkbox', { name: '삼성전자 공개할 종목으로 선택' }),
    ).not.toBeChecked();
    await expect(page.getByText('+10.00%', { exact: true })).toHaveCount(2);
    await page.reload();
    await expect(
      page.getByRole('checkbox', { name: '삼성전자 공개할 종목으로 선택' }),
    ).not.toBeChecked();
    await expect(page.getByText('10주', { exact: true })).toBeVisible();
    await checkWidth(page);
    await page.getByRole('checkbox', { name: '삼성전자 공개할 종목으로 선택' }).click();
    await expect(
      page.getByRole('checkbox', { name: '삼성전자 공개할 종목으로 선택' }),
    ).toBeChecked();
    await expect(page.getByRole('heading', { name: '리그 종목 1개' })).toBeVisible();
    expect(
      await page.evaluate(() =>
        JSON.parse(localStorage.getItem('daynomy:portfolio-holdings:v1') ?? '[]'),
      ),
    ).toEqual(original);
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
    await expect(page.getByRole('button', { name: /자산 추가/ })).toBeVisible();
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

test('모바일: 공개 범위가 미리 보이고 동의한 항목만 저장한다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/users/me', (route) =>
    route.fulfill({ json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' } }),
  );
  const profile = {
    publicId: 'ux-investor',
    displayName: '차분한초보',
    bio: '',
    experienceLevel: 'BEGINNER',
    riskProfile: 'BALANCED',
    profilePublic: false,
    leagueEnabled: false,
    allocationPublic: true,
    detailPublic: false,
    leagueEnabledAt: null,
  };
  await page.route('**/api/users/me/investor-profile', (route) => route.fulfill({ json: profile }));
  await page.route('**/api/users/me/portfolio/publication', (route) =>
    route.fulfill({ json: { ...profile, ...route.request().postDataJSON() } }),
  );
  await page.route('**/api/users/me/shared-portfolio', (route) =>
    route.fulfill({
      json: {
        holdings: [
          {
            ...stock,
            assetName: stock.name,
            quantity: 2,
            averagePurchasePrice: 70000,
            hidden: false,
            reason: '장기 성장 기대',
            baseDate: '2026-10-02',
            closePrice: 77000,
            evaluationAmount: 154000,
            returnRate: 10,
          },
        ],
        totalPurchaseAmount: 140000,
        totalEvaluationAmount: 154000,
        totalReturnRate: 10,
        pricesComplete: true,
      },
    }),
  );
  await page.goto('/league');
  await page.getByRole('link', { name: '리그 설정', exact: true }).click();
  await expect(page).toHaveURL('/portfolio/publication');
  await expect(page.getByRole('heading', { name: '리그 설정', exact: true })).toBeVisible();
  await expect(page.getByRole('radio', { name: /^나만 보기/ })).toBeChecked();
  await expect(page.getByLabel('공개 닉네임', { exact: true })).toBeVisible();
  await expect(page.getByRole('radio', { name: '투자 1년 미만' })).toBeChecked();
  await expect(page.getByRole('radio', { name: '중립형' })).toBeChecked();
  await expect(page.locator('main details')).toHaveCount(0);
  await expect(page.getByRole('checkbox', { name: '주식·ETF의 비중도 보여주기' })).toBeDisabled();
  await expect(
    page.getByRole('checkbox', { name: '주간 순위에 참여하기', exact: true }),
  ).toBeEnabled();
  await page.getByRole('checkbox', { name: '주간 순위에 참여하기', exact: true }).check();
  await expect(page.getByRole('radio', { name: /^성과만 공유/ })).toBeChecked();
  await expect(page.getByRole('checkbox', { name: '주식·ETF의 비중도 보여주기' })).toBeEnabled();
  await page.getByRole('button', { name: '저장' }).click();
  await expect(page.getByText(/저장했습니다\./)).toBeVisible();
  await expect(page.getByRole('link', { name: '주간 순위 보기 →' })).toBeVisible();
  await checkWidth(page);
  await page.getByRole('link', { name: '내 리그', exact: true }).click();
  await expect(page.getByRole('heading', { name: '내 리그', exact: true })).toBeVisible();
  await expect(page.getByRole('radio')).toHaveCount(0);
  await page.getByRole('link', { name: '리그 설정', exact: true }).click();
  await page.getByRole('link', { name: '← 투자 리그' }).click();
  await expect(page).toHaveURL('/league');
});

test('모바일: 공개 종목 상세와 8주 기록이 화면 밖으로 넘치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/users/me', (route) =>
    route.fulfill({ json: { id: 2, email: 'ux@example.invalid', nickname: '초보', role: 'USER' } }),
  );
  await page.route('**/api/league/investors/ux', (route) =>
    route.fulfill({
      json: {
        publicId: 'ux',
        displayName: '차분한초보',
        bio: '기록으로 배우는 투자',
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
        history: Array.from({ length: 8 }, (_, index) => ({
          weekStart: `2026-09-${String(index + 1).padStart(2, '0')}`,
          weeklyReturnRate: index / 2,
          maxDrawdownRate: -1,
        })),
        decisionCount: 4,
        reviewCompletionRate: 75,
        followed: false,
        detailAvailable: true,
      },
    }),
  );
  await page.route('**/api/league/investors/ux/details', (route) =>
    route.fulfill({
      json: {
        publicId: 'ux',
        asOfDate: '2026-09-26',
        holdings: [
          { assetName: '삼성전자', category: 'STOCK', weight: 60, weeklyContributionRate: 1.2 },
        ],
        decisions: [],
      },
    }),
  );
  await page.route('**/api/league/weeks', (route) =>
    route.fulfill({
      json: {
        weeks: [
          { weekStart: '2026-09-28', weekEnd: '2026-10-04', confirmed: true },
          { weekStart: '2026-09-21', weekEnd: '2026-09-27', confirmed: true },
        ],
      },
    }),
  );
  await page.route('**/api/league/investors/ux/daily-history**', (route) => {
    const weekStart = new URL(route.request().url()).searchParams.get('weekStart') || '2026-09-28';
    const missing = weekStart === '2026-09-21';
    return route.fulfill({
      json: {
        weekStart,
        weekEnd: missing ? '2026-09-27' : '2026-10-04',
        asOfDate: missing ? null : '2026-09-28',
        eligibleFrom: '2026-08-03',
        confirmed: !missing,
        weeklyReturnRate: missing ? null : 2.5,
        days: [
          {
            baseDate: weekStart,
            dailyReturnRate: missing ? null : 2.5,
            cumulativeReturnRate: missing ? null : 2.5,
            status: missing ? 'EXCLUDED' : 'CALCULATED',
            reason: missing ? 'MISSING_PRICE' : null,
          },
        ],
      },
    });
  });
  await page.goto('/league/ux#investment-details');
  await expect(page.getByRole('heading', { name: '공개 종목' })).toBeVisible();
  await expect(page.getByText('+1.20% 기여')).toBeVisible();
  await page
    .getByRole('navigation', { name: '투자자 상세 탐색' })
    .getByRole('link', { name: '성과', exact: true })
    .click();
  await page.getByRole('button', { name: '일별', exact: true }).click();
  await expect(page.getByRole('table')).toBeVisible();
  await page.getByRole('combobox', { name: '조회 주차' }).selectOption('2026-09-21');
  await expect(page.getByText('종가 누락 · 집계 대기')).toBeVisible();
  await expect(page.getByText('주간 누적 집계 대기')).toBeVisible();
  await checkWidth(page);
});
