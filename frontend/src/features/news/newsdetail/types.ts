import type { Category } from '../newslist/types';

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

export type MarketAnalysisResponse = {
  summary: string;
};

export type MarketAnalysisState =
  { status: 'success'; data: MarketAnalysisResponse } | { status: 'empty' } | { status: 'error' };

export type NewsDetailPayload = {
  news: NewsDetailResponse;
  keywords: KeywordResponse[];
  marketAnalysis: MarketAnalysisState;
};
