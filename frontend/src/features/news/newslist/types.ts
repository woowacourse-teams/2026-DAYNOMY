export const CATEGORY_LABELS = {
  REAL_ESTATE: '부동산',
  DEPOSIT_SAVINGS: '예금·적금',
  STOCK: '주식',
  ECONOMY: '경제지표',
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

export function getCategoryLabel(value: string) {
  if (value === 'ALL') {
    return '전체';
  }

  return isCategory(value) ? CATEGORY_LABELS[value] : value;
}

export type NewsListItemResponse = {
  id: number;
  title: string;
  description: string | null;
  category: Category;
  imageUrl: string | null;
  publishedAt: string | null;
};

export type NewsListItem = {
  id: number;
  title: string;
  description: string | null;
  category: NewsCategory;
  imageUrl: string | null;
  publishedAt: string | null;
};

export type NewsPage = {
  content: NewsListItem[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
};

export type NewsCategoryOption = {
  label: string;
  value: NewsCategory;
};

export function toNewsListItem(response: NewsListItemResponse): NewsListItem {
  return {
    id: response.id,
    title: response.title,
    description: response.description,
    category: response.category,
    imageUrl: response.imageUrl,
    publishedAt: response.publishedAt,
  };
}
