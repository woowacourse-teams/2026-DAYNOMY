import type { Category } from '../newslist/types';

export type Direction = 'positive' | 'negative' | 'neutral';

export type NewsDetail = {
  id: number;
  title: string;
  category: Category;
  publishedAt: string;
  description: string;
  content: string[];
  imageUrl?: string;
};

export type Impact = {
  asset: string;
  direction: Direction;
  impactLevel: 'HIGH' | 'MEDIUM' | 'LOW';
  evidence: string;
};

export type RelatedIssue = {
  keyword?: string;
  title: string;
  probability?: number;
  description: string;
};

export type NewsDetailPayload = {
  news: NewsDetail;
  marketCause: string;
  impacts: Impact[];
  relatedIssues: RelatedIssue[];
};
