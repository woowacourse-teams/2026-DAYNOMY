import type { Category } from '../newslist/types';

export type NewsDetailResponse = {
  id: number;
  title: string;
  category: Category;
  publishedAt: string;
  content: string | string[];
  imageUrl?: string | null;
  imageSource?: NewsImageSource;
  sources: NewsSource[];
};

export type NewsImageSource = {
  name: string;
  url: string;
  author: string;
  license: string;
  licenseUrl: string;
  type: NewsImageSourceType;
};

export type NewsImageSourceType = 'NONE' | 'AI_GENERATED' | 'WIKIMEDIA' | 'MANUAL';

export type NewsSource = {
  name: string;
  url: string;
};

export type KeywordCategory = 'PERSON' | 'POLICY' | 'EVENT' | 'TERM' | 'TREND';

export type KeywordResponse = {
  keyword: string;
  category: KeywordCategory;
  definition: string;
  context: string;
  watchPoint: string;
};

export type KeywordsResponse = {
  keywords: KeywordResponse[];
};

export type MarketAnalysisResponse = {
  observedFact: string;
  interpretation: string;
  risk: string;
  watchPoint: string;
  limitation: string;
};

export type MarketAnalysisState =
  { status: 'success'; data: MarketAnalysisResponse } | { status: 'empty' } | { status: 'error' };

export type NewsDetailPayload = {
  news: NewsDetailResponse;
  keywords: KeywordResponse[];
  marketAnalysis: MarketAnalysisState;
};
