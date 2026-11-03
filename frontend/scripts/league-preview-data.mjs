const week = {
  weekStart: '2026-10-26',
  weekEnd: '2026-11-01',
  confirmed: false,
  collectionStatus: 'IN_PROGRESS',
};
const baseProfile = {
  publicId: 'preview-me',
  displayName: '통합투자자',
  bio: '국내·미국 종목을 함께 운용하는 예시 포트폴리오입니다.',
  experienceLevel: 'BEGINNER',
  riskProfile: 'BALANCED',
  profilePublic: true,
  leagueEnabled: true,
  allocationPublic: true,
  detailPublic: true,
  leagueEnabledAt: '2026-10-23T01:00:00Z',
  trackingStartedOn: '2026-10-23',
  eligibleFrom: '2026-10-26',
};
const security = (
  assetId,
  assetName,
  assetCode,
  market,
  currency,
  quantity,
  averagePurchasePrice,
  closePrice,
  hidden,
  reason,
) => ({
  assetId,
  assetName,
  assetCode,
  market,
  currency,
  quantity,
  averagePurchasePrice,
  closePrice,
  category: 'STOCK',
  hidden,
  reason,
  pendingReason: !reason,
  sourceManaged: true,
  baseDate: '2026-10-27',
});
const initialHoldings = () => [
  security(
    1,
    '삼성전자',
    '005930',
    'KOSPI',
    'KRW',
    10,
    70000,
    84700,
    false,
    '실적 개선을 확인하며 보유합니다.',
  ),
  security(
    2,
    '애플',
    'AAPL.US',
    'NASDAQ',
    'USD',
    2,
    260000,
    242,
    false,
    '서비스 매출과 현금 흐름을 확인합니다.',
  ),
  security(
    3,
    'SK하이닉스',
    '000660',
    'KOSPI',
    'KRW',
    5,
    100000,
    121000,
    true,
    '반도체 업황을 확인합니다.',
  ),
  {
    ...security(
      4,
      'Vanguard Total Stock Market ETF',
      'VTI.US',
      'NYSE_ARCA',
      'USD',
      1,
      390000,
      363,
      true,
      '',
    ),
    category: 'ETF',
  },
];
const priceKrw = (holding) => holding.closePrice * (holding.currency === 'USD' ? 1300 : 1);

export function createLeaguePreview() {
  let holdings = initialHoldings();
  let profile = { ...baseProfile };
  let partial = false;
  let changed = false;
  let displayScenario = 'reset';
  const response = (data, status = 200) => ({ data, status });
  const portfolio = () => {
    const values = holdings.map((h) => ({
      ...h,
      evaluationAmount: priceKrw(h) * h.quantity,
      returnRate: Math.round((priceKrw(h) / h.averagePurchasePrice - 1) * 10000) / 100,
    }));
    const totalPurchaseAmount = values.reduce(
      (sum, h) => sum + h.quantity * h.averagePurchasePrice,
      0,
    );
    const totalEvaluationAmount = values.reduce((sum, h) => sum + h.evaluationAmount, 0);
    return {
      holdings: values,
      totalPurchaseAmount,
      totalEvaluationAmount,
      totalReturnRate: Math.round((totalEvaluationAmount / totalPurchaseAmount - 1) * 10000) / 100,
      pricesComplete: true,
    };
  };
  const decisions = () =>
    holdings
      .filter((h) => h.reason)
      .map((h) => ({
        ...h,
        id: h.assetId,
        transactionType: 'HOLD',
        unitPrice: h.averagePurchasePrice,
        fee: 0,
        tradedOn: '2026-10-23',
        expectedHoldingPeriod: 'OVER_SIX_MONTHS',
        expectedChange: '실적 개선',
        invalidationCondition: '투자 근거 훼손',
        maximumAcceptableLossRate: 15,
        writtenAfterTrade: true,
        reviews: [],
      }));
  const commonWaiting = () => displayScenario === 'common-wait';
  const personalGap = () => displayScenario === 'member-gap';
  const rankingDate = () =>
    commonWaiting()
      ? '2026-10-26'
      : displayScenario === 'holiday'
        ? '2026-10-30'
        : displayScenario === 'us-wait'
          ? '2026-10-29'
          : '2026-10-27';
  const personalDate = () =>
    personalGap() ? '2026-10-26' : commonWaiting() ? '2026-10-27' : rankingDate();
  const metric = () => ({
    weeklyReturnRate:
      partial || personalGap() || ['holiday', 'us-wait'].includes(displayScenario) ? 10 : 21,
    threeWeekReturnRate: null,
    maxDrawdownRate: 0,
    volatilityRate: null,
    maxHoldingWeight: 40,
    currentHoldingWeight: 40,
    trackingStartedOn: profile.trackingStartedOn,
    consecutiveRecordedWeeks: 1,
  });
  const aggregation = (ranking = false) =>
    personalGap()
      ? {
          baseDate: '2026-10-27',
          expectedAt: null,
          firstReturnExpectedAt: null,
          reason: 'MISSING_PRICE',
          delayed: true,
        }
      : commonWaiting() && ranking
        ? {
            baseDate: '2026-10-27',
            expectedAt: '2026-10-28T08:20:00Z',
            firstReturnExpectedAt: null,
            reason: 'PRICE_COLLECTION_PENDING',
            delayed: false,
          }
        : displayScenario === 'us-wait'
          ? {
              baseDate: '2026-10-30',
              expectedAt: '2026-10-31T08:20:00Z',
              firstReturnExpectedAt: null,
              reason: 'PRICE_COLLECTION_PENDING',
              delayed: false,
            }
          : null;
  const snapshot = () => ({
    baseDate: '2026-10-27',
    priceBaseDate: '2026-10-27',
    totalPurchaseAmount: portfolio().totalPurchaseAmount,
    totalEvaluationAmount: portfolio().totalEvaluationAmount,
    totalProfitLoss: portfolio().totalEvaluationAmount - portfolio().totalPurchaseAmount,
    totalReturnRate: portfolio().totalReturnRate,
    dailyProfitLoss: 0,
    dailyReturnRate: 10,
  });
  return {
    request(method, url, body = {}) {
      const path = url.pathname;
      if (path === '/api/__league-preview/scenario' && method === 'POST') {
        if (
          ![
            'reset',
            'sync',
            'partial',
            'holiday',
            'us-wait',
            'snapshot',
            'common-wait',
            'common-ready',
            'member-gap',
          ].includes(body.scenario)
        )
          return response({ message: '지원하지 않는 목업입니다.' }, 400);
        displayScenario = body.scenario;
        if (body.scenario !== 'sync' || partial) {
          holdings = initialHoldings();
          profile = { ...baseProfile };
          partial = false;
          changed = false;
        }
        if (['common-wait', 'common-ready'].includes(body.scenario)) {
          profile = {
            ...profile,
            displayName: '국내투자자',
            bio: '국내 종목만 보유한 투자자입니다.',
          };
          holdings = holdings.filter((h) => h.currency === 'KRW');
        }
        if (body.scenario === 'snapshot') {
          profile = {
            ...profile,
            leagueEnabledAt: '2026-10-28T09:00:00Z',
            trackingStartedOn: '2026-10-26',
            eligibleFrom: '2026-10-26',
          };
        } else if (body.scenario === 'partial') {
          partial = true;
          profile = {
            ...profile,
            leagueEnabledAt: '2026-10-26T01:00:00Z',
            trackingStartedOn: '2026-10-26',
          };
        } else if (body.scenario === 'sync' && !changed) {
          holdings = holdings
            .filter((h) => h.assetId !== 4)
            .map((h) => ({ ...h, quantity: h.assetId === 1 ? 15 : h.assetId === 2 ? 3 : 7 }));
          holdings.push(
            security(5, '테슬라', 'TSLA.US', 'NASDAQ', 'USD', 2, 260000, 210, true, ''),
          );
          changed = true;
        }
        return response({ changed, partial });
      }
      if (path === '/api/auth/csrf')
        return response({ headerName: 'X-XSRF-TOKEN', token: 'local-preview' });
      if (path === '/api/users/me')
        return response({
          id: 1,
          email: 'preview@example.invalid',
          name: null,
          nickname: profile.displayName,
          role: 'USER',
        });
      if (
        path === '/api/users/me/investor-profile' ||
        path === '/api/users/me/portfolio/publication'
      ) {
        if (method === 'PUT') profile = { ...profile, ...body };
        return response(profile);
      }
      if (path === '/api/league/weeks') return response({ weeks: [week] });
      if (path === '/api/league/rankings') {
        const rows = [
          {
            ...profile,
            ...metric(),
            publicId: 'preview-first',
            displayName: ['common-wait', 'common-ready'].includes(displayScenario)
              ? '미국투자자'
              : '꾸준한분산',
            weeklyReturnRate: commonWaiting() ? 12 : displayScenario === 'common-ready' ? 18 : 25.4,
          },
          { ...profile, ...metric(), ...(commonWaiting() ? { weeklyReturnRate: 10 } : {}) },
          {
            ...profile,
            ...metric(),
            publicId: 'preview-third',
            displayName: '장기투자중',
            weeklyReturnRate: 5.2,
          },
        ]
          .filter((row) => !personalGap() || row.publicId !== 'preview-me')
          .sort((a, b) => b.weeklyReturnRate - a.weeklyReturnRate)
          .map((row, index) => ({
            ...row,
            rank: index + 1,
            decisionCount: 3,
            reviewCompletionRate: 50,
            followed: false,
          }));
        return response({
          ...week,
          leagueType: url.searchParams.get('leagueType') ?? 'WEEKLY_RETURN',
          asOfDate: rankingDate(),
          totalCount: rows.length,
          rankings: rows,
          aggregation: aggregation(true),
        });
      }
      if (/^\/api\/league\/investors\/[^/]+\/daily-history$/.test(path))
        return response({
          ...week,
          asOfDate: personalDate(),
          eligibleFrom: profile.eligibleFrom,
          trackingStartedOn: profile.trackingStartedOn,
          weeklyReturnRate:
            partial || personalGap() || ['holiday', 'us-wait'].includes(displayScenario) ? 10 : 21,
          aggregation: aggregation(),
          days: Array.from({ length: 7 }, (_, index) => {
            const date = new Date(Date.UTC(2026, 9, 26 + index)).toISOString().slice(0, 10);
            const completed =
              displayScenario === 'holiday' ||
              (personalGap() ? index === 0 : displayScenario === 'us-wait' ? index < 4 : index < 2);
            const holiday = displayScenario === 'holiday' && index >= 5;
            const dailyReturnRate = !completed
              ? null
              : index === 0
                ? partial
                  ? 0
                  : 10
                : ['holiday', 'us-wait'].includes(displayScenario)
                  ? 0
                  : 10;
            return {
              baseDate: date,
              dailyReturnRate,
              cumulativeReturnRate: !completed
                ? null
                : ['holiday', 'us-wait'].includes(displayScenario)
                  ? 10
                  : index === 0
                    ? partial
                      ? 0
                      : 10
                    : partial
                      ? 10
                      : 21,
              status: completed ? 'CALCULATED' : 'PENDING',
              reason: holiday
                ? 'MARKET_CLOSED'
                : completed
                  ? partial && index === 0
                    ? 'TRACKING_BASELINE'
                    : null
                  : personalGap() && index === 1
                    ? 'MISSING_PRICE'
                    : displayScenario === 'us-wait'
                      ? 'PRICE_COLLECTION_PENDING'
                      : 'FUTURE_DATE',
            };
          }),
        });
      if (/^\/api\/league\/investors\/[^/]+\/details$/.test(path)) {
        const visible = portfolio().holdings.filter((h) => !h.hidden);
        return response({
          publicId: path.split('/')[4],
          asOfDate: personalDate(),
          holdings: visible.map((h) => ({
            assetName: h.assetName,
            category: h.category,
            weight: (h.evaluationAmount / portfolio().totalEvaluationAmount) * 100,
            weeklyContributionRate: null,
            returnRate: h.returnRate,
            reason: h.reason,
          })),
          decisions: decisions()
            .filter((d) => visible.some((h) => h.assetId === d.assetId))
            .map((d) => ({ ...d, transactionId: d.id })),
        });
      }
      if (/^\/api\/league\/investors\/[^/]+$/.test(path))
        return response({
          ...profile,
          publicId: path.split('/')[4],
          performance: {
            ...metric(),
            weeklyRank: personalGap() ? null : displayScenario === 'common-ready' ? 1 : 2,
            asOfDate: personalDate(),
            rankingAsOfDate: personalGap() ? null : rankingDate(),
          },
          allocation: { stockWeight: 80, etfWeight: 20 },
          history: [
            {
              ...week,
              weeklyReturnRate:
                partial || personalGap() || ['holiday', 'us-wait'].includes(displayScenario)
                  ? 10
                  : 21,
              maxDrawdownRate: 0,
              status: 'CALCULATED',
              weeklyRank: 2,
            },
          ],
          decisionCount: 3,
          reviewCompletionRate: 50,
          followed: false,
          detailAvailable: true,
        });
      if (path === '/api/users/me/shared-portfolio') return response(portfolio());
      const visibility = path.match(
        /^\/api\/users\/me\/shared-portfolio\/holdings\/(\d+)\/visibility$/,
      );
      if (visibility && method === 'PATCH') {
        const h = holdings.find((value) => value.assetId === Number(visibility[1]));
        if (!h) return response({ message: '종목을 찾을 수 없습니다.' }, 404);
        if (!body.hidden && !h.reason)
          return response(
            {
              code: 'HOLDING_REASON_REQUIRED',
              message: '공개할 종목의 보유 이유를 작성해 주세요.',
            },
            400,
          );
        h.hidden = Boolean(body.hidden);
        return response(portfolio());
      }
      if (path === '/api/users/me/portfolio/transactions')
        return response({ transactions: decisions() });
      if (path === '/api/users/me/portfolio/decisions' && method === 'POST') {
        const h = holdings.find((value) => value.assetId === body.assetId);
        if (!h || !body.decision?.reason?.trim())
          return response({ message: '보유 이유를 입력해 주세요.' }, 400);
        h.reason = body.decision.reason.trim();
        h.pendingReason = false;
        return response(decisions().find((d) => d.assetId === h.assetId));
      }
      if (path === '/api/users/me/league/follows/summary') return response({ investors: [] });
      if (path.startsWith('/api/users/me/league/follows/')) return response(null, 204);
      const sourceHolding = path.match(/^\/api\/users\/me\/portfolio\/holdings\/(\d+)$/);
      if (sourceHolding) {
        const h = holdings.find((value) => value.assetId === Number(sourceHolding[1]));
        if (!h) return response({ message: '종목을 찾을 수 없습니다.' }, 404);
        if (method === 'DELETE') {
          holdings = holdings.filter((value) => value !== h);
          return response(null, 204);
        }
        if (method === 'PATCH') {
          if (
            !Number.isSafeInteger(body.quantity) ||
            body.quantity < 1 ||
            !Number.isFinite(body.averagePurchasePrice) ||
            body.averagePurchasePrice <= 0
          )
            return response({ message: '수량과 평균 매수가를 확인해 주세요.' }, 400);
          h.quantity = body.quantity;
          h.averagePurchasePrice = body.averagePurchasePrice;
          return response({ ...h, name: h.assetName });
        }
      }
      if (path === '/api/users/me/portfolio')
        return response({
          holdings: holdings.map((h) => ({ ...h, name: h.assetName })),
          totalRealizedProfitLoss: 0,
        });
      if (path === '/api/users/me/portfolio/performance/prepare')
        return response({ status: 'READY' });
      if (path === '/api/users/me/portfolio/performance')
        return response({
          status: 'READY',
          reason: null,
          baseDate: '2026-10-27',
          previousBaseDate: '2026-10-26',
          points: [{ ...snapshot(), priceBaseDate: undefined }],
          currentPoint: snapshot(),
        });
      if (path === '/api/portfolio/histories') return response([]);
      if (path === '/api/portfolio/calculate')
        return response({
          baseDate: '2026-10-27',
          pricingStatus: 'COMPLETE',
          pricedHoldingCount: holdings.length,
          unpricedHoldingCount: 0,
          ...snapshot(),
          holdings: portfolio().holdings.map((h) => ({
            ...h,
            name: h.assetName,
            priceAvailable: true,
            purchaseAmount: h.quantity * h.averagePurchasePrice,
            profitLoss: h.evaluationAmount - h.quantity * h.averagePurchasePrice,
            weight: (h.evaluationAmount / portfolio().totalEvaluationAmount) * 100,
          })),
          marketAllocations: [],
        });
      if (path.startsWith('/api/users/me/portfolio/analysis'))
        return response({
          status: 'EMPTY',
          analysisId: null,
          analysis: null,
          matchesCurrentPortfolio: true,
          summaryStatus: null,
          lastCheckOutcome: null,
          lastSucceededAt: null,
          lastFailedAt: null,
          failureCode: null,
          changeReason: null,
        });
      if (path.endsWith('/analysis/tasks')) return response({ tasks: [] });
      if (path.endsWith('/analysis')) return response({ status: 'EMPTY', analysis: null });
      return response({ message: '이 API는 목업에 포함되지 않았습니다.' }, 404);
    },
  };
}
