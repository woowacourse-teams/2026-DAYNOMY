import { ApiError, getApiUrl, request, requestWithCsrf } from '../../api/client';
import type { MemberRole } from '../../auth/AuthContext';

export interface MemberResponse {
  id: number;
  email: string;
  name: string | null;
  nickname: string;
  role: MemberRole;
}

export { ApiError, getApiUrl };

export async function getMyProfile(signal?: AbortSignal): Promise<MemberResponse> {
  const value = await request<unknown>('/api/users/me', { signal });
  return parseMemberResponse(value);
}

function parseMemberResponse(value: unknown): MemberResponse {
  if (
    typeof value !== 'object' ||
    value === null ||
    !('id' in value) ||
    !Number.isSafeInteger(value.id) ||
    Number(value.id) <= 0 ||
    !('email' in value) ||
    typeof value.email !== 'string' ||
    ('name' in value && value.name !== null && typeof value.name !== 'string') ||
    !('nickname' in value) ||
    typeof value.nickname !== 'string' ||
    !('role' in value) ||
    (value.role !== 'USER' && value.role !== 'ADMIN')
  ) {
    throw new Error('계정 정보를 확인할 수 없습니다. 다시 로그인해 주세요.');
  }
  return {
    id: Number(value.id),
    email: value.email,
    // 이전 API 응답과의 배포 호환성: 누락된 이름은 닉네임으로 추측하지 않는다.
    name: 'name' in value && typeof value.name === 'string' ? value.name : null,
    nickname: value.nickname,
    role: value.role,
  };
}

export async function updateMyProfile(nickname: string): Promise<MemberResponse> {
  const value = await requestWithCsrf<unknown>('/api/users/me', {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nickname }),
  });
  return parseMemberResponse(value);
}

export function logout() {
  return requestWithCsrf<void>('/api/auth/logout', { method: 'POST' });
}

export function withdraw() {
  return requestWithCsrf<void>('/api/users/me', { method: 'DELETE' });
}
