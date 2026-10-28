import { request, requestWithCsrf } from '../../api/client';
import { isCategory } from '../news/newslist/types';
import { isStock } from '../stocks/api';
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
  AdminStockPriceBackfillResponse,
  AdminLeagueRecalculationResponse,
  AdminContentCollectionBatchResult,
  AdminContentCollectionResult,
  AdminContentCollectionManualRequest,
  AdminContentCollectionSource,
  AdminContentCollectionSourceRequest,
  AdminContentCollectionReview,
  AdminContentCollectionReviewPage,
  AdminContentCollectionRematchResult,
  ContentCollectionSourceType,
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
      (Array.isArray(value.relatedAssets) && value.relatedAssets.every(isStock)))
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
    (value.baseDate === null || typeof value.baseDate === 'string') &&
    typeof value.receivedCount === 'number' &&
    typeof value.createdCount === 'number' &&
    typeof value.updatedCount === 'number' &&
    typeof value.skippedCount === 'number' &&
    Number.isInteger(value.synchronizedDateCount) &&
    Number(value.synchronizedDateCount) >= 0 &&
    Number.isInteger(value.pendingDateCount) &&
    Number(value.pendingDateCount) >= 0 &&
    Number.isInteger(value.failedDateCount) &&
    Number(value.failedDateCount) >= 0
  );
}

function isContentCollectionSourceType(value: unknown): value is ContentCollectionSourceType {
  return value === 'YOUTUBE' || value === 'THREADS' || value === 'X';
}

function isAdminContentCollectionSource(value: unknown): value is AdminContentCollectionSource {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.id) &&
    Number(value.id) > 0 &&
    typeof value.name === 'string' &&
    isContentCollectionSourceType(value.sourceType) &&
    typeof value.sourceUrl === 'string' &&
    (value.assetId === null ||
      (Number.isSafeInteger(value.assetId) && Number(value.assetId) > 0)) &&
    isNullableString(value.assetName) &&
    typeof value.enabled === 'boolean' &&
    isNullableString(value.lastCollectedAt)
  );
}

function isNonNegativeInteger(value: unknown) {
  return Number.isSafeInteger(value) && Number(value) >= 0;
}

function isAdminContentCollectionResult(value: unknown): value is AdminContentCollectionResult {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.sourceId) &&
    Number(value.sourceId) > 0 &&
    ['fetchedCount', 'savedCount', 'duplicateCount', 'linkedCount'].every((key) =>
      isNonNegativeInteger(value[key]),
    )
  );
}

function isAdminContentCollectionBatchResult(
  value: unknown,
): value is AdminContentCollectionBatchResult {
  return (
    isRecord(value) &&
    [
      'sourceCount',
      'successCount',
      'failureCount',
      'fetchedCount',
      'savedCount',
      'duplicateCount',
      'linkedCount',
    ].every((key) => isNonNegativeInteger(value[key]))
  );
}

function isAdminContentCollectionReview(value: unknown): value is AdminContentCollectionReview {
  return (
    isRecord(value) &&
    Number.isSafeInteger(value.id) &&
    Number(value.id) > 0 &&
    typeof value.sourceName === 'string' &&
    isContentCollectionSourceType(value.sourceType) &&
    typeof value.title === 'string' &&
    isNullableString(value.description) &&
    typeof value.url === 'string' &&
    isNullableString(value.imageUrl) &&
    isNullableString(value.publishedDate) &&
    Array.isArray(value.suggestedAssets) &&
    value.suggestedAssets.every(
      (asset) =>
        isRecord(asset) &&
        Number.isSafeInteger(asset.id) &&
        Number(asset.id) > 0 &&
        typeof asset.name === 'string' &&
        typeof asset.assetCode === 'string',
    ) &&
    typeof value.createdAt === 'string'
  );
}

function isAdminContentCollectionReviewPage(
  value: unknown,
): value is AdminContentCollectionReviewPage {
  return (
    isRecord(value) &&
    Array.isArray(value.items) &&
    value.items.every(isAdminContentCollectionReview) &&
    ['page', 'size', 'totalPages', 'totalElements'].every((key) =>
      isNonNegativeInteger(value[key]),
    ) &&
    typeof value.hasNext === 'boolean'
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

export async function generateAdminKoreanEconomicNewsDrafts(): Promise<AdminNewsGenerationResponse> {
  const response = await requestWithCsrf<unknown>('/api/admin/news/generate/economy/korea', {
    method: 'POST',
  });

  return assertResponse(response, isAdminNewsGenerationResponse);
}

export async function generateAdminUsEconomicNewsDrafts(): Promise<AdminNewsGenerationResponse> {
  const response = await requestWithCsrf<unknown>('/api/admin/news/generate/economy/us', {
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

export async function syncAdminUsStocks(): Promise<AdminStockSyncResponse> {
  const response = await requestWithCsrf<unknown>('/api/admin/stocks/us/sync', {
    method: 'POST',
  });
  return assertResponse(response, isAdminStockSyncResponse);
}

export async function syncAdminUsStockPrices(date = ''): Promise<AdminStockPriceSyncResponse> {
  if (date) {
    const parsed = new Date(date);
    const today = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/New_York',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date());
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      !Number.isFinite(parsed.getTime()) ||
      parsed.toISOString().slice(0, 10) !== date ||
      date >= today
    ) {
      throw new Error('뉴욕 기준 오늘 이전의 올바른 날짜를 선택해 주세요.');
    }
  }
  const query = date ? `?${new URLSearchParams({ date })}` : '';
  const response = await requestWithCsrf<unknown>(`/api/admin/stocks/us/prices/sync${query}`, {
    method: 'POST',
  });
  return assertResponse(response, isAdminStockPriceSyncResponse);
}

export async function backfillAdminStockPrices(from: string, to: string) {
  const dates = [from, to].map((value) => new Date(value));
  if (
    [from, to].some(
      (value, index) =>
        !/^\d{4}-\d{2}-\d{2}$/.test(value) ||
        !Number.isFinite(dates[index].getTime()) ||
        dates[index].toISOString().slice(0, 10) !== value,
    ) ||
    from > to ||
    dates[1].getTime() - dates[0].getTime() > 30 * 86_400_000
  )
    throw new Error('시작일과 종료일을 포함해 31일 이내의 올바른 기간을 선택해 주세요.');
  const query = new URLSearchParams({ from, to });
  const response = await requestWithCsrf<unknown>(`/api/admin/stocks/prices/backfill?${query}`, {
    method: 'POST',
  });
  return assertResponse(
    response,
    (value): value is AdminStockPriceBackfillResponse =>
      isRecord(value) &&
      value.from === from &&
      value.to === to &&
      [
        'synchronizedDateCount',
        'receivedCount',
        'createdCount',
        'updatedCount',
        'skippedCount',
      ].every((key) => Number.isSafeInteger(value[key]) && Number(value[key]) >= 0),
  );
}

export async function recalculateAdminLeague() {
  const response = await requestWithCsrf<unknown>('/api/admin/league/recalculate', {
    method: 'POST',
  });
  return assertResponse(
    response,
    (value): value is AdminLeagueRecalculationResponse =>
      isRecord(value) &&
      Number.isSafeInteger(value.calculatedCount) &&
      Number(value.calculatedCount) >= 0,
  );
}

export async function getAdminContentCollectionSources(signal?: AbortSignal) {
  const response = await request<unknown>('/api/admin/content-collection-sources', { signal });
  return assertResponse(
    response,
    (value): value is { sources: AdminContentCollectionSource[] } =>
      isRecord(value) &&
      Array.isArray(value.sources) &&
      value.sources.every(isAdminContentCollectionSource),
  ).sources;
}

export async function createAdminContentCollectionSource(
  values: AdminContentCollectionSourceRequest,
) {
  const response = await requestWithCsrf<unknown>('/api/admin/content-collection-sources', {
    method: 'POST',
    body: JSON.stringify(values),
    headers: { 'Content-Type': 'application/json' },
  });
  return assertResponse(response, isAdminContentCollectionSource);
}

export async function updateAdminContentCollectionSource(
  sourceId: number,
  values: AdminContentCollectionSourceRequest,
) {
  const response = await requestWithCsrf<unknown>(
    `/api/admin/content-collection-sources/${sourceId}`,
    {
      method: 'PUT',
      body: JSON.stringify(values),
      headers: { 'Content-Type': 'application/json' },
    },
  );
  return assertResponse(response, isAdminContentCollectionSource);
}

export async function collectAdminContentCollectionSource(sourceId: number) {
  const response = await requestWithCsrf<unknown>(
    `/api/admin/content-collection-sources/${sourceId}/collect`,
    { method: 'POST' },
  );
  return assertResponse(response, isAdminContentCollectionResult);
}

export async function registerAdminContentCollectionContent(
  sourceId: number,
  values: AdminContentCollectionManualRequest,
) {
  const response = await requestWithCsrf<unknown>(
    `/api/admin/content-collection-sources/${sourceId}/contents`,
    {
      method: 'POST',
      body: JSON.stringify(values),
      headers: { 'Content-Type': 'application/json' },
    },
  );
  return assertResponse(response, isAdminContentCollectionResult);
}

export async function collectAllAdminContentCollectionSources() {
  const response = await requestWithCsrf<unknown>(
    '/api/admin/content-collection-sources/collect-all',
    { method: 'POST' },
  );
  return assertResponse(response, isAdminContentCollectionBatchResult);
}

export async function getAdminContentCollectionReviews(signal?: AbortSignal) {
  const response = await request<unknown>('/api/admin/content-collection-reviews?page=1&size=100', {
    signal,
  });
  return assertResponse(response, isAdminContentCollectionReviewPage);
}

export async function approveAdminContentCollectionReview(
  contentId: number,
  assetIds: number[],
  publishedDate: string | null,
) {
  await requestWithCsrf<void>(`/api/admin/content-collection-reviews/${contentId}/approve`, {
    method: 'POST',
    body: JSON.stringify({ assetIds, publishedDate }),
    headers: { 'Content-Type': 'application/json' },
  });
}

export async function rejectAdminContentCollectionReview(contentId: number) {
  await requestWithCsrf<void>(`/api/admin/content-collection-reviews/${contentId}/reject`, {
    method: 'POST',
  });
}

export async function approveAdminContentCollectionReviews(
  items: { contentId: number; assetIds: number[]; publishedDate: string | null }[],
) {
  await requestWithCsrf<void>('/api/admin/content-collection-reviews/bulk-approve', {
    method: 'POST',
    body: JSON.stringify({ items }),
    headers: { 'Content-Type': 'application/json' },
  });
}

export async function rejectAdminContentCollectionReviews(contentIds: number[]) {
  await requestWithCsrf<void>('/api/admin/content-collection-reviews/bulk-reject', {
    method: 'POST',
    body: JSON.stringify({ contentIds }),
    headers: { 'Content-Type': 'application/json' },
  });
}

export async function rematchAdminContentCollectionReviews() {
  const response = await requestWithCsrf<unknown>('/api/admin/content-collection-reviews/rematch', {
    method: 'POST',
  });
  return assertResponse(
    response,
    (value): value is AdminContentCollectionRematchResult =>
      isRecord(value) &&
      ['processedCount', 'matchedCount', 'suggestionCount'].every((key) =>
        isNonNegativeInteger(value[key]),
      ),
  );
}
