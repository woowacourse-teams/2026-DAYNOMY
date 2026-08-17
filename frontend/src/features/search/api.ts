import { isCategory } from '../news/newslist/types';
import type { NewsArticle, NewsCategory, NewsPage } from '../news/newslist/types';

type SearchNewsResponse = { body?: unknown };

function isNewsArticle(value: unknown): value is NewsArticle {
  if (!value || typeof value !== 'object') return false;

  const article = value as Record<string, unknown>;
  return (
    (typeof article.id === 'number' || typeof article.id === 'string') &&
    typeof article.title === 'string' &&
    isCategory(article.category) &&
    (article.publishedAt === undefined || typeof article.publishedAt === 'string')
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
  const response = await fetch(buildNewsSearchUrl(keyword, category, page, size));

  if (!response.ok) throw new Error('검색 결과를 불러오지 못했습니다.');

  const contentType = response.headers.get('content-type') ?? '';

  if (!contentType.includes('application/json')) {
    throw new Error('검색 API 응답 형식이 올바르지 않습니다.');
  }

  const data = (await response.json()) as SearchNewsResponse;

  if (!isNewsPage(data.body)) {
    throw new Error('검색 API 응답 형식이 올바르지 않습니다.');
  }

  return data.body;
}
