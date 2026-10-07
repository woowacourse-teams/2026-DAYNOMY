import { request, requestWithCsrf } from '../../api/client';
import type { InvestmentPlan, InvestmentPlanRequest } from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function hasNumber(value: Record<string, unknown>, key: string) {
  return typeof value[key] === 'number' && Number.isFinite(value[key]);
}

function isInvestmentProduct(value: unknown): boolean {
  if (!isRecord(value)) return false;
  return (
    typeof value.id === 'string' &&
    typeof value.name === 'string' &&
    typeof value.type === 'string' &&
    hasNumber(value, 'baseRate') &&
    hasNumber(value, 'maxRate') &&
    hasNumber(value, 'maxLimit') &&
    hasNumber(value, 'termMonths') &&
    hasNumber(value, 'monthlyDeposit') &&
    hasNumber(value, 'expectedMaturityAmount') &&
    hasNumber(value, 'expectedInterest') &&
    typeof value.liquidity === 'string' &&
    typeof value.earlyWithdrawalNote === 'string' &&
    typeof value.depositProtection === 'boolean' &&
    typeof value.eligibility === 'string' &&
    typeof value.recommendationReason === 'string' &&
    typeof value.dataNote === 'string' &&
    typeof value.companyName === 'string' &&
    typeof value.joinWay === 'string' &&
    Array.isArray(value.benefitConditions) &&
    value.benefitConditions.every(
      (condition) =>
        isRecord(condition) &&
        typeof condition.description === 'string' &&
        typeof condition.status === 'string',
    ) &&
    Array.isArray(value.amountConditions) &&
    value.amountConditions.every(
      (condition) =>
        isRecord(condition) &&
        typeof condition.description === 'string' &&
        (condition.thresholdAmount === null || hasNumber(condition, 'thresholdAmount')) &&
        typeof condition.status === 'string',
    ) &&
    Array.isArray(value.termOptions) &&
    value.termOptions.every(
      (term) =>
        isRecord(term) &&
        typeof term.id === 'string' &&
        hasNumber(term, 'termMonths') &&
        hasNumber(term, 'baseRate') &&
        hasNumber(term, 'maxRate') &&
        hasNumber(term, 'monthlyDeposit') &&
        hasNumber(term, 'expectedMaturityAmount') &&
        hasNumber(term, 'expectedInterest'),
    ) &&
    typeof value.disclosureMonth === 'string' &&
    typeof value.sourceUrl === 'string'
  );
}

function isInvestmentPlan(value: unknown): value is InvestmentPlan {
  if (!isRecord(value)) return false;
  return (
    typeof value.birthDate === 'string' &&
    hasNumber(value, 'monthlyIncome') &&
    hasNumber(value, 'monthlyFixedExpense') &&
    hasNumber(value, 'monthlyVariableExpense') &&
    hasNumber(value, 'irregularExpenseReserve') &&
    hasNumber(value, 'emergencyFundContribution') &&
    hasNumber(value, 'monthlyDebtRepayment') &&
    hasNumber(value, 'currentCash') &&
    (value.existingDepositSavings === undefined || hasNumber(value, 'existingDepositSavings')) &&
    (value.investmentAssets === undefined || hasNumber(value, 'investmentAssets')) &&
    (value.otherAssets === undefined || hasNumber(value, 'otherAssets')) &&
    (value.totalAssets === undefined || hasNumber(value, 'totalAssets')) &&
    hasNumber(value, 'availableCurrentCash') &&
    hasNumber(value, 'goalAmount') &&
    hasNumber(value, 'goalMonths') &&
    hasNumber(value, 'safeMonthlyCapacity') &&
    hasNumber(value, 'requiredMonthlySaving') &&
    typeof value.interpretation === 'string' &&
    Array.isArray(value.scenarios) &&
    Array.isArray(value.products) &&
    value.products.every(isInvestmentProduct)
  );
}

function assertInvestmentPlan(value: unknown): InvestmentPlan {
  if (!isInvestmentPlan(value)) {
    throw new Error('금융 계획 응답 형식이 올바르지 않습니다.');
  }
  const existingDepositSavings = hasNumber(value, 'existingDepositSavings')
    ? value.existingDepositSavings
    : 0;
  const investmentAssets = hasNumber(value, 'investmentAssets') ? value.investmentAssets : 0;
  const otherAssets = hasNumber(value, 'otherAssets') ? value.otherAssets : 0;
  const totalAssets = hasNumber(value, 'totalAssets')
    ? value.totalAssets
    : value.currentCash + existingDepositSavings + investmentAssets + otherAssets;
  return {
    ...value,
    existingDepositSavings,
    investmentAssets,
    otherAssets,
    totalAssets,
  };
}

export async function getInvestmentPlan() {
  const response = await request<unknown>('/api/users/me/investment-plan');
  return response === undefined ? undefined : assertInvestmentPlan(response);
}

export async function saveInvestmentPlan(input: InvestmentPlanRequest) {
  const response = await requestWithCsrf<unknown>('/api/users/me/investment-plan', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  return assertInvestmentPlan(response);
}
