import type { NewsArticle, NewsCategoryOption } from './types';

export const NEWS_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  { label: '부동산', value: 'REAL_ESTATE' },
  { label: '예금·적금', value: 'DEPOSIT_SAVINGS' },
  { label: '주식', value: 'STOCK' },
  { label: 'ETF', value: 'ETF' },
  { label: '채권', value: 'BOND' },
  { label: '연금', value: 'PENSION' },
  { label: '외화·환율', value: 'FOREIGN_EXCHANGE' },
  { label: '가상자산', value: 'VIRTUAL_ASSET' },
  { label: '금', value: 'GOLD' },
];

const NEWS_CATEGORY_LABELS: Record<string, string> = {
  ALL: '전체',
  REAL_ESTATE: '부동산',
  DEPOSIT_SAVINGS: '예금·적금',
  STOCK: '주식',
  ETF: 'ETF',
  BOND: '채권',
  PENSION: '연금',
  FOREIGN_EXCHANGE: '외화·환율',
  VIRTUAL_ASSET: '가상자산',
  GOLD: '금',
};

const emptyTodayNews: NewsArticle = {
  id: '',
  title: '오늘의 뉴스가 없습니다',
  summary: '오늘 발행된 뉴스가 있으면 이 영역에 표시됩니다.',
  category: 'ALL',
  publishedAt: '',
  source: '',
};

export function getCategoryLabel(value: string) {
  return NEWS_CATEGORY_LABELS[value] ?? value;
}

export function getEmptyTodayNews() {
  return emptyTodayNews;
}
