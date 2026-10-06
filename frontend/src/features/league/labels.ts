import type { ExperienceLevel, HoldingPeriod, LeagueType, RiskProfile } from './types';

export const leagueLabels: Record<LeagueType, string> = {
  WEEKLY_RETURN: '이번 주 수익률',
  CONSISTENT: '꾸준한 투자',
  STABLE: '안정형',
  BEGINNER: '초보 리그',
};

export const experienceLabels: Record<ExperienceLevel, string> = {
  BEGINNER: '투자 1년 미만',
  ONE_TO_THREE_YEARS: '투자 1~3년',
  OVER_THREE_YEARS: '투자 3년 이상',
};

export const riskLabels: Record<RiskProfile, string> = {
  CONSERVATIVE: '안정형',
  BALANCED: '균형형',
  AGGRESSIVE: '적극형',
};

export const holdingPeriodLabels: Record<HoldingPeriod, string> = {
  UNDER_ONE_MONTH: '1개월 미만',
  ONE_TO_THREE_MONTHS: '1~3개월',
  THREE_TO_SIX_MONTHS: '3~6개월',
  OVER_SIX_MONTHS: '6개월 이상',
};

export function formatRate(value: number, signed = true) {
  const sign = signed && value > 0 ? '+' : '';
  return `${sign}${value.toFixed(2)}%`;
}
