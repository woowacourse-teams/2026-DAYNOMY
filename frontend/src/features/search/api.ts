import axios from 'axios';
import { toApiError } from '../../api/error';
import type { NewsCategory } from '../news/newslist/types';
import { toNewsPage } from './mapper';
import { isNewsSearchResponse } from './response';

export function buildNewsSearchUrl(keyword: string, category: NewsCategory, page = 1, size = 10) {
  const params = new URLSearchParams({
    q: keyword,
    page: String(page),
    size: String(size),
  });

  if (category !== 'ALL') params.set('category', category);

  return `/api/search/news?${params.toString()}`;
}

export async function searchNews(keyword: string, category: NewsCategory, page = 1, size = 10) {
  try {
    const { data } = await axios.get<unknown>(buildNewsSearchUrl(keyword, category, page, size));

    if (!isNewsSearchResponse(data)) throw new Error('검색 API 응답 형식이 올바르지 않습니다.');

    return toNewsPage(data);
  } catch (error) {
    const apiError = toApiError(error, '검색 결과를 불러오지 못했습니다.');
    if (apiError) throw apiError;
    throw error;
  }
}
