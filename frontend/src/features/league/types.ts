export type CollectionStatus =
  'CALENDAR_PENDING' | 'IN_PROGRESS' | 'COLLECTION_PENDING' | 'COMPLETE' | 'NO_TRADING_DAYS';
export type ExperienceLevel = 'BEGINNER' | 'ONE_TO_THREE_YEARS' | 'OVER_THREE_YEARS';
export type RiskProfile = 'CONSERVATIVE' | 'BALANCED' | 'AGGRESSIVE';
export type LeagueType = 'WEEKLY_RETURN' | 'STABLE' | 'BEGINNER';
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
  trackingStartedOn?: string | null;
  eligibleFrom?: string | null;
};

export type RankingEntry = {
  rank: number;
  publicId: string;
  displayName: string;
  experienceLevel: ExperienceLevel;
  riskProfile: RiskProfile;
  weeklyReturnRate: number;
  eightWeekReturnRate: number | null;
  maxDrawdownRate: number | null;
  volatilityRate: number | null;
  maxHoldingWeight: number | null;
  decisionCount: number;
  reviewCompletionRate: number;
  followed: boolean;
  profileImageUrl?: string | null;
  cumulativeReturnRate?: number | null;
  trackingStartedOn?: string | null;
  currentHoldingWeight?: number | null;
  consecutiveRecordedWeeks?: number;
};

export type RankingResponse = {
  weekStart: string;
  weekEnd: string;
  leagueType: LeagueType;
  confirmed: boolean;
  totalCount: number;
  rankings: RankingEntry[];
  asOfDate: string | null;
  calculationVersion?: number | null;
  finalizedAt?: string | null;
  changeReason?: string | null;
  collectionStatus?: CollectionStatus;
};

export type LeagueWeek = {
  weekStart: string;
  weekEnd: string;
  confirmed: boolean;
  collectionStatus?: CollectionStatus;
};

export type Performance = {
  weeklyReturnRate: number | null;
  eightWeekReturnRate: number | null;
  maxDrawdownRate: number | null;
  volatilityRate: number | null;
  maxHoldingWeight: number | null;
  cumulativeReturnRate?: number | null;
  participationReturnRate?: number | null;
  trackingStartedOn?: string | null;
  eligibleFrom?: string | null;
  currentHoldingWeight?: number | null;
  consecutiveRecordedWeeks?: number;
  weeklyRank?: number | null;
};

export type DailyReturnPoint = {
  baseDate: string;
  dailyReturnRate: number | null;
  cumulativeReturnRate: number | null;
  status: 'CALCULATED' | 'PENDING' | 'EXCLUDED' | 'NOT_PARTICIPATING';
  reason: string | null;
};

export type DailyHistory = {
  weekStart: string;
  weekEnd: string;
  asOfDate: string | null;
  eligibleFrom: string | null;
  confirmed: boolean;
  weeklyReturnRate: number | null;
  days: DailyReturnPoint[];
  trackingStartedOn?: string | null;
  participationReturnRate?: number | null;
  collectionStatus?: CollectionStatus;
};

export type PublicInvestor = {
  publicId: string;
  displayName: string;
  bio: string;
  experienceLevel: ExperienceLevel;
  riskProfile: RiskProfile;
  performance: Performance;
  allocation: { stockWeight: number; etfWeight: number } | null;
  history: Array<{
    weekStart: string;
    weeklyReturnRate: number | null;
    maxDrawdownRate: number | null;
    status?: DailyReturnPoint['status'];
    confirmed?: boolean;
    collectionStatus?: CollectionStatus;
    weeklyRank?: number | null;
  }>;
  decisionCount: number;
  reviewCompletionRate: number;
  followed: boolean;
  detailAvailable: boolean;
  profileImageUrl?: string | null;
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
  expectedHoldingPeriod: HoldingPeriod | null;
  expectedChange: string;
  invalidationCondition: string;
  maximumAcceptableLossRate: number | null;
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
  expectedHoldingPeriod: HoldingPeriod | null;
  expectedChange: string;
  invalidationCondition: string;
  maximumAcceptableLossRate: number | null;
  writtenAfterTrade: boolean;
  reviews: InvestmentReview[];
};

export type PublicDecision = Omit<
  InvestmentTransaction,
  'id' | 'assetId' | 'assetCode' | 'quantity' | 'unitPrice' | 'fee'
> & { transactionId: number };

export type InvestorDetail = {
  publicId: string;
  asOfDate: string | null;
  holdings: Array<{
    assetName: string;
    category: string;
    weight: number | null;
    weeklyContributionRate: number | null;
    returnRate?: number | null;
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
  previousRank?: number | null;
  rankChange?: number | null;
  comparisonWeekStart?: string | null;
};
