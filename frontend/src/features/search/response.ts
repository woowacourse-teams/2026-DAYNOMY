import { isCategory, type Category } from '../news/newslist/types';
import type { FeedItem } from '../news/issue/feed';

export type ContentSearchSource = 'ALL' | FeedItem['source'];
export type ContentSearchSort = 'LATEST' | 'POPULAR';

type BaseItem = { id: number; title: string; date: string | null };
export type ContentSearchItemResponse =
  | (BaseItem & {
      kind: 'NEWS';
      source: 'DAYNOMY';
      category: Category;
      imageUrl: string | null;
    })
  | (BaseItem & {
      kind: 'PERSON_POST';
      source: 'X' | 'THREADS';
      url: string;
      personId: number;
      personName: string;
    })
  | (BaseItem & {
      kind: 'ASSET_CONTENT';
      source: 'X' | 'THREADS' | 'YOUTUBE';
      url: string;
      assetId: number;
      imageUrl: string | null;
      publishedDate: string | null;
    });

export type ContentSearchResponse = {
  items: ContentSearchItemResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export function isContentSearchSource(value: unknown): value is ContentSearchSource {
  return (
    value === 'ALL' ||
    value === 'DAYNOMY' ||
    value === 'X' ||
    value === 'THREADS' ||
    value === 'YOUTUBE'
  );
}

function isItem(value: unknown): value is ContentSearchItemResponse {
  if (!value || typeof value !== 'object') return false;
  const item = value as Record<string, unknown>;
  if (
    !Number.isInteger(item.id) ||
    typeof item.title !== 'string' ||
    (item.date !== null && typeof item.date !== 'string')
  )
    return false;
  if (item.kind === 'NEWS')
    return (
      item.source === 'DAYNOMY' &&
      isCategory(item.category) &&
      (item.imageUrl === null || typeof item.imageUrl === 'string')
    );
  if (item.kind === 'PERSON_POST')
    return (
      (item.source === 'X' || item.source === 'THREADS') &&
      typeof item.url === 'string' &&
      Number.isInteger(item.personId) &&
      typeof item.personName === 'string'
    );
  if (item.kind === 'ASSET_CONTENT')
    return (
      (item.source === 'X' || item.source === 'THREADS' || item.source === 'YOUTUBE') &&
      typeof item.url === 'string' &&
      Number.isInteger(item.assetId) &&
      (item.imageUrl === null || typeof item.imageUrl === 'string') &&
      (item.publishedDate === null || typeof item.publishedDate === 'string')
    );
  return false;
}

export function isContentSearchResponse(value: unknown): value is ContentSearchResponse {
  if (!value || typeof value !== 'object') return false;
  const page = value as Record<string, unknown>;
  return (
    Array.isArray(page.items) &&
    page.items.every(isItem) &&
    Number.isInteger(page.page) &&
    Number.isInteger(page.size) &&
    Number.isInteger(page.totalElements) &&
    Number.isInteger(page.totalPages)
  );
}
