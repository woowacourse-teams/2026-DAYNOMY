import { request, requestWithCsrf } from '../../api/client';
import type { AdminFeedback, AdminFeedbackPage, FeedbackStatus } from './types';

const DEFAULT_PAGE_SIZE = 15;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isFeedbackStatus(value: unknown): value is FeedbackStatus {
  return value === 'PENDING' || value === 'RESOLVED';
}

function isAdminFeedback(value: unknown): value is AdminFeedback {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.content === 'string' &&
    typeof value.pagePath === 'string' &&
    isFeedbackStatus(value.status) &&
    typeof value.createdAt === 'string' &&
    typeof value.updatedAt === 'string'
  );
}

function assertAdminFeedback(value: unknown): AdminFeedback {
  if (!isAdminFeedback(value)) {
    throw new Error('피드백 API 응답 형식이 올바르지 않습니다.');
  }
  return value;
}

function assertAdminFeedbackPage(value: unknown): AdminFeedbackPage {
  if (
    !isRecord(value) ||
    !Array.isArray(value.items) ||
    !value.items.every(isAdminFeedback) ||
    typeof value.page !== 'number' ||
    typeof value.size !== 'number' ||
    typeof value.totalPages !== 'number' ||
    typeof value.totalElements !== 'number' ||
    typeof value.hasNext !== 'boolean'
  ) {
    throw new Error('피드백 목록 응답 형식이 올바르지 않습니다.');
  }
  return value as AdminFeedbackPage;
}

export function createFeedback(content: string, pagePath: string) {
  return requestWithCsrf<void>('/api/feedback', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content, pagePath }),
  });
}

export async function getAdminFeedbacks(page = 1, signal?: AbortSignal) {
  const params = new URLSearchParams({ page: String(page), size: String(DEFAULT_PAGE_SIZE) });
  const response = await request<unknown>(`/api/admin/feedbacks?${params.toString()}`, { signal });
  return assertAdminFeedbackPage(response);
}

export async function getAdminFeedback(id: number, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/admin/feedbacks/${id}`, { signal });
  return assertAdminFeedback(response);
}

export async function updateAdminFeedbackStatus(id: number, status: FeedbackStatus) {
  const response = await requestWithCsrf<unknown>(`/api/admin/feedbacks/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  });
  return assertAdminFeedback(response);
}

export function deleteAdminFeedback(id: number) {
  return requestWithCsrf<void>(`/api/admin/feedbacks/${id}`, { method: 'DELETE' });
}
