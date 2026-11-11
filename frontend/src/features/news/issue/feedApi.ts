import axios from 'axios';
import { toApiError } from '../../../api/error';
import { isCategory, type Category } from '../newslist/types';
import type { FeedItem } from './feed';

export type NewsFeedSource = 'ALL' | FeedItem['source'];
export type NewsFeedSort = 'LATEST' | 'POPULAR';
export type Industry = { code: string; name: string };

type BaseItem = { id: number; title: string; date: string | null };
type NewsFeedItemResponse =
  | (BaseItem & {
      kind: 'NEWS';
      source: 'DAYNOMY';
      category: Category;
      imageUrl: string | null;
    })
  | (BaseItem & {
      kind: 'PERSON_POST';
      source: 'X' | 'THREADS' | 'YOUTUBE';
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

type NewsFeedResponse = {
  items: NewsFeedItemResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

function isNewsFeedItem(value: unknown): value is NewsFeedItemResponse {
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
      (item.source === 'X' || item.source === 'THREADS' || item.source === 'YOUTUBE') &&
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

function isNewsFeedResponse(value: unknown): value is NewsFeedResponse {
  if (!value || typeof value !== 'object') return false;
  const page = value as Record<string, unknown>;
  return (
    Array.isArray(page.items) &&
    page.items.every(isNewsFeedItem) &&
    Number.isInteger(page.page) &&
    Number.isInteger(page.size) &&
    Number.isInteger(page.totalElements) &&
    Number.isInteger(page.totalPages)
  );
}

function isIndustriesResponse(value: unknown): value is { industries: Industry[] } {
  if (!value || typeof value !== 'object') return false;
  const response = value as Record<string, unknown>;
  return (
    Array.isArray(response.industries) &&
    response.industries.every(
      (industry) =>
        industry !== null &&
        typeof industry === 'object' &&
        typeof (industry as Record<string, unknown>).code === 'string' &&
        /^[A-Z][A-Z0-9_]{0,49}$/.test((industry as Record<string, unknown>).code as string) &&
        typeof (industry as Record<string, unknown>).name === 'string',
    )
  );
}

function toFeedItem(item: NewsFeedItemResponse): FeedItem {
  if (item.kind === 'NEWS')
    return {
      key: `news-${item.id}`,
      source: item.source,
      title: item.title,
      date: item.date,
      article: {
        id: item.id,
        title: item.title,
        category: item.category,
        imageUrl: item.imageUrl,
        publishedAt: item.date,
      },
    };
  if (item.kind === 'PERSON_POST')
    return {
      key: `person-${item.personId}-${item.id}`,
      source: item.source,
      title: item.title,
      date: item.date,
      person: { id: item.personId, name: item.personName },
      post: {
        id: item.id,
        sourceType: item.source,
        title: item.title,
        url: item.url,
        createdAt: item.date,
      },
    };
  return {
    key: `content-${item.id}`,
    source: item.source,
    title: item.title,
    date: item.date,
    content: {
      id: item.id,
      assetId: item.assetId,
      sourceType: item.source,
      title: item.title,
      url: item.url,
      imageUrl: item.imageUrl,
      createdAt: item.date,
      publishedDate: item.publishedDate,
    },
  };
}

export function buildNewsFeedUrl(
  source: NewsFeedSource,
  page = 1,
  size = 8,
  sort: NewsFeedSort = 'LATEST',
  industry?: string,
) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (source !== 'ALL') params.set('source', source);
  if (sort === 'POPULAR') params.set('sort', sort);
  if (industry) params.set('industry', industry);
  return `/api/news/feed?${params.toString()}`;
}

export async function getIndustries(signal?: AbortSignal) {
  try {
    const { data } = await axios.get<unknown>('/api/industries', { signal });
    if (!isIndustriesResponse(data)) throw new Error('업종 응답 형식이 올바르지 않습니다.');
    return data.industries;
  } catch (error) {
    const apiError = toApiError(error, '업종을 불러오지 못했습니다.');
    if (apiError) throw apiError;
    throw error;
  }
}

export async function getNewsFeed(
  source: NewsFeedSource,
  page = 1,
  size = 8,
  sort: NewsFeedSort = 'LATEST',
  signal?: AbortSignal,
  industry?: string,
) {
  try {
    const { data } = await axios.get<unknown>(
      buildNewsFeedUrl(source, page, size, sort, industry),
      { signal },
    );
    if (!isNewsFeedResponse(data)) throw new Error('소식 피드 응답 형식이 올바르지 않습니다.');
    return { ...data, items: data.items.map(toFeedItem) };
  } catch (error) {
    const apiError = toApiError(error, '소식을 불러오지 못했습니다.');
    if (apiError) throw apiError;
    throw error;
  }
}
