// Capture the deployed UI with display-only fixtures; never send fixture data to the API.
import { chromium } from '@playwright/test';
import { readFile, mkdir } from 'node:fs/promises';
const output = new URL('../src/assets/landing/', import.meta.url).pathname;
await mkdir(output, { recursive: true });
const analysis = JSON.parse(
  await readFile(new URL('./landing-analysis.fixture.json', import.meta.url), 'utf8'),
);
const holdings = [
  {
    assetId: 1,
    assetCode: '005930',
    name: '삼성전자',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 10,
    averagePurchasePrice: 220000,
    closePrice: 214500,
  },
  {
    assetId: 2,
    assetCode: '000660',
    name: 'SK하이닉스',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 2,
    averagePurchasePrice: 911500,
    closePrice: 1272000,
  },
];
const total = holdings.reduce((sum, h) => sum + h.quantity * h.closePrice, 0);
const cost = holdings.reduce((sum, h) => sum + h.quantity * h.averagePurchasePrice, 0);
const calculation = {
  baseDate: '2026-10-23',
  pricingStatus: 'COMPLETE',
  pricedHoldingCount: 2,
  unpricedHoldingCount: 0,
  totalPurchaseAmount: cost,
  totalEvaluationAmount: total,
  dailyProfitLoss: -90000,
  dailyReturnRate: -1.88,
  totalProfitLoss: total - cost,
  totalReturnRate: (total / cost - 1) * 100,
  holdings: holdings.map((h) => ({
    ...h,
    priceAvailable: true,
    baseDate: '2026-10-23',
    purchaseAmount: h.quantity * h.averagePurchasePrice,
    evaluationAmount: h.quantity * h.closePrice,
    profitLoss: h.quantity * (h.closePrice - h.averagePurchasePrice),
    returnRate: (h.closePrice / h.averagePurchasePrice - 1) * 100,
    weight: ((h.quantity * h.closePrice) / total) * 100,
  })),
  marketAllocations: [{ market: 'KOSPI', evaluationAmount: total, weight: 100 }],
};
analysis.impacts.forEach((impact, i) => (impact.weight = calculation.holdings[i].weight));
const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: { width: 1100, height: 850 },
  deviceScaleFactor: 2,
  reducedMotion: 'reduce',
});
await page.addInitScript(() => {
  localStorage.setItem('daynomy:service-started:v1', '1');
  localStorage.setItem('daynomy:portfolio-tour:v1', 'done');
});
await page.route('**/*', (route) => {
  const url = new URL(route.request().url()),
    path = url.pathname;
  if (!path.startsWith('/api/')) {
    if (url.hostname.includes('posthog') || url.hostname.includes('sentry')) return route.abort();
    return route.continue();
  }
  const json = (value) => route.fulfill({ json: value });
  if (path === '/api/users/me')
    return json({ id: 1, nickname: '데이노미', email: 'demo@example.invalid', role: 'USER' });
  if (path === '/api/users/me/investor-profile')
    return json({
      publicId: 'demo',
      displayName: '데이노미',
      bio: '',
      experienceLevel: 'BEGINNER',
      riskProfile: 'BALANCED',
      profilePublic: true,
      leagueEnabled: true,
      allocationPublic: true,
      detailPublic: false,
      leagueEnabledAt: '2026-10-19T01:00:00Z',
      trackingStartedOn: '2026-10-19',
      eligibleFrom: '2026-10-19',
    });
  if (path === '/api/auth/csrf') return json({ token: 'preview', headerName: 'X-CSRF-TOKEN' });
  if (path === '/api/users/me/portfolio')
    return json({ holdings, totalRealizedProfitLoss: 120000 });
  if (path === '/api/portfolio/calculate') return json(calculation);
  if (path === '/api/users/me/portfolio/analysis')
    return json({
      status: 'READY',
      lastCheckOutcome: 'UPDATED',
      analysisId: 1,
      matchesCurrentPortfolio: true,
      lastSucceededAt: '2026-10-23T08:00:00Z',
      lastFailedAt: null,
      failureCode: null,
      changeReason: null,
      analysis,
    });
  if (path === '/api/users/me/portfolio/performance/prepare') return json({ status: 'READY' });
  if (path === '/api/users/me/portfolio/performance') {
    const points = Array.from({ length: 14 }, (_, index) => ({
      baseDate: `2026-10-${String(index + 10).padStart(2, '0')}`,
      totalPurchaseAmount: cost,
      totalEvaluationAmount: total - (13 - index) * 22000,
      totalProfitLoss: total - (13 - index) * 22000 - cost,
      totalReturnRate: ((total - (13 - index) * 22000) / cost - 1) * 100,
      dailyProfitLoss: 22000,
      dailyReturnRate: 0.5,
    }));
    return json({
      status: 'READY',
      reason: null,
      baseDate: '2026-10-23',
      previousBaseDate: '2026-10-22',
      points,
      currentPoint: { ...points.at(-1), priceBaseDate: '2026-10-23' },
    });
  }
  if (/\/api\/assets\/\d+\/contents/.test(path))
    return json({
      assetId: 1,
      assetCode: '005930',
      assetName: '삼성전자',
      contents: [
        {
          id: 1,
          assetId: 1,
          sourceType: 'YOUTUBE',
          title: '삼성전자 엑시노스2700 성능 테스트 결과',
          url: 'https://www.youtube.com/watch?v=eyd5f1cJAnY',
          imageUrl: null,
          createdAt: '2026-10-09T09:00:00Z',
          publishedDate: '2026-10-09',
        },
        {
          id: 2,
          assetId: 1,
          sourceType: 'YOUTUBE',
          title: '삼성전자, 테슬라에 22.8조 원 반도체 계약 수주',
          url: 'https://www.youtube.com/watch?v=lXaCjxZCUYc',
          imageUrl: null,
          createdAt: '2026-07-28T09:00:00Z',
          publishedDate: '2026-07-28',
        },
      ],
    });
  const week = { weekStart: '2026-10-19', weekEnd: '2026-10-25', confirmed: true };
  if (path === '/api/league/weeks') return json({ weeks: [week] });
  if (path === '/api/league/rankings')
    return json({
      ...week,
      leagueType: 'WEEKLY_RETURN',
      asOfDate: '2026-10-23',
      totalCount: 3,
      rankings: ['꾸준한 하루', '시장을 읽는 사람', '내일의 투자자'].map((displayName, i) => ({
        publicId: `demo-${i}`,
        displayName,
        bio: '투자 기록을 꾸준히 남겨요.',
        experienceLevel: 'BEGINNER',
        riskProfile: 'BALANCED',
        profilePublic: true,
        leagueEnabled: true,
        allocationPublic: true,
        detailPublic: false,
        rank: i + 1,
        weeklyReturnRate: [8.4, 6.2, 4.7][i],
        threeWeekReturnRate: 10,
        maxDrawdownRate: -2,
        volatilityRate: 1,
        maxHoldingWeight: 60,
        decisionCount: 4,
        reviewCompletionRate: 75,
        followed: false,
      })),
    });
  return route.fulfill({ status: 404 });
});
await page.goto('https://daynomy.com/', { waitUntil: 'networkidle' });
await page.locator('.portfolio-holding-row').first().waitFor();
await page.addStyleTag({ content: '.feedback-trigger { visibility: hidden; }' });
await page.locator('.portfolio-news-close').click();
await page.locator('.portfolio-page').screenshot({ path: output + 'overview.png' });
await page.getByRole('button', { name: '소식', exact: true }).click();
await page.locator('.portfolio-news-list li').first().waitFor({ timeout: 5000 });
await page.locator('.portfolio-news-more').click();
await page.locator('.issue-page').waitFor();
await page.waitForLoadState('networkidle');
await page.addStyleTag({ content: '.feedback-trigger { visibility: hidden; }' });
await page.locator('.portfolio-contents-layout').screenshot({ path: output + 'news.png' });
await page.goto('https://daynomy.com/', { waitUntil: 'networkidle' });
await page.addStyleTag({ content: '.feedback-trigger { visibility: hidden; }' });
await page.locator('.portfolio-news-close').click();
await page.locator('.portfolio-analysis-section').scrollIntoViewIfNeeded();
await page.evaluate(() => {
  const el = document.querySelector('.portfolio-analysis-section');
  window.scrollTo(0, el.getBoundingClientRect().top + scrollY - 90);
});
await page.locator('.portfolio-evidence').first().locator('summary').click();
await page.locator('.portfolio-analysis-section').screenshot({ path: output + 'sources.png' });
await page.goto('https://daynomy.com/league', { waitUntil: 'networkidle' });
await page.addStyleTag({ content: '.feedback-trigger { visibility: hidden; }' });
await page.evaluate(() => document.activeElement?.blur());
await page.screenshot({ path: output + 'league.png' });
await browser.close();
