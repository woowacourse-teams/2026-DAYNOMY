import axios from 'axios';
import { toApiError } from '../../api/error';
import { isCategory } from '../news/newslist/types';
import type { NewsArticle, NewsCategory, NewsPage } from '../news/newslist/types';

export const SEARCH_ERROR_CODES = {
  SEARCH_KEYWORD_REQUIRED: 'SEARCH_KEYWORD_REQUIRED',
  SEARCH_INVALID_KEYWORD: 'SEARCH_INVALID_KEYWORD',
  SEARCH_INVALID_CATEGORY: 'SEARCH_INVALID_CATEGORY',
  SEARCH_INVALID_PAGE_CONDITION: 'SEARCH_INVALID_PAGE_CONDITION',
} as const;

function isNewsArticle(value: unknown): value is NewsArticle {
  if (!value || typeof value !== 'object') return false;

  const article = value as Record<string, unknown>;
  return (
    typeof article.id === 'number' &&
    typeof article.title === 'string' &&
    (article.description === null || typeof article.description === 'string') &&
    (article.imageUrl === null || typeof article.imageUrl === 'string') &&
    isCategory(article.category) &&
    typeof article.publishedAt === 'string'
  );
}

function isNewsPage(value: unknown): value is NewsPage {
  if (!value || typeof value !== 'object') return false;

  const page = value as Record<string, unknown>;
  return (
    Array.isArray(page.content) &&
    page.content.every(isNewsArticle) &&
    Number.isInteger(page.page) &&
    Number.isInteger(page.size) &&
    Number.isInteger(page.totalElements) &&
    Number.isInteger(page.totalPages)
  );
}

export function buildNewsSearchUrl(keyword: string, category: NewsCategory, page = 0, size = 10) {
  const params = new URLSearchParams({
    q: keyword,
    page: String(page),
    size: String(size),
  });

  if (category !== 'ALL') params.set('category', category);

  return `/api/search/news?${params.toString()}`;
}

export async function searchNews(keyword: string, category: NewsCategory, page = 0, size = 10) {
  try {
    const { data } = await axios.get<unknown>(buildNewsSearchUrl(keyword, category, page, size));

    if (!isNewsPage(data)) throw new Error('검색 API 응답 형식이 올바르지 않습니다.');

    return data;
  } catch (error) {
    const apiError = toApiError(error, '검색 결과를 불러오지 못했습니다.');
    if (apiError) throw apiError;
    throw error;
  }
}
