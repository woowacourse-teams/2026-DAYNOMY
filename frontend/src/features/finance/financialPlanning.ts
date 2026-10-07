export const CONSULTATIONS_STORAGE_KEY = 'daynomy:financial-plans:v1';
export const ACCOUNT_STEPS_STORAGE_KEY = 'daynomy:account-steps:v1';

export type Consultation = {
  id: string;
  topic: '예금·적금 비교' | '청년미래적금' | '저축·투자 계획';
  title: string;
  summary: string;
  details: string[];
  createdAt: string;
  actions?: Array<{ id: string; label: string; completed: boolean }>;
  monthlyPlan?: MonthlyPlan;
};

export type SalaryBudgetInput = {
  monthlyIncome: number;
  rent: number;
  livingExpenses: number;
  fixedExpenses: number;
  emergencySavings: number;
  goalAmount: number;
  goalSaved: number;
  goalMonths: number;
  risk: FinancialPlanInput['risk'];
  hasHighInterestDebt: boolean;
};

export type MonthlyPlan = {
  monthlyIncome: number;
  monthlyExpenses: number;
  goalAmount: number;
  goalSaved: number;
  goalMonths: number;
  savings: number;
  emergency: number;
  investment: number;
  debt: number;
};

function isMoney(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0 && value <= 1e12;
}

export function createSalaryBudget(input: SalaryBudgetInput) {
  if (
    ![
      input.monthlyIncome,
      input.rent,
      input.livingExpenses,
      input.fixedExpenses,
      input.emergencySavings,
      input.goalAmount,
      input.goalSaved,
    ].every(isMoney) ||
    !Number.isInteger(input.goalMonths) ||
    input.goalMonths < 1 ||
    input.goalMonths > 120
  ) {
    throw new Error('금액은 0 이상의 정수, 목표 기간은 1~120개월로 입력해 주세요.');
  }
  const monthlyExpenses = input.rent + input.livingExpenses + input.fixedExpenses;
  if (monthlyExpenses > 1e12) throw new Error('월 지출 합계는 1조 원 이하로 입력해 주세요.');
  const monthlyAvailable = Math.max(0, input.monthlyIncome - monthlyExpenses);
  const requiredSavings = Math.ceil(
    Math.max(0, input.goalAmount - input.goalSaved) / input.goalMonths,
  );
  const base = createFinancialPlan({
    ...input,
    monthlyAvailable,
    monthlyExpenses,
    goalYears: input.goalMonths / 12,
  });
  // 고금리 부채가 있으면 투자 몫도 상환으로 돌린다. 목표 저축은 투자 수익 없이 계산한다.
  const debt = base.debt + (input.hasHighInterestDebt ? base.investment : 0);
  const emergency = Math.min(base.emergency, base.emergencyGap, monthlyAvailable - debt);
  const savings = input.hasHighInterestDebt
    ? monthlyAvailable - debt - emergency
    : Math.min(monthlyAvailable - debt - emergency, Math.max(base.savings, requiredSavings));
  const investment = monthlyAvailable - debt - emergency - savings;
  const monthlyPlan: MonthlyPlan = {
    monthlyIncome: input.monthlyIncome,
    monthlyExpenses,
    goalAmount: input.goalAmount,
    goalSaved: input.goalSaved,
    goalMonths: input.goalMonths,
    debt,
    emergency,
    savings,
    investment,
  };
  return {
    ...base,
    debt,
    emergency,
    savings,
    investment,
    monthlyExpenses,
    monthlyAvailable,
    requiredSavings,
    monthlyPlan,
    expenseDeficit: Math.max(0, monthlyExpenses - input.monthlyIncome),
    goalShortfall: Math.max(0, requiredSavings - savings),
    goalBalance: input.goalSaved + savings * input.goalMonths,
    monthsToGoal:
      requiredSavings === 0
        ? 0
        : savings > 0
          ? Math.ceil((input.goalAmount - input.goalSaved) / savings)
          : null,
  };
}

export function weeklyTargetsFromPlan(plan: MonthlyPlan, weekStart: string) {
  const date = new Date(`${weekStart}T00:00:00Z`);
  if (
    !/^\d{4}-\d{2}-\d{2}$/.test(weekStart) ||
    Number.isNaN(date.getTime()) ||
    date.toISOString().slice(0, 10) !== weekStart ||
    date.getUTCDay() !== 1
  ) {
    throw new Error('주 시작일은 월요일로 선택해 주세요.');
  }
  const days = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0)).getUTCDate();
  const firstDay = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), 1)).getUTCDay();
  const firstMonday = 1 + ((8 - firstDay) % 7);
  const weeks = Math.floor((days - firstMonday) / 7) + 1;
  const index = Math.floor((date.getUTCDate() - firstMonday) / 7);
  const split = (amount: number) => Math.floor(amount / weeks) + (index < amount % weeks ? 1 : 0);
  return {
    targetSavings: split(plan.savings + plan.emergency),
    targetInvestment: split(plan.investment),
    targetDebtPayment: split(plan.debt),
  };
}

function isMonthlyPlan(value: unknown): value is MonthlyPlan {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const plan = value as Record<string, unknown>;
  const valid =
    [
      'monthlyIncome',
      'monthlyExpenses',
      'goalAmount',
      'goalSaved',
      'savings',
      'emergency',
      'investment',
      'debt',
    ].every((key) => isMoney(plan[key])) &&
    typeof plan.goalMonths === 'number' &&
    Number.isInteger(plan.goalMonths) &&
    plan.goalMonths >= 1 &&
    plan.goalMonths <= 120;
  if (!valid) return false;
  const checked = value as MonthlyPlan;
  return (
    checked.savings + checked.emergency + checked.investment + checked.debt ===
    Math.max(0, checked.monthlyIncome - checked.monthlyExpenses)
  );
}

export type FinancialPlanInput = {
  monthlyAvailable: number;
  monthlyExpenses: number;
  emergencySavings: number;
  goalYears: number;
  risk: 'LOW' | 'MEDIUM' | 'HIGH';
  hasHighInterestDebt: boolean;
};

type DepositComparisonInput = {
  principal: number;
  monthlyDeposit: number;
  months: number;
  depositRate: number;
  savingsRate: number;
};

type YouthSavingsInput = {
  monthlyDeposit: number;
  annualRate: number;
  matchRate: number;
};

const INTEREST_TAX_RATE = 0.154;
const YOUTH_SAVINGS_MONTHS = 36;

export function calculateDepositComparison(input: DepositComparisonInput) {
  const depositInterest = input.principal * (input.depositRate / 100) * (input.months / 12);
  const savingsPrincipal = input.monthlyDeposit * input.months;
  const savingsInterest =
    input.monthlyDeposit *
    (input.savingsRate / 100 / 12) *
    ((input.months * (input.months + 1)) / 2);

  return {
    depositPrincipal: input.principal,
    depositInterestAfterTax: depositInterest * (1 - INTEREST_TAX_RATE),
    depositMaturity: input.principal + depositInterest * (1 - INTEREST_TAX_RATE),
    savingsPrincipal,
    savingsInterestAfterTax: savingsInterest * (1 - INTEREST_TAX_RATE),
    savingsMaturity: savingsPrincipal + savingsInterest * (1 - INTEREST_TAX_RATE),
  };
}

export function calculateYouthSavings(input: YouthSavingsInput) {
  const principal = input.monthlyDeposit * YOUTH_SAVINGS_MONTHS;
  const bankInterest =
    input.monthlyDeposit *
    (input.annualRate / 100 / 12) *
    ((YOUTH_SAVINGS_MONTHS * (YOUTH_SAVINGS_MONTHS + 1)) / 2);
  const governmentContribution = principal * input.matchRate;

  return {
    principal,
    bankInterest,
    governmentContribution,
    maturity: principal + bankInterest + governmentContribution,
  };
}

export function createFinancialPlan(input: FinancialPlanInput) {
  const monthlyAvailable = Math.max(0, input.monthlyAvailable);
  const emergencyTarget = Math.max(0, input.monthlyExpenses) * 3;
  const emergencyGap = Math.max(0, emergencyTarget - Math.max(0, input.emergencySavings));

  let ratios: { debt: number; emergency: number; savings: number; investment: number };
  let stage: string;

  if (input.hasHighInterestDebt) {
    ratios =
      emergencyGap > 0
        ? { debt: 0.45, emergency: 0.35, savings: 0.15, investment: 0.05 }
        : { debt: 0.6, emergency: 0, savings: 0.3, investment: 0.1 };
    stage = '고금리 부채와 비상금부터 정리하는 단계';
  } else if (emergencyGap > 0) {
    ratios = { debt: 0, emergency: 0.5, savings: 0.35, investment: 0.15 };
    stage = '비상금을 만들면서 소액 투자를 경험하는 단계';
  } else if (input.goalYears <= 3 || input.risk === 'LOW') {
    ratios = { debt: 0, emergency: 0, savings: 0.8, investment: 0.2 };
    stage = '원금 변동을 줄이고 저축을 중심으로 준비하는 단계';
  } else if (input.risk === 'MEDIUM') {
    ratios = { debt: 0, emergency: 0, savings: 0.5, investment: 0.5 };
    stage = '저축과 장기 투자를 균형 있게 병행하는 단계';
  } else {
    ratios = { debt: 0, emergency: 0, savings: 0.3, investment: 0.7 };
    stage = '긴 기간 동안 투자 변동을 감수하는 단계';
  }

  const debt = Math.round(monthlyAvailable * ratios.debt);
  const emergency = Math.round(monthlyAvailable * ratios.emergency);
  const savings = Math.round(monthlyAvailable * ratios.savings);
  const investment = monthlyAvailable - debt - emergency - savings;

  return { debt, emergency, savings, investment, emergencyTarget, emergencyGap, stage };
}

export function calculateInvestmentProjection(
  monthlyInvestment: number,
  years: number,
  annualRate: number,
) {
  const months = Math.max(0, Math.round(years * 12));
  const principal = Math.max(0, monthlyInvestment) * months;
  const monthlyRate = Math.max(0, annualRate) / 100 / 12;
  const estimatedValue =
    monthlyRate === 0
      ? principal
      : Math.max(0, monthlyInvestment) * (((1 + monthlyRate) ** months - 1) / monthlyRate);

  return {
    principal,
    estimatedValue,
    estimatedReturn: estimatedValue - principal,
    afterTwentyPercentDrop: estimatedValue * 0.8,
  };
}

export function isConsultation(value: unknown): value is Consultation {
  if (!value || typeof value !== 'object') return false;
  const record = value as Record<string, unknown>;

  return (
    typeof record.id === 'string' &&
    (record.topic === '예금·적금 비교' ||
      record.topic === '청년미래적금' ||
      record.topic === '저축·투자 계획') &&
    typeof record.title === 'string' &&
    typeof record.summary === 'string' &&
    Array.isArray(record.details) &&
    record.details.every((detail) => typeof detail === 'string') &&
    typeof record.createdAt === 'string' &&
    !Number.isNaN(Date.parse(record.createdAt)) &&
    (record.monthlyPlan === undefined || isMonthlyPlan(record.monthlyPlan)) &&
    (record.actions === undefined ||
      (Array.isArray(record.actions) &&
        record.actions.every(
          (action) =>
            action &&
            typeof action === 'object' &&
            typeof action.id === 'string' &&
            typeof action.label === 'string' &&
            typeof action.completed === 'boolean',
        )))
  );
}

export function loadConsultations(storage: Storage = localStorage): Consultation[] {
  try {
    const value: unknown = JSON.parse(storage.getItem(CONSULTATIONS_STORAGE_KEY) ?? '[]');
    return Array.isArray(value) ? value.filter(isConsultation) : [];
  } catch {
    return [];
  }
}

export function saveConsultation(consultation: Consultation, storage: Storage = localStorage) {
  const consultations = [
    consultation,
    ...loadConsultations(storage).filter((item) => item.id !== consultation.id),
  ];
  storage.setItem(CONSULTATIONS_STORAGE_KEY, JSON.stringify(consultations));
}

export function deleteConsultation(id: string, storage: Storage = localStorage) {
  storage.setItem(
    CONSULTATIONS_STORAGE_KEY,
    JSON.stringify(loadConsultations(storage).filter((consultation) => consultation.id !== id)),
  );
}

export function toggleConsultationAction(
  consultationId: string,
  actionId: string,
  storage: Storage = localStorage,
) {
  const consultations = loadConsultations(storage).map((consultation) =>
    consultation.id === consultationId
      ? {
          ...consultation,
          actions: consultation.actions?.map((action) =>
            action.id === actionId ? { ...action, completed: !action.completed } : action,
          ),
        }
      : consultation,
  );
  storage.setItem(CONSULTATIONS_STORAGE_KEY, JSON.stringify(consultations));
}

export function loadAccountSteps(storage: Storage = localStorage): string[] {
  try {
    const value: unknown = JSON.parse(storage.getItem(ACCOUNT_STEPS_STORAGE_KEY) ?? '[]');
    return Array.isArray(value) ? value.filter((step) => typeof step === 'string') : [];
  } catch {
    return [];
  }
}

export function saveAccountSteps(steps: string[], storage: Storage = localStorage) {
  storage.setItem(ACCOUNT_STEPS_STORAGE_KEY, JSON.stringify(steps));
}

export type PaymentDecisionInput = {
  canPayInFull: boolean;
  tracksSpending: boolean;
  monthlyCardSpending: number;
  expectedMonthlyBenefit: number;
  annualFee: number;
  usesRevolving: boolean;
};

export function recommendPaymentTool(input: PaymentDecisionInput) {
  const annualBenefit = Math.max(0, input.expectedMonthlyBenefit) * 12;
  const netBenefit = annualBenefit - Math.max(0, input.annualFee);
  const hasControlRisk = !input.canPayInFull || !input.tracksSpending || input.usesRevolving;

  if (hasControlRisk) {
    return {
      recommendation: 'CHECK' as const,
      title: '지금은 체크카드가 더 안전해요',
      reason: '혜택보다 연체·과소비 위험을 먼저 줄이는 단계예요.',
      netBenefit,
    };
  }

  if (input.monthlyCardSpending < 300000 || netBenefit <= 0) {
    return {
      recommendation: 'HYBRID' as const,
      title: '체크카드 중심으로 필요한 결제만 신용카드를 써보세요',
      reason: '예상 지출로는 연회비와 실적 조건을 채운 혜택이 크지 않아요.',
      netBenefit,
    };
  }

  return {
    recommendation: 'CREDIT' as const,
    title: '한도를 정한 신용카드 사용을 검토할 수 있어요',
    reason: '매달 전액 결제와 지출 관리가 가능하고 예상 혜택이 연회비보다 커요.',
    netBenefit,
  };
}

export type MockTradeForCalculation = {
  id: number;
  assetId: number;
  assetCode: string;
  assetName: string;
  category: 'STOCK' | 'ETF';
  market: 'KOSPI' | 'KOSDAQ';
  tradeType: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  tradedOn: string;
};

export function calculateMockPortfolio(
  trades: MockTradeForCalculation[],
  latestPrices: Record<number, number>,
) {
  const positions = new Map<
    number,
    Omit<MockTradeForCalculation, 'id' | 'tradeType' | 'price' | 'tradedOn'> & {
      quantity: number;
      costBasis: number;
      realizedProfit: number;
    }
  >();

  const sorted = [...trades].sort(
    (a, b) => a.tradedOn.localeCompare(b.tradedOn) || Math.abs(a.id) - Math.abs(b.id),
  );
  for (const trade of sorted) {
    const position = positions.get(trade.assetId) ?? {
      assetId: trade.assetId,
      assetCode: trade.assetCode,
      assetName: trade.assetName,
      category: trade.category,
      market: trade.market,
      quantity: 0,
      costBasis: 0,
      realizedProfit: 0,
    };
    if (trade.tradeType === 'BUY') {
      position.quantity += trade.quantity;
      position.costBasis += trade.quantity * trade.price;
    } else if (trade.quantity <= position.quantity) {
      const averagePrice = position.quantity === 0 ? 0 : position.costBasis / position.quantity;
      position.realizedProfit += (trade.price - averagePrice) * trade.quantity;
      position.quantity -= trade.quantity;
      position.costBasis = averagePrice * position.quantity;
    }
    positions.set(trade.assetId, position);
  }

  const active = [...positions.values()].filter((position) => position.quantity > 0);
  const totalEvaluation = active.reduce(
    (total, position) =>
      total +
      position.quantity *
        (latestPrices[position.assetId] ?? position.costBasis / position.quantity),
    0,
  );
  const holdings = active.map((position) => {
    const averagePrice = position.costBasis / position.quantity;
    const currentPrice = latestPrices[position.assetId] ?? averagePrice;
    const evaluation = position.quantity * currentPrice;
    const unrealizedProfit = evaluation - position.costBasis;
    return {
      ...position,
      averagePrice,
      currentPrice,
      evaluation,
      unrealizedProfit,
      returnRate: position.costBasis === 0 ? 0 : (unrealizedProfit / position.costBasis) * 100,
      weight: totalEvaluation === 0 ? 0 : (evaluation / totalEvaluation) * 100,
    };
  });

  return {
    holdings,
    totalCost: holdings.reduce((total, holding) => total + holding.costBasis, 0),
    totalEvaluation,
    totalUnrealizedProfit: holdings.reduce((total, holding) => total + holding.unrealizedProfit, 0),
    totalRealizedProfit: [...positions.values()].reduce(
      (total, position) => total + position.realizedProfit,
      0,
    ),
  };
}

export function estimateStockTax(
  market: 'DOMESTIC' | 'OVERSEAS',
  annualRealizedProfit: number,
  annualDividends: number,
) {
  const taxableCapitalGain =
    market === 'OVERSEAS' ? Math.max(0, annualRealizedProfit - 2_500_000) : 0;
  return {
    capitalGainsTax: taxableCapitalGain * 0.22,
    dividendIncomeTax: Math.max(0, annualDividends) * 0.154,
    taxableCapitalGain,
  };
}

export function calculateCheckInStreak(weekStarts: string[], currentWeekStart: string) {
  const completed = new Set(weekStarts);
  const cursor = new Date(`${currentWeekStart}T00:00:00Z`);
  let streak = 0;
  while (completed.has(cursor.toISOString().slice(0, 10))) {
    streak += 1;
    cursor.setUTCDate(cursor.getUTCDate() - 7);
  }
  return streak;
}
