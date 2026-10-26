import { expect, test, type Page } from '@playwright/test';
import { accessibilityArticle, installAccessibilityFixtures } from './fixtures/accessibility';

async function tabTo(page: Page, selector: string) {
  const target = page.locator(selector);
  for (let i = 0; i < 60; i++) {
    if (await target.evaluate((element) => element === document.activeElement)) return;
    await page.keyboard.press('Tab');
  }
  await expect(target).toBeFocused();
}

test('키보드로 소개·거래 입력 오류 수정·뉴스 검색과 읽기·로그인 실패 및 복귀를 완료한다', async ({
  page,
}) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await installAccessibilityFixtures(page);
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page).toHaveURL('/');
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: '본문 바로가기' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('main')).toBeFocused();
  await tabTo(page, '.hero .primary-cta');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toBeFocused();
  await expect(page.getByRole('button', { name: '사용 가이드', exact: true })).toHaveCount(0);
  await expect(page.getByRole('complementary', { name: '처음 이용 안내' })).toHaveCount(0);
  await tabTo(page, '.portfolio-page-actions .portfolio-primary-button');
  await page.keyboard.press('Enter');
  const quantity = page.getByLabel('수량 (주)');
  await expect(page.getByRole('searchbox', { name: '종목 검색' })).toBeFocused();
  await page.keyboard.insertText('삼성');
  await expect(page.getByRole('button', { name: /삼성전자.*005930/ })).toBeVisible();
  await tabTo(page, '.portfolio-stock-results li button');
  await page.keyboard.press('Enter');
  await tabTo(page, 'input[name="quantity"]');
  await page.keyboard.insertText('0');
  await tabTo(page, '.portfolio-sheet-footer button[type="submit"]');
  await page.keyboard.press('Enter');
  await expect(quantity).toBeFocused();
  await expect(quantity).toHaveAttribute('aria-invalid', 'true');
  await expect(quantity).toHaveAccessibleDescription('수량은 1주 이상의 정수로 입력해 주세요.');
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.insertText('1');
  await tabTo(page, '.portfolio-sheet-footer button[type="submit"]');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '종목 추가', exact: true })).toBeFocused();
  await expect(page.getByRole('status').filter({ hasText: '거래를 기록했어요.' })).toBeVisible();
  await expect(page.getByRole('button', { name: '삼성전자 보유 상세' })).toBeVisible();

  await tabTo(page, 'a.service-header-tab[href="/news"]');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: '소식', exact: true })).toBeFocused();
  await tabTo(page, 'button.service-search-link');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('textbox', { name: '소식 검색' })).toBeFocused();
  await page.keyboard.insertText('금리');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { level: 1 })).toBeFocused();
  await expect(
    page.getByRole('link', { name: new RegExp(accessibilityArticle.title) }),
  ).toBeVisible();
  await tabTo(page, '.search-page .issue-row-title');
  await page.keyboard.press('Enter');
  await expect(
    page.getByRole('heading', { name: accessibilityArticle.title, exact: true }),
  ).toBeFocused();
  await expect(page.getByRole('region', { name: '뉴스 본문' })).toContainText(
    '기준금리는 자산 가격',
  );
  await tabTo(page, '.detail-close-button');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: '소식', exact: true })).toBeFocused();
  await tabTo(page, 'a.service-login-link');
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'DAYNOMY 로그인' })).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('link', { name: 'Google로 시작하기' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('alert')).toBeFocused();
  await expect(page.getByRole('alert')).toHaveText('Google 로그인에 실패했습니다.');
  await tabTo(page, 'a.google-login-button');
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL('/news');
  await expect(page.getByRole('link', { name: '마이페이지', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: '소식', exact: true })).toBeFocused();
});
