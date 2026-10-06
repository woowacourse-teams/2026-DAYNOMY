import { request, requestWithCsrf } from '../../api/client';
import type {
  ExperienceLevel,
  FollowSummary,
  HoldingPeriod,
  InvestmentReview,
  InvestmentDecisionInput,
  InvestmentTransaction,
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

const experienceLevels = new Set<ExperienceLevel>([
  'BEGINNER',
  'ONE_TO_THREE_YEARS',
  'OVER_THREE_YEARS',
]);
const riskProfiles = new Set<RiskProfile>(['CONSERVATIVE', 'BALANCED', 'AGGRESSIVE']);
const leagueTypes = new Set<LeagueType>(['WEEKLY_RETURN', 'CONSISTENT', 'STABLE', 'BEGINNER']);
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
    (value.leagueEnabledAt === null || typeof value.leagueEnabledAt === 'string')
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
    isNumber(value.eightWeekReturnRate) &&
    isNumber(value.maxDrawdownRate) &&
    isNumber(value.volatilityRate) &&
    isNumber(value.maxHoldingWeight) &&
    Number.isInteger(value.decisionCount) &&
    isNumber(value.reviewCompletionRate) &&
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
    isNumber(performance.weeklyReturnRate) &&
    isNumber(performance.eightWeekReturnRate) &&
    isNumber(performance.maxDrawdownRate) &&
    isNumber(performance.volatilityRate) &&
    isNumber(performance.maxHoldingWeight) &&
    (allocation === null ||
      (isRecord(allocation) &&
        isNumber(allocation.stockWeight) &&
        isNumber(allocation.etfWeight))) &&
    Array.isArray(value.history) &&
    value.history.every(
      (point) =>
        isRecord(point) &&
        typeof point.weekStart === 'string' &&
        isNumber(point.weeklyReturnRate) &&
        isNumber(point.maxDrawdownRate),
    ) &&
    Number.isInteger(value.decisionCount) &&
    isNumber(value.reviewCompletionRate) &&
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
    typeof value.createdAt === 'string'
  );
}

export function isTransaction(value: unknown): value is InvestmentTransaction {
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
    holdingPeriods.has(value.expectedHoldingPeriod as HoldingPeriod) &&
    typeof value.expectedChange === 'string' &&
    typeof value.invalidationCondition === 'string' &&
    isNumber(value.maximumAcceptableLossRate) &&
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
    holdingPeriods.has(value.expectedHoldingPeriod as HoldingPeriod) &&
    typeof value.expectedChange === 'string' &&
    typeof value.invalidationCondition === 'string' &&
    isNumber(value.maximumAcceptableLossRate) &&
    typeof value.writtenAfterTrade === 'boolean' &&
    Array.isArray(value.reviews) &&
    value.reviews.every(isReview)
  );
}

function isInvestorDetail(value: unknown): value is InvestorDetail {
  return (
    isRecord(value) &&
    typeof value.publicId === 'string' &&
    typeof value.asOfDate === 'string' &&
    Array.isArray(value.holdings) &&
    value.holdings.every(
      (holding) =>
        isRecord(holding) &&
        typeof holding.assetName === 'string' &&
        typeof holding.category === 'string' &&
        (holding.weight === null || isNumber(holding.weight)) &&
        (holding.weeklyContributionRate === null || isNumber(holding.weeklyContributionRate)) &&
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
        typeof week.confirmed === 'boolean',
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
) {
  const query = new URLSearchParams({ leagueType, size: '100' });
  if (weekStart) query.set('weekStart', weekStart);
  const response = await request<unknown>(`/api/league/rankings?${query.toString()}`, { signal });
  if (!isRankingResponse(response)) throw new Error('리그 응답 형식이 올바르지 않습니다.');
  return response;
}

export async function getPublicInvestor(publicId: string, signal?: AbortSignal) {
  const response = await request<unknown>(`/api/league/investors/${encodeURIComponent(publicId)}`, {
    signal,
  });
  if (!isPublicInvestor(response)) throw new Error('투자자 응답 형식이 올바르지 않습니다.');
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

export async function getTransactions(signal?: AbortSignal) {
  const response = await request<unknown>('/api/users/me/portfolio/transactions', { signal });
  if (
    !isRecord(response) ||
    !Array.isArray(response.transactions) ||
    !response.transactions.every(isTransaction)
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
  if (!isTransaction(response)) throw new Error('판단 기록 응답 형식이 올바르지 않습니다.');
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
        (item.rank === null || Number.isInteger(item.rank)) &&
        (item.weeklyReturnRate === null || isNumber(item.weeklyReturnRate)) &&
        Number.isInteger(item.newReviewCount),
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
