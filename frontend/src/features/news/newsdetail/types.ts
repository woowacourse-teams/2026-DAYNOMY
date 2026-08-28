import type { Category } from '../newslist/types';

export type ImpactDirection = 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL';
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
  id: number;
  title: string;
  category: Category;
  publishedAt: string;
  description: string | null;
  content: string | string[];
  imageUrl?: string | null;
  source?: string | null;
  originalUrl?: string | null;
  sourceUrl?: string | null;
};

export type KeywordCategory = 'PERSON' | 'POLICY' | 'EVENT' | 'TERM' | 'TREND';

export type KeywordResponse = {
  keyword: string;
  category: KeywordCategory;
  points: string[];
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
  news: NewsDetailResponse;
  keywords: KeywordResponse[];
  marketAnalysis?: MarketAnalysisResponse;
};
