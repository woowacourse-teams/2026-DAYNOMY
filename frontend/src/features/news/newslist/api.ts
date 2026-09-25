import { isCategory, toNewsListItem } from './types';
import type { NewsCategory, NewsListItemResponse, NewsPage } from './types';

type NewsPageResponse = {
  items: NewsListItemResponse[];
  page: number;
  size: number;
  totalPages: number;
  totalElements: number;
  hasNext: boolean;
};

const DEFAULT_PAGE_SIZE = 6;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNewsListItemResponse(value: unknown): value is NewsListItemResponse {
  return (
    isRecord(value) &&
    Number.isInteger(value.id) &&
    typeof value.title === 'string' &&
    isCategory(value.category) &&
    (value.imageUrl === null || typeof value.imageUrl === 'string') &&
    (value.publishedAt === null || typeof value.publishedAt === 'string')
  );
}

function isNewsPageResponse(value: unknown): value is NewsPageResponse {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isNewsListItemResponse) &&
    Number.isInteger(value.page) &&
    Number.isInteger(value.size) &&
    Number.isInteger(value.totalPages) &&
    Number.isInteger(value.totalElements) &&
    typeof value.hasNext === 'boolean'
  );
}

function normalizeNewsPage(data: unknown): NewsPage {
  if (!isNewsPageResponse(data)) {
    throw new Error('이슈 API 응답 형식이 올바르지 않습니다.');
  }

  return {
    content: data.items.map(toNewsListItem),
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
    throw new Error('이슈 목록을 불러오지 못했습니다.');
  }

  const contentType = response.headers.get('content-type') ?? '';

  if (!contentType.includes('application/json')) {
    throw new Error('이슈 API 응답 형식이 올바르지 않습니다.');
  }

  const data: unknown = await response.json();

  return normalizeNewsPage(data);
}

export async function getTodayNews(): Promise<NewsPage> {
  const response = await fetch('/api/news/today');

  if (!response.ok) {
    throw new Error('오늘의 이슈를 불러오지 못했습니다.');
  }

  const data: unknown = await response.json();

  return normalizeNewsPage(data);
}
