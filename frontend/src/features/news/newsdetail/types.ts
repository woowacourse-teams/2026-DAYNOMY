import type { Category } from '../newslist/types';

export type Direction = 'positive' | 'negative' | 'neutral';

export type NewsDetail = {
  title: string;
  category: Category;
  source: string;
  publishedAt: string;
  originalUrl?: string;
  summary: string;
  body: string[];
  imageUrl?: string;
};

export type Impact = {
  asset: string;
  direction: Direction;
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
  impacts: Impact[];
  relatedIssues: RelatedIssue[];
};
