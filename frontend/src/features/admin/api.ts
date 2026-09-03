import { request, requestWithCsrf } from '../../api/client';
import { isCategory } from '../news/newslist/types';
import type {
  AdminNewsFilterCategory,
  AdminNewsFilterStatus,
  AdminNewsFormValues,
  AdminNewsListItemResponse,
  AdminNewsPageResponse,
  AdminNewsResponse,
  AdminNewsSource,
  AdminNewsStatus,
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

function isAdminNewsSource(value: unknown): value is AdminNewsSource | null {
  return value === null || value === 'DART' || value === 'KOSIS' || value === 'BOK';
}

function isAdminNewsListItem(value: unknown): value is AdminNewsListItemResponse {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.title === 'string' &&
    isNullableString(value.description) &&
    isNullableString(value.imageUrl) &&
    isAdminNewsSource(value.source) &&
    typeof value.sourceUrl === 'string' &&
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
    isNullableString(value.description) &&
    isNullableString(value.imageUrl) &&
    isAdminNewsSource(value.source) &&
    typeof value.sourceUrl === 'string' &&
    isCategory(value.category) &&
    isNullableString(value.publishedAt) &&
    isAdminNewsStatus(value.status)
  );
}

function assertResponse<T>(value: unknown, isValid: (value: unknown) => value is T): T {
  if (!isValid(value)) {
    throw new Error('관리자 뉴스 API 응답 형식이 올바르지 않습니다.');
  }

  return value;
}

export async function getAdminNews(
  page = 1,
  status: AdminNewsFilterStatus = 'ALL',
  category: AdminNewsFilterCategory = 'ALL',
  signal?: AbortSignal,
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

export async function updateAdminNews(id: number, values: AdminNewsFormValues, image: File | null) {
  const response = await requestWithCsrf<unknown>(`/api/admin/news/${id}`, {
    method: 'PUT',
    body: createNewsFormData(values, image),
  });

  return assertResponse(response, isAdminNewsResponse);
}

export async function deleteAdminNews(id: number) {
  return requestWithCsrf<void>(`/api/admin/news/${id}`, { method: 'DELETE' });
}
