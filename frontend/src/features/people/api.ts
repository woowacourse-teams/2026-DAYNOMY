import { request, requestWithCsrf } from '../../api/client';
import type {
  FeaturedPeopleResponse,
  FeaturedPerson,
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
    isOptionalString(value.createdAt)
  );
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

export async function deleteFeaturedPersonPost(personId: number, postId: number) {
  await requestWithCsrf<void>(`/api/admin/people/${personId}/posts/${postId}`, {
    method: 'DELETE',
  });
}
