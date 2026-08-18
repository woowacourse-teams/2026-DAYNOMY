export interface Member {
  id: number;
  email: string;
  nickname: string;
  role: 'USER' | 'ADMIN';
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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(getApiUrl(path), {
    ...init,
    credentials: 'include',
  });

  if (!response.ok) {
    throw await parseError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function requestWithCsrf<T>(path: string, init: RequestInit): Promise<T> {
  const csrfToken = await request<CsrfTokenResponse>('/api/auth/csrf');
  const headers = new Headers(init.headers);
  headers.set(csrfToken.headerName, csrfToken.token);

  return request<T>(path, { ...init, headers });
}

export function getMyProfile(signal?: AbortSignal) {
  return request<Member>('/api/users/me', { signal });
}

export function updateMyProfile(nickname: string) {
  return requestWithCsrf<Member>('/api/users/me', {
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
