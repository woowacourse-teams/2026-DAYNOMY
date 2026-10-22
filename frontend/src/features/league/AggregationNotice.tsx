import type { AggregationEstimate } from './types';

const formatter = new Intl.DateTimeFormat('ko-KR', {
  month: 'numeric',
  day: 'numeric',
  weekday: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: 'Asia/Seoul',
});

const reasons: Record<string, string> = {
  CALENDAR_PENDING: '거래일 정보를 확인한 뒤 집계 일정을 안내해요.',
  MARKET_CLOSE_PENDING: '장 마감 후 제공되는 종가로 집계해요.',
  PRICE_COLLECTION_PENDING: '종가 수집이 끝나면 수익률과 순위에 반영해요.',
  PRICE_COLLECTION_DELAYED: '시세 제공이 늦어져 다음 수집을 기다리고 있어요.',
  CALCULATION_PENDING: '수집된 종가를 바탕으로 집계를 기다리고 있어요.',
  MISSING_PRICE: '일부 종목의 종가가 누락되어 반영 시각을 확인하고 있어요.',
  NO_HOLDINGS: '집계할 보유 기록이 없어요. 종목 관리에서 보유 종목을 확인해 주세요.',
  UNSUPPORTED_ASSET: '집계할 수 없는 종목이 있어요. 종목 관리에서 확인해 주세요.',
  COLLECTION_PAUSED: '집계가 일시 중지되어 있어요.',
};

export function AggregationNotice({
  aggregation,
  label,
}: {
  aggregation: AggregationEstimate | null | undefined;
  label: string;
}) {
  if (!aggregation) return null;
  return (
    <div className="league-aggregation-notice" role="status">
      {aggregation.baseDate ? <p>{aggregation.baseDate} 종가 집계</p> : null}
      {aggregation.expectedAt ? (
        <p>
          <strong>{label}</strong>{' '}
          <span className="league-aggregation-time">
            <time dateTime={aggregation.expectedAt}>
              {formatter.format(new Date(aggregation.expectedAt))}
            </time>{' '}
            이후
          </span>
        </p>
      ) : null}
      {aggregation.firstReturnExpectedAt ? (
        <p>
          첫 수익률 변화 반영 예상{' '}
          <span className="league-aggregation-time">
            <time dateTime={aggregation.firstReturnExpectedAt}>
              {formatter.format(new Date(aggregation.firstReturnExpectedAt))}
            </time>{' '}
            이후
          </span>
        </p>
      ) : null}
      <p>{reasons[aggregation.reason] ?? '집계 일정을 확인하고 있어요.'}</p>
    </div>
  );
}
