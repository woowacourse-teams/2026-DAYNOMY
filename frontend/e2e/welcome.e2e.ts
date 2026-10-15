import { expect, test } from '@playwright/test';

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
    await expect(page).toHaveURL('/welcome');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('매일의 경제 이슈를');
    await expect(page.locator('iframe')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.getByRole('link', { name: 'DAYNOMY 시작하기', exact: true }).first().click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toBeFocused();
    await expect(page).toHaveTitle('내 포트폴리오 | DAYNOMY');
    await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('daynomy:service-started:v1'))).toBe('1');
    await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toHaveCSS(
      'font-size',
      width === 390 ? '22px' : '24px',
    );
    await page.reload();
    await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeVisible();
    await expect(page.locator('.landing-page')).toHaveCount(0);
    await page.getByRole('link', { name: '서비스 소개', exact: true }).click();
    await expect(page).toHaveURL('/welcome');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('매일의 경제 이슈를');
  });
}

test('한국어 문서와 랜드마크를 제공하고 장식용 복제 문구를 반복해서 읽지 않는다', async ({
  page,
}) => {
  await page.goto('/welcome');
  await expect(page.locator('html')).toHaveAttribute('lang', 'ko');
  await expect(page).toHaveTitle('서비스 소개 | DAYNOMY');
  await expect(page.getByRole('banner')).toHaveCount(1);
  await expect(page.getByRole('main')).toHaveCount(1);
  await expect(page.getByRole('contentinfo')).toHaveCount(1);
  await expect(
    page.getByRole('list', { name: '분석 대상 경제 이슈' }).getByRole('listitem'),
  ).toHaveCount(5);
  await expect(
    page.getByRole('list', { name: '주요 기능 목록' }).getByRole('listitem'),
  ).toHaveCount(8);
  await expect(page.getByRole('figure')).toHaveCount(3);
  await expect(page.getByRole('button', { name: '모션 정지', exact: true })).toBeDisabled();
});

test('인트로 중에도 본문으로 건너뛰고 자동 움직임을 정지·재생할 수 있다', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.clock.install();
  await page.goto('/welcome');
  await expect(page.getByRole('button', { name: '인트로 건너뛰기' })).toBeVisible();
  await page.keyboard.press('Tab');
  const skipLink = page.getByRole('link', { name: '본문 바로가기' });
  await expect(skipLink).toBeFocused();
  expect(
    await skipLink.evaluate((element) => element.getBoundingClientRect().top),
  ).toBeGreaterThanOrEqual(0);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  await expect(page.locator('.hero .cta-row')).toHaveCSS('opacity', '1');
  await expect(page.locator('.brand-intro')).toHaveCount(0);
  await page.clock.fastForward(5000);
  await expect(page.locator('.brand-intro')).toHaveCount(0);
  expect(await page.evaluate(() => document.body.style.overflow)).toBe('');

  const pause = page.getByRole('button', { name: '모션 정지', exact: true });
  await pause.focus();
  await page.keyboard.press('Space');
  await expect(pause).toHaveAttribute('aria-pressed', 'true');
  expect(
    await page.evaluate(
      () => document.getAnimations().filter((a) => a.playState === 'running').length,
    ),
  ).toBe(0);
  await page.keyboard.press('Space');
  await expect(pause).toHaveAttribute('aria-pressed', 'false');
  expect(
    await page.evaluate(
      () => document.getAnimations().filter((a) => a.playState === 'running').length,
    ),
  ).toBeGreaterThan(0);

  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(pause).toBeDisabled();
  await expect(pause).toHaveAttribute('aria-pressed', 'true');
  expect(
    await page.evaluate(
      () => document.getAnimations().filter((a) => a.playState === 'running').length,
    ),
  ).toBe(0);
});

test('키보드로 메뉴·FAQ·시작하기·거래 기록을 사용하고 닫으면 초점이 돌아온다', async ({ page }) => {
  await page.goto('/welcome');
  const menu = page.getByRole('button', { name: '메뉴 열기' });
  await menu.focus();
  await page.keyboard.press('Enter');
  const dialog = page.getByRole('dialog', { name: '전체 메뉴' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: '메뉴 닫기' })).toBeFocused();
  await menu.evaluate((element) => element.focus());
  await expect(dialog.getByRole('button', { name: '메뉴 닫기' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(dialog.getByRole('link', { name: '내 포트폴리오', exact: true })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(menu).toBeFocused();

  const question = page.locator('summary').first();
  await question.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('details').first()).toHaveAttribute('open', '');
  await page.locator('.hero .primary-cta').focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toBeFocused();
  const add = page.getByRole('button', { name: '거래 기록', exact: true });
  await add.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog', { name: '거래 기록' })).toBeVisible();
  await expect(page.getByRole('searchbox', { name: '종목 검색' })).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(add).toBeFocused();
});

for (const width of [320, 1440]) {
  test(`${width}px: 움직임을 줄여도 내용이 잘리지 않고 보조 글자 대비는 4.5 이상이다`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/welcome');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    const nav = page.locator('.nav-inner');
    expect(await nav.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(true);
    const contrasts = await page
      .locator('.feature-detail, .feature-number, .product-mockup figcaption, .story-kicker')
      .evaluateAll((elements) => {
        function luminance(color: string) {
          const channels = color
            .match(/[\d.]+/g)!
            .slice(0, 3)
            .map((channel) => {
              const value = Number(channel) / 255;
              return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
            });
          return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
        }
        return elements.map((element) => {
          let background: Element | null = element;
          while (background && getComputedStyle(background).backgroundColor === 'rgba(0, 0, 0, 0)')
            background = background.parentElement;
          const foreground = luminance(getComputedStyle(element).color);
          const backdrop = luminance(getComputedStyle(background!).backgroundColor);
          return {
            text: element.textContent,
            ratio:
              (Math.max(foreground, backdrop) + 0.05) / (Math.min(foreground, backdrop) + 0.05),
          };
        });
      });
    for (const { text, ratio } of contrasts) expect(ratio, text ?? '').toBeGreaterThanOrEqual(4.5);
  });
}

test('공유 뉴스 상세는 소개를 거치지 않고 원래 주소로 열린다', async ({ page }) => {
  await page.route('**/api/news/7', (route) =>
    route.fulfill({
      json: {
        id: 7,
        title: '공유 뉴스 테스트',
        category: 'ECONOMY',
        imageUrl: null,
        publishedAt: '2026-10-14T10:00:00Z',
        content: '공유한 뉴스 본문입니다.',
        sources: [],
      },
    }),
  );
  await page.route('**/api/news/7/keywords', (route) => route.fulfill({ json: { keywords: [] } }));
  await page.route('**/api/news/7/market-analysis', (route) =>
    route.fulfill({ json: { summary: '시장 분석' } }),
  );
  await page.goto('/news/7?source=share#analysis');
  await expect(page.getByRole('heading', { name: '공유 뉴스 테스트' })).toBeVisible();
  await expect(page).toHaveURL('/news/7?source=share#analysis');
  await expect(page.locator('.landing-page')).toHaveCount(0);
  await page.getByRole('link', { name: '포트폴리오', exact: true }).click();
  await expect(page).toHaveURL('/');
  await expect(page.getByRole('button', { name: '거래 기록', exact: true })).toBeVisible();
});
