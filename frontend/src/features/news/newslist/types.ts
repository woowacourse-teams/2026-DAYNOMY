export const CATEGORY_LABELS = {
  REAL_ESTATE: '부동산',
  DEPOSIT_SAVINGS: '예금·적금',
  STOCK: '주식',
  ETF: 'ETF',
  BOND: '채권',
  PENSION: '연금',
  FOREIGN_EXCHANGE: '외화·환율',
  VIRTUAL_ASSET: '가상자산',
  GOLD: '금',
} as const;

export type Category = keyof typeof CATEGORY_LABELS;
export type NewsCategory = 'ALL' | Category;

export function isCategory(value: unknown): value is Category {
  return typeof value === 'string' && Object.hasOwn(CATEGORY_LABELS, value);
}

export function getCategoryLabel(value: NewsCategory) {
  return value === 'ALL' ? '전체' : CATEGORY_LABELS[value];
}

export type NewsArticle = {
  id: number | string;
  title: string;
  summary?: string;
  category: Category;
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
