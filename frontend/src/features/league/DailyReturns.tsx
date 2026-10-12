import { useEffect, useState } from 'react';
import { getDailyHistory, getLeagueWeeks } from './api';
import { formatRate, collectionLabels } from './labels';
import type { DailyHistory, DailyReturnPoint, LeagueWeek } from './types';

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
    const controller = new AbortController();
    setLoading(true);
    setError('');
    setHistory(null);
    Promise.all([
      getLeagueWeeks(controller.signal),
      getDailyHistory(publicId, weekStart || undefined, controller.signal),
    ])
      .then(([availableWeeks, value]) => {
        if (controller.signal.aborted) return;
        setWeeks(availableWeeks);
        setHistory(value);
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
        <div>
          <h2 id="daily-history-title">일별 수익률 · 주간 누적</h2>
        </div>
        <label>
          조회 주차
          <select
            value={weekStart || history?.weekStart || ''}
            onChange={(event) => onWeekChange(event.target.value)}
          >
            {weeks.length === 0 ? <option value={weekStart}>이번 주</option> : null}
            {weeks.map((week) => (
              <option key={week.weekStart} value={week.weekStart}>
                {week.weekStart} ~ {week.weekEnd}
              </option>
            ))}
          </select>
        </label>
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
          {history.participationReturnRate !== undefined && history.weeklyReturnRate === null ? (
            <p>참여 후 주간 수익률 {formatRate(history.participationReturnRate)}</p>
          ) : null}
          <p className="league-daily-summary">
            <span>
              {history.weekStart} ~ {history.weekEnd} ·{' '}
              {history.confirmed
                ? '주간 수익률 확정'
                : collectionLabels[history.collectionStatus ?? 'IN_PROGRESS']}
            </span>
            <strong>
              주간 누적{' '}
              {history.weeklyReturnRate === null
                ? '집계 대기'
                : formatRate(history.weeklyReturnRate)}
            </strong>
            <small>{history.asOfDate ? `${history.asOfDate} 종가 기준` : '집계 대기'}</small>
          </p>
          {days.length === 0 ? (
            <p>거래일 기록 없음</p>
          ) : (
            <>
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
                      <th scope="col">거래일</th>
                      <th scope="col">일별</th>
                      <th scope="col">주간 누적</th>
                      <th scope="col">상태</th>
                    </tr>
                  </thead>
                  <tbody>
                    {days.map((day) => (
                      <tr key={day.baseDate}>
                        <th scope="row">{day.baseDate.slice(5)}</th>
                        <td>
                          {day.dailyReturnRate === null ? '—' : formatRate(day.dailyReturnRate)}
                        </td>
                        <td>
                          {day.cumulativeReturnRate === null
                            ? '—'
                            : formatRate(day.cumulativeReturnRate)}
                        </td>
                        <td>{statusLabel(day)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )}
          {days.some((day) => day.status === 'NOT_PARTICIPATING') && history.eligibleFrom ? (
            <p className="league-calculation-note">공식 순위 시작일: {history.eligibleFrom}</p>
          ) : null}
        </>
      ) : null}
    </section>
  );
}
