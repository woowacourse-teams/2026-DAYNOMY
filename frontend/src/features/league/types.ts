export type ExperienceLevel = 'BEGINNER' | 'ONE_TO_THREE_YEARS' | 'OVER_THREE_YEARS';
export type RiskProfile = 'CONSERVATIVE' | 'BALANCED' | 'AGGRESSIVE';
export type LeagueType = 'WEEKLY_RETURN' | 'CONSISTENT' | 'STABLE' | 'BEGINNER';
export type TransactionType = 'BUY' | 'SELL' | 'HOLD';
export type HoldingPeriod =
  'UNDER_ONE_MONTH' | 'ONE_TO_THREE_MONTHS' | 'THREE_TO_SIX_MONTHS' | 'OVER_SIX_MONTHS';

export type InvestorProfile = {
  publicId: string;
  displayName: string;
  bio: string;
  experienceLevel: ExperienceLevel;
  riskProfile: RiskProfile;
  profilePublic: boolean;
  leagueEnabled: boolean;
  allocationPublic: boolean;
  detailPublic: boolean;
  leagueEnabledAt: string | null;
};

export type RankingEntry = {
  rank: number;
  publicId: string;
  displayName: string;
  experienceLevel: ExperienceLevel;
  riskProfile: RiskProfile;
  weeklyReturnRate: number;
  eightWeekReturnRate: number;
  maxDrawdownRate: number;
  volatilityRate: number;
  maxHoldingWeight: number;
  decisionCount: number;
  reviewCompletionRate: number;
  followed: boolean;
};

export type RankingResponse = {
  weekStart: string;
  weekEnd: string;
  leagueType: LeagueType;
  confirmed: boolean;
  totalCount: number;
  rankings: RankingEntry[];
};

export type LeagueWeek = { weekStart: string; weekEnd: string; confirmed: boolean };

export type Performance = {
  weeklyReturnRate: number;
  eightWeekReturnRate: number;
  maxDrawdownRate: number;
  volatilityRate: number;
  maxHoldingWeight: number;
};

export type PublicInvestor = {
  publicId: string;
  displayName: string;
  bio: string;
  experienceLevel: ExperienceLevel;
  riskProfile: RiskProfile;
  performance: Performance;
  allocation: { stockWeight: number; etfWeight: number } | null;
  history: Array<{ weekStart: string; weeklyReturnRate: number; maxDrawdownRate: number }>;
  decisionCount: number;
  reviewCompletionRate: number;
  followed: boolean;
  detailAvailable: boolean;
};

export type InvestmentReview = {
  id: number;
  actualResult: string;
  differenceFromExpectation: string;
  nextAction: string;
  createdAt: string;
};

export type InvestmentDecisionInput = {
  reason: string;
  expectedHoldingPeriod: HoldingPeriod;
  expectedChange: string;
  invalidationCondition: string;
  maximumAcceptableLossRate: number;
};

export type InvestmentTransaction = {
  id: number;
  assetId: number;
  assetCode: string;
  assetName: string;
  category: string;
  transactionType: TransactionType;
  quantity: number;
  unitPrice: number;
  fee: number;
  tradedOn: string;
  reason: string;
  expectedHoldingPeriod: HoldingPeriod;
  expectedChange: string;
  invalidationCondition: string;
  maximumAcceptableLossRate: number;
  writtenAfterTrade: boolean;
  reviews: InvestmentReview[];
};

export type PublicDecision = Omit<
  InvestmentTransaction,
  'id' | 'assetId' | 'assetCode' | 'quantity' | 'unitPrice' | 'fee'
> & { transactionId: number };

export type InvestorDetail = {
  publicId: string;
  asOfDate: string;
  holdings: Array<{
    assetName: string;
    category: string;
    weight: number | null;
    weeklyContributionRate: number | null;
    reason?: string;
  }>;
  decisions: PublicDecision[];
};

export type FollowSummary = {
  publicId: string;
  displayName: string;
  rank: number | null;
  weeklyReturnRate: number | null;
  newReviewCount: number;
};
