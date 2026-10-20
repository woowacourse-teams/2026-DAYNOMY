export type StockMarket =
  'KOSPI' | 'KOSDAQ' | 'NASDAQ' | 'NYSE' | 'NYSE_ARCA' | 'NYSE_AMERICAN' | 'BATS';
export type AssetCategory = 'STOCK' | 'ETF';

export type StockSearchItem = {
  assetId: number;
  assetCode: string;
  name: string;
  category: AssetCategory;
  market: StockMarket;
  currency?: 'KRW' | 'USD';
};

export type StockPrice = {
  assetId: number;
  assetCode: string;
  name: string;
  baseDate: string;
  closePrice: number;
  currency?: 'KRW' | 'USD';
};

export function stockCurrency(stock: { assetCode: string; currency?: 'KRW' | 'USD' }) {
  return stock.currency ?? (stock.assetCode.endsWith('.US') ? 'USD' : 'KRW');
}
