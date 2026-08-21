import type { Category } from '../newslist/types';

export type Direction = 'positive' | 'negative';

export type NewsDetail = {
  id: number;
  title: string;
  category: Category;
  publishedAt: string;
  description: string;
  content: string[];
  imageUrl?: string;
  source?: string;
  sourceUrl?: string;
};

export type Impact = {
  asset: string;
  direction: Direction;
  impactLevel: 'HIGH' | 'MEDIUM' | 'LOW';
};

export type Scenario = {
  title: string;
  probability?: number;
  description: string;
};

export type NewsKeyword = {
  keyword: string;
  description: string;
};

export type MarketAnalysis = {
  cause: string;
  impacts: Impact[];
  scenarios: Scenario[];
};

export type NewsDetailPayload = {
  news: NewsDetail;
  keywords: NewsKeyword[];
  marketAnalysis?: MarketAnalysis;
};
