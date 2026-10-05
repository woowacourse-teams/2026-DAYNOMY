import { request, requestWithCsrf } from '../../api/client';
import type {
  StockRelatedContentRequest,
  StockRelatedContentSource,
  StockRelatedContentsResponse,
  YouTubeSearchItem,
} from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isSource(value: unknown): value is StockRelatedContentSource {
  return (
    value === 'YOUTUBE' || value === 'THREADS' || value === 'INTERNAL_NEWS' || value === 'OTHER'
  );
}

function isSafeContentUrl(value: unknown): value is string {
  if (typeof value !== 'string' || value.trim() === '' || value.trim().startsWith('//')) {
    return false;
  }

  try {
    const parsedUrl = new URL(value, 'https://daynomy.local');
    return parsedUrl.protocol === 'http:' || parsedUrl.protocol === 'https:';
  } catch {
    return false;
  }
}

function isRelatedContent(value: unknown) {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.assetId === 'number' &&
    isSource(value.sourceType) &&
    typeof value.title === 'string' &&
    isSafeContentUrl(value.url) &&
    (value.imageUrl === null || typeof value.imageUrl === 'string') &&
    (value.createdAt === null || typeof value.createdAt === 'string')
  );
}

function isContentsResponse(value: unknown): value is StockRelatedContentsResponse {
  return (
    isRecord(value) &&
    typeof value.assetId === 'number' &&
    typeof value.assetCode === 'string' &&
    typeof value.assetName === 'string' &&
    Array.isArray(value.contents) &&
    value.contents.every(isRelatedContent)
  );
}

function isYouTubeSearchItem(value: unknown): value is YouTubeSearchItem {
  return (
    isRecord(value) &&
    typeof value.title === 'string' &&
    typeof value.url === 'string' &&
    typeof value.channelTitle === 'string' &&
    typeof value.publishedAt === 'string' &&
    typeof value.thumbnailUrl === 'string'
  );
}

function isYouTubeSearchResponse(value: unknown): value is { items: YouTubeSearchItem[] } {
  return isRecord(value) && Array.isArray(value.items) && value.items.every(isYouTubeSearchItem);
}

function assertContentsResponse(value: unknown) {
  if (!isContentsResponse(value)) {
    throw new Error('종목 관련 자료 API 응답 형식이 올바르지 않습니다.');
  }
  return value;
}

export async function getStockRelatedContents(assetId: number, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/assets/${assetId}/contents`, { signal });
  return assertContentsResponse(response);
}

export async function getAdminStockRelatedContents(assetId: number, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/admin/assets/${assetId}/contents`, { signal });
  return assertContentsResponse(response);
}

export async function searchAdminYouTubeVideos(
  assetId: number,
  keyword: string,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ keyword });
  const response = await request<unknown>(
    `/api/admin/assets/${assetId}/contents/youtube-search?${params.toString()}`,
    { signal },
  );
  if (!isYouTubeSearchResponse(response)) {
    throw new Error('YouTube 검색 API 응답 형식이 올바르지 않습니다.');
  }
  return response.items;
}

export async function createAdminStockRelatedContent(
  assetId: number,
  content: StockRelatedContentRequest,
) {
  const response = await requestWithCsrf<unknown>(`/api/admin/assets/${assetId}/contents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(content),
  });

  if (!isRelatedContent(response)) {
    throw new Error('종목 관련 자료 등록 API 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

export async function deleteAdminStockRelatedContent(assetId: number, contentId: number) {
  await requestWithCsrf<void>(`/api/admin/assets/${assetId}/contents/${contentId}`, {
    method: 'DELETE',
  });
}
