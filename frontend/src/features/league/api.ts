import { request, requestWithCsrf } from '../../api/client';
import { trackLeagueEvent } from './analytics';
import type {
  DailyHistory,
  ExperienceLevel,
  FollowSummary,
  HoldingPeriod,
  InvestmentReview,
  InvestmentDecisionInput,
  InvestmentDecisionRecord,
  InvestorProfile,
  LeagueType,
  LeagueWeek,
  InvestorDetail,
  PublicInvestor,
  RankingEntry,
  RankingResponse,
  RiskProfile,
  TransactionType,
} from './types';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function isNullableNumber(value: unknown): value is number | null {
  return value === null || isNumber(value);
}

function isDate(value: unknown): value is string {
  return (
    typeof value === 'string' &&
    /^\d{4}-\d{2}-\d{2}$/.test(value) &&
    !Number.isNaN(Date.parse(value)) &&
    new Date(value).toISOString().slice(0, 10) === value
  );
}

function optionalCollectionStatus(value: unknown) {
  return (
    value === undefined ||
    [
      'CALENDAR_PENDING',
      'IN_PROGRESS',
      'COLLECTION_PENDING',
      'COMPLETE',
      'NO_TRADING_DAYS',
    ].includes(String(value))
  );
}

function optionalNumber(value: unknown) {
  return value === undefined || isNullableNumber(value);
}
function optionalDate(value: unknown) {
  return value === undefined || value === null || isDate(value);
}
function isNullableInstant(value: unknown) {
  return (
    value === null ||
    (typeof value === 'string' &&
      /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(value) &&
      isDate(value.slice(0, 10)) &&
      Number.isFinite(Date.parse(value)))
  );
}
function optionalAggregation(value: unknown) {
  return (
    value === undefined ||
    value === null ||
    (isRecord(value) &&
      (value.baseDate === null || isDate(value.baseDate)) &&
      isNullableInstant(value.expectedAt) &&
      isNullableInstant(value.firstReturnExpectedAt) &&
      typeof value.reason === 'string' &&
      typeof value.delayed === 'boolean')
  );
}
function optionalImage(value: unknown) {
  return (
    value === undefined ||
    value === null ||
    (typeof value === 'string' && /^https:\/\//.test(value))
  );
}
function isPerformanceExtension(value: Record<string, unknown>) {
  return (
    optionalDate(value.trackingStartedOn) &&
    optionalDate(value.eligibleFrom) &&
    optionalNumber(value.currentHoldingWeight) &&
    (value.consecutiveRecordedWeeks === undefined ||
      (Number.isInteger(value.consecutiveRecordedWeeks) &&
        Number(value.consecutiveRecordedWeeks) >= 0))
  );
}

const experienceLevels = new Set<ExperienceLevel>([
  'BEGINNER',
  'ONE_TO_THREE_YEARS',
  'OVER_THREE_YEARS',
]);
const riskProfiles = new Set<RiskProfile>(['CONSERVATIVE', 'BALANCED', 'AGGRESSIVE']);
const leagueTypes = new Set<LeagueType>(['WEEKLY_RETURN', 'STABLE', 'BEGINNER']);
const transactionTypes = new Set<TransactionType>(['BUY', 'SELL', 'HOLD']);
const holdingPeriods = new Set<HoldingPeriod>([
  'UNDER_ONE_MONTH',
  'ONE_TO_THREE_MONTHS',
  'THREE_TO_SIX_MONTHS',
  'OVER_SIX_MONTHS',
]);

function isProfile(value: unknown): value is InvestorProfile {
  return (
    isRecord(value) &&
    typeof value.publicId === 'string' &&
    typeof value.displayName === 'string' &&
    typeof value.bio === 'string' &&
    experienceLevels.has(value.experienceLevel as ExperienceLevel) &&
    riskProfiles.has(value.riskProfile as RiskProfile) &&
    typeof value.profilePublic === 'boolean' &&
    typeof value.leagueEnabled === 'boolean' &&
    typeof value.allocationPublic === 'boolean' &&
    typeof value.detailPublic === 'boolean' &&
    (value.leagueEnabledAt === null || typeof value.leagueEnabledAt === 'string') &&
    optionalDate(value.trackingStartedOn) &&
    optionalDate(value.eligibleFrom)
  );
}

function isRankingEntry(value: unknown): value is RankingEntry {
  return (
    isRecord(value) &&
    Number.isInteger(value.rank) &&
    typeof value.publicId === 'string' &&
    typeof value.displayName === 'string' &&
    experienceLevels.has(value.experienceLevel as ExperienceLevel) &&
    riskProfiles.has(value.riskProfile as RiskProfile) &&
    isNumber(value.weeklyReturnRate) &&
    optionalNumber(value.threeWeekReturnRate) &&
    isNullableNumber(value.maxDrawdownRate) &&
    isNullableNumber(value.volatilityRate) &&
    isNullableNumber(value.maxHoldingWeight) &&
    Number.isInteger(value.decisionCount) &&
    isNumber(value.reviewCompletionRate) &&
    isPerformanceExtension(value) &&
    optionalImage(value.profileImageUrl) &&
    typeof value.followed === 'boolean'
  );
}

function isRankingResponse(value: unknown): value is RankingResponse {
  return (
    isRecord(value) &&
    typeof value.weekStart === 'string' &&
    typeof value.weekEnd === 'string' &&
    leagueTypes.has(value.leagueType as LeagueType) &&
    typeof value.confirmed === 'boolean' &&
    optionalCollectionStatus(value.collectionStatus) &&
    optionalAggregation(value.aggregation) &&
    optionalNumber(value.calculationVersion) &&
    (value.finalizedAt === undefined ||
      value.finalizedAt === null ||
      typeof value.finalizedAt === 'string') &&
    (value.changeReason === undefined ||
      value.changeReason === null ||
      typeof value.changeReason === 'string') &&
    (value.asOfDate === null || isDate(value.asOfDate)) &&
    Number.isInteger(value.totalCount) &&
    Array.isArray(value.rankings) &&
    value.rankings.every(isRankingEntry)
  );
}

function isPublicInvestor(value: unknown): value is PublicInvestor {
  if (!isRecord(value) || !isRecord(value.performance)) return false;
  const performance = value.performance;
  const allocation = value.allocation;
  return (
    typeof value.publicId === 'string' &&
    typeof value.displayName === 'string' &&
    typeof value.bio === 'string' &&
    experienceLevels.has(value.experienceLevel as ExperienceLevel) &&
    riskProfiles.has(value.riskProfile as RiskProfile) &&
    isNullableNumber(performance.weeklyReturnRate) &&
    optionalNumber(performance.threeWeekReturnRate) &&
    isNullableNumber(performance.maxDrawdownRate) &&
    isNullableNumber(performance.volatilityRate) &&
    isNullableNumber(performance.maxHoldingWeight) &&
    isPerformanceExtension(performance) &&
    (performance.weeklyRank === undefined ||
      performance.weeklyRank === null ||
      (Number.isInteger(performance.weeklyRank) && Number(performance.weeklyRank) > 0)) &&
    optionalImage(value.profileImageUrl) &&
    (allocation === null ||
      (isRecord(allocation) &&
        isNumber(allocation.stockWeight) &&
        isNumber(allocation.etfWeight))) &&
    Array.isArray(value.history) &&
    value.history.every(
      (point) =>
        isRecord(point) &&
        typeof point.weekStart === 'string' &&
        isNullableNumber(point.weeklyReturnRate) &&
        isNullableNumber(point.maxDrawdownRate) &&
        (point.status === undefined ||
          ['CALCULATED', 'PENDING', 'EXCLUDED', 'NOT_PARTICIPATING'].includes(
            String(point.status),
          )) &&
        (point.confirmed === undefined || typeof point.confirmed === 'boolean') &&
        optionalCollectionStatus(point.collectionStatus) &&
        (point.weeklyRank === undefined ||
          point.weeklyRank === null ||
          (Number.isInteger(point.weeklyRank) && Number(point.weeklyRank) > 0)),
    ) &&
    Number.isInteger(value.decisionCount) &&
    isNumber(value.reviewCompletionRate) &&
    isPerformanceExtension(value) &&
    optionalImage(value.profileImageUrl) &&
    typeof value.followed === 'boolean' &&
    typeof value.detailAvailable === 'boolean'
  );
}

function isReview(value: unknown): value is InvestmentReview {
  return (
    isRecord(value) &&
    Number.isInteger(value.id) &&
    typeof value.actualResult === 'string' &&
    typeof value.differenceFromExpectation === 'string' &&
    typeof value.nextAction === 'string' &&
    typeof value.createdAt === 'string' &&
    (value.updatedAt === undefined ||
      value.updatedAt === null ||
      typeof value.updatedAt === 'string')
  );
}

export function isDecisionRecord(value: unknown): value is InvestmentDecisionRecord {
  return (
    isRecord(value) &&
    Number.isInteger(value.id) &&
    Number.isInteger(value.assetId) &&
    typeof value.assetCode === 'string' &&
    typeof value.assetName === 'string' &&
    typeof value.category === 'string' &&
    transactionTypes.has(value.transactionType as TransactionType) &&
    Number.isInteger(value.quantity) &&
    isNumber(value.unitPrice) &&
    isNumber(value.fee) &&
    typeof value.tradedOn === 'string' &&
    typeof value.reason === 'string' &&
    (value.expectedHoldingPeriod === null ||
      holdingPeriods.has(value.expectedHoldingPeriod as HoldingPeriod)) &&
    typeof value.expectedChange === 'string' &&
    typeof value.invalidationCondition === 'string' &&
    isNullableNumber(value.maximumAcceptableLossRate) &&
    typeof value.writtenAfterTrade === 'boolean' &&
    Array.isArray(value.reviews) &&
    value.reviews.every(isReview)
  );
}

function isPublicDecision(value: unknown): value is InvestorDetail['decisions'][number] {
  return (
    isRecord(value) &&
    Number.isInteger(value.transactionId) &&
    typeof value.assetName === 'string' &&
    typeof value.category === 'string' &&
    transactionTypes.has(value.transactionType as TransactionType) &&
    typeof value.tradedOn === 'string' &&
    typeof value.reason === 'string' &&
    (value.expectedHoldingPeriod === null ||
      holdingPeriods.has(value.expectedHoldingPeriod as HoldingPeriod)) &&
    typeof value.expectedChange === 'string' &&
    typeof value.invalidationCondition === 'string' &&
    isNullableNumber(value.maximumAcceptableLossRate) &&
    typeof value.writtenAfterTrade === 'boolean' &&
    Array.isArray(value.reviews) &&
    value.reviews.every(isReview)
  );
}

function isInvestorDetail(value: unknown): value is InvestorDetail {
  return (
    isRecord(value) &&
    typeof value.publicId === 'string' &&
    (value.asOfDate === null || isDate(value.asOfDate)) &&
    Array.isArray(value.holdings) &&
    value.holdings.every(
      (holding) =>
        isRecord(holding) &&
        typeof holding.assetName === 'string' &&
        typeof holding.category === 'string' &&
        (holding.weight === null || isNumber(holding.weight)) &&
        (holding.weeklyContributionRate === null || isNumber(holding.weeklyContributionRate)) &&
        optionalNumber(holding.returnRate) &&
        (holding.reason === undefined || typeof holding.reason === 'string'),
    ) &&
    Array.isArray(value.decisions) &&
    value.decisions.every(isPublicDecision)
  );
}

export async function getLeagueWeeks(signal?: AbortSignal) {
  const response = await request<unknown>('/api/league/weeks', { signal });
  if (
    !isRecord(response) ||
    !Array.isArray(response.weeks) ||
    !response.weeks.every(
      (week): week is LeagueWeek =>
        isRecord(week) &&
        typeof week.weekStart === 'string' &&
        typeof week.weekEnd === 'string' &&
        typeof week.confirmed === 'boolean' &&
        optionalCollectionStatus(week.collectionStatus),
    )
  ) {
    throw new Error('리그 주차 응답 형식이 올바르지 않습니다.');
  }
  return response.weeks;
}

export async function getRankings(
  leagueType: LeagueType,
  weekStart?: string,
  signal?: AbortSignal,
  page = 0,
) {
  const query = new URLSearchParams({ leagueType, size: '20', page: String(page) });
  if (weekStart) query.set('weekStart', weekStart);
  const response = await request<unknown>(`/api/league/rankings?${query.toString()}`, { signal });
  if (!isRankingResponse(response)) throw new Error('리그 응답 형식이 올바르지 않습니다.');
  return {
    ...response,
    rankings: response.rankings.map((entry) => ({
      ...entry,
      threeWeekReturnRate: entry.threeWeekReturnRate ?? null,
    })),
  };
}

export async function getPublicInvestor(publicId: string, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/league/investors/${encodeURIComponent(publicId)}`, {
    signal,
  });
  if (!isPublicInvestor(response)) throw new Error('투자자 응답 형식이 올바르지 않습니다.');
  return {
    ...response,
    performance: {
      ...response.performance,
      threeWeekReturnRate: response.performance.threeWeekReturnRate ?? null,
    },
  };
}

function isDailyHistory(value: unknown): value is DailyHistory {
  if (
    !isRecord(value) ||
    !isDate(value.weekStart) ||
    !isDate(value.weekEnd) ||
    Date.parse(value.weekEnd) - Date.parse(value.weekStart) !== 6 * 86400000 ||
    new Date(value.weekStart).getUTCDay() !== 1 ||
    !(value.asOfDate === null || isDate(value.asOfDate)) ||
    !(value.eligibleFrom === null || isDate(value.eligibleFrom)) ||
    typeof value.confirmed !== 'boolean' ||
    !isNullableNumber(value.weeklyReturnRate) ||
    !optionalCollectionStatus(value.collectionStatus) ||
    !optionalDate(value.trackingStartedOn) ||
    !optionalAggregation(value.aggregation) ||
    !Array.isArray(value.days)
  )
    return false;
  let previousDate = '';
  let complete = true;
  let begun = false;
  let asOfDate: string | null = null;
  let latestCumulativeReturnRate: number | null = null;
  for (const day of value.days) {
    if (
      !isRecord(day) ||
      !isDate(day.baseDate) ||
      day.baseDate < value.weekStart ||
      day.baseDate > value.weekEnd ||
      day.baseDate <= previousDate ||
      !isNullableNumber(day.dailyReturnRate) ||
      !isNullableNumber(day.cumulativeReturnRate) ||
      !(day.reason === null || typeof day.reason === 'string') ||
      !['CALCULATED', 'PENDING', 'EXCLUDED', 'NOT_PARTICIPATING'].includes(String(day.status))
    )
      return false;
    previousDate = day.baseDate;
    const calculated = day.status === 'CALCULATED';
    if (calculated !== (day.dailyReturnRate !== null)) return false;
    if (calculated) begun = true;
    if (!calculated && (begun || day.status !== 'NOT_PARTICIPATING')) complete = false;
    if ((complete && calculated) !== (day.cumulativeReturnRate !== null)) return false;
    if (calculated) {
      asOfDate = day.baseDate;
      latestCumulativeReturnRate = day.cumulativeReturnRate;
    }
  }
  return (
    value.asOfDate === asOfDate &&
    (value.weeklyReturnRate === latestCumulativeReturnRate ||
      (!complete && value.weeklyReturnRate === null)) &&
    (!value.confirmed || (complete && value.weeklyReturnRate !== null))
  );
}

export async function getDailyHistory(publicId: string, weekStart?: string, signal?: AbortSignal) {
  const query = weekStart ? `?${new URLSearchParams({ weekStart })}` : '';
  const response = await request<unknown>(
    `/api/league/investors/${encodeURIComponent(publicId)}/daily-history${query}`,
    { signal },
  );
  if (!isDailyHistory(response)) {
    throw new Error('일별 수익률 응답 형식이 올바르지 않습니다.');
  }
  return response;
}

export async function getMyInvestorProfile(signal?: AbortSignal) {
  const response = await request<unknown>('/api/users/me/investor-profile', { signal });
  if (response === undefined) return null;
  if (!isProfile(response)) throw new Error('공개 프로필 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function saveInvestorProfile(input: {
  displayName: string;
  bio: string;
  experienceLevel: ExperienceLevel;
  riskProfile: RiskProfile;
}) {
  const response = await requestWithCsrf<unknown>('/api/users/me/investor-profile', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (!isProfile(response)) throw new Error('공개 프로필 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function savePublication(input: {
  profilePublic: boolean;
  leagueEnabled: boolean;
  allocationPublic: boolean;
  detailPublic: boolean;
}) {
  const response = await requestWithCsrf<unknown>('/api/users/me/portfolio/publication', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (!isProfile(response)) throw new Error('공개 설정 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function getInvestmentDecisions(signal?: AbortSignal) {
  const response = await request<unknown>('/api/users/me/portfolio/transactions', { signal });
  if (
    !isRecord(response) ||
    !Array.isArray(response.transactions) ||
    !response.transactions.every(isDecisionRecord)
  ) {
    throw new Error('투자 기록 응답 형식이 올바르지 않습니다.');
  }
  return response.transactions;
}

export async function recordDecision(input: {
  requestKey: string;
  assetId: number;
  decision: InvestmentDecisionInput;
}) {
  const response = await requestWithCsrf<unknown>('/api/users/me/portfolio/decisions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (!isDecisionRecord(response)) throw new Error('투자 기록 응답 형식이 올바르지 않습니다.');
  trackLeagueEvent('save_investment_decision', { record_type: response.transactionType });
  return response;
}

export async function updateDecisionReason(transactionId: number, reason: string) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/decisions/${transactionId}/reason`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason }),
    },
  );
  if (!isDecisionRecord(response)) throw new Error('투자 기록 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function createReview(
  transactionId: number,
  input: { actualResult: string; differenceFromExpectation: string; nextAction: string },
) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/decisions/${transactionId}/reviews`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  );
  if (!isReview(response)) throw new Error('복기 응답 형식이 올바르지 않습니다.');
  trackLeagueEvent('save_investment_review', { origin: 'shared_portfolio' });
  return response;
}

export async function updateReview(
  transactionId: number,
  reviewId: number,
  input: { actualResult: string; differenceFromExpectation: string; nextAction: string },
) {
  const response = await requestWithCsrf<unknown>(
    `/api/users/me/portfolio/decisions/${transactionId}/reviews/${reviewId}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    },
  );
  if (!isReview(response)) throw new Error('복기 응답 형식이 올바르지 않습니다.');
  trackLeagueEvent('edit_investment_review', { origin: 'shared_portfolio' });
  return response;
}

export function followInvestor(publicId: string) {
  return requestWithCsrf<void>(`/api/users/me/league/follows/${encodeURIComponent(publicId)}`, {
    method: 'POST',
  });
}

export function unfollowInvestor(publicId: string) {
  return requestWithCsrf<void>(`/api/users/me/league/follows/${encodeURIComponent(publicId)}`, {
    method: 'DELETE',
  });
}

export async function getFollowSummary(signal?: AbortSignal) {
  const response = await request<unknown>('/api/users/me/league/follows/summary', { signal });
  if (
    !isRecord(response) ||
    !Array.isArray(response.investors) ||
    !response.investors.every(
      (item): item is FollowSummary =>
        isRecord(item) &&
        typeof item.publicId === 'string' &&
        typeof item.displayName === 'string' &&
        (item.rank === null || (Number.isInteger(item.rank) && Number(item.rank) > 0)) &&
        (item.weeklyReturnRate === null || isNumber(item.weeklyReturnRate)) &&
        Number.isInteger(item.newReviewCount) &&
        Number(item.newReviewCount) >= 0 &&
        (item.previousRank == null ||
          (Number.isInteger(item.previousRank) && Number(item.previousRank) > 0)) &&
        (item.rankChange == null ||
          (Number.isInteger(item.rankChange) &&
            typeof item.rank === 'number' &&
            typeof item.previousRank === 'number' &&
            item.rankChange === item.previousRank - item.rank)) &&
        optionalDate(item.comparisonWeekStart),
    )
  ) {
    throw new Error('팔로우 요약 응답 형식이 올바르지 않습니다.');
  }
  return response.investors;
}

export async function getInvestorDetail(publicId: string, signal?: AbortSignal) {
  const response = await request<unknown>(
    `/api/league/investors/${encodeURIComponent(publicId)}/details`,
    { signal },
  );
  if (!isInvestorDetail(response)) {
    throw new Error('투자 상세 응답 형식이 올바르지 않습니다.');
  }
  return response;
}
