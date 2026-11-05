import { expect, test, type Page } from '@playwright/test';

async function introDistance(page: Page) {
  return page
    .locator('.film-scroll-stops > div')
    .nth(1)
    .evaluate((el) => (el as HTMLElement).offsetTop);
}

test.beforeEach(async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    (route) => route.fulfill({ status: 401 }),
  );
});

for (const width of [1440, 390]) {
  test(`${width}px: 첫 방문 소개에서 시작하고 재방문과 소개 다시 보기를 지원한다`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('AI로 한눈에.');
    await expect(page).toHaveTitle('서비스 소개 | DAYNOMY');
    await expect(page.getByRole('banner')).toHaveCount(1);
    await expect(page.getByRole('contentinfo')).toHaveCount(1);
    await expect(page.locator('iframe, video')).toHaveCount(0);
    await expect(page.locator('.hero .primary-cta')).toBeInViewport({ ratio: 1 });
    await page.locator('.hero .primary-cta').click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toBeFocused();
    await expect(page.getByRole('button', { name: '종목 추가', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: '사용 가이드', exact: true })).toHaveCount(0);
    await expect(page.getByRole('complementary', { name: '처음 이용 안내' })).toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem('daynomy:service-started:v1'))).toBe('1');
    await page.reload();
    await expect(page.locator('.landing-page')).toHaveCount(0);
    await expect(page.getByRole('button', { name: '종목 추가', exact: true })).toBeVisible();
    await page.getByRole('link', { name: '서비스 소개', exact: true }).click();
    await expect(page).toHaveURL('/welcome');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('AI로 한눈에.');
  });
}

test('검은 브랜드 인트로가 합쳐지고 갈라진 뒤 소개 장면은 자동으로 넘어가지 않는다', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/welcome');
  const intro = page.locator('.brand-intro');
  await expect(intro).toBeVisible();
  await expect(intro).toHaveCSS('background-color', 'rgb(9, 9, 9)');
  await expect(page.locator('.welcome-film')).toHaveCSS('overflow-y', 'hidden');
  await expect(intro).toHaveClass('brand-intro phase-combine');
  await expect(intro).toHaveClass('brand-intro phase-seam');
  await expect(intro.locator('.curtain')).toHaveCount(2);
  await expect(intro).toHaveClass('brand-intro phase-open');
  expect(
    await intro.locator('.curtain-top').evaluate((el) => getComputedStyle(el).transitionDuration),
  ).not.toBe('0s');
  await expect(page.locator('.hero .primary-cta')).toBeVisible();
  await expect(intro).toHaveCount(0);
  await expect(page.locator('.welcome-film')).toHaveCSS('overflow-y', 'auto');
  await expect(page.getByRole('button', { name: /소개 재생|소개 일시정지/ })).toHaveCount(0);
  await page.clock.install();
  await page.clock.fastForward(60000);
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'portfolio');
  expect(await page.locator('.welcome-film').evaluate((el) => el.scrollTop)).toBe(0);
});

test('인트로를 건너뛰면 본문에 초점을 두고 사용법을 바로 스크롤할 수 있다', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/welcome');
  await page.getByRole('button', { name: '인트로 건너뛰기' }).click();
  await expect(page.locator('.brand-intro')).toHaveCount(0);
  await expect(page.getByRole('main')).toBeFocused();
  await expect(page.locator('.welcome-film')).toHaveCSS('overflow-y', 'auto');
  await page
    .getByRole('group', { name: '소개 장면 선택' })
    .getByRole('button', { name: '투자 리그' })
    .click();
  await page.getByRole('button', { name: '사용법 살펴보기' }).click();
  const root = page.locator('.welcome-film');
  const start = await root.evaluate((el) => el.scrollTop);
  await page.locator('#guide').getByRole('figure').hover();
  await page.mouse.wheel(0, 120);
  await expect.poll(() => root.evaluate((el) => el.scrollTop)).toBe(start + 120);
});

test('동작 줄이기 설정에서는 인트로를 생략하고 재생 중 설정을 바꿔도 종료한다', async ({
  page,
}) => {
  await page.goto('/welcome');
  await expect(page.locator('.brand-intro')).toHaveCount(0);
  await expect(page.locator('.hero .primary-cta')).toBeVisible();
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.reload();
  await expect(page.locator('.brand-intro')).toBeVisible();
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('.brand-intro')).toHaveCount(0);
  await expect(page.locator('.welcome-film')).toHaveCSS('overflow-y', 'auto');
});

test('기기 위에서 휠로 앞뒤 장면을 넘겨도 기기와 시작 버튼은 같은 자리에 머문다', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/welcome');
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-motion-paused', 'true', {
    timeout: 10000,
  });
  await expect(page.locator('.brand-intro')).toHaveCount(0);
  const root = page.locator('.welcome-film');
  const phone = page.locator('.film-device .mockup-hardware');
  const original = await phone.elementHandle();
  await phone.hover();
  const box = await phone.boundingBox();
  const distance = await introDistance(page);
  for (const [index, scene] of [
    [1, 'news'],
    [2, 'analysis'],
    [3, 'league'],
  ] as const) {
    await page.mouse.wheel(0, 120);
    await expect(root).toHaveAttribute('data-scene', scene);
    await expect.poll(() => root.evaluate((el) => el.scrollTop)).toBe(index * distance);
    expect(
      await original!.evaluate(
        (el) => el === document.querySelector('.film-device .mockup-hardware'),
      ),
    ).toBe(true);
    await expect.poll(() => phone.boundingBox()).toEqual(box);
    await expect(page.locator('.hero .primary-cta')).toBeInViewport({ ratio: 1 });
  }
  await page.mouse.wheel(0, -120);
  await expect(root).toHaveAttribute('data-scene', 'analysis');
  await expect.poll(() => root.evaluate((el) => el.scrollTop)).toBe(2 * distance);
});

test('장면 버튼으로 AI와 근거를 바로 열고 동작 줄이기에서도 동일하게 탐색한다', async ({
  page,
}) => {
  await page.goto('/welcome');
  const choices = page.getByRole('group', { name: '소개 장면 선택' });
  await choices.getByRole('button', { name: 'AI 분석 · 근거' }).click();
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'analysis');
  await expect(page.locator('.film-step-copy')).toHaveText(
    '03 / 04AI 분석과 판단의 근거를 함께 확인해요.',
  );
  await expect(
    page.getByRole('img', { name: /AI 분석 · 근거 실제 서비스 화면.*출처/ }),
  ).toBeVisible();
  await expect(choices.getByRole('button')).toHaveCount(4);
  expect(
    await page.evaluate(
      () => document.getAnimations().filter((a) => a.playState === 'running').length,
    ),
  ).toBe(0);
  await choices.getByRole('button', { name: '투자 리그' }).click();
  await expect(page.getByRole('figure', { name: '투자 리그 소개용 목업' })).toBeInViewport({
    ratio: 1,
  });
  await expect(page.locator('.film-step-copy')).toHaveText('04 / 04다른 투자자의 기록도 살펴봐요.');
  await page.getByRole('link', { name: 'DAYNOMY 홈' }).click();
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'portfolio');
  await page.getByRole('button', { name: '다음 소개 장면으로' }).click();
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'news');
});

test('화면 크기를 바꿔도 선택한 장면과 스크롤 위치가 일치한다', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 568 });
  await page.goto('/welcome');
  await page
    .getByRole('group', { name: '소개 장면 선택' })
    .getByRole('button', { name: '투자 리그' })
    .click();
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'league');
  await page.setViewportSize({ width: 1440, height: 900 });
  await expect
    .poll(() => page.locator('.welcome-film').evaluate((el) => el.scrollTop))
    .toBe(3 * (await introDistance(page)));
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'league');
  await expect(page.locator('.hero .primary-cta')).toBeInViewport({ ratio: 1 });
});

for (const viewport of [
  { width: 320, height: 568 },
  { width: 320, height: 700 },
  { width: 1280, height: 600 },
  { width: 390, height: 844 },
  { width: 768, height: 900 },
  { width: 1440, height: 900 },
]) {
  test(`${viewport.width}×${viewport.height}: 목업과 시작 버튼이 겹치지 않고 한 화면에 들어온다`, async ({
    page,
  }) => {
    await page.setViewportSize(viewport);
    await page.goto('/welcome');
    await expect(page.getByRole('main')).toHaveCount(1);
    for (const name of ['내 포트폴리오', '관련 소식', 'AI 분석 · 근거', '투자 리그']) {
      await page
        .getByRole('group', { name: '소개 장면 선택' })
        .getByRole('button', { name })
        .click();
      await expect(page.locator('.hero .primary-cta')).toBeInViewport({ ratio: 1 });
      const fits = await page.evaluate(() => {
        const phone = document
          .querySelector('.film-device .mockup-hardware')!
          .getBoundingClientRect();
        const copy = document.querySelector('.hero-content')!.getBoundingClientRect();
        const cta = document.querySelector('.film-actions')!.getBoundingClientRect();
        const controls = document.querySelector('.film-controls')!.getBoundingClientRect();
        return {
          page:
            document.documentElement.scrollWidth <= innerWidth &&
            document.documentElement.scrollHeight <= innerHeight + 1,
          phone:
            phone.width >= 110 &&
            phone.left >= 0 &&
            phone.right <= innerWidth &&
            (innerWidth > 800 ? phone.left >= copy.right : phone.top >= copy.bottom - 1) &&
            phone.bottom <= (innerWidth > 800 ? controls.top : cta.top) + 1,
        };
      });
      expect(fits.page, `${name}: 페이지 넘침`).toBe(true);
      expect(fits.phone, `${name}: 목업 겹침`).toBe(true);
    }
    await page.getByRole('button', { name: '사용법 살펴보기' }).click();
    await expect(page.locator('#guide-title')).toBeInViewport({ ratio: 1 });
    await expect(page.locator('.welcome-guide-page')).toHaveCount(5);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
  });
}

test('소개용 예시임을 밝히고 실제 AI 요청 없이 한국어 안내와 약관을 제공한다', async ({ page }) => {
  const requests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/') && request.url().includes('/analysis'))
      requests.push(request.url());
  });
  await page.goto('/welcome');
  await expect(page).toHaveTitle('서비스 소개 | DAYNOMY');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ko');
  await expect(page.getByRole('figure', { name: '내 포트폴리오 소개용 목업' })).toBeVisible();
  await expect(page.getByText('소개용 예시 · 투자 참고 자료')).toBeVisible();
  await expect(page.getByRole('link', { name: '이용약관', exact: true })).toHaveAttribute(
    'href',
    '/terms',
  );
  await expect(page.getByRole('link', { name: '개인정보처리방침', exact: true })).toHaveAttribute(
    'href',
    '/privacy',
  );
  expect(requests).toEqual([]);
});

test('키보드로 본문·시작하기·거래 기록을 사용하고 닫으면 초점이 돌아온다', async ({ page }) => {
  await page.goto('/welcome');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: '본문 바로가기' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  await expect(page.getByRole('button', { name: '메뉴 열기' })).toHaveCount(0);
  await page.locator('.hero .primary-cta').focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toBeFocused();
  const add = page.getByRole('button', { name: '종목 추가', exact: true });
  await add.focus();
  await page.keyboard.press('Enter');
  await page.getByRole('button', { name: '한 종목 추가', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog', { name: '거래 기록' })).toBeVisible();
  await expect(page.getByRole('searchbox', { name: '종목 검색' })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(add).toBeFocused();
});

for (const width of [1440, 390]) {
  test(`${width}px: 사용법은 휠 입력만큼 스크롤되고 기술력과 FAQ로 이어진다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: width === 1440 ? 'no-preference' : 'reduce' });
    await page.goto('/welcome');
    await page
      .getByRole('group', { name: '소개 장면 선택' })
      .getByRole('button', { name: '투자 리그' })
      .click();
    const root = page.locator('.welcome-film');
    await expect
      .poll(() => root.evaluate((el) => el.scrollTop))
      .toBe(3 * (await introDistance(page)));
    const introEnd = await root.evaluate((el) => el.scrollTop);
    await page.locator('.film-device').hover();
    await page.mouse.wheel(0, 120);
    await expect.poll(() => root.evaluate((el) => el.scrollTop)).toBe(introEnd + 120);
    const guidePages = page.locator('.welcome-guide-page');
    await expect(guidePages).toHaveCount(5);
    await guidePages
      .first()
      .evaluate((el) => el.scrollIntoView({ block: 'start', behavior: 'instant' }));
    await guidePages.first().getByRole('figure').hover();
    const start = await root.evaluate((el) => el.scrollTop);
    let distance = 0;
    for (const delta of [40, 40, 120, -90, -110]) {
      distance += delta;
      await page.mouse.wheel(0, delta);
      await expect.poll(() => root.evaluate((el) => el.scrollTop)).toBe(start + distance);
    }
    for (let index = 0; index < 5; index++) {
      const guide = guidePages.nth(index);
      await expect(guide.getByRole('heading', { level: 2 })).toBeInViewport({ ratio: 1 });
      await expect(guide.getByRole('figure')).toBeInViewport({ ratio: 1 });
      await expect(guide.getByRole('listitem')).toHaveCount(3);
      await guide.getByRole('link', { name: index < 4 ? /다음:/ : /기술력 살펴보기/ }).click();
    }
    await expect(page.getByRole('heading', { name: /근거가 보이는/ })).toBeInViewport();
    await expect(page.getByRole('button', { name: '메뉴 열기' })).toHaveCount(0);
    await expect(
      page.getByRole('list', { name: 'AI 분석 과정' }).getByRole('listitem'),
    ).toHaveCount(3);
    const league = page.getByRole('region', { name: /요청이 몰려도/ });
    await league.getByRole('heading', { level: 2 }).scrollIntoViewIfNeeded();
    await expect(league.getByRole('heading', { level: 2 })).toBeInViewport();
    await expect(league.getByRole('definition')).toHaveText([
      '5.05건 →100건',
      '약 10초 →21.9ms',
      '타임아웃 →0건',
    ]);
    await expect(
      league.getByRole('list', { name: '투자 리그 성능과 안정성 개선' }).getByRole('listitem'),
    ).toHaveCount(3);
    await expect(league.getByRole('link', { name: '투자 리그 살펴보기' })).toHaveAttribute(
      'href',
      '/league',
    );
    await page.getByText('회원가입 없이 이용할 수 있나요?', { exact: true }).click();
    await expect(page.locator('.welcome-faq details[open]')).toContainText('비회원도 1회 체험');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.locator('.welcome-start .primary-cta').click();
    await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toBeFocused();
  });
}

test('사용법 바로가기로 이동하고 로고로 첫 소개 장면에 돌아온다', async ({ page }) => {
  await page.goto('/welcome');
  await page
    .getByRole('group', { name: '소개 장면 선택' })
    .getByRole('button', { name: '투자 리그' })
    .click();
  await page.getByRole('button', { name: '사용법 살펴보기' }).click();
  await expect(page.locator('#guide-title')).toBeFocused();
  await expect(page.locator('#guide-title')).toBeInViewport({ ratio: 1 });
  await page.getByRole('link', { name: 'DAYNOMY 홈' }).click();
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'portfolio');
  await expect.poll(() => page.locator('.welcome-film').evaluate((el) => el.scrollTop)).toBe(0);
});

test('키보드 PageDown과 PageUp으로 스크롤 장면을 앞뒤로 살펴본다', async ({ page }) => {
  await page.goto('/welcome');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await page.getByRole('main').focus();
  await expect(page.getByRole('main')).toBeFocused();
  await page.keyboard.press('PageDown');
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'news');
  await expect
    .poll(() => page.locator('.welcome-film').evaluate((el) => el.scrollTop))
    .toBe(await introDistance(page));
  await page.keyboard.press('PageUp');
  await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'portfolio');
});

test.describe('터치 탐색', () => {
  test.use({ viewport: { width: 390, height: 844 }, hasTouch: true, isMobile: true });
  test('터치로 소개 장면을 바꾸고 사용법은 연속해서 스크롤한다', async ({ page }) => {
    await page.goto('/welcome');
    await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'portfolio');
    const input = await page.context().newCDPSession(page);
    async function swipe(from: number, to: number) {
      await input.send('Input.dispatchTouchEvent', {
        type: 'touchStart',
        touchPoints: [{ x: 195, y: from }],
      });
      for (let index = 1; index <= 6; index++) {
        await input.send('Input.dispatchTouchEvent', {
          type: 'touchMove',
          touchPoints: [{ x: 195, y: from + ((to - from) * index) / 6 }],
        });
        await page.waitForTimeout(30);
      }
      await input.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    }
    await swipe(600, 420);
    await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'news');
    await expect
      .poll(() => page.locator('.welcome-film').evaluate((el) => el.scrollTop))
      .toBe(await introDistance(page));
    await swipe(420, 600);
    await expect(page.locator('.welcome-film')).toHaveAttribute('data-scene', 'portfolio');
    await expect.poll(() => page.locator('.welcome-film').evaluate((el) => el.scrollTop)).toBe(0);
    await expect(page.locator('.hero .primary-cta')).toBeInViewport({ ratio: 1 });
    await page
      .getByRole('group', { name: '소개 장면 선택' })
      .getByRole('button', { name: '투자 리그' })
      .click();
    await expect
      .poll(() => page.locator('.welcome-film').evaluate((el) => el.scrollTop))
      .toBe(3 * (await introDistance(page)));
    await page.getByRole('button', { name: '사용법 살펴보기' }).click();
    await expect(page.locator('#guide-title')).toBeInViewport();
    const root = page.locator('.welcome-film');
    const start = await root.evaluate((el) => el.scrollTop);
    const pageHeight = await page.locator('#guide').evaluate((el) => el.clientHeight);
    await swipe(650, 430);
    await expect.poll(() => root.evaluate((el) => el.scrollTop)).toBeGreaterThan(start + 100);
    await page.waitForTimeout(250); // Let native touch inertia settle before reversing.
    const after = await root.evaluate((el) => el.scrollTop);
    expect(after).toBeLessThan(start + pageHeight - 100);
    await swipe(430, 650);
    await expect.poll(() => root.evaluate((el) => el.scrollTop)).toBeLessThan(after - 100);
    await input.detach();
  });
});

test('공유 뉴스 상세는 소개를 거치지 않고 쿼리·해시를 보존한다', async ({ page }) => {
  await page.route('**/api/news/7', (route) =>
    route.fulfill({
      json: {
        id: 7,
        title: '공유 뉴스 테스트',
        category: 'ECONOMY',
        imageUrl: null,
        publishedAt: '2026-10-26T10:00:00Z',
        content: '공유 뉴스 본문',
        sources: [],
      },
    }),
  );
  await page.route('**/api/news/7/keywords', (route) => route.fulfill({ json: { keywords: [] } }));
  await page.route('**/api/news/7/market-analysis', (route) =>
    route.fulfill({ json: { risk: '확인할 위험', watchPoint: '확인할 지표' } }),
  );
  await page.goto('/news/7?source=share#analysis');
  await expect(page.getByRole('heading', { name: '공유 뉴스 테스트' })).toBeVisible();
  await expect(page).toHaveURL('/news/7?source=share#analysis');
  await expect(page.locator('.landing-page')).toHaveCount(0);
  await page.getByRole('link', { name: '포트폴리오', exact: true }).click();
  await expect(page.getByRole('button', { name: '종목 추가', exact: true })).toBeVisible();
});

test('목업 재생 중에도 연속 휠 입력은 다음 한 단계만 보여준다', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.goto('/welcome');
  await page.getByRole('button', { name: '인트로 건너뛰기' }).click();
  const root = page.locator('.welcome-film');
  await page.locator('.film-browser-screen img').evaluate(async (image: HTMLImageElement) => {
    await image.decode();
    image.getAnimations().forEach((animation) => {
      animation.currentTime = 6000;
    });
  });
  await expect(root).toHaveAttribute('data-scene', 'portfolio');
  expect(await root.evaluate((element) => element.scrollTop)).toBe(0);
  const box = (await page.locator('.film-browser').boundingBox())!;
  await page.mouse.move(box.x + box.width / 2, box.y + 100);
  const distance = await introDistance(page);
  for (const [index, scene] of ['news', 'analysis', 'league'].entries()) {
    for (const delta of [400, 200, 80]) {
      await page.mouse.wheel(0, delta);
      await page.waitForTimeout(40);
    }
    await expect(root).toHaveAttribute('data-scene', scene);
    await expect
      .poll(() => root.evaluate((element) => element.scrollTop))
      .toBe((index + 1) * distance);
    await page.waitForTimeout(250);
  }
  await page.mouse.wheel(0, 120);
  await expect.poll(() => root.evaluate((element) => element.scrollTop)).toBe(3 * distance + 120);
});
