import { expect, test } from '@playwright/test';
import { emailAnalysisDetail, emailAnalysisPage } from '../tests/admin/emailAnalysisFixtures';

for (const viewport of [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`관리자 이메일 분석 필터·상세·목록 복귀: ${viewport.name}`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport);
    const methods: string[] = [];
    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.pathname === '/api/users/me')
        return route.fulfill({
          json: { id: 1, email: 'admin@example.invalid', nickname: '관리자', role: 'ADMIN' },
        });
      if (url.pathname.startsWith('/api/admin/email-analyses')) {
        methods.push(request.method());
        if (url.pathname === '/api/admin/email-analyses') {
          expect(url.searchParams.get('page')).toBe('0');
          expect(url.searchParams.get('size')).toBe('20');
          return route.fulfill({ json: emailAnalysisPage() });
        }
        return route.fulfill({ json: emailAnalysisDetail });
      }
      return route.fulfill({ status: 404, json: {} });
    });
    await page.goto('/admin/email-analyses');
    await expect(page.getByRole('heading', { name: '이메일 알림 관리' })).toBeVisible();
    await expect(page.getByRole('link', { name: '이메일 알림 관리' })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await page.getByRole('combobox', { name: '상태', exact: true }).selectOption('VERIFIED');
    await page.getByLabel('자산 ID', { exact: true }).fill('1');
    await page.getByRole('button', { name: '조회', exact: true }).click();
    await expect(page).toHaveURL('/admin/email-analyses?status=VERIFIED&assetId=1');
    await expect(page.getByRole('link', { name: '삼성전자 분석 #77 상세' })).toBeVisible();
    if (viewport.name === 'mobile')
      expect(
        (await page.getByRole('form', { name: '분석 목록 필터' }).boundingBox())!.height,
      ).toBeLessThan(300);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('admin-email-list.png') });
    await page.getByRole('link', { name: '삼성전자 분석 #77 상세' }).click();
    await expect(page.getByRole('heading', { name: '삼성전자 분석', exact: true })).toBeFocused();
    await expect(page.getByText('검증용 실적 발표 소식', { exact: true })).toBeVisible();
    await expect(page.getByText('This is a test quote.', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: '원문 검증 결과' })).toBeVisible();
    const analysisPanel = await page
      .getByRole('region', { name: '종목 분석', exact: true })
      .boundingBox();
    const reason = await page
      .getByText(emailAnalysisDetail.content.reason!, { exact: true })
      .boundingBox();
    expect(reason!.width).toBeGreaterThan(analysisPanel!.width * 0.7);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page
      .locator('main.admin-email-page')
      .screenshot({ path: testInfo.outputPath('admin-email-detail.png') });
    await page.getByRole('link', { name: '목록으로 돌아가기' }).click();
    await expect(page).toHaveURL('/admin/email-analyses?status=VERIFIED&assetId=1');
    await expect(page.getByRole('combobox', { name: '상태', exact: true })).toHaveValue('VERIFIED');
    await expect(page.getByLabel('자산 ID', { exact: true })).toHaveValue('1');
    expect(methods.every((method) => method === 'GET')).toBe(true);
  });
}
