import assert from 'node:assert/strict';
import test from 'node:test';
import {
  calculateDepositComparison,
  calculateCheckInStreak,
  calculateInvestmentProjection,
  calculateMockPortfolio,
  calculateYouthSavings,
  createFinancialPlan,
  createSalaryBudget,
  weeklyTargetsFromPlan,
  estimateStockTax,
  recommendPaymentTool,
} from '../../src/features/finance/financialPlanning';

const salaryInput = {
  monthlyIncome: 3_000_000,
  rent: 600_000,
  livingExpenses: 900_000,
  fixedExpenses: 300_000,
  emergencySavings: 3_000_000,
  goalAmount: 10_000_000,
  goalSaved: 1_000_000,
  goalMonths: 24,
  risk: 'MEDIUM' as const,
  hasHighInterestDebt: false,
};

test('월급에서 지출을 빼고 비상금·목표 저축·투자를 중복 없이 배분한다', () => {
  const plan = createSalaryBudget(salaryInput);
  assert.equal(plan.monthlyExpenses, 1_800_000);
  assert.equal(plan.monthlyAvailable, 1_200_000);
  assert.equal(plan.requiredSavings, 375_000);
  assert.equal(plan.emergency, 600_000);
  assert.equal(plan.savings, 420_000);
  assert.equal(plan.investment, 180_000);
  assert.equal(plan.debt + plan.emergency + plan.savings + plan.investment, plan.monthlyAvailable);
  assert.equal(plan.goalBalance, 11_080_000);
});

test('목표 저축이 부족하면 투자 몫에서 먼저 채우고 부족액은 숨기지 않는다', () => {
  const affordable = createSalaryBudget({ ...salaryInput, goalAmount: 13_000_000 });
  assert.equal(affordable.savings, 500_000);
  assert.equal(affordable.investment, 100_000);
  const tooLarge = createSalaryBudget({ ...salaryInput, goalAmount: 40_000_000, goalMonths: 12 });
  assert.equal(tooLarge.savings, 600_000);
  assert.equal(tooLarge.investment, 0);
  assert.equal(tooLarge.goalShortfall, 2_650_000);
  assert.equal(tooLarge.monthsToGoal, 65);
});

test('지출 초과·수입 없음·이미 달성한 목표에서 음수나 무한대 배분을 만들지 않는다', () => {
  for (const income of [0, 1_700_000, 1_800_000]) {
    const plan = createSalaryBudget({ ...salaryInput, monthlyIncome: income });
    assert.equal(plan.monthlyAvailable, 0);
    assert.equal(plan.savings + plan.investment + plan.emergency + plan.debt, 0);
    assert.equal(plan.expenseDeficit, 1_800_000 - income);
    assert.equal(plan.monthsToGoal, null);
  }
  const complete = createSalaryBudget({ ...salaryInput, goalSaved: 11_000_000 });
  assert.equal(complete.requiredSavings, 0);
  assert.equal(complete.monthsToGoal, 0);
});

test('비상금 부족액까지만 배분하고 고금리 부채가 있으면 투자를 하지 않는다', () => {
  const plan = createSalaryBudget({ ...salaryInput, emergencySavings: 5_399_999 });
  assert.equal(plan.emergency, 1);
  assert.equal(plan.debt + plan.emergency + plan.savings + plan.investment, 1_200_000);
  const withDebt = createSalaryBudget({ ...salaryInput, hasHighInterestDebt: true });
  assert.equal(withDebt.investment, 0);
  assert.ok(withDebt.debt > 0);
});

test('잘못된 금액과 기간은 계산 전에 거부한다', () => {
  for (const value of [-1, Infinity, NaN, 1.5, 1e13]) {
    assert.throws(() => createSalaryBudget({ ...salaryInput, monthlyIncome: value }));
  }
  for (const goalMonths of [0, 121, 1.5, NaN]) {
    assert.throws(() => createSalaryBudget({ ...salaryInput, goalMonths }));
  }
});

test('4주·5주인 달 모두 주 목표의 합계가 월 계획과 정확히 일치한다', () => {
  const plan = {
    ...createSalaryBudget(salaryInput).monthlyPlan,
    savings: 420_003,
    investment: 180_001,
  };
  for (const weeks of [
    ['2026-10-05', '2026-10-12', '2026-10-19', '2026-10-26'],
    ['2026-03-02', '2026-03-09', '2026-03-16', '2026-03-23', '2026-03-30'],
  ]) {
    const goals = weeks.map((week) => weeklyTargetsFromPlan(plan, week));
    assert.equal(
      goals.reduce((sum, item) => sum + item.targetSavings, 0),
      plan.savings + plan.emergency,
    );
    assert.equal(
      goals.reduce((sum, item) => sum + item.targetInvestment, 0),
      plan.investment,
    );
  }
  for (const invalidDate of ['2026-10-06', '2026-02-30', '', 'broken']) {
    assert.throws(() => weeklyTargetsFromPlan(plan, invalidDate));
  }
});

test('예금과 적금의 세후 만기액을 계산한다', () => {
  const result = calculateDepositComparison({
    principal: 10_000_000,
    monthlyDeposit: 500_000,
    months: 12,
    depositRate: 3,
    savingsRate: 4,
  });

  assert.equal(Math.round(result.depositMaturity), 10_253_800);
  assert.equal(Math.round(result.savingsMaturity), 6_109_980);
});

test('청년미래적금의 납입액과 정부기여금을 계산한다', () => {
  const result = calculateYouthSavings({ monthlyDeposit: 500_000, annualRate: 4, matchRate: 0.06 });

  assert.equal(result.principal, 18_000_000);
  assert.equal(result.governmentContribution, 1_080_000);
  assert.equal(Math.round(result.maturity), 20_190_000);
});

test('비상금이 부족하면 저축과 투자를 함께 배분한다', () => {
  const result = createFinancialPlan({
    monthlyAvailable: 800_000,
    monthlyExpenses: 1_500_000,
    emergencySavings: 3_000_000,
    goalYears: 5,
    risk: 'MEDIUM',
    hasHighInterestDebt: false,
  });

  assert.deepEqual(
    { emergency: result.emergency, savings: result.savings, investment: result.investment },
    { emergency: 400_000, savings: 280_000, investment: 120_000 },
  );
  assert.equal(result.emergencyGap, 1_500_000);
});

test('매월 투자금의 복리 예상액과 20% 하락 금액을 계산한다', () => {
  const result = calculateInvestmentProjection(100_000, 1, 12);

  assert.equal(result.principal, 1_200_000);
  assert.equal(Math.round(result.estimatedValue), 1_268_250);
  assert.equal(Math.round(result.afterTwentyPercentDrop), 1_014_600);
});

test('결제 통제가 어렵다면 예상 혜택보다 체크카드를 우선한다', () => {
  const result = recommendPaymentTool({
    canPayInFull: false,
    tracksSpending: true,
    monthlyCardSpending: 1_000_000,
    expectedMonthlyBenefit: 30_000,
    annualFee: 10_000,
    usesRevolving: false,
  });

  assert.equal(result.recommendation, 'CHECK');
  assert.equal(result.netBenefit, 350_000);
});

test('모의 매수와 매도를 평균단가, 실현손익, 비중으로 계산한다', () => {
  const shared = {
    assetId: 1,
    assetCode: '005930',
    assetName: '삼성전자',
    category: 'STOCK' as const,
    market: 'KOSPI' as const,
  };
  const result = calculateMockPortfolio(
    [
      { ...shared, id: 1, tradeType: 'BUY', quantity: 10, price: 10000, tradedOn: '2026-10-01' },
      { ...shared, id: 2, tradeType: 'BUY', quantity: 10, price: 20000, tradedOn: '2026-10-02' },
      { ...shared, id: 3, tradeType: 'SELL', quantity: 5, price: 18000, tradedOn: '2026-10-03' },
    ],
    { 1: 16000 },
  );

  assert.equal(result.holdings[0]?.quantity, 15);
  assert.equal(result.holdings[0]?.averagePrice, 15000);
  assert.equal(result.totalRealizedProfit, 15000);
  assert.equal(result.totalUnrealizedProfit, 15000);
  assert.equal(result.holdings[0]?.weight, 100);
});

test('해외주식은 연간 손익에서 기본공제를 뺀 뒤 세금을 추정한다', () => {
  const result = estimateStockTax('OVERSEAS', 3_000_000, 100_000);

  assert.equal(result.taxableCapitalGain, 500_000);
  assert.equal(result.capitalGainsTax, 110_000);
  assert.equal(result.dividendIncomeTax, 15_400);
});

test('현재 주부터 이어진 체크인만 연속 기록으로 계산한다', () => {
  assert.equal(calculateCheckInStreak(['2026-10-05', '2026-09-28', '2026-09-14'], '2026-10-05'), 2);
});
