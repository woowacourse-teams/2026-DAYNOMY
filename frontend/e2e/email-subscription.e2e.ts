import { expect, test } from '@playwright/test';

for (const viewport of [
  { name: 'desktop', width: 1280, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`최초 구독 안내의 표시·키보드·선택 저장: ${viewport.name}`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport);
    let status = 'UNDECIDED';
    let writes = 0;
    const email =
      viewport.name === 'mobile'
        ? 'long-google-account-name-for-delivery@example.invalid'
        : 'member@example.invalid';
    await page.route('**/api/**', async (route) => {
      const request = route.request();
      const path = new URL(request.url()).pathname;
      const json = (body: unknown, code = 200) => route.fulfill({ status: code, json: body });
      if (path === '/api/users/me') return json({ id: 1, email, nickname: '회원', role: 'USER' });
      if (path === '/api/auth/csrf')
        return json({ headerName: 'X-CSRF-TOKEN', token: 'test-csrf' });
      if (path.startsWith('/api/users/me/email-subscription')) {
        if (request.method() !== 'GET') {
          expect(request.headers()['x-csrf-token']).toBe('test-csrf');
          expect(request.postData()).toBeNull();
          writes++;
          status = request.method() === 'PUT' ? 'SUBSCRIBED' : 'DECLINED';
        }
        return json({
          status,
          email,
          promptRequired: status === 'UNDECIDED',
          subscribedAt: status === 'SUBSCRIBED' ? '2026-10-28T00:00:00Z' : null,
        });
      }
      if (path === '/api/users/me/portfolio')
        return json({ holdings: [], totalRealizedProfitLoss: 0 });
      if (path === '/api/portfolio/calculate')
        return json({
          baseDate: '2026-10-28',
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: 0,
          unpricedHoldingCount: 0,
          totalPurchaseAmount: 0,
          totalEvaluationAmount: 0,
          totalProfitLoss: 0,
          totalReturnRate: 0,
          dailyProfitLoss: null,
          dailyReturnRate: null,
          holdings: [],
        });
      return json({}, 404);
    });
    await page.goto('/');
    const dialog = page.getByRole('dialog', { name: /내 투자에 중요한 변화/ });
    const subscribe = dialog.getByRole('button', { name: '이메일 알림 구독하기' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText(email, { exact: true })).toBeVisible();
    await expect(subscribe).toBeFocused();
    const bounds = await dialog.boundingBox();
    expect(bounds!.x).toBeGreaterThanOrEqual(15);
    expect(bounds!.y).toBeGreaterThanOrEqual(15);
    expect(bounds!.width).toBeLessThanOrEqual(viewport.width - 32);
    await expect(subscribe).toHaveCSS('background-color', 'rgb(10, 10, 10)');
    await page.keyboard.press('Tab');
    await expect(dialog.getByRole('button', { name: '지금은 괜찮아요' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(dialog.getByRole('button', { name: '구독 안내 닫기' })).toBeFocused();
    await page.keyboard.press('Tab');
    await expect(subscribe).toBeFocused();
    expect(await dialog.evaluate((element) => element.scrollWidth <= element.clientWidth)).toBe(
      true,
    );
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('email-subscription-prompt.png') });
    if (viewport.name === 'desktop') {
      await subscribe.click();
      await expect(page.getByRole('heading', { name: '이메일 알림 구독 완료' })).toBeVisible();
      await page.getByRole('button', { name: '확인', exact: true }).click();
    } else {
      await page.keyboard.press('Escape');
      await expect(page).toHaveURL('/');
      await expect(page.getByRole('heading', { name: '이메일 알림 구독 완료' })).toHaveCount(0);
    }
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(writes).toBe(1);
    await page.reload();
    await expect(page.getByRole('heading', { name: '내 포트폴리오', exact: true })).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
  });
}
