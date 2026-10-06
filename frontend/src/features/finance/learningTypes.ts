export type LearningItemType = 'GUIDE' | 'MISSION';
export type SimulatedTradeType = 'BUY' | 'SELL';

export type LearningProgress = {
  itemKey: string;
  itemType: LearningItemType;
  completed: boolean;
  bookmarked: boolean;
};

export type WeeklyCheckIn = {
  weekStart: string;
  targetSavings: number;
  targetInvestment: number;
  targetDebtPayment: number;
  actualSavings: number;
  actualInvestment: number;
  actualDebtPayment: number;
  note: string | null;
};

export type LearningStock = {
  assetId: number;
  assetCode: string;
  assetName: string;
  category: 'STOCK' | 'ETF';
  market: 'KOSPI' | 'KOSDAQ';
};

export type LearningStockPrice = {
  assetId: number;
  assetCode: string;
  name: string;
  baseDate: string;
  closePrice: number;
};

export type SimulatedTrade = LearningStock & {
  id: number;
  tradeType: SimulatedTradeType;
  quantity: number;
  price: number;
  reason: string | null;
  tradedOn: string;
};

export type SimulatedTradeInput = Omit<SimulatedTrade, 'id'>;
