import type { LeagueWeek } from './types';

export function LeagueWeekNavigation({
  weeks,
  selected,
  onChange,
}: {
  weeks: LeagueWeek[];
  selected: string;
  onChange: (weekStart: string) => void;
}) {
  if (weeks.length <= 1) {
    return (
      <span className="league-week-period">
        이번 주{weeks[0] ? ` · ${weeks[0].weekStart.slice(5)} ~ ${weeks[0].weekEnd.slice(5)}` : ''}
      </span>
    );
  }
  const index = Math.max(
    0,
    weeks.findIndex((week) => week.weekStart === selected),
  );
  const week = weeks[index];
  return (
    <nav className="league-week-navigation" aria-label="조회 주차">
      <button
        type="button"
        aria-label="이전 주차"
        disabled={index === weeks.length - 1}
        onClick={() => onChange(weeks[index + 1].weekStart)}
      >
        <span aria-hidden="true">‹</span>
      </button>
      <span aria-live="polite">
        {['이번 주', '지난주', '지지난주'][index]} · {week.weekStart.slice(5)} ~{' '}
        {week.weekEnd.slice(5)}
      </span>
      <button
        type="button"
        aria-label="다음 주차"
        disabled={index === 0}
        onClick={() => onChange(weeks[index - 1].weekStart)}
      >
        <span aria-hidden="true">›</span>
      </button>
    </nav>
  );
}
