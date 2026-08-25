import { toNewsListItem } from '../news/newslist/types';
import type { NewsPage } from '../news/newslist/types';
import type { NewsSearchResponse } from './types/response';

export function toNewsPage(response: NewsSearchResponse): NewsPage {
  return {
    content: response.content.map(toNewsListItem),
    page: response.page,
    size: response.size,
    totalElements: response.totalElements,
    totalPages: response.totalPages,
  };
}
