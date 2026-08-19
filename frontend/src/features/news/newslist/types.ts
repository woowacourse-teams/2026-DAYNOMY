export type NewsCategory =
  | 'ALL'
  | 'REAL_ESTATE'
  | 'DEPOSIT_SAVINGS'
  | 'STOCK'
  | 'ETF'
  | 'BOND'
  | 'PENSION'
  | 'FOREIGN_EXCHANGE'
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
