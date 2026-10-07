import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getInvestmentPlan,
  saveInvestmentPlan,
} from '../../src/features/investment-capacity/api.ts';
import type { InvestmentPlanRequest } from '../../src/features/investment-capacity/types.ts';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

const plan = {
  birthDate: '2000-01-01',
  monthlyIncome: 3000000,
  monthlyFixedExpense: 1000000,
  monthlyVariableExpense: 0,
  irregularExpenseReserve: 0,
  emergencyFundContribution: 0,
  monthlyDebtRepayment: 0,
  currentCash: 3000000,
  existingDepositSavings: 0,
  investmentAssets: 0,
  otherAssets: 0,
  totalAssets: 3000000,
  availableCurrentCash: 0,
  goalAmount: 10000000,
  goalMonths: 24,
  emergencyFundTarget: 6000000,
  emergencyFundGap: 3000000,
  safeMonthlyCapacity: 2000000,
  requiredMonthlySaving: 416667,
  status: 'ADJUSTABLE',
  interpretation: '비상금을 함께 준비해요.',
  selectedScenario: 'STABLE',
  selectedProductId: 'regular-savings',
  scenarios: [],
  products: [],
};

test('저장된 금융 계획이 없으면 undefined를 반환한다', async () => {
  globalThis.fetch = async () => new Response(null, { status: 204 });

  assert.equal(await getInvestmentPlan(), undefined);
});

test('금융 계획 저장 API는 CSRF 토큰과 입력값을 함께 보낸다', async () => {
  const calls: Array<{ url: string; init?: RequestInit }> = [];
  globalThis.fetch = async (input, init) => {
    calls.push({ url: String(input), init });
    if (String(input) === '/api/auth/csrf') {
      return jsonResponse({ token: 'csrf-token', headerName: 'X-CSRF-TOKEN' });
    }
    return jsonResponse(plan);
  };

  const input: InvestmentPlanRequest = {
    birthDate: '2000-01-01',
    monthlyIncome: 3000000,
    monthlyFixedExpense: 1000000,
    monthlyVariableExpense: 0,
    irregularExpenseReserve: 0,
    emergencyFundContribution: 0,
    monthlyDebtRepayment: 0,
    currentCash: 3000000,
    existingDepositSavings: 0,
    investmentAssets: 0,
    otherAssets: 0,
    goalAmount: 10000000,
    goalMonths: 24,
    selectedScenario: 'STABLE',
    selectedProductId: 'regular-savings',
  };

  const saved = await saveInvestmentPlan(input);

  assert.equal(saved.goalAmount, 10000000);
  assert.deepEqual(
    calls.map(({ url }) => url),
    ['/api/auth/csrf', '/api/users/me/investment-plan'],
  );
  assert.equal(calls[1]?.init?.method, 'PUT');
  assert.equal(new Headers(calls[1]?.init?.headers).get('X-CSRF-TOKEN'), 'csrf-token');
  assert.deepEqual(JSON.parse(String(calls[1]?.init?.body)), input);
});
