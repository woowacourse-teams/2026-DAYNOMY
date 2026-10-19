import { CATEGORY_LABELS, getCategoryLabel } from './types';
import type { NewsCategoryOption } from './types';

export const NEWS_LIST_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  { label: CATEGORY_LABELS.STOCK, value: 'STOCK' },
  { label: CATEGORY_LABELS.REAL_ESTATE, value: 'REAL_ESTATE' },
  { label: CATEGORY_LABELS.ETF, value: 'ETF' },
];

export { getCategoryLabel };
