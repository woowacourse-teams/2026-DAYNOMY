import { isCategory } from '../news/newslist/types';
import type { NewsListItemResponse } from '../news/newslist/types';

export type NewsSearchResponse = {
  content: NewsListItemResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function isNewsListItemResponse(value: unknown): value is NewsListItemResponse {
  if (!value || typeof value !== 'object') return false;

  const article = value as Record<string, unknown>;
  return (
    typeof article.id === 'number' &&
    typeof article.title === 'string' &&
    (article.description === null || typeof article.description === 'string') &&
    (article.imageUrl === null || typeof article.imageUrl === 'string') &&
    isCategory(article.category) &&
    (article.publishedAt === null || typeof article.publishedAt === 'string')
  );
}

export function isNewsSearchResponse(value: unknown): value is NewsSearchResponse {
  if (!value || typeof value !== 'object') return false;

  const page = value as Record<string, unknown>;
  return (
    Array.isArray(page.content) &&
    page.content.every(isNewsListItemResponse) &&
    Number.isInteger(page.page) &&
    Number.isInteger(page.size) &&
    Number.isInteger(page.totalElements) &&
    Number.isInteger(page.totalPages)
  );
}
