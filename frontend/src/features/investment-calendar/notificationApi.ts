import { request, requestWithCsrf } from '../../api/client';
import type {
  InvestmentCalendarNotificationScope,
  InvestmentCalendarNotificationSetting,
  InvestmentCalendarNotificationTiming,
  InvestmentCalendarNotificationUpdateRequest,
  NotificationEmailVerificationRequest,
} from './notificationTypes';

const NOTIFICATION_PATH = '/api/users/me/investment-calendar-notification';
const MOCK_STORAGE_KEY = 'daynomy:investment-calendar-notification';

const SCOPES = new Set<InvestmentCalendarNotificationScope>([
  'PORTFOLIO',
  'WATCHLIST',
  'MAJOR_ECONOMIC',
]);
const TIMINGS = new Set<InvestmentCalendarNotificationTiming>([
  'SEVEN_DAYS_BEFORE',
  'ONE_DAY_BEFORE',
  'SAME_DAY_MORNING',
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isScope(value: unknown): value is InvestmentCalendarNotificationScope {
  return typeof value === 'string' && SCOPES.has(value as InvestmentCalendarNotificationScope);
}

function isTiming(value: unknown): value is InvestmentCalendarNotificationTiming {
  return typeof value === 'string' && TIMINGS.has(value as InvestmentCalendarNotificationTiming);
}

export function isInvestmentCalendarNotificationSetting(
  value: unknown,
): value is InvestmentCalendarNotificationSetting {
  return (
    isRecord(value) &&
    typeof value.email === 'string' &&
    typeof value.emailVerified === 'boolean' &&
    Array.isArray(value.scopes) &&
    value.scopes.every(isScope) &&
    Array.isArray(value.timings) &&
    value.timings.every(isTiming) &&
    typeof value.sendBeforeAnnouncement === 'boolean' &&
    typeof value.sendAfterAnnouncement === 'boolean'
  );
}

function shouldUseMockData() {
  return import.meta.env.DEV && import.meta.env.VITE_INVESTMENT_CALENDAR_MOCK_ENABLED !== 'false';
}

function readMockSetting(): InvestmentCalendarNotificationSetting | null {
  const saved = localStorage.getItem(MOCK_STORAGE_KEY);
  if (!saved) return null;

  try {
    const parsed: unknown = JSON.parse(saved);
    if (isInvestmentCalendarNotificationSetting(parsed)) return parsed;
  } catch {
    // Invalid development data is discarded below.
  }

  localStorage.removeItem(MOCK_STORAGE_KEY);
  return null;
}

export async function getInvestmentCalendarNotification(
  signal?: AbortSignal,
): Promise<InvestmentCalendarNotificationSetting | null> {
  if (shouldUseMockData()) return readMockSetting();

  const response = await request<unknown>(NOTIFICATION_PATH, { signal });
  if (response === null || response === undefined) return null;
  if (!isInvestmentCalendarNotificationSetting(response)) {
    throw new Error('투자 캘린더 알림 API 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

export async function updateInvestmentCalendarNotification(
  setting: InvestmentCalendarNotificationUpdateRequest,
): Promise<InvestmentCalendarNotificationSetting> {
  if (shouldUseMockData()) {
    const saved = { ...setting, emailVerified: true };
    localStorage.setItem(MOCK_STORAGE_KEY, JSON.stringify(saved));
    return saved;
  }

  const response = await requestWithCsrf<unknown>(NOTIFICATION_PATH, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(setting),
  });
  if (!isInvestmentCalendarNotificationSetting(response)) {
    throw new Error('투자 캘린더 알림 API 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

export async function deleteInvestmentCalendarNotification(): Promise<void> {
  if (shouldUseMockData()) {
    localStorage.removeItem(MOCK_STORAGE_KEY);
    return;
  }
  await requestWithCsrf<void>(NOTIFICATION_PATH, { method: 'DELETE' });
}

export async function requestNotificationEmailVerification(
  verification: NotificationEmailVerificationRequest,
): Promise<void> {
  if (shouldUseMockData()) return;

  await requestWithCsrf<void>(`${NOTIFICATION_PATH}/email-verifications`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(verification),
  });
}
