import { useId } from 'react';
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

const messages: Record<string, { title: string; description: string }> = {
  SNAPSHOT_PENDING: {
    title: '이번 주 성과를 준비하고 있어요',
    description: '일부 보유 데이터가 아직 준비되지 않았어요. 확인이 끝나면 주간 수익률에 반영돼요.',
  },
  MARKET_CLOSE_PENDING: {
    title: '장 마감 후 성과를 계산해요',
    description: '종가가 수집되면 보유 성과와 순위에 반영돼요.',
  },
  PRICE_COLLECTION_PENDING: {
    title: '종가 데이터를 기다리고 있어요',
    description: '종가 수집이 끝나면 수익률과 순위가 갱신돼요.',
  },
  PRICE_COLLECTION_DELAYED: {
    title: '종가 데이터 제공이 늦어지고 있어요',
    description: '다음 수집 일정에 다시 확인해요. 이미 집계된 성과는 그대로 볼 수 있어요.',
  },
  CALCULATION_PENDING: {
    title: '수익률과 순위를 계산하고 있어요',
    description: '수집된 종가를 바탕으로 다음 집계 때 성과를 반영해요.',
  },
  COLLECTION_PAUSED: {
    title: '자동 집계가 잠시 멈춰 있어요',
    description:
      '현재는 예상 일정을 안내하기 어려워요. 데이터 준비와 집계가 재개되면 성과가 반영돼요.',
  },
  CALENDAR_PENDING: {
    title: '거래일 정보를 확인하고 있어요',
    description: '거래일이 확인되면 수집·집계 일정을 안내해요.',
  },
  MISSING_PRICE: {
    title: '일부 종가 데이터를 확인하고 있어요',
    description: '필요한 종가가 모두 준비되어야 주간 수익률을 계산할 수 있어요.',
  },
  NO_HOLDINGS: {
    title: '집계할 보유 기록이 없어요',
    description: '리그에 가져온 종목과 실제 보유 기간을 확인해 주세요.',
  },
  UNSUPPORTED_ASSET: {
    title: '집계 대상 종목을 확인해 주세요',
    description: '리그에서 지원하는 국내 주식·ETF의 성과를 집계해요.',
  },
};

export function AggregationNotice({
  aggregation,
  onRefresh,
}: {
  aggregation: AggregationEstimate;
  onRefresh: () => void;
}) {
  const titleId = useId();
  const message = Object.hasOwn(messages, aggregation.reason)
    ? messages[aggregation.reason]
    : {
        title: '다음 집계를 기다리고 있어요',
        description: '데이터 준비와 집계가 끝나면 성과가 반영돼요.',
      };

  return (
    <aside className="league-aggregation" aria-labelledby={titleId}>
      <div className="league-aggregation-copy">
        <div className="league-aggregation-eyebrow">
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
          <span>집계 현황</span>
          {aggregation.delayed ? <span className="league-status-badge">제공 지연</span> : null}
        </div>
        <h3 id={titleId}>{message.title}</h3>
        <p>{message.description}</p>
        {aggregation.reason === 'SNAPSHOT_PENDING' && aggregation.baseDate ? (
          <span className="league-aggregation-date">
            {dateFormatter.format(new Date(aggregation.baseDate))} 보유 데이터 확인 중
          </span>
        ) : null}
      </div>
      <div className="league-aggregation-schedule">
        <dl>
          <div>
            <dt>다음 집계 예정</dt>
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
              <dt>첫 수익률 변화 예상</dt>
              <dd>
                <time dateTime={aggregation.firstReturnExpectedAt}>
                  {scheduleFormatter.format(new Date(aggregation.firstReturnExpectedAt))}
                </time>
              </dd>
            </div>
          ) : null}
        </dl>
        <div className="league-aggregation-actions">
          <small>
            {aggregation.expectedAt
              ? '데이터 제공 상황에 따라 달라질 수 있어요.'
              : '예정 시각이 확인되면 안내해요.'}
          </small>
          <button type="button" onClick={onRefresh} aria-label="집계 현황 새로고침">
            새로고침 <span aria-hidden="true">↻</span>
          </button>
        </div>
      </div>
    </aside>
  );
}
