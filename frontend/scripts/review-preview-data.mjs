const week = {
  weekStart: '2026-10-26',
  weekEnd: '2026-11-01',
  confirmed: false,
  collectionStatus: 'IN_PROGRESS',
};
const profile = {
  publicId: 'review-preview',
  displayName: '복기 체험',
  bio: '가상 데이터로 확인하는 투자 기록입니다.',
  experienceLevel: 'BEGINNER',
  riskProfile: 'BALANCED',
  profilePublic: true,
  leagueEnabled: true,
  allocationPublic: true,
  detailPublic: false,
  leagueEnabledAt: '2026-10-19T00:00:00Z',
  trackingStartedOn: '2026-10-19',
  eligibleFrom: '2026-10-26',
};
const article = {
  id: 900001,
  title: '가상 기사: 삼성전자 실적과 비용 변화',
  category: 'ECONOMY',
  publishedAt: '2026-10-28T09:00:00Z',
  content:
    '이 기사는 복기 UI를 확인하기 위한 가상 자료입니다. 예시에서 매출은 늘었지만 비용 증가로 영업이익이 감소했습니다. 실제 기업의 실적을 나타내지 않습니다.',
  imageUrl: null,
  sources: [],
};
const original = {
  id: 7,
  assetId: 1,
  assetCode: '005930',
  assetName: '삼성전자',
  category: 'STOCK',
  transactionType: 'HOLD',
  quantity: 10,
  unitPrice: 70000,
  fee: 0,
  tradedOn: '2026-10-23',
  reason: '실적 개선과 비용 감소를 기대하며 보유합니다.',
  expectedHoldingPeriod: 'THREE_TO_SIX_MONTHS',
  expectedChange: '영업이익 개선',
  invalidationCondition: '비용 증가가 이어지면 다시 확인',
  maximumAcceptableLossRate: 10,
  writtenAfterTrade: false,
  reviews: [],
};
const sourceHoldings = [
  {
    assetId: 1,
    assetCode: '005930',
    name: '삼성전자',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 10,
    averagePurchasePrice: 70000,
  },
  {
    assetId: 2,
    assetCode: '000660',
    name: 'SK하이닉스',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 5,
    averagePurchasePrice: 110000,
  },
];
const validDraft = (body) =>
  ['actualResult', 'differenceFromExpectation', 'nextAction'].every(
    (key) => typeof body[key] === 'string' && body[key].trim() && body[key].length <= 500,
  );

export function createReviewPreview() {
  let records = [structuredClone(original)];
  let sequence = 100;
  let imported = [structuredClone(sourceHoldings[0])];
  const sharedPortfolio = () => {
    const holdings = imported.map((holding) => ({
      ...holding,
      assetName: holding.name,
      hidden: holding.hidden ?? false,
      reason: records.find((record) => record.assetId === holding.assetId)?.reason ?? '',
      baseDate: '2026-10-29',
      closePrice: holding.averagePurchasePrice * 1.05,
      evaluationAmount: holding.quantity * holding.averagePurchasePrice * 1.05,
      returnRate: 5,
    }));
    const totalPurchaseAmount = holdings.reduce(
      (sum, holding) => sum + holding.quantity * holding.averagePurchasePrice,
      0,
    );
    return {
      holdings,
      totalPurchaseAmount,
      totalEvaluationAmount: totalPurchaseAmount * 1.05,
      totalReturnRate: holdings.length ? 5 : null,
      pricesComplete: true,
    };
  };
  const response = (data, status = 200) => ({ data, status });
  return {
    request(method, url, body = {}) {
      const path = url.pathname;
      if (method === 'GET' && path === '/api/users/me')
        return response({
          id: 1,
          nickname: profile.displayName,
          email: 'review@example.invalid',
          role: 'USER',
        });
      if (method === 'GET' && path === '/api/users/me/portfolio')
        return response({ holdings: structuredClone(sourceHoldings), totalRealizedProfitLoss: 0 });
      if (method === 'GET' && path === '/api/users/me/shared-portfolio')
        return response(sharedPortfolio());
      if (method === 'POST' && path === '/api/users/me/shared-portfolio/import') {
        const values = body.holdings;
        if (
          !Array.isArray(values) ||
          !values.length ||
          new Set(values.map((value) => value.assetId)).size !== values.length ||
          !values.every((value) => {
            const known = sourceHoldings.some((holding) => holding.assetId === value.assetId);
            const reason =
              records.find((record) => record.assetId === value.assetId)?.reason ||
              value.decision?.reason;
            return (
              known &&
              Number.isSafeInteger(value.quantity) &&
              value.quantity > 0 &&
              Number.isFinite(value.averagePurchasePrice) &&
              value.averagePurchasePrice > 0 &&
              typeof reason === 'string' &&
              reason.trim() &&
              reason.length <= 500
            );
          })
        )
          return response({ message: '선택한 종목과 보유 이유를 확인해 주세요.' }, 400);
        if (
          !body.overwriteExisting &&
          values.some((value) => imported.some((holding) => holding.assetId === value.assetId))
        )
          return response({ message: '기존 종목 변경을 확인해 주세요.' }, 409);
        for (const value of values) {
          const source = sourceHoldings.find((holding) => holding.assetId === value.assetId);
          const previous = imported.find((holding) => holding.assetId === value.assetId);
          const saved = {
            ...source,
            quantity: value.quantity,
            averagePurchasePrice: value.averagePurchasePrice,
            hidden: previous?.hidden ?? false,
          };
          imported = [...imported.filter((holding) => holding.assetId !== value.assetId), saved];
          if (!records.some((record) => record.assetId === value.assetId))
            records.unshift({
              ...structuredClone(original),
              id: sequence++,
              assetId: source.assetId,
              assetCode: source.assetCode,
              assetName: source.name,
              quantity: value.quantity,
              unitPrice: value.averagePurchasePrice,
              ...value.decision,
              reviews: [],
            });
        }
        return response(sharedPortfolio());
      }
      const holdingMatch = path.match(
        /^\/api\/users\/me\/shared-portfolio\/holdings\/(\d+)(\/visibility)?$/,
      );
      if (holdingMatch) {
        const holding = imported.find((value) => value.assetId === Number(holdingMatch[1]));
        if (!holding) return response({ message: '종목이 없습니다.' }, 404);
        if (method === 'PATCH' && holdingMatch[2] && typeof body.hidden === 'boolean') {
          holding.hidden = body.hidden;
          return response(sharedPortfolio());
        }
        if (method === 'DELETE' && !holdingMatch[2]) {
          imported = imported.filter((value) => value !== holding);
          return response(sharedPortfolio());
        }
      }
      if (method === 'GET' && path === '/api/auth/csrf')
        return response({ token: 'local-preview-only', headerName: 'X-CSRF-TOKEN' });
      if (method === 'GET' && path === '/api/users/me/investor-profile') return response(profile);
      if (method === 'GET' && path === '/api/league/weeks') return response({ weeks: [week] });
      if (method === 'GET' && path === '/api/league/rankings')
        return response({
          ...week,
          leagueType: 'WEEKLY_RETURN',
          aggregation: {
            baseDate: '2026-10-29',
            expectedAt: '2026-10-30T07:00:00Z',
            firstReturnExpectedAt: null,
            reason: 'MARKET_CLOSE_PENDING',
            delayed: false,
          },
          asOfDate: '2026-10-28',
          totalCount: 1,
          rankings: [
            {
              ...profile,
              rank: 1,
              weeklyReturnRate: 2.5,
              threeWeekReturnRate: null,
              maxDrawdownRate: -1,
              volatilityRate: 0.7,
              maxHoldingWeight: 100,
              decisionCount: records.length,
              reviewCompletionRate: records.every((r) => r.reviews.length) ? 100 : 0,
              followed: false,
            },
          ],
        });
      if (method === 'GET' && path === '/api/users/me/portfolio/transactions')
        return response({ transactions: structuredClone(records) });
      if (method === 'GET' && path === '/api/users/me/league/follows/summary')
        return response({ investors: [] });
      if (method === 'GET' && path === `/api/news/${article.id}`) return response(article);
      if (method === 'GET' && path === `/api/news/${article.id}/keywords`)
        return response({ keywords: [] });
      if (method === 'GET' && path === `/api/news/${article.id}/market-analysis`)
        return response({ risk: '가상 자료입니다.', watchPoint: '실제 기업의 공시를 확인하세요.' });
      if (method === 'POST' && path === '/api/__review-preview/reset') {
        records = [structuredClone(original)];
        imported = [structuredClone(sourceHoldings[0])];
        return response(null, 204);
      }
      const match = path.match(
        /^\/api\/users\/me\/portfolio\/decisions\/(\d+)\/(reviews(?:\/(\d+))?|review-guide|reason)$/,
      );
      if (match) {
        const record = records.find((r) => r.id === Number(match[1]));
        if (!record) return response({ message: '기록이 없습니다.' }, 404);
        if (method === 'POST' && match[2] === 'review-guide') {
          if (record.reviews.length)
            return response({ code: 'REVIEW_GUIDE_ALREADY_REVIEWED' }, 409);
          return response({
            transactionId: record.id,
            retrievalStatus: 'FOUND',
            evidence: [
              {
                id: `NEWS:${article.id}`,
                title: article.title,
                publishedAt: article.publishedAt,
                excerpt: article.content,
                href: `/news/${article.id}`,
              },
            ],
            basis: null,
            questions: {
              actualResult: {
                text: '예시 기사에서 어떤 결과를 확인했나요?',
                evidenceIds: [`NEWS:${article.id}`],
              },
              differenceFromExpectation: {
                text: '당시의 기대와 어떤 차이가 있었나요?',
                evidenceIds: [`NEWS:${article.id}`],
              },
              nextAction: { text: '다음 판단에서 어떤 조건을 확인할까요?', evidenceIds: [] },
            },
          });
        }
        if (method === 'POST' && match[2] === 'reviews') {
          if (!validDraft(body))
            return response({ message: '세 항목을 1~500자로 작성해 주세요.' }, 400);
          const review = {
            id: sequence++,
            actualResult: body.actualResult,
            differenceFromExpectation: body.differenceFromExpectation,
            nextAction: body.nextAction,
            createdAt: new Date().toISOString(),
          };
          record.reviews.push(review);
          return response(review);
        }
        if (method === 'PUT' && match[3]) {
          const review = record.reviews.find((r) => r.id === Number(match[3]));
          if (!review) return response({ message: '복기가 없습니다.' }, 404);
          if (!validDraft(body))
            return response({ message: '세 항목을 1~500자로 작성해 주세요.' }, 400);
          Object.assign(review, {
            actualResult: body.actualResult,
            differenceFromExpectation: body.differenceFromExpectation,
            nextAction: body.nextAction,
            updatedAt: new Date().toISOString(),
          });
          return response(review);
        }
        if (method === 'PUT' && match[2] === 'reason') {
          if (typeof body.reason !== 'string' || !body.reason.trim() || body.reason.length > 500)
            return response({ message: '보유 이유를 확인해 주세요.' }, 400);
          record.reason = body.reason;
          return response(record);
        }
      }
      return response(
        { message: '이 로컬 예시는 투자리그·복기·참고 기사 화면을 제공합니다.' },
        404,
      );
    },
  };
}
