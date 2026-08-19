import type { NewsArticle, NewsCategoryOption } from './types';

export const NEWS_CATEGORIES: NewsCategoryOption[] = [
  { label: '전체', value: 'ALL' },
  { label: '방안·개편', value: 'POLICY' },
  { label: '부동산', value: 'REAL_ESTATE' },
  { label: '금리', value: 'INTEREST_RATE' },
  { label: '환율', value: 'EXCHANGE_RATE' },
  { label: '기업 발표', value: 'COMPANY' },
  { label: '가상자산', value: 'VIRTUAL_ASSET' },
];

const NEWS_CATEGORY_LABELS: Record<string, string> = {
  ALL: '전체',
  POLICY: '방안·개편',
  REAL_ESTATE: '부동산',
  INTEREST_RATE: '금리',
  EXCHANGE_RATE: '환율',
  COMPANY: '기업 발표',
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
