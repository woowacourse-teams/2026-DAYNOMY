import { expect, test } from '@playwright/test';
import { mockLeagueApi } from './league-api';

const record = {
  id: 7,
  assetId: 1,
  assetCode: '005930',
  assetName: '삼성전자',
  category: 'STOCK',
  transactionType: 'HOLD',
  quantity: 1,
  unitPrice: 100,
  fee: 0,
  tradedOn: '2026-10-13',
  reason: '실적을 확인하며 보유',
  expectedHoldingPeriod: null,
  expectedChange: '',
  invalidationCondition: '',
  maximumAcceptableLossRate: null,
  writtenAfterTrade: true,
  reviews: [
    {
      id: 2,
      actualResult: '기존 복기 결과',
      differenceFromExpectation: '기존 차이',
      nextAction: '다음 실적 확인',
      createdAt: '2026-10-14T00:00:00Z',
    },
  ],
};

for (const width of [390, 1440]) {
  test(`${width}px: 팔로잉 목록과 보유 이유 수정·재시도·재조회`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.addInitScript(() => localStorage.setItem('daynomy:service-started:v1', '1'));
    await page.route('**/api/**', (route) => route.fulfill({ status: 404 }));
    await page.route('**/api/users/me', (route) =>
      route.fulfill({
        json: { id: 1, nickname: '테스트 사용자', email: 'test@example.invalid', role: 'USER' },
      }),
    );
    await page.route('**/api/auth/csrf', (route) =>
      route.fulfill({
        json: { token: 'test-token', headerName: 'X-CSRF-TOKEN' },
      }),
    );
    await mockLeagueApi(page);
    await page.route('**/api/users/me/league/follows/summary', (route) =>
      route.fulfill({
        json: {
          investors: [
            {
              publicId: 'investor-1',
              displayName: '팔로우한 투자자',
              rank: 1,
              weeklyReturnRate: 12.5,
              newReviewCount: 3,
              previousRank: 2,
              rankChange: 1,
            },
          ],
        },
      }),
    );
    await page.route('**/api/users/me/shared-portfolio', (route) =>
      route.fulfill({
        json: {
          holdings: Array.from({ length: 5 }, (_, index) => ({
            assetId: index + 1,
            assetCode: `00593${index}`,
            assetName: index === 0 ? '삼성전자' : `테스트 종목 ${index + 1}`,
            category: 'STOCK',
            market: 'KOSPI',
            quantity: 10,
            averagePurchasePrice: 70000,
            hidden: false,
            reason: record.reason,
            baseDate: null,
            closePrice: null,
            evaluationAmount: null,
            returnRate: null,
          })),
          totalPurchaseAmount: 3500000,
          totalEvaluationAmount: null,
          totalReturnRate: null,
          pricesComplete: false,
        },
      }),
    );
    let saved = { ...record };
    let attempts = 0;
    await page.route('**/api/users/me/portfolio/transactions', (route) =>
      route.fulfill({
        json: { transactions: [saved] },
      }),
    );
    await page.route('**/api/users/me/portfolio/decisions/7/reason', (route) => {
      expect(route.request().method()).toBe('PUT');
      expect(route.request().headers()['x-csrf-token']).toBe('test-token');
      expect(route.request().postDataJSON()).toEqual({ reason: '수정한 보유 이유' });
      if (++attempts === 1) return route.fulfill({ status: 503, json: { message: '저장 실패' } });
      saved = { ...saved, reason: route.request().postDataJSON().reason };
      return route.fulfill({ json: saved });
    });

    await page.goto('/league/following');
    const list = page.getByRole('list', { name: '팔로우한 투자자' });
    await expect(list.getByRole('listitem')).toHaveCount(1);
    await expect(list.getByRole('link', { name: '팔로우한 투자자' })).toHaveAttribute(
      'href',
      '/league/investor-1',
    );
    await expect(list).toHaveText('팔로우한 투자자');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: testInfo.outputPath('following.png') });

    await page.goto('/league/portfolio');
    await expect(page.getByRole('article', { name: /기록 상세$/ })).toHaveCount(4);
    await page.getByRole('button', { name: '다음 방향 2페이지로 이동' }).click();
    await expect(page.getByRole('article', { name: /기록 상세$/ })).toHaveCount(1);
    await expect(page.getByRole('article', { name: '테스트 종목 5 기록 상세' })).toBeVisible();
    await page.getByRole('button', { name: '이전 방향 1페이지로 이동' }).click();
    await page.screenshot({ path: testInfo.outputPath('holding-cards.png'), fullPage: true });
    const edit = page.getByRole('button', { name: '삼성전자 2026-10-13 보유 기록 보유 이유 수정' });
    await expect(edit).toHaveCSS('border-top-width', '0px');
    await expect(page.locator('.shared-holding details')).toHaveCount(0);
    await edit.click();
    const input = page.getByRole('textbox', { name: '보유 이유', exact: true });
    await expect(input).toHaveValue(record.reason);
    await expect(input).toBeFocused();
    await expect(page.getByRole('button', { name: '다음 방향 2페이지로 이동' })).toBeDisabled();
    await input.fill('수정한 보유 이유');
    await page.getByRole('button', { name: '보유 이유 수정 저장' }).click();
    await expect(page.getByRole('alert')).toContainText('저장 실패');
    await expect(input).toHaveValue('수정한 보유 이유');
    await page.getByRole('button', { name: '보유 이유 수정 저장' }).click();
    await expect(page.getByText('보유 이유를 수정했습니다.')).toBeVisible();
    await expect(edit).toBeFocused();
    await expect(page.getByText('기존 복기 결과')).toBeVisible();
    await page.reload();
    await expect(page.getByText('수정한 보유 이유', { exact: false })).toBeVisible();
    await expect(page.getByText('기존 복기 결과')).toBeVisible();
    expect(attempts).toBe(2);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: testInfo.outputPath('edited-record.png') });
  });
}
