import axios from 'axios';
import { toApiError } from '../../api/error';
import { toContentSearchPage } from './mapper';
import { isContentSearchResponse, type ContentSearchSource } from './response';

export function buildContentSearchUrl(
  keyword: string,
  source: ContentSearchSource,
  page = 1,
  size = 12,
) {
  const params = new URLSearchParams({ q: keyword, page: String(page), size: String(size) });
  if (source !== 'ALL') params.set('source', source);
  return `/api/search/contents?${params.toString()}`;
}

export async function searchContents(
  keyword: string,
  source: ContentSearchSource,
  page = 1,
  size = 12,
) {
  try {
    const { data } = await axios.get<unknown>(buildContentSearchUrl(keyword, source, page, size));
    if (!isContentSearchResponse(data)) throw new Error('검색 API 응답 형식이 올바르지 않습니다.');
    return toContentSearchPage(data);
  } catch (error) {
    const apiError = toApiError(error, '검색 결과를 불러오지 못했습니다.');
    if (apiError) throw apiError;
    throw error;
  }
}
