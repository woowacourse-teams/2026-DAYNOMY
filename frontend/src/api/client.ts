function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
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

const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
let refreshPromise: Promise<void> | null = null;

export function getApiUrl(path: string) {
  return `${API_BASE_URL}${path}`;
}

async function parseError(response: Response): Promise<ApiError> {
  const contentType = response.headers.get('content-type') ?? '';

  if (contentType.includes('application/json')) {
    try {
      const error: unknown = await response.json();
      if (isRecord(error)) {
        const code = typeof error.code === 'string' ? error.code : undefined;
        const message = typeof error.message === 'string' ? error.message : undefined;
        return new ApiError(response.status, code, message);
      }
    } catch {
      // Fall back to the HTTP status when the error body is not JSON.
    }
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

export async function request<T>(
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

export async function requestWithCsrf<T>(
  path: string,
  init: RequestInit,
  retryOnUnauthorized = true,
): Promise<T> {
  const csrfToken = await request<unknown>('/api/auth/csrf', {}, false);
  if (
    !isRecord(csrfToken) ||
    typeof csrfToken.headerName !== 'string' ||
    !csrfToken.headerName ||
    typeof csrfToken.token !== 'string' ||
    !csrfToken.token
  ) {
    throw new Error('CSRF 응답 형식이 올바르지 않습니다.');
  }
  const headers = new Headers(init.headers);
  headers.set(csrfToken.headerName, csrfToken.token);

  return request<T>(path, { ...init, headers }, retryOnUnauthorized);
}
