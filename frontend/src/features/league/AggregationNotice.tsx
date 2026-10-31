import { useEffect, useId, useRef } from 'react';
import { trackLeagueEvent } from './analytics';
import { useContentImpression } from '../news/issue/analytics';
import type { AggregationEstimate } from './types';

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  timeZone: 'UTC',
});
const scheduleFormatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'long',
  day: 'numeric',
  weekday: 'short',
  hour: 'numeric',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'Asia/Seoul',
});

const messages: Record<string, string> = {
  SNAPSHOT_PENDING: '기록 확인 중',
  MARKET_CLOSE_PENDING: '장 마감 대기',
  PRICE_COLLECTION_PENDING: '종가 수집 중',
  PRICE_COLLECTION_DELAYED: '종가 제공 지연',
  CALCULATION_PENDING: '수익률 집계 중',
  COLLECTION_PAUSED: '자동 집계 중지',
  CALENDAR_PENDING: '거래일 확인 중',
  EXCHANGE_RATE_PENDING: '환율 수집 중',
  MISSING_PRICE: '종가 확인 중',
  NO_HOLDINGS: '보유 종목 없음',
  UNSUPPORTED_ASSET: '지원 종목 확인 필요',
};

export function AggregationNotice({
  aggregation,
  onRefresh,
  surface = 'ranking',
}: {
  aggregation: AggregationEstimate;
  onRefresh: () => void;
  surface?: 'ranking' | 'daily_history';
}) {
  const titleId = useId();
  const notice = useRef<HTMLElement>(null);
  const reason = Object.hasOwn(messages, aggregation.reason) ? aggregation.reason : 'UNKNOWN';
  const properties = {
    surface,
    aggregation_reason: reason,
    delayed: aggregation.delayed,
    has_expected_at: Boolean(aggregation.expectedAt),
    has_first_return_expected_at: Boolean(aggregation.firstReturnExpectedAt),
  };
  useContentImpression(
    notice,
    JSON.stringify([
      surface,
      aggregation.baseDate,
      aggregation.expectedAt,
      reason,
      aggregation.delayed,
    ]),
    'league_aggregation_viewed',
    properties,
  );
  const recordRefresh = useRef(properties);
  useEffect(() => {
    recordRefresh.current = properties;
  });
  const refresh = useRef(onRefresh);
  useEffect(() => {
    refresh.current = onRefresh;
  }, [onRefresh]);
  useEffect(() => {
    if (!aggregation.expectedAt) return;
    const delay = Date.parse(aggregation.expectedAt) + 30_000 - Date.now();
    if (delay <= 0 || delay > 2_147_483_647) return;
    const timer = window.setTimeout(() => {
      if (document.visibilityState === 'visible') {
        trackLeagueEvent('league_aggregation_refresh', {
          ...recordRefresh.current,
          trigger: 'scheduled',
        });
        refresh.current();
      }
    }, delay);
    return () => window.clearTimeout(timer);
  }, [aggregation.expectedAt]);
  const message = Object.hasOwn(messages, aggregation.reason)
    ? messages[aggregation.reason]
    : '집계 대기';

  return (
    <aside ref={notice} className="league-aggregation" aria-labelledby={titleId}>
      <div className="league-aggregation-copy">
        <h3 id={titleId}>
          <svg
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.6"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="9" />
            <path d="M12 7v5l3 2" />
          </svg>
          {message}
          {aggregation.delayed && aggregation.reason !== 'PRICE_COLLECTION_DELAYED' ? (
            <span className="league-status-badge">지연</span>
          ) : null}
        </h3>
        {aggregation.reason === 'SNAPSHOT_PENDING' && aggregation.baseDate ? (
          <span className="league-aggregation-date">
            {dateFormatter.format(new Date(aggregation.baseDate))} 기록
          </span>
        ) : null}
      </div>
      <div className="league-aggregation-schedule">
        <dl>
          <div>
            <dt>집계 예정</dt>
            <dd>
              {aggregation.expectedAt ? (
                <time dateTime={aggregation.expectedAt}>
                  {scheduleFormatter.format(new Date(aggregation.expectedAt))}
                </time>
              ) : (
                '일정 확인 중'
              )}
            </dd>
          </div>
          {aggregation.firstReturnExpectedAt ? (
            <div>
              <dt>첫 변동 예상</dt>
              <dd>
                <time dateTime={aggregation.firstReturnExpectedAt}>
                  {scheduleFormatter.format(new Date(aggregation.firstReturnExpectedAt))}
                </time>
              </dd>
            </div>
          ) : null}
        </dl>
        <div className="league-aggregation-actions">
          <button
            type="button"
            onClick={() => {
              trackLeagueEvent('league_aggregation_refresh', { ...properties, trigger: 'manual' });
              onRefresh();
            }}
            aria-label="집계 현황 새로고침"
          >
            새로고침 <span aria-hidden="true">↻</span>
          </button>
        </div>
      </div>
    </aside>
  );
}
