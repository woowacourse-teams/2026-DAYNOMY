import { CATEGORY_LABELS, getCategoryLabel } from './types';
import type { NewsCategoryOption, NewsListItemResponse } from './types';

export const NEWS_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  { label: CATEGORY_LABELS.STOCK, value: 'STOCK' },
  { label: CATEGORY_LABELS.REAL_ESTATE, value: 'REAL_ESTATE' },
  { label: CATEGORY_LABELS.GOLD, value: 'GOLD' },
  { label: CATEGORY_LABELS.BOND, value: 'BOND' },
  { label: CATEGORY_LABELS.ECONOMY, value: 'ECONOMY' },
  { label: CATEGORY_LABELS.DEPOSIT_SAVINGS, value: 'DEPOSIT_SAVINGS' },
  { label: CATEGORY_LABELS.ETF, value: 'ETF' },
  { label: CATEGORY_LABELS.PENSION, value: 'PENSION' },
  { label: CATEGORY_LABELS.FOREIGN_EXCHANGE, value: 'FOREIGN_EXCHANGE' },
  { label: CATEGORY_LABELS.VIRTUAL_ASSET, value: 'VIRTUAL_ASSET' },
];

const emptyTodayNews: NewsListItemResponse = {
  id: '',
  title: '오늘의 뉴스가 없습니다',
  description: '오늘 발행된 뉴스가 있으면 이 영역에 표시됩니다.',
  category: 'ALL',
  publishedAt: '',
  source: '',
};

export function getEmptyTodayNews() {
  return emptyTodayNews;
}

export { getCategoryLabel };
