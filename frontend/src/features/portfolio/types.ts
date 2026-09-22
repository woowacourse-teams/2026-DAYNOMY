export type StockMarket = 'KOSPI' | 'KOSDAQ';

export type StockSearchItem = {
  assetId: number;
  assetCode: string;
  name: string;
  market: StockMarket;
};

export type PortfolioHoldingInput = StockSearchItem & {
  quantity: number;
  averagePurchasePrice: number;
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
  totalProfitLoss: number;
  totalReturnRate: number;
  holdings: PortfolioHoldingResult[];
  marketAllocations: MarketAllocation[];
};
