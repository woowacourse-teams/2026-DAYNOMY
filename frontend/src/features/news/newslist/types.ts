export type NewsCategory =
  | 'ALL'
  | 'POLICY'
  | 'REAL_ESTATE'
  | 'INTEREST_RATE'
  | 'EXCHANGE_RATE'
  | 'COMPANY'
  | 'VIRTUAL_ASSET'
  | 'GOLD';

export type NewsArticle = {
  id: number | string;
  title: string;
  summary?: string;
  category: string;
  thumbnailUrl?: string;
  publishedAt?: string;
  source?: string;
  body?: string;
};

export type NewsPage = {
  content: NewsArticle[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
};

export type NewsCategoryOption = {
  label: string;
  value: NewsCategory;
};
