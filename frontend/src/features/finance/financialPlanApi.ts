import { request, requestWithCsrf } from '../../api/client';
import {
  deleteConsultation,
  isConsultation,
  loadConsultations,
  saveConsultation,
  type Consultation,
} from './financialPlanning';

const path = '/api/users/me/financial-plans';
export async function loadFinancialPlans(
  isLoggedIn: boolean,
  signal?: AbortSignal,
): Promise<Consultation[]> {
  if (!isLoggedIn) return loadConsultations();
  const value = await request<unknown>(path, { signal });
  if (
    !value ||
    typeof value !== 'object' ||
    !('plans' in value) ||
    !Array.isArray(value.plans) ||
    !value.plans.every(isConsultation)
  ) {
    throw new Error('저장한 계획 응답 형식이 올바르지 않습니다.');
  }
  return value.plans;
}
export async function saveFinancialPlan(
  isLoggedIn: boolean,
  plan: Consultation,
): Promise<Consultation> {
  if (!isLoggedIn) {
    saveConsultation(plan);
    return plan;
  }
  if (!plan.monthlyPlan) throw new Error('월 계획을 먼저 계산해 주세요.');
  const { id, topic, title, summary, details, actions, monthlyPlan } = plan;
  const response = await requestWithCsrf<unknown>(`${path}/${encodeURIComponent(id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ topic, title, summary, details, actions: actions ?? [], monthlyPlan }),
  });
  if (!isConsultation(response)) throw new Error('계획 저장 응답 형식이 올바르지 않습니다.');
  return response;
}
export async function deleteFinancialPlan(isLoggedIn: boolean, id: string) {
  if (!isLoggedIn) {
    deleteConsultation(id);
    return;
  }
  await requestWithCsrf<void>(`${path}/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
