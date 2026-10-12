import { request, requestWithCsrf } from '../../api/client';
import { isCategory } from '../news/newslist/types';
import type { StockSearchItem } from '../portfolio/types';
import type {
  AdminNewsFilterCategory,
  AdminNewsFilterStatus,
  AdminNewsFormValues,
  AdminNewsImageSource,
  AdminNewsImageSourceType,
  AdminNewsListItemResponse,
  AdminNewsPageResponse,
  AdminNewsResponse,
  AdminNewsSource,
  AdminNewsStatus,
  AdminNewsGenerationResponse,
  AdminWikimediaImageCandidate,
  AdminStockPriceSyncResponse,
  AdminStockSyncResponse,
} from './types';

const DEFAULT_PAGE_SIZE = 15;
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function isAdminNewsStatus(value: unknown): value is AdminNewsStatus {
  return value === 'DRAFT' || value === 'PUBLISHED' || value === 'REJECTED' || value === 'DELETED';
}

function isStockSearchItem(value: unknown): value is StockSearchItem {
  return (
    isRecord(value) &&
    typeof value.assetId === 'number' &&
    typeof value.assetCode === 'string' &&
    typeof value.name === 'string' &&
    (value.category === 'STOCK' || value.category === 'ETF') &&
    (value.market === 'KOSPI' || value.market === 'KOSDAQ')
  );
}

function isAdminNewsSource(value: unknown): value is AdminNewsSource {
  return isRecord(value) && typeof value.name === 'string' && typeof value.url === 'string';
}

function isAdminNewsImageSource(value: unknown): value is AdminNewsImageSource {
  return (
    isRecord(value) &&
    typeof value.name === 'string' &&
    typeof value.url === 'string' &&
    typeof value.author === 'string' &&
    typeof value.license === 'string' &&
    typeof value.licenseUrl === 'string' &&
    isAdminNewsImageSourceType(value.type)
  );
}

function isAdminNewsImageSourceType(value: unknown): value is AdminNewsImageSourceType {
  return (
    value === 'NONE' || value === 'AI_GENERATED' || value === 'WIKIMEDIA' || value === 'MANUAL'
  );
}

function isWikimediaImageCandidate(value: unknown): value is AdminWikimediaImageCandidate {
  return (
    isRecord(value) &&
    typeof value.title === 'string' &&
    typeof value.thumbnailUrl === 'string' &&
    typeof value.sourceUrl === 'string' &&
    typeof value.author === 'string' &&
    typeof value.license === 'string' &&
    typeof value.licenseUrl === 'string' &&
    typeof value.width === 'number' &&
    typeof value.height === 'number'
  );
}

function isAdminNewsListItem(value: unknown): value is AdminNewsListItemResponse {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.title === 'string' &&
    isNullableString(value.imageUrl) &&
    Array.isArray(value.sources) &&
    value.sources.every(isAdminNewsSource) &&
    isCategory(value.category) &&
    isNullableString(value.publishedAt) &&
    isAdminNewsStatus(value.status) &&
    typeof value.createdAt === 'string'
  );
}

function isAdminNewsPageResponse(value: unknown): value is AdminNewsPageResponse {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isAdminNewsListItem) &&
    typeof value.page === 'number' &&
    typeof value.size === 'number' &&
    typeof value.totalPages === 'number' &&
    typeof value.totalElements === 'number' &&
    typeof value.hasNext === 'boolean'
  );
}

function isAdminNewsResponse(value: unknown): value is AdminNewsResponse {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.title === 'string' &&
    typeof value.content === 'string' &&
    isNullableString(value.imageUrl) &&
    (value.imageSource === undefined || isAdminNewsImageSource(value.imageSource)) &&
    Array.isArray(value.sources) &&
    value.sources.every(isAdminNewsSource) &&
    isCategory(value.category) &&
    isNullableString(value.publishedAt) &&
    isAdminNewsStatus(value.status) &&
    (value.relatedAssets === undefined ||
      (Array.isArray(value.relatedAssets) && value.relatedAssets.every(isStockSearchItem)))
  );
}

function isWikimediaImageSearchResponse(
  value: unknown,
): value is { items: AdminWikimediaImageCandidate[] } {
  return (
    isRecord(value) && Array.isArray(value.items) && value.items.every(isWikimediaImageCandidate)
  );
}

function isAdminNewsGenerationResponse(value: unknown): value is AdminNewsGenerationResponse {
  return isRecord(value) && typeof value.savedCount === 'number';
}

function isAdminStockSyncResponse(value: unknown): value is AdminStockSyncResponse {
  return (
    isRecord(value) &&
    typeof value.baseDate === 'string' &&
    typeof value.syncedCount === 'number' &&
    typeof value.createdCount === 'number' &&
    typeof value.updatedCount === 'number' &&
    typeof value.delistedCount === 'number'
  );
}

function isAdminStockPriceSyncResponse(value: unknown): value is AdminStockPriceSyncResponse {
  return (
    isRecord(value) &&
    typeof value.baseDate === 'string' &&
    typeof value.receivedCount === 'number' &&
    typeof value.createdCount === 'number' &&
    typeof value.updatedCount === 'number' &&
    typeof value.skippedCount === 'number'
  );
}

function assertResponse<T>(value: unknown, isValid: (value: unknown) => value is T): T {
  if (!isValid(value)) {
    throw new Error('관리자 API 응답 형식이 올바르지 않습니다.');
  }

  return value;
}

export async function getAdminNews(
  page = 1,
  status: AdminNewsFilterStatus = 'ALL',
  category: AdminNewsFilterCategory = 'ALL',
  signal?: AbortSignal,
  keyword = '',
): Promise<AdminNewsPageResponse> {
  const params = new URLSearchParams({
    page: String(page),
    size: String(DEFAULT_PAGE_SIZE),
  });

  if (status !== 'ALL') {
    params.set('status', status);
  }

  if (category !== 'ALL') {
    params.set('category', category);
  }

  if (keyword.trim()) {
    params.set('q', keyword.trim());
  }

  const response = await request<unknown>(`/api/admin/news?${params.toString()}`, { signal });

  return assertResponse(response, isAdminNewsPageResponse);
}

export async function getAdminNewsDetail(id: number, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/admin/news/${id}`, { signal });

  return assertResponse(response, isAdminNewsResponse);
}

function createNewsFormData(values: AdminNewsFormValues, image: File | null) {
  const formData = new FormData();
  const requestBlob = new Blob([JSON.stringify(values)], { type: 'application/json' });
  formData.append('request', requestBlob);

  if (image) {
    formData.append('image', image);
  }

  return formData;
}

export function isSupportedNewsImage(file: File) {
  return IMAGE_TYPES.has(file.type) && file.size <= 5 * 1024 * 1024;
}

export async function createAdminNews(values: AdminNewsFormValues, image: File | null) {
  const response = await requestWithCsrf<unknown>('/api/admin/news', {
    method: 'POST',
    body: createNewsFormData(values, image),
  });

  return assertResponse(response, isAdminNewsResponse);
}

export async function searchWikimediaImages(
  keyword: string,
  signal?: AbortSignal,
): Promise<AdminWikimediaImageCandidate[]> {
  const params = new URLSearchParams({ keyword: keyword.trim() });
  const response = await request<unknown>(`/api/admin/news/image-search?${params.toString()}`, {
    signal,
  });
  return assertResponse(response, isWikimediaImageSearchResponse).items;
}

export async function updateAdminNews(id: number, values: AdminNewsFormValues, image: File | null) {
  const response = await requestWithCsrf<unknown>(`/api/admin/news/${id}`, {
    method: 'PUT',
    body: createNewsFormData(values, image),
  });

  return assertResponse(response, isAdminNewsResponse);
}

export async function publishAdminNews(id: number) {
  const response = await requestWithCsrf<unknown>(`/api/admin/news/${id}/publish`, {
    method: 'POST',
  });

  return assertResponse(response, isAdminNewsResponse);
}

export async function rejectAdminNews(id: number) {
  const response = await requestWithCsrf<unknown>(`/api/admin/news/${id}/reject`, {
    method: 'POST',
  });

  return assertResponse(response, isAdminNewsResponse);
}

export async function generateAdminNewsImage(id: number) {
  const response = await requestWithCsrf<unknown>(`/api/admin/news/${id}/generate-image`, {
    method: 'POST',
  });

  return assertResponse(response, isAdminNewsResponse);
}

export async function generateAdminEconomyNewsDrafts(): Promise<AdminNewsGenerationResponse> {
  const response = await requestWithCsrf<unknown>('/api/admin/news/generate/economy', {
    method: 'POST',
  });

  return assertResponse(response, isAdminNewsGenerationResponse);
}

export async function deleteAdminNews(id: number) {
  return requestWithCsrf<void>(`/api/admin/news/${id}`, { method: 'DELETE' });
}

export async function syncAdminStocks(): Promise<AdminStockSyncResponse> {
  const response = await requestWithCsrf<unknown>('/api/admin/stocks/sync', {
    method: 'POST',
  });

  return assertResponse(response, isAdminStockSyncResponse);
}

export async function syncAdminStockPrices(): Promise<AdminStockPriceSyncResponse> {
  const response = await requestWithCsrf<unknown>('/api/admin/stocks/prices/sync', {
    method: 'POST',
  });

  return assertResponse(response, isAdminStockPriceSyncResponse);
}
