import { request } from '../../../api/client';
import { isRelatedContent } from '../../stock-content/api';
import type { ExternalContent } from './feed';

type ContentPage = { items: ExternalContent[]; page: number; totalPages: number };
export async function getPublicContents(
  page = 1,
  size = 12,
  signal?: AbortSignal,
): Promise<ContentPage> {
  const result = await request<unknown>(`/api/contents?page=${page}&size=${size}`, { signal });
  if (!result || typeof result !== 'object') throw new Error('소식 응답 오류');
  const data = result as Record<string, unknown>;
  if (
    !Array.isArray(data.items) ||
    !data.items.every(
      (item) => isRelatedContent(item) && ['YOUTUBE', 'THREADS', 'X'].includes(item.sourceType),
    ) ||
    data.page !== page ||
    !Number.isInteger(data.totalPages) ||
    (data.totalPages as number) < 0
  )
    throw new Error('소식 응답 오류');
  return data as ContentPage;
}

export async function getAllContents(signal?: AbortSignal) {
  let current = await getPublicContents(1, 100, signal);
  const items = [...current.items];
  while (current.page < current.totalPages && current.items.length) {
    current = await getPublicContents(current.page + 1, 100, signal);
    items.push(...current.items);
  }
  return { ...current, items };
}
