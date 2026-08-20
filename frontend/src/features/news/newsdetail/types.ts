import type { Category } from '../newslist/types';

export type ImpactDirection = 'POSITIVE' | 'NEGATIVE';
export type ImpactLevel = 'HIGH' | 'MEDIUM' | 'LOW';
export type Asset =
  | 'GOLD'
  | 'STOCK'
  | 'BOND'
  | 'REAL_ESTATE'
  | 'FOREIGN_EXCHANGE'
  | 'VIRTUAL_ASSET'
  | 'DEPOSIT_SAVINGS'
  | 'ETF'
  | 'PENSION'
  | 'MOCK';
export type TimeHorizon = 'SHORT_TERM' | 'MID_TERM' | 'LONG_TERM';

export type NewsDetailResponse = {
  id?: number;
  title: string;
  category: Category;
  publishedAt: string;
  description: string;
  content: string | string[];
  imageUrl?: string | null;
  source?: string;
  originalUrl?: string;
  sourceUrl?: string;
  originalText?: string;
  date?: string;
};

export type NewsDetailView = Omit<NewsDetailResponse, 'content'> & {
  source: string;
  originalUrl?: string;
  content: string[];
  imageUrl?: string;
};

export type KeywordResponse = {
  keyword: string;
  description: string;
};

export type KeywordsResponse = {
  keywords: KeywordResponse[];
};

export type AssetImpactResponse = {
  asset: Asset;
  direction: ImpactDirection;
  impactLevel: ImpactLevel;
  reason: string;
};

export type ScenarioResponse = {
  timeHorizon: TimeHorizon;
  prediction: string;
  probability: number;
  reason: string;
};

export type MarketAnalysisResponse = {
  cause: string;
  assets: AssetImpactResponse[];
  scenarios: ScenarioResponse[];
};

export type NewsDetailPayload = {
  news: NewsDetailView;
  marketAnalysis: MarketAnalysisResponse;
  keywords: KeywordResponse[];
};
