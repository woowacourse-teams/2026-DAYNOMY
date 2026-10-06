import { request } from '../../api/client';
import { getMockInvestmentCalendar } from './mockData';
import type {
  InvestmentCalendar,
  InvestmentCalendarEvent,
  InvestmentCalendarLoadResult,
  InvestmentCalendarScope,
  InvestmentEventDirection,
  InvestmentEventType,
  PortfolioEventAnalysisStatus,
  PortfolioEventImpactLevel,
  PortfolioReactionStatistics,
} from './types';

const EVENT_TYPES = new Set<InvestmentEventType>([
  'US_CPI',
  'KOREA_BASE_RATE',
  'CORPORATE_EARNINGS',
]);
const DIRECTIONS = new Set<InvestmentEventDirection>([
  'DECREASED',
  'UNCHANGED',
  'INCREASED',
  'UNAVAILABLE',
]);
const ANALYSIS_STATUSES = new Set<PortfolioEventAnalysisStatus>([
  'READY',
  'NO_PORTFOLIO',
  'INSUFFICIENT_DATA',
]);
const IMPACT_LEVELS = new Set<PortfolioEventImpactLevel>(['LOW', 'MEDIUM', 'HIGH']);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isNullableNumber(value: unknown): value is number | null {
  return value === null || (typeof value === 'number' && Number.isFinite(value));
}

function isDirection(value: unknown): value is InvestmentEventDirection {
  return typeof value === 'string' && DIRECTIONS.has(value as InvestmentEventDirection);
}

function isReaction(value: unknown): value is PortfolioReactionStatistics {
  return (
    isRecord(value) &&
    isDirection(value.direction) &&
    value.direction !== 'UNAVAILABLE' &&
    Number.isInteger(value.sampleCount) &&
    isNullableNumber(value.medianReturnRate) &&
    isNullableNumber(value.lowerReturnRate) &&
    isNullableNumber(value.upperReturnRate) &&
    isNullableNumber(value.lowerEstimatedAmount) &&
    isNullableNumber(value.upperEstimatedAmount)
  );
}

function isEvent(value: unknown): value is InvestmentCalendarEvent {
  if (!isRecord(value) || !isRecord(value.value) || !isRecord(value.source)) return false;
  if (!isRecord(value.portfolioAnalysis)) return false;
  const analysis = value.portfolioAnalysis;

  return (
    typeof value.id === 'number' &&
    typeof value.type === 'string' &&
    EVENT_TYPES.has(value.type as InvestmentEventType) &&
    typeof value.title === 'string' &&
    typeof value.announcedAt === 'string' &&
    typeof value.daysUntil === 'number' &&
    typeof value.upcoming === 'boolean' &&
    isNullableNumber(value.value.previousValue) &&
    isNullableNumber(value.value.actualValue) &&
    typeof value.value.unit === 'string' &&
    isDirection(value.value.direction) &&
    typeof value.source.name === 'string' &&
    typeof value.source.url === 'string' &&
    typeof analysis.status === 'string' &&
    ANALYSIS_STATUSES.has(analysis.status as PortfolioEventAnalysisStatus) &&
    typeof analysis.impactLevel === 'string' &&
    IMPACT_LEVELS.has(analysis.impactLevel as PortfolioEventImpactLevel) &&
    Number.isInteger(analysis.relatedAssetCount) &&
    typeof analysis.totalEvaluationAmount === 'number' &&
    (analysis.priceBaseDate === null || typeof analysis.priceBaseDate === 'string') &&
    Array.isArray(analysis.historicalReactions) &&
    analysis.historicalReactions.every(isReaction)
  );
}

export function isInvestmentCalendar(value: unknown): value is InvestmentCalendar {
  return (
    isRecord(value) &&
    Number.isInteger(value.year) &&
    Number.isInteger(value.month) &&
    Array.isArray(value.events) &&
    value.events.every(isEvent)
  );
}

function shouldUseMockData() {
  return import.meta.env.DEV && import.meta.env.VITE_INVESTMENT_CALENDAR_MOCK_ENABLED === 'true';
}

export async function getInvestmentCalendar(
  year: number,
  month: number,
  scope: InvestmentCalendarScope,
  signal?: AbortSignal,
): Promise<InvestmentCalendarLoadResult> {
  if (shouldUseMockData()) {
    return { calendar: getMockInvestmentCalendar(year, month), isDemo: true };
  }

  const query = new URLSearchParams({ year: String(year), month: String(month), scope });
  const response = await request<unknown>(`/api/users/me/investment-calendar?${query.toString()}`, {
    signal,
  });
  if (!isInvestmentCalendar(response)) {
    throw new Error('투자 캘린더 API 응답 형식이 올바르지 않습니다.');
  }
  return { calendar: response, isDemo: false };
}
