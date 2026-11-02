import type {
  PortfolioCalculation,
  PortfolioHoldingHistory,
  PortfolioHoldingInput,
} from '../features/portfolio/types';

// Local UI demo only. These are fictional prices, never market data or persisted holdings.
export const demoHoldings: PortfolioHoldingInput[] = [
  {
    assetId: 2,
    assetCode: '000660',
    name: 'SK하이닉스',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 4,
    averagePurchasePrice: 180000,
  },
  {
    assetId: 1,
    assetCode: '005930',
    name: '삼성전자',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 8,
    averagePurchasePrice: 70000,
  },
  {
    assetId: 4,
    assetCode: '069500',
    name: 'KODEX 200',
    category: 'ETF',
    market: 'KOSPI',
    quantity: 10,
    averagePurchasePrice: 32000,
  },
  {
    assetId: 5,
    assetCode: '035720',
    name: '카카오',
    category: 'STOCK',
    market: 'KOSPI',
    quantity: 2,
    averagePurchasePrice: 50000,
  },
];
const demoPrices: Record<number, number | null> = { 2: 198000, 1: 66500, 4: 33600, 5: null };
const baseDate = '2026-10-28';
const evaluationTotal = demoHoldings.reduce(
  (total, holding) => total + (demoPrices[holding.assetId] ?? 0) * holding.quantity,
  0,
);
const holdings = demoHoldings.map((holding) => {
  const closePrice = demoPrices[holding.assetId];
  const purchaseAmount = holding.quantity * holding.averagePurchasePrice;
  const evaluationAmount = closePrice === null ? null : holding.quantity * closePrice;
  const profitLoss = evaluationAmount === null ? null : evaluationAmount - purchaseAmount;
  return {
    ...holding,
    priceAvailable: closePrice !== null,
    baseDate: closePrice === null ? null : baseDate,
    closePrice,
    purchaseAmount,
    evaluationAmount,
    profitLoss,
    returnRate: profitLoss === null ? null : (profitLoss / purchaseAmount) * 100,
    weight: evaluationAmount === null ? null : (evaluationAmount / evaluationTotal) * 100,
  };
});
export const demoCalculation: PortfolioCalculation = {
  baseDate,
  pricingStatus: 'PARTIAL',
  pricedHoldingCount: 3,
  unpricedHoldingCount: 1,
  totalPurchaseAmount: holdings.reduce((total, holding) => total + holding.purchaseAmount, 0),
  totalEvaluationAmount: evaluationTotal,
  totalProfitLoss: null,
  totalReturnRate: null,
  dailyProfitLoss: null,
  dailyReturnRate: null,
  holdings,
  marketAllocations: [{ market: 'KOSPI', evaluationAmount: evaluationTotal, weight: 100 }],
};
const demoTrades = [
  [2, 'BUY', 2, 180000, 2, '2026-10-20'],
  [2, 'BUY', 3, 180000, 5, '2026-10-22'],
  [2, 'SELL', 1, 186000, 4, '2026-10-24'],
  [2, 'BUY', 1, 180000, 5, '2026-10-25'],
  [2, 'SELL', 1, 186000, 4, '2026-10-26'],
  [1, 'BUY', 8, 70000, 8, '2026-10-23'],
  [4, 'BUY', 10, 32000, 10, '2026-10-25'],
  [5, 'BUY', 2, 50000, 2, '2026-10-27'],
] as const;
export const demoHistories: PortfolioHoldingHistory[] = demoTrades.map(
  ([assetId, changeType, transactionQuantity, unitPrice, quantity, effectiveDate], index) => {
    const holding = demoHoldings.find((item) => item.assetId === assetId)!;
    return {
      ...holding,
      historyId: index + 1,
      changeType,
      transactionQuantity,
      unitPrice,
      quantity,
      effectiveDate,
      occurredAt: `${effectiveDate}T10:30:00+09:00`,
      tradeTime: '10:30',
      fee: 0,
      tax: 0,
    };
  },
);
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

// Installed only by the separate development HTML entry, never by the application entry.
// Every request is handled locally; unknown requests and writes never reach a backend.
export const portfolioDemoFetch: typeof fetch = async (input, init) => {
  const requestUrl = input instanceof Request ? input.url : String(input);
  const url = new URL(requestUrl, 'http://127.0.0.1:5173');
  const path = url.pathname;
  const method = init?.method ?? (input instanceof Request ? input.method : 'GET');
  if (method === 'POST' && path === '/api/portfolio/calculate') return json(demoCalculation);
  if (method === 'POST' && path === '/api/users/me/portfolio/performance/prepare')
    return json({ status: 'READY' });
  if (method !== 'GET')
    return json(
      {
        code: 'DEMO_READ_ONLY',
        message: '데모에서는 거래가 저장되지 않아요. 필터·정렬·상세보기를 이용해 보세요.',
      },
      405,
    );
  if (path === '/api/auth/csrf')
    return json({ token: 'local-demo-only', headerName: 'X-CSRF-TOKEN' });
  if (path === '/api/users/me/portfolio')
    return json({ holdings: demoHoldings, totalRealizedProfitLoss: 12000 });
  if (path === '/api/portfolio/histories') return json(demoHistories);
  if (path === '/api/users/me/portfolio/performance')
    return json({
      status: 'INSUFFICIENT_DATA',
      reason: 'SNAPSHOT_DATA_INSUFFICIENT',
      baseDate: null,
      previousBaseDate: null,
      points: [],
      currentPoint: null,
    });
  if (path === '/api/portfolio/analysis/tasks')
    return json({
      taskId: null,
      status: 'EMPTY',
      analysis: null,
      summaryStatus: null,
      failureCode: null,
      retryAllowed: false,
    });
  if (path === '/api/stocks') {
    const query = (
      url.searchParams.get('q') ??
      url.searchParams.get('keyword') ??
      ''
    ).toLocaleLowerCase();
    return json({
      stocks: demoHoldings.filter((item) =>
        `${item.name} ${item.assetCode}`.toLocaleLowerCase().includes(query),
      ),
    });
  }
  const price = path.match(/^\/api\/stocks\/(\d+)\/price$/);
  if (price) {
    const holding = demoHoldings.find((item) => item.assetId === Number(price[1]));
    const closePrice = demoPrices[Number(price[1])];
    return holding && closePrice != null
      ? json({ ...holding, baseDate, closePrice })
      : json({ code: 'STOCK_PRICE_NOT_FOUND', message: '데모 시세가 없는 종목입니다.' }, 404);
  }
  const contents = path.match(/^\/api\/assets\/(\d+)\/contents$/);
  if (contents) {
    const stock = demoHoldings.find((item) => item.assetId === Number(contents[1]));
    return json({
      assetId: stock?.assetId,
      assetCode: stock?.assetCode,
      assetName: stock?.name,
      contents: [],
    });
  }
  return json({ code: 'DEMO_UNAVAILABLE', message: '이 기능은 데모에 포함되지 않았어요.' }, 404);
};
