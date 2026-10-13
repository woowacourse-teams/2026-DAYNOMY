import { useEffect, useState } from 'react';
import { getDailyHistory, getLeagueWeeks } from './api';
import { recentLeagueWeeks } from './weeks';
import { LeagueWeekNavigation } from './LeagueWeekNavigation';
import { formatRate, collectionLabels } from './labels';
import type { DailyHistory, DailyReturnPoint, LeagueWeek } from './types';

const dayFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  timeZone: 'UTC',
});

function statusLabel(day: DailyReturnPoint) {
  if (day.status === 'CALCULATED')
    return day.reason === 'TRACKING_BASELINE' ? '성과 시작 종가 · 0%' : '집계 완료';
  if (day.status === 'NOT_PARTICIPATING') return '참여 전 / 미참여';
  if (day.status === 'PENDING') return '집계 대기';
  switch (day.reason) {
    case 'MISSING_PRICE':
      return '종가 누락 · 집계 대기';
    case 'NO_HOLDINGS':
      return '공유 자산 없음';
    case 'PRICE_COLLECTION_PENDING':
      return '종가 수집 대기';
    case 'UNSUPPORTED_ASSET':
      return '지원하지 않는 자산';
    default:
      return '집계 제외';
  }
}

export function DailyReturns({
  publicId,
  weekStart,
  onWeekChange,
}: {
  publicId: string;
  weekStart: string;
  onWeekChange: (weekStart: string) => void;
}) {
  const [history, setHistory] = useState<DailyHistory | null>(null);
  const [weeks, setWeeks] = useState<LeagueWeek[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    function refreshWhenVisible() {
      if (document.visibilityState === 'visible') setRetry((value) => value + 1);
    }
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => document.removeEventListener('visibilitychange', refreshWhenVisible);
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setHistory(null);
    getLeagueWeeks(controller.signal)
      .then(async (availableWeeks) => {
        if (controller.signal.aborted) return;
        const recentWeeks = recentLeagueWeeks(availableWeeks);
        setWeeks(recentWeeks);
        const selectedWeek = recentWeeks.some((week) => week.weekStart === weekStart)
          ? weekStart
          : recentWeeks[0]?.weekStart;
        const value = await getDailyHistory(publicId, selectedWeek, controller.signal);
        if (!controller.signal.aborted) setHistory(value);
      })
      .catch(() => {
        if (!controller.signal.aborted) setError('일별 수익률을 불러오지 못했습니다.');
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [publicId, weekStart, retry]);

  const days = history?.days ?? [];
  const hasReturns = days.some((day) => day.dailyReturnRate !== null);
  const participatingDays = days.filter((day) => day.status !== 'NOT_PARTICIPATING');
  const pendingClose = participatingDays.find((day) => day.reason === 'PRICE_COLLECTION_PENDING');
  const emptyDetail =
    days.length > 0
      ? participatingDays.length === 0
        ? '이 주에는 참여 기록이 없어요.'
        : pendingClose
          ? `${dayFormatter.format(new Date(pendingClose.baseDate))} 종가 수집 대기 · 다음 영업일 오후 1시 이후 제공됩니다.`
          : [...new Set(participatingDays.map(statusLabel))].join(', ')
      : history?.collectionStatus
        ? collectionLabels[history.collectionStatus]
        : '거래일 기록 없음';
  const rates = days.map((day) => day.cumulativeReturnRate ?? 0);
  const min = Math.min(0, ...rates);
  const max = Math.max(0, ...rates);
  const range = Math.max(max - min, 1);
  const x = (index: number) => 28 + (index * 344) / Math.max(days.length - 1, 1);
  const y = (rate: number) => 126 - ((rate - min) / range) * 104;

  return (
    <section
      className="league-history-card league-daily-history"
      aria-labelledby="daily-history-title"
    >
      <header>
        <h2 id="daily-history-title">일별 수익률</h2>
        <LeagueWeekNavigation
          weeks={weeks}
          selected={weeks.some((week) => week.weekStart === weekStart) ? weekStart : ''}
          onChange={onWeekChange}
        />
      </header>
      {loading ? (
        <p role="status">일별 기록을 불러오고 있습니다.</p>
      ) : error ? (
        <div role="alert">
          <p>{error}</p>
          <button type="button" onClick={() => setRetry((value) => value + 1)}>
            일별 기록 다시 불러오기
          </button>
        </div>
      ) : history ? (
        <>
          {!hasReturns ? (
            <div className="league-daily-empty">
              <p>아직 집계된 수익률이 없어요.</p>
              <small>{emptyDetail}</small>
              {participatingDays.length > 0 ? (
                <button type="button" onClick={() => setRetry((value) => value + 1)}>
                  일별 기록 다시 확인
                </button>
              ) : null}
            </div>
          ) : (
            <>
              {history.weeklyReturnRate !== null ? (
                <p className="league-daily-summary">
                  <strong>주간 누적 {formatRate(history.weeklyReturnRate)}</strong>
                  {history.asOfDate ? (
                    <small>
                      {history.asOfDate} 종가 기준{history.confirmed ? ' · 확정' : ''}
                    </small>
                  ) : null}
                </p>
              ) : null}
              {days.filter((day) => day.cumulativeReturnRate !== null).length >= 2 ? (
                <svg
                  className="league-daily-chart"
                  viewBox="0 0 400 156"
                  role="img"
                  aria-label={`${history.weekStart} 주간 누적 수익률 그래프`}
                >
                  <line x1="20" x2="380" y1={y(0)} y2={y(0)} className="league-chart-baseline" />
                  <text x="20" y={Math.max(14, y(0) - 6)}>
                    0%
                  </text>
                  {days.map((day, index) => {
                    const previous = days[index - 1];
                    const rate = day.cumulativeReturnRate;
                    return (
                      <g key={day.baseDate}>
                        {rate !== null && previous?.cumulativeReturnRate != null ? (
                          <line
                            x1={x(index - 1)}
                            y1={y(previous.cumulativeReturnRate)}
                            x2={x(index)}
                            y2={y(rate)}
                            className="league-chart-line"
                          />
                        ) : null}
                        {rate !== null ? (
                          <circle cx={x(index)} cy={y(rate)} r="4">
                            <title>
                              {day.baseDate}: {formatRate(rate)}
                            </title>
                          </circle>
                        ) : null}
                        <text x={x(index)} y="151" textAnchor="middle">
                          {day.baseDate.slice(5)}
                        </text>
                      </g>
                    );
                  })}
                </svg>
              ) : null}
              <div className="league-daily-table-wrap">
                <table className="league-daily-table">
                  <caption className="league-sr-only">
                    거래일별 수익률과 해당 주의 누적 수익률
                  </caption>
                  <thead>
                    <tr>
                      <th scope="col">날짜</th>
                      <th scope="col">하루 수익률</th>
                      <th scope="col">주간 누적</th>
                    </tr>
                  </thead>
                  <tbody>
                    {days.map((day) => (
                      <tr key={day.baseDate}>
                        <th scope="row">
                          <time dateTime={day.baseDate}>
                            {dayFormatter.format(new Date(day.baseDate))}
                          </time>
                          {day.status !== 'CALCULATED' || day.reason === 'TRACKING_BASELINE' ? (
                            <small>{statusLabel(day)}</small>
                          ) : null}
                        </th>
                        <td>
                          {day.dailyReturnRate === null ? '—' : formatRate(day.dailyReturnRate)}
                        </td>
                        <td>
                          {day.cumulativeReturnRate === null
                            ? '—'
                            : formatRate(day.cumulativeReturnRate)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </>
      ) : null}
    </section>
  );
}
