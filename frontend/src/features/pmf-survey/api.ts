import { ApiError, request, requestWithCsrf } from '../../api/client';
import type { PmfAnswer, PmfSurveyStatus } from './types';

function isId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
}

export async function getCurrentPmfSurvey(signal?: AbortSignal): Promise<PmfSurveyStatus | null> {
  let value: unknown;
  try {
    value = await request<unknown>('/api/pmf-surveys/current', { signal }, false);
  } catch (error) {
    // Until the separate BE API is deployed, do not offer a survey that cannot be saved.
    if (error instanceof ApiError && [401, 403, 404].includes(error.status)) return null;
    throw error;
  }
  if (value === undefined || value === null) return null;
  if (
    typeof value !== 'object' ||
    !('surveyId' in value) ||
    !isId(value.surveyId) ||
    !('eligible' in value) ||
    typeof value.eligible !== 'boolean' ||
    !('responseId' in value) ||
    (value.responseId !== null && !isId(value.responseId)) ||
    !('isFirstVisit' in value) ||
    typeof value.isFirstVisit !== 'boolean' ||
    !('hiddenUntil' in value) ||
    (value.hiddenUntil !== null &&
      (typeof value.hiddenUntil !== 'string' || !Number.isFinite(Date.parse(value.hiddenUntil))))
  ) {
    throw new Error('PMF 설문 API 응답 형식이 올바르지 않습니다.');
  }
  return {
    surveyId: value.surveyId,
    eligible: value.eligible,
    responseId: value.responseId,
    isFirstVisit: value.isFirstVisit,
    hiddenUntil: value.hiddenUntil,
  };
}

export async function submitPmfResponse(
  surveyId: number,
  answer: PmfAnswer,
  reason: string | null,
  signal?: AbortSignal,
): Promise<number> {
  const value = await requestWithCsrf<unknown>(`/api/pmf-surveys/${surveyId}/responses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ answer, reason }),
    signal,
  });
  if (typeof value !== 'object' || value === null || !('id' in value) || !isId(value.id)) {
    throw new Error('PMF 설문 저장 응답 형식이 올바르지 않습니다.');
  }
  return value.id;
}

export async function hidePmfSurveyToday(surveyId: number, signal?: AbortSignal) {
  await requestWithCsrf<void>(`/api/pmf-surveys/${surveyId}/snooze`, {
    method: 'POST',
    signal,
  });
}
