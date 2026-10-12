import type {
  CollectionStatus,
  ExperienceLevel,
  HoldingPeriod,
  LeagueType,
  RiskProfile,
  TransactionType,
} from './types';

export const leagueLabels: Record<LeagueType, string> = {
  WEEKLY_RETURN: '주간 수익률',
  STABLE: '안정형',
  BEGINNER: '초보리그',
};

export const leagueCriteria: Record<LeagueType, string> = {
  WEEKLY_RETURN: '주간 수익률순',
  STABLE: '주간 수익률 · 변동성 감점',
  BEGINNER: '투자 경력 1년 미만 · 주간 수익률순',
};

export const experienceLabels: Record<ExperienceLevel, string> = {
  BEGINNER: '투자 1년 미만',
  ONE_TO_THREE_YEARS: '투자 1~3년',
  OVER_THREE_YEARS: '투자 3년 이상',
};

export const riskLabels: Record<RiskProfile, string> = {
  CONSERVATIVE: '보수형',
  BALANCED: '중립형',
  AGGRESSIVE: '공격형',
};

export const holdingPeriodLabels: Record<HoldingPeriod, string> = {
  UNDER_ONE_MONTH: '1개월 미만',
  ONE_TO_THREE_MONTHS: '1~3개월',
  THREE_TO_SIX_MONTHS: '3~6개월',
  OVER_SIX_MONTHS: '6개월 이상',
};

export const transactionLabels: Record<TransactionType, string> = {
  HOLD: '보유 기록',
  BUY: '매수 기록',
  SELL: '매도 기록',
};

export function formatRate(value: number | null | undefined, signed = true) {
  if (value == null) return '데이터 부족';
  const sign = signed && value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}

export const collectionLabels: Record<CollectionStatus, string> = {
  CALENDAR_PENDING: '거래일 확인 대기',
  IN_PROGRESS: '집계 중',
  COLLECTION_PENDING: '종가 수집 대기',
  COMPLETE: '종가 수집 완료',
  NO_TRADING_DAYS: '휴장 주',
};

export function concentrationLabel(value: number | null | undefined) {
  return value == null ? '데이터 부족' : value < 30 ? '분산' : value < 50 ? '보통' : '집중 주의';
}
