import { request, requestWithCsrf } from '../../api/client';
import type {
  FeaturedPeopleResponse,
  FeaturedPerson,
  FeaturedPersonPostCandidate,
  FeaturedPersonPostCandidateInboxItem,
  FeaturedPersonPostCandidatePage,
  FeaturedPersonPostCandidateBulkResponse,
  FeaturedPersonPost,
  FeaturedPersonPostRequest,
  FeaturedPersonRequest,
  SocialPostSearchItem,
  SocialPlatform,
} from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isSocialPlatform(value: unknown): value is SocialPlatform {
  return value === 'THREADS' || value === 'X' || value === 'YOUTUBE';
}

function isOptionalString(value: unknown) {
  return value === null || typeof value === 'string';
}

function isPost(value: unknown): value is FeaturedPersonPost {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    isSocialPlatform(value.sourceType) &&
    typeof value.title === 'string' &&
    typeof value.url === 'string' &&
    (value.publishedAt === undefined || isOptionalString(value.publishedAt)) &&
    isOptionalString(value.createdAt)
  );
}

function isPostCandidate(value: unknown): value is FeaturedPersonPostCandidate {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    isSocialPlatform(value.sourceType) &&
    typeof value.title === 'string' &&
    typeof value.url === 'string' &&
    isOptionalString(value.publishedAt) &&
    value.status === 'PENDING' &&
    isOptionalString(value.createdAt)
  );
}

function isInboxPostCandidate(value: unknown): value is FeaturedPersonPostCandidateInboxItem {
  if (!isRecord(value)) return false;
  const personId = value.personId;
  const personName = value.personName;
  const personProfileImageUrl = value.personProfileImageUrl;
  return (
    isPostCandidate(value) &&
    typeof personId === 'number' &&
    typeof personName === 'string' &&
    isOptionalString(personProfileImageUrl)
  );
}

function assertPostCandidates(value: unknown) {
  if (
    !isRecord(value) ||
    !Array.isArray(value.candidates) ||
    !value.candidates.every(isPostCandidate)
  ) {
    throw new Error('수집한 게시물 후보 API 응답 형식이 올바르지 않습니다.');
  }
  return value.candidates;
}

function assertPostCandidatePage(value: unknown): FeaturedPersonPostCandidatePage {
  if (
    !isRecord(value) ||
    !Array.isArray(value.candidates) ||
    !value.candidates.every(isInboxPostCandidate) ||
    typeof value.page !== 'number' ||
    typeof value.size !== 'number' ||
    typeof value.totalElements !== 'number' ||
    typeof value.totalPages !== 'number'
  ) {
    throw new Error('전체 게시물 후보 API 응답 형식이 올바르지 않습니다.');
  }
  return value as FeaturedPersonPostCandidatePage;
}

function assertPostCandidateBulkResponse(value: unknown): FeaturedPersonPostCandidateBulkResponse {
  if (
    !isRecord(value) ||
    !Array.isArray(value.succeeded) ||
    !value.succeeded.every(
      (item) =>
        isRecord(item) &&
        typeof item.candidateId === 'number' &&
        (item.post === null || isPost(item.post)),
    ) ||
    !Array.isArray(value.failed) ||
    !value.failed.every(
      (item) =>
        isRecord(item) &&
        typeof item.candidateId === 'number' &&
        typeof item.errorCode === 'string' &&
        typeof item.message === 'string',
    )
  ) {
    throw new Error('게시물 후보 일괄 처리 API 응답 형식이 올바르지 않습니다.');
  }
  return value as FeaturedPersonPostCandidateBulkResponse;
}

function isSearchItem(value: unknown): value is SocialPostSearchItem {
  return (
    isRecord(value) &&
    isSocialPlatform(value.sourceType) &&
    typeof value.title === 'string' &&
    typeof value.url === 'string' &&
    typeof value.publishedDate === 'string'
  );
}

function isPerson(value: unknown): value is FeaturedPerson {
  return (
    isRecord(value) &&
    typeof value.id === 'number' &&
    typeof value.name === 'string' &&
    isOptionalString(value.xProfileUrl) &&
    isOptionalString(value.threadsProfileUrl) &&
    (value.youtubeProfileUrl === undefined || isOptionalString(value.youtubeProfileUrl)) &&
    (value.profileImageUrl === undefined || isOptionalString(value.profileImageUrl)) &&
    isOptionalString(value.createdAt) &&
    Array.isArray(value.posts) &&
    value.posts.every(isPost)
  );
}

function assertPeopleResponse(value: unknown): FeaturedPeopleResponse {
  if (!isRecord(value) || !Array.isArray(value.people) || !value.people.every(isPerson)) {
    throw new Error('주요 인물 API 응답 형식이 올바르지 않습니다.');
  }
  return { people: value.people };
}

function assertPerson(value: unknown) {
  if (!isPerson(value)) throw new Error('주요 인물 API 응답 형식이 올바르지 않습니다.');
  return value;
}

function assertPost(value: unknown) {
  if (!isPost(value)) throw new Error('주요 인물 게시물 API 응답 형식이 올바르지 않습니다.');
  return value;
}

export async function getFeaturedPeople(signal?: AbortSignal) {
  return assertPeopleResponse(await request<unknown>('/api/people', { signal }));
}

export async function getAdminFeaturedPeople(signal?: AbortSignal) {
  return assertPeopleResponse(await request<unknown>('/api/admin/people', { signal }));
}

export async function searchFeaturedPersonPosts(personId: number, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/admin/people/${personId}/posts/search`, {
    signal,
  });
  if (
    !isRecord(response) ||
    !Array.isArray(response.items) ||
    !response.items.every(isSearchItem)
  ) {
    throw new Error('주요 인물 게시물 검색 API 응답 형식이 올바르지 않습니다.');
  }
  return response.items;
}

export async function createFeaturedPerson(input: FeaturedPersonRequest) {
  return assertPerson(
    await requestWithCsrf<unknown>('/api/admin/people', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
  );
}

export async function updateFeaturedPerson(personId: number, input: FeaturedPersonRequest) {
  return assertPerson(
    await requestWithCsrf<unknown>(`/api/admin/people/${personId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
  );
}

export async function deleteFeaturedPerson(personId: number) {
  await requestWithCsrf<void>(`/api/admin/people/${personId}`, { method: 'DELETE' });
}

export async function createFeaturedPersonPost(personId: number, input: FeaturedPersonPostRequest) {
  return assertPost(
    await requestWithCsrf<unknown>(`/api/admin/people/${personId}/posts`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
  );
}

export async function getFeaturedPersonPostCandidates(personId: number, signal?: AbortSignal) {
  return assertPostCandidates(
    await request<unknown>(`/api/admin/people/${personId}/post-candidates`, { signal }),
  );
}

export async function getAllFeaturedPersonPostCandidates(
  params: {
    page?: number;
    size?: number;
    personId?: number;
    sourceType?: SocialPlatform;
  } = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    page: String(params.page ?? 1),
    size: String(params.size ?? 20),
  });
  if (params.personId !== undefined) query.set('personId', String(params.personId));
  if (params.sourceType !== undefined) query.set('sourceType', params.sourceType);
  return assertPostCandidatePage(
    await request<unknown>(`/api/admin/people/post-candidates?${query.toString()}`, { signal }),
  );
}

export async function collectFeaturedPersonPosts(personId: number, from: string, to: string) {
  const query = new URLSearchParams({ from, to });
  return assertPostCandidates(
    await requestWithCsrf<unknown>(
      `/api/admin/people/${personId}/post-candidates/collect?${query.toString()}`,
      { method: 'POST' },
    ),
  );
}

export async function approveFeaturedPersonPostCandidate(personId: number, candidateId: number) {
  return assertPost(
    await requestWithCsrf<unknown>(
      `/api/admin/people/${personId}/post-candidates/${candidateId}/approve`,
      { method: 'POST' },
    ),
  );
}

export async function rejectFeaturedPersonPostCandidate(personId: number, candidateId: number) {
  await requestWithCsrf<void>(
    `/api/admin/people/${personId}/post-candidates/${candidateId}/reject`,
    { method: 'POST' },
  );
}

async function processFeaturedPersonPostCandidates(
  personId: number,
  candidateIds: number[],
  action: 'bulk-approve' | 'bulk-reject',
) {
  return assertPostCandidateBulkResponse(
    await requestWithCsrf<unknown>(`/api/admin/people/${personId}/post-candidates/${action}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ candidateIds }),
    }),
  );
}

export async function approveFeaturedPersonPostCandidates(
  personId: number,
  candidateIds: number[],
) {
  return processFeaturedPersonPostCandidates(personId, candidateIds, 'bulk-approve');
}

export async function rejectFeaturedPersonPostCandidates(personId: number, candidateIds: number[]) {
  return processFeaturedPersonPostCandidates(personId, candidateIds, 'bulk-reject');
}

async function processAllFeaturedPersonPostCandidates(
  candidateIds: number[],
  action: 'bulk-approve' | 'bulk-reject',
) {
  return assertPostCandidateBulkResponse(
    await requestWithCsrf<unknown>(`/api/admin/people/post-candidates/${action}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ candidateIds }),
    }),
  );
}

export async function approveAllFeaturedPersonPostCandidates(candidateIds: number[]) {
  return processAllFeaturedPersonPostCandidates(candidateIds, 'bulk-approve');
}

export async function rejectAllFeaturedPersonPostCandidates(candidateIds: number[]) {
  return processAllFeaturedPersonPostCandidates(candidateIds, 'bulk-reject');
}

export async function deleteFeaturedPersonPost(personId: number, postId: number) {
  await requestWithCsrf<void>(`/api/admin/people/${personId}/posts/${postId}`, {
    method: 'DELETE',
  });
}
