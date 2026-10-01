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

export type SavedPortfolio = {
  holdings: PortfolioHoldingInput[];
};

export type PortfolioPerformanceStatus = 'READY' | 'INSUFFICIENT_DATA';
export type PortfolioPerformanceUnavailableReason = 'SNAPSHOT_DATA_INSUFFICIENT';

export type PortfolioPerformancePoint = {
  baseDate: string;
  totalPurchaseAmount: number;
  totalEvaluationAmount: number;
  totalProfitLoss: number;
  totalReturnRate: number;
};

export type PortfolioPerformance = {
  status: PortfolioPerformanceStatus;
  reason: PortfolioPerformanceUnavailableReason | null;
  baseDate: string | null;
  previousBaseDate: string | null;
  points: PortfolioPerformancePoint[];
};

export type PortfolioHoldingChangeType = 'ADDED' | 'UPDATED' | 'REMOVED';

export type PortfolioHoldingHistory = {
  assetId: number;
  assetCode: string;
  name: string;
  changeType: PortfolioHoldingChangeType;
  quantity: number;
  averagePurchasePrice: number;
  occurredAt: string;
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

export type PortfolioAssetImpactResponse = {
  assetName: string;
  weight: number;
  direction: PortfolioImpactDirection;
  impactLevel: PortfolioImpactLevel;
  summary: string;
  reason: string;
  evidenceSentence: string;
  rank: number;
};

export type PortfolioAnalysisResponse = {
  totalAssetCount: number;
  analyzedAssetCount: number;
  impacts: PortfolioAssetImpactResponse[];
};
