import { request, requestWithCsrf } from '../../api/client';

export type EmailSubscriptionResponse = {
  status: 'UNDECIDED' | 'DECLINED' | 'SUBSCRIBED' | 'UNSUBSCRIBED';
  email: string;
  promptRequired: boolean;
  subscribedAt: string | null;
};

const PATH = '/api/users/me/email-subscription';

function parseSubscription(value: unknown): EmailSubscriptionResponse {
  if (
    typeof value !== 'object' ||
    value === null ||
    !('status' in value) ||
    !['UNDECIDED', 'DECLINED', 'SUBSCRIBED', 'UNSUBSCRIBED'].includes(String(value.status)) ||
    !('email' in value) ||
    typeof value.email !== 'string' ||
    !value.email.trim() ||
    !('promptRequired' in value) ||
    typeof value.promptRequired !== 'boolean' ||
    value.promptRequired !== (value.status === 'UNDECIDED') ||
    !('subscribedAt' in value) ||
    (value.subscribedAt !== null &&
      (typeof value.subscribedAt !== 'string' || !Number.isFinite(Date.parse(value.subscribedAt))))
  ) {
    throw new Error('이메일 구독 정보를 확인할 수 없습니다.');
  }
  return {
    status: value.status as EmailSubscriptionResponse['status'],
    email: value.email,
    promptRequired: value.promptRequired,
    subscribedAt: value.subscribedAt,
  };
}

export async function getEmailSubscription(signal?: AbortSignal) {
  return parseSubscription(await request<unknown>(PATH, { signal }));
}

export async function subscribeToEmail(signal?: AbortSignal) {
  return parseSubscription(await requestWithCsrf<unknown>(PATH, { method: 'PUT', signal }));
}

export async function dismissEmailSubscription(signal?: AbortSignal) {
  return parseSubscription(
    await requestWithCsrf<unknown>(`${PATH}/dismiss`, { method: 'POST', signal }),
  );
}
