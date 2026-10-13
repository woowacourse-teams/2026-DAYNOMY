import type { LeagueWeek } from './types';

// 리그를 시작한 주 이전의 서버 달력은 조회 대상에서 제외한다.
export const LEAGUE_FIRST_WEEK = '2026-10-12';

export function recentLeagueWeeks(weeks: LeagueWeek[]) {
  return weeks
    .filter((week) => week.weekStart >= LEAGUE_FIRST_WEEK)
    .sort((a, b) => b.weekStart.localeCompare(a.weekStart))
    .slice(0, 3);
}
