import type { Page } from '@playwright/test';

export const accessibilityArticle = {
  id: 7,
  title: '기준금리와 내 포트폴리오',
  category: 'ECONOMY',
  imageUrl: null,
  publishedAt: new Date().toISOString(),
  content:
    '기준금리는 자산 가격과 기업의 자금 조달 비용에 영향을 줍니다.\n투자 판단 전에는 자신의 투자 기간과 위험을 함께 확인하세요.',
  sources: [],
};

export async function installAccessibilityFixtures(page: Page) {
  let authenticated = false;
  let oauthAttempts = 0;
  const stock = {
    assetId: 1,
    assetCode: '005930',
    name: '삼성전자',
    category: 'STOCK',
    market: 'KOSPI',
  };
  const date = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date());
  const newsPage = {
    items: [accessibilityArticle],
    page: 1,
    size: 12,
    totalPages: 1,
    totalElements: 1,
    hasNext: false,
  };
  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route) => {
      const url = new URL(route.request().url());
      const path = url.pathname;
      const json = (data: unknown) => route.fulfill({ json: data });
      if (path === '/api/auth/csrf')
        return json({ token: 'accessibility-fixture', headerName: 'X-CSRF-TOKEN' });
      if (path === '/api/users/me')
        return authenticated
          ? json({ id: 1, nickname: '검증용 회원', email: 'test@example.invalid', role: 'USER' })
          : route.fulfill({ status: 401 });
      if (path === '/api/auth/google') {
        oauthAttempts += 1;
        authenticated = oauthAttempts > 1;
        return route.fulfill({
          status: 302,
          headers: {
            location: `${new URL(page.url()).origin}${authenticated ? '/' : '/login?error=oauth'}`,
          },
        });
      }
      if (path === '/api/stocks')
        return json({ stocks: url.searchParams.get('q')?.includes('없는종목') ? [] : [stock] });
      if (path === '/api/stocks/1/price')
        return json({ ...stock, baseDate: date, closePrice: 70000 });
      if (path === '/api/assets/1/contents')
        return json({ assetId: 1, assetCode: '005930', assetName: '삼성전자', contents: [] });
      if (path === '/api/portfolio/calculate') {
        const holdings = route.request().postDataJSON().holdings;
        const quantity = holdings[0]?.quantity ?? 0;
        const purchase = quantity * (holdings[0]?.averagePurchasePrice ?? 0);
        const evaluation = quantity * 70000;
        return json({
          baseDate: date,
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: quantity ? 1 : 0,
          unpricedHoldingCount: 0,
          totalPurchaseAmount: purchase,
          totalEvaluationAmount: evaluation,
          dailyProfitLoss: null,
          dailyReturnRate: null,
          totalProfitLoss: evaluation - purchase,
          totalReturnRate: purchase ? ((evaluation - purchase) / purchase) * 100 : 0,
          holdings: quantity
            ? [
                {
                  ...stock,
                  ...holdings[0],
                  priceAvailable: true,
                  baseDate: date,
                  closePrice: 70000,
                  purchaseAmount: purchase,
                  evaluationAmount: evaluation,
                  profitLoss: evaluation - purchase,
                  returnRate: 0,
                  weight: 100,
                },
              ]
            : [],
          marketAllocations: quantity
            ? [{ market: 'KOSPI', evaluationAmount: evaluation, weight: 100 }]
            : [],
        });
      }
      if (path === '/api/people') return json({ people: [] });
      if (path === '/api/contents') return json({ items: [], page: 1, totalPages: 0 });
      if (path === '/api/news' || path === '/api/news/today') return json(newsPage);
      if (path === '/api/news/7') return json(accessibilityArticle);
      if (path === '/api/news/7/keywords') return json({ keywords: [] });
      if (path === '/api/news/7/market-analysis')
        return json({
          risk: '금리 변화에 따른 가격 변동을 확인하세요.',
          watchPoint: '다음 기준금리 발표 일정을 확인하세요.',
        });
      if (path === '/api/search/contents')
        return json({
          ...newsPage,
          items: [
            {
              kind: 'NEWS',
              id: 7,
              source: 'DAYNOMY',
              title: accessibilityArticle.title,
              date: accessibilityArticle.publishedAt,
              category: accessibilityArticle.category,
              imageUrl: null,
            },
          ],
        });
      if (path === '/api/users/me/portfolio')
        return json({ holdings: [], totalRealizedProfitLoss: 0 });
      return route.fulfill({ status: 401 });
    },
  );
}
