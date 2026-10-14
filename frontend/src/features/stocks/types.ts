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
