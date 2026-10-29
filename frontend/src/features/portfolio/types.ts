import type { StockMarket, StockSearchItem } from '../stocks/types';

export type PortfolioHoldingInput = StockSearchItem & {
  quantity: number;
  averagePurchasePrice: number;
};

export type PortfolioHoldingChangeType =
  'ADDED' | 'UPDATED' | 'REMOVED' | 'OPENING_BALANCE' | 'ADJUSTED' | 'BUY' | 'SELL';
export type PortfolioTrendPeriod = 'YTD' | '1M' | '6M' | '1Y' | '5Y';
export type PortfolioTransactionType = 'BUY' | 'SELL';
export type PortfolioTransactionUpdateRequest = {
  tradeDate: string;
  exchangeRate?: number;
  quantity: number;
  unitPrice: number;
  fee: number;
  tax: number;
};
export type PortfolioTransactionRequest = PortfolioTransactionUpdateRequest & {
  assetId: number;
  type: PortfolioTransactionType;
};
export type PortfolioTransactionResponse = {
  transactionId: number;
  holding: PortfolioHoldingInput | null;
  totalRealizedProfitLoss: number;
};
export type PortfolioHoldingHistory = {
  historyId: number;
  assetId: number;
  assetCode: string;
  name: string;
  changeType: PortfolioHoldingChangeType;
  quantity: number;
  averagePurchasePrice: number;
  effectiveDate: string;
  transactionQuantity: number | null;
  unitPrice: number | null;
  fee: number;
  tax: number;
  occurredAt: string;
  currency?: 'KRW' | 'USD';
  exchangeRate?: number;
};

export type PortfolioPerformancePoint = {
  baseDate: string;
  totalPurchaseAmount: number;
  totalEvaluationAmount: number;
  totalProfitLoss: number;
  totalReturnRate: number;
  dailyProfitLoss: number | null;
  dailyReturnRate: number | null;
};

export type PortfolioPerformanceStatus = 'READY' | 'INSUFFICIENT_DATA';
export type PortfolioPerformanceUnavailableReason = 'SNAPSHOT_DATA_INSUFFICIENT';

export type PortfolioPerformanceResponse = {
  status: PortfolioPerformanceStatus;
  reason: PortfolioPerformanceUnavailableReason | null;
  baseDate: string | null;
  previousBaseDate: string | null;
  points: PortfolioPerformancePoint[];
  currentPoint: (PortfolioPerformancePoint & { priceBaseDate: string | null }) | null;
};

export type SavedPortfolioResponse = {
  holdings: PortfolioHoldingInput[];
  totalRealizedProfitLoss: number;
};

export type PortfolioHoldingResult = PortfolioHoldingInput & {
  priceAvailable: boolean;
  baseDate: string | null;
  closePrice: number | null;
  purchaseAmount: number;
  evaluationAmount: number | null;
  profitLoss: number | null;
  returnRate: number | null;
  weight: number | null;
};

export type MarketAllocation = {
  market: StockMarket;
  evaluationAmount: number;
  weight: number;
};

export type PortfolioPricingStatus = 'COMPLETE' | 'MIXED_DATES' | 'PARTIAL';

export type PortfolioCalculation = {
  baseDate: string | null;
  pricingStatus: PortfolioPricingStatus;
  pricedHoldingCount: number;
  unpricedHoldingCount: number;
  totalPurchaseAmount: number;
  totalEvaluationAmount: number;
  dailyProfitLoss: number | null;
  dailyReturnRate: number | null;
  totalProfitLoss: number | null;
  totalReturnRate: number | null;
  holdings: PortfolioHoldingResult[];
  marketAllocations: MarketAllocation[];
};

export type PortfolioAsset = {
  assetName: string;
  weight: number;
};

export type PortfolioAnalysisRequest = {
  assets: PortfolioAsset[];
};

export type PortfolioImpactDirection = 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
export type PortfolioImpactLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export type PortfolioAnalysisSource = {
  title: string;
  url: string;
  publishedOn?: string | null;
  occurredOn?: string | null;
  excerpt?: string;
  factQuote?: string | null;
};

export type PortfolioAssetImpactResponse = {
  assetName: string;
  weight: number;
  direction: PortfolioImpactDirection | null;
  impactLevel: PortfolioImpactLevel | null;
  issueSummary: string;
  expectedReaction: string;
  outlook: string;
  reason: string;
  evidenceSentence: string;
  sources: PortfolioAnalysisSource[];
  rank: number;
};

export type PortfolioAnalysisResponse = {
  excludedAssets?: PortfolioAsset[];
  coverage?: 'COMPLETE' | 'PARTIAL' | 'INSUFFICIENT';
  analyzedWeight?: number;
  totalAssetCount: number;
  analyzedAssetCount: number;
  overallDirection: PortfolioImpactDirection | null;
  overallScore: number | null;
  positiveImpactScore: number;
  negativeImpactScore: number;
  analyzedAt: string | null;
  overallImpact: string;
  impacts: PortfolioAssetImpactResponse[];
  sources: PortfolioAnalysisSource[];
};

export type SavedPortfolioAnalysisResponse = {
  remainingRefreshCount?: number;
  refreshResetAt?: string;
  summaryStatus?: 'PENDING' | 'READY' | 'FAILED' | null;
  status: 'EMPTY' | 'READY' | 'PORTFOLIO_CHANGED' | 'PROCESSING' | 'RECHECK_REQUIRED' | 'FAILED';
  lastCheckOutcome: 'UPDATED' | 'UNCHANGED' | 'UNCONFIRMED' | null;
  analysisId: number | null;
  matchesCurrentPortfolio: boolean;
  lastSucceededAt: string | null;
  lastFailedAt: string | null;
  failureCode: string | null;
  changeReason: string | null;
  analysis: PortfolioAnalysisResponse | null;
};

export type GuestPortfolioAnalysisTaskResponse = {
  taskId: string | null;
  status: 'EMPTY' | 'PROCESSING' | 'READY' | 'FAILED';
  analysis: PortfolioAnalysisResponse | null;
  summaryStatus: 'PENDING' | 'READY' | 'FAILED' | null;
  failureCode: string | null;
  retryAllowed: boolean;
};
