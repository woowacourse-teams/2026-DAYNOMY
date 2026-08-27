import { toNewsListItem } from './types';
import type { NewsCategory, NewsListItem, NewsListItemResponse, NewsPage } from './types';

type NewsPageResponse = {
  items: NewsListItemResponse[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};

const DEFAULT_PAGE_SIZE = 10;

function normalizeNewsPage(data: NewsPageResponse): NewsPage {
  const content = data.items.map(toNewsListItem);
  return {
    content,
    page: data.page,
    size: data.size,
    totalPages: data.totalPages,
    totalElements: data.totalElements,
  };
}

export async function getNews(
  category: NewsCategory = 'ALL',
  page = 1,
  size = DEFAULT_PAGE_SIZE,
): Promise<NewsPage> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size),
  });

  if (category !== 'ALL') {
    params.set('category', category);
  }

  const response = await fetch(`/api/news?${params.toString()}`);

  if (!response.ok) {
    throw new Error('뉴스 목록을 불러오지 못했습니다.');
  }

  const contentType = response.headers.get('content-type') ?? '';

  if (!contentType.includes('application/json')) {
    throw new Error('뉴스 API 응답 형식이 올바르지 않습니다.');
  }

  const data = (await response.json()) as NewsPageResponse;

  return normalizeNewsPage(data);
}

export async function getTodayNews(): Promise<NewsListItem> {
  const response = await fetch('/api/news/today');

  if (!response.ok) {
    throw new Error('오늘의 뉴스를 불러오지 못했습니다.');
  }

  const data = (await response.json()) as NewsListItemResponse | null;

  if (!data) {
    throw new Error('오늘의 뉴스를 불러오지 못했습니다.');
  }

  return toNewsListItem(data);
}
