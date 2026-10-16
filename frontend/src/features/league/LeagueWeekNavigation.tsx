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
  return (
    <select
      className="league-week-select"
      aria-label="조회 주차"
      value={selected || weeks[0].weekStart}
      onChange={(event) => onChange(event.target.value)}
    >
      {weeks.map((week, index) => (
        <option key={week.weekStart} value={week.weekStart}>
          {['이번 주', '지난주', '지지난주'][index]} · {week.weekStart.slice(5)} ~{' '}
          {week.weekEnd.slice(5)}
        </option>
      ))}
    </select>
  );
}
