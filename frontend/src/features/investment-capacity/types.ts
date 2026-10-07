export type InvestmentPlanStatus = 'GOAL_NOT_SET' | 'ACHIEVABLE' | 'ADJUSTABLE' | 'DIFFICULT';

export type InvestmentScenario = {
  type: 'STABLE' | 'BALANCED';
  label: string;
  description: string;
  monthlySaving: number;
  monthlyInvesting: number;
  monthlyEmergencyFund: number;
  expectedAmount: number;
  status: InvestmentPlanStatus;
  caution: string;
};

export type InvestmentProduct = {
  id: string;
  name: string;
  type: string;
  baseRate: number;
  maxRate: number;
  maxLimit: number;
  termMonths: number;
  monthlyDeposit: number;
  expectedMaturityAmount: number;
  expectedInterest: number;
  liquidity: string;
  earlyWithdrawalNote: string;
  depositProtection: boolean;
  eligibility: string;
  recommendationReason: string;
  dataNote: string;
  companyName: string;
  joinWay: string;
  benefitConditions: Array<{
    description: string;
    status: string;
  }>;
  amountConditions: Array<{
    description: string;
    thresholdAmount: number | null;
    status: string;
  }>;
  disclosureMonth: string;
  sourceUrl: string;
  termOptions: Array<{
    id: string;
    termMonths: number;
    baseRate: number;
    maxRate: number;
    monthlyDeposit: number;
    expectedMaturityAmount: number;
    expectedInterest: number;
  }>;
};

export type InvestmentPlan = {
  birthDate: string;
  monthlyIncome: number;
  monthlyFixedExpense: number;
  monthlyVariableExpense: number;
  irregularExpenseReserve: number;
  emergencyFundContribution: number;
  monthlyDebtRepayment: number;
  currentCash: number;
  existingDepositSavings: number;
  investmentAssets: number;
  otherAssets: number;
  totalAssets: number;
  availableCurrentCash: number;
  goalAmount: number;
  goalMonths: number;
  emergencyFundTarget: number;
  emergencyFundGap: number;
  safeMonthlyCapacity: number;
  requiredMonthlySaving: number;
  status: InvestmentPlanStatus;
  interpretation: string;
  selectedScenario: string | null;
  selectedProductId: string | null;
  scenarios: InvestmentScenario[];
  products: InvestmentProduct[];
};

export type InvestmentPlanRequest = {
  birthDate: string;
  monthlyIncome: number;
  monthlyFixedExpense: number;
  monthlyVariableExpense: number;
  irregularExpenseReserve: number;
  emergencyFundContribution: number;
  monthlyDebtRepayment: number;
  currentCash: number;
  existingDepositSavings: number;
  investmentAssets: number;
  otherAssets: number;
  goalAmount: number;
  goalMonths: number;
  selectedScenario: string | null;
  selectedProductId: string | null;
};
