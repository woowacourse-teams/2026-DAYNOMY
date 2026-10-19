import { useEffect, useState } from 'react';
import { getDailyHistory, getLeagueWeeks } from './api';
import { leagueErrorType, trackLeagueEvent } from './analytics';
import { recentLeagueWeeks } from './weeks';
import { ReturnLine } from './ReturnLine';
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

function rateClass(rate: number | null) {
  return rate === null || rate === 0 ? '' : rate > 0 ? 'positive' : 'negative';
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
  function retryLoad() {
    trackLeagueEvent('league_action', {
      surface: 'daily_history',
      action: 'retry',
      status: 'clicked',
    });
    setRetry((value) => value + 1);
  }

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
        if (!controller.signal.aborted) {
          setHistory(value);
          trackLeagueEvent('league_load_result', {
            surface: 'daily_history',
            status: 'success',
            result_count: value.days.length,
            confirmed: value.confirmed,
            collection_status: value.collectionStatus,
          });
        }
      })
      .catch((caught: unknown) => {
        if (!controller.signal.aborted) {
          setError('일별 수익률을 불러오지 못했습니다.');
          trackLeagueEvent('league_load_result', {
            surface: 'daily_history',
            status: 'error',
            error_type: leagueErrorType(caught),
          });
        }
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
          onChange={(value) => {
            trackLeagueEvent('league_action', {
              surface: 'daily_history',
              action: 'change_daily_week',
              status: 'clicked',
              relative_week: -weeks.findIndex((week) => week.weekStart === value),
            });
            onWeekChange(value);
          }}
        />
      </header>
      {loading ? (
        <p role="status">일별 기록을 불러오고 있습니다.</p>
      ) : error ? (
        <div role="alert">
          <p>{error}</p>
          <button type="button" onClick={retryLoad}>
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
                <button type="button" onClick={retryLoad}>
                  일별 기록 다시 확인
                </button>
              ) : null}
            </div>
          ) : (
            <>
              {history.asOfDate ? (
                <p className="league-daily-summary">
                  {history.asOfDate} 종가 기준 · {history.confirmed ? '확정' : '집계 중'}
                </p>
              ) : null}
              <div className="league-history-scroll">
                <div
                  className="league-history-chart"
                  role="group"
                  aria-label="일별 수익률 그래프"
                  style={{
                    gridTemplateColumns: `repeat(${days.length}, minmax(0, 1fr))`,
                    minWidth: `${days.length * 56}px`,
                  }}
                >
                  <ReturnLine rates={days.map((day) => day.dailyReturnRate)} />
                  {days.map((day) => (
                    <div
                      role="group"
                      key={day.baseDate}
                      aria-label={`${dayFormatter.format(new Date(day.baseDate))} 하루 수익률 ${day.dailyReturnRate === null ? statusLabel(day) : formatRate(day.dailyReturnRate)}`}
                    >
                      <span className="league-week-label">
                        <strong className={rateClass(day.dailyReturnRate)}>
                          {day.dailyReturnRate === null ? '—' : formatRate(day.dailyReturnRate)}
                        </strong>
                        <span className="league-week-meta">{statusLabel(day)}</span>
                      </span>
                      <span aria-hidden="true" />
                      <time dateTime={day.baseDate}>{day.baseDate.slice(5)}</time>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </>
      ) : null}
    </section>
  );
}
