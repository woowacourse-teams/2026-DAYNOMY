export type StockMarket = 'KOSPI' | 'KOSDAQ';
export type AssetCategory = 'STOCK' | 'ETF';

export type StockSearchItem = {
  assetId: number;
  assetCode: string;
  name: string;
  category: AssetCategory;
  market: StockMarket;
};

export type StockPrice = {
  assetId: number;
  assetCode: string;
  name: string;
  baseDate: string;
  closePrice: number;
};

export type PortfolioHoldingInput = StockSearchItem & {
  quantity: number;
  averagePurchasePrice: number;
};

export type PortfolioHoldingChangeType = 'ADDED' | 'UPDATED' | 'REMOVED';

export type PortfolioTrendPeriod = 'YTD' | '1M' | '6M' | '1Y' | '5Y';

export type PortfolioHoldingHistory = {
  changeType: PortfolioHoldingChangeType;
  occurredAt: string;
  previousHolding: PortfolioHoldingInput | null;
  holding: PortfolioHoldingInput | null;
};

export type PortfolioPerformancePoint = {
  baseDate: string;
  priceBaseDate?: string | null;
  recordedAt: string;
  source: 'CLOSE' | 'HOLDING_CHANGE';
  totalPurchaseAmount: number;
  totalEvaluationAmount: number;
  totalProfitLoss: number;
  totalReturnRate: number;
};

export type PortfolioPerformanceStatus = 'READY' | 'INSUFFICIENT_DATA';
export type PortfolioPerformanceUnavailableReason = 'SNAPSHOT_DATA_INSUFFICIENT';

export type PortfolioPerformanceResponse = {
  status: PortfolioPerformanceStatus;
  reason: PortfolioPerformanceUnavailableReason | null;
  baseDate: string | null;
  previousBaseDate: string | null;
  points: PortfolioPerformancePoint[];
  currentPoint: PortfolioPerformancePoint;
};

export type SavedPortfolioResponse = {
  holdings: PortfolioHoldingInput[];
};

export type PortfolioHoldingResult = PortfolioHoldingInput & {
  baseDate: string;
  closePrice: number;
  purchaseAmount: number;
  evaluationAmount: number;
  profitLoss: number;
  returnRate: number;
  weight: number;
};

export type MarketAllocation = {
  market: StockMarket;
  evaluationAmount: number;
  weight: number;
};

export type PortfolioCalculation = {
  baseDate: string;
  totalPurchaseAmount: number;
  totalEvaluationAmount: number;
  dailyProfitLoss: number | null;
  dailyReturnRate: number | null;
  totalProfitLoss: number;
  totalReturnRate: number;
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
};

export type PortfolioAssetImpactResponse = {
  assetName: string;
  weight: number;
  direction: PortfolioImpactDirection;
  impactLevel: PortfolioImpactLevel;
  issueSummary: string;
  expectedReaction: string;
  outlook: string;
  reason: string;
  evidenceSentence: string;
  sources: PortfolioAnalysisSource[];
  rank: number;
};

export type PortfolioAnalysisResponse = {
  totalAssetCount: number;
  analyzedAssetCount: number;
  overallDirection: PortfolioImpactDirection;
  overallScore: number;
  positiveImpactScore: number;
  negativeImpactScore: number;
  analyzedAt: string | null;
  overallImpact: string;
  impacts: PortfolioAssetImpactResponse[];
  sources: PortfolioAnalysisSource[];
};
