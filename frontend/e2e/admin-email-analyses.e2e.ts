import { expect, test } from '@playwright/test';
import { emailAnalysisDetail, emailAnalysisPage } from '../tests/admin/emailAnalysisFixtures';

for (const viewport of [
  { name: 'desktop', width: 1440, height: 1000 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`관리자 이메일 분석 필터·상세·목록 복귀: ${viewport.name}`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport);
    const methods: string[] = [];
    let invalidated = false;
    const deliveryId = '00000000-0000-0000-0000-000000000001';
    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.pathname === '/api/users/me')
        return route.fulfill({
          json: { id: 1, email: 'admin@example.invalid', nickname: '관리자', role: 'ADMIN' },
        });
      if (url.pathname === '/api/auth/csrf')
        return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } });
      if (url.pathname.startsWith('/api/admin/email-analyses')) {
        methods.push(request.method());
        if (url.pathname.endsWith('/correction-review-deliveries')) {
          expect(url.searchParams.get('size')).toBe('50');
          return route.fulfill({ json: [deliveryId] });
        }
        if (url.pathname.endsWith('/invalidate')) {
          expect(request.method()).toBe('POST');
          expect(request.headers()['x-csrf-token']).toBe('test-csrf');
          expect(request.postDataJSON()).toEqual({
            reason: '공식 자료의 수치와 분석 내용이 다릅니다.',
          });
          invalidated = true;
          return route.fulfill({
            json: { analysisId: 77, invalidatedAt: '2026-10-28T00:00:00Z', cancelledDeliveries: 2 },
          });
        }
        if (url.pathname === '/api/admin/email-analyses') {
          expect(url.searchParams.get('page')).toBe('0');
          expect(url.searchParams.get('size')).toBe('20');
          return route.fulfill({ json: emailAnalysisPage() });
        }
        return route.fulfill({
          json: invalidated
            ? {
                ...emailAnalysisDetail,
                analysis: {
                  ...emailAnalysisDetail.analysis,
                  status: 'INVALIDATED',
                  invalidatedAt: '2026-10-28T00:00:00Z',
                },
                invalidationReason: '공식 자료의 수치와 분석 내용이 다릅니다.',
              }
            : emailAnalysisDetail,
        });
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
    expect(methods.every((method) => method === 'GET')).toBe(true);
    await expect(page.getByText(deliveryId, { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '분석 무효화', exact: true }).click();
    await expect(page.getByLabel('무효화 사유', { exact: true })).toBeFocused();
    await page.getByRole('button', { name: '무효화 확정' }).click();
    await expect(page.getByRole('alert')).toContainText('1~500자');
    expect(methods.every((method) => method === 'GET')).toBe(true);
    await page
      .getByLabel('무효화 사유', { exact: true })
      .fill('  공식 자료의 수치와 분석 내용이 다릅니다.  ');
    await page
      .getByRole('region', { name: '분석 무효화', exact: true })
      .screenshot({ path: testInfo.outputPath('admin-email-invalidation-form.png') });
    await page.getByRole('button', { name: '무효화 확정' }).click();
    await expect(page.getByText(/이번 요청에서 취소된 대기·재시도 메일은 2건/)).toBeVisible();
    await expect(
      page.getByText('공식 자료의 수치와 분석 내용이 다릅니다.', { exact: true }),
    ).toBeVisible();
    await expect(page.getByRole('button', { name: '분석 무효화', exact: true })).toHaveCount(0);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page
      .getByRole('region', { name: '정정 검토 대상', exact: true })
      .screenshot({ path: testInfo.outputPath('admin-email-correction-review.png') });
    await page.reload();
    await expect(
      page.getByText(
        '이미 무효화된 분석입니다. 최초 사유와 시각은 분석 정보에서 확인할 수 있습니다.',
      ),
    ).toBeVisible();
    await page.getByRole('link', { name: '목록으로 돌아가기' }).click();
    await expect(page).toHaveURL('/admin/email-analyses?status=VERIFIED&assetId=1');
    await expect(page.getByRole('combobox', { name: '상태', exact: true })).toHaveValue('VERIFIED');
    await expect(page.getByLabel('자산 ID', { exact: true })).toHaveValue('1');
    expect(methods.filter((method) => method === 'POST')).toHaveLength(1);
  });
}
