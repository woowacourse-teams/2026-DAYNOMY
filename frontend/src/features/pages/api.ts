import { ApiError, getApiUrl, request, requestWithCsrf } from '../../api/client';
import type { MemberRole } from '../../auth/AuthContext';

export interface MemberResponse {
  id: number;
  email: string;
  nickname: string;
  role: MemberRole;
}

export { ApiError, getApiUrl };

export function getMyProfile(signal?: AbortSignal): Promise<MemberResponse> {
  return request<MemberResponse>('/api/users/me', { signal });
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
