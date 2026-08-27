import { CATEGORY_LABELS, getCategoryLabel } from './types';
import type { NewsCategoryOption, NewsListItem } from './types';

export const NEWS_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  ...Object.entries(CATEGORY_LABELS).map(([value, label]) => ({
    label,
    value: value as keyof typeof CATEGORY_LABELS,
  })),
];

const emptyTodayNews: NewsListItem = {
  id: 0,
  title: '오늘의 뉴스가 없습니다',
  description: '오늘 발행된 뉴스가 있으면 이 영역에 표시됩니다.',
  category: 'ALL',
  imageUrl: null,
  publishedAt: null,
};

export function getEmptyTodayNews() {
  return emptyTodayNews;
}

export { getCategoryLabel };
