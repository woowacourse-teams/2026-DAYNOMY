export interface MemberResponse {
  id: number;
  email: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
}

export interface BookmarkResponse {
  id: number;
  targetId: number;
}

interface ErrorResponse {
  code?: string;
  message?: string;
}

interface CsrfTokenResponse {
  token: string;
  headerName: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(status: number, code?: string, message = '요청을 처리하지 못했습니다.') {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
let refreshPromise: Promise<void> | null = null;

export function getApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

async function parseError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('content-type') ?? '';

  if (contentType.includes('application/json')) {
    const error = (await response.json()) as ErrorResponse;
    return new ApiError(response.status, error.code, error.message);
  }

  return new ApiError(response.status);
}

async function refreshAccessToken(): Promise<void> {
  if (!refreshPromise) {
    refreshPromise = requestWithCsrf<void>('/api/auth/refresh', { method: 'POST' }, false).finally(
      () => {
        refreshPromise = null;
      },
    );
  }

  return refreshPromise;
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  retryOnUnauthorized = true,
): Promise<T> {
  const response = await fetch(getApiUrl(path), {
    ...init,
    credentials: 'include',
  });

  if (response.status === 401 && retryOnUnauthorized) {
    await refreshAccessToken();
    return request<T>(path, init, false);
  }

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function requestWithCsrf<T>(
  path: string,
  init: RequestInit,
  retryOnUnauthorized = true,
): Promise<T> {
  const csrfToken = await request<CsrfTokenResponse>('/api/auth/csrf', {}, false);
  const headers = new Headers(init.headers);
  headers.set(csrfToken.headerName, csrfToken.token);

  return request<T>(path, { ...init, headers }, retryOnUnauthorized);
}

export function getMyProfile(signal?: AbortSignal): Promise<MemberResponse> {
  return request<MemberResponse>('/api/users/me', { signal });
}

export function getMyBookmarks(signal?: AbortSignal): Promise<BookmarkResponse[]> {
  return request<BookmarkResponse[]>('/api/users/me/bookmarks', { signal });
}

export function deleteBookmark(targetId: number): Promise<void> {
  return requestWithCsrf<void>(`/api/assets/bookmarks?targetId=${targetId}`, {
    method: 'DELETE',
  });
}

export function updateMyProfile(nickname: string): Promise<MemberResponse> {
  return requestWithCsrf<MemberResponse>('/api/users/me', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  });
}

export function logout() {
  return requestWithCsrf<void>('/api/auth/logout', { method: 'POST' });
}

export function withdraw() {
  return requestWithCsrf<void>('/api/users/me', { method: 'DELETE' });
}
