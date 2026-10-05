import { useCallback, useEffect, useMemo, useState } from 'react';
import { getInvestmentCalendar } from './api';
import type {
  InvestmentCalendarEvent,
  InvestmentEventDirection,
  InvestmentEventType,
  PortfolioReactionStatistics,
} from './types';
import './investmentCalendar.css';

const SEOUL_TIME_ZONE = 'Asia/Seoul';
const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'];

function ChevronIcon({ direction }: { direction: 'left' | 'right' }) {
  const path = direction === 'left' ? '15 18 9 12 15 6' : '9 18 15 12 9 6';
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <polyline points={path} />
    </svg>
  );
}

function initialMonth() {
  if (import.meta.env.DEV && import.meta.env.VITE_INVESTMENT_CALENDAR_MOCK_ENABLED !== 'false') {
    return { year: 2026, month: 10 };
  }
  const now = new Date();
  return { year: now.getFullYear(), month: now.getMonth() + 1 };
}

function moveMonth(year: number, month: number, amount: number) {
  const target = new Date(year, month - 1 + amount, 1);
  return { year: target.getFullYear(), month: target.getMonth() + 1 };
}

function eventDateParts(announcedAt: string) {
  const parts = new Intl.DateTimeFormat('ko-KR', {
    timeZone: SEOUL_TIME_ZONE,
    month: 'numeric',
    day: 'numeric',
    weekday: 'long',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  }).formatToParts(new Date(announcedAt));
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? '';
  return {
    month: Number(value('month')),
    day: Number(value('day')),
    label: new Intl.DateTimeFormat('ko-KR', {
      timeZone: SEOUL_TIME_ZONE,
      month: 'long',
      day: 'numeric',
      weekday: 'long',
      hour: 'numeric',
      minute: '2-digit',
      hour12: true,
    }).format(new Date(announcedAt)),
  };
}

function formatValue(value: number | null, unit: string) {
  if (value === null) return '발표 전';
  const fractionDigits = unit === '%' ? 2 : Number.isInteger(value) ? 0 : 1;
  return `${value.toLocaleString('ko-KR', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })}${unit}`;
}

function formatRate(value: number | null) {
  if (value === null) return '표본 부족';
  return `${value > 0 ? '+' : ''}${value.toFixed(1)}%`;
}

function compactWonMagnitude(value: number) {
  const absolute = Math.abs(value);
  if (absolute >= 100_000_000) return `${(absolute / 100_000_000).toFixed(1)}억원`;
  if (absolute >= 10_000) return `${Math.round(absolute / 10_000).toLocaleString('ko-KR')}만원`;
  return `${absolute.toLocaleString('ko-KR')}원`;
}

function formatRange(statistics: PortfolioReactionStatistics) {
  if (statistics.lowerEstimatedAmount === null || statistics.upperEstimatedAmount === null) {
    return '-';
  }

  const lower = statistics.lowerEstimatedAmount;
  const upper = statistics.upperEstimatedAmount;
  if (lower === 0 && upper === 0) return '변화 없음';
  if (lower === 0) return `변화 없음 – ${compactWonMagnitude(upper)} 증가`;
  if (upper === 0) return `${compactWonMagnitude(lower)} 감소 – 변화 없음`;
  if (lower >= 0) {
    return `${compactWonMagnitude(lower)} – ${compactWonMagnitude(upper)} 증가`;
  }
  if (upper <= 0) {
    return `${compactWonMagnitude(upper)} – ${compactWonMagnitude(lower)} 감소`;
  }
  return `${compactWonMagnitude(lower)} 감소 – ${compactWonMagnitude(upper)} 증가`;
}

function impactLabel(level: InvestmentCalendarEvent['portfolioAnalysis']['impactLevel']) {
  return { HIGH: '높음', MEDIUM: '보통', LOW: '낮음' }[level];
}

function directionLabel(type: InvestmentEventType, direction: InvestmentEventDirection) {
  if (direction === 'UNAVAILABLE') return '결과 없음';
  const labels: Record<
    InvestmentEventType,
    Record<Exclude<InvestmentEventDirection, 'UNAVAILABLE'>, string>
  > = {
    US_CPI: { DECREASED: '하락', UNCHANGED: '비슷', INCREASED: '상승' },
    KOREA_BASE_RATE: { DECREASED: '인하', UNCHANGED: '동결', INCREASED: '인상' },
    CORPORATE_EARNINGS: { DECREASED: '감소', UNCHANGED: '비슷', INCREASED: '증가' },
  };
  return labels[type][direction];
}

function resultColumnTitle(type: InvestmentEventType) {
  return type === 'KOREA_BASE_RATE' ? '금리 결정' : '발표 결과';
}

function rateTone(value: number | null) {
  if (value === null || value === 0) return 'neutral';
  return value > 0 ? 'positive' : 'negative';
}

function dDayLabel(daysUntil: number) {
  if (daysUntil === 0) return '오늘';
  return daysUntil > 0 ? `D-${daysUntil}` : `D+${Math.abs(daysUntil)}`;
}

function defaultCopy(event: InvestmentCalendarEvent) {
  const previous = formatValue(event.value.previousValue, event.value.unit);
  return {
    shortTitle: event.title.replace(' 발표', ''),
    relatedLabel: `관련 ${event.portfolioAnalysis.relatedAssetCount}종목`,
    takeaway:
      event.portfolioAnalysis.status === 'READY'
        ? '발표 결과에 따라 내 자산이 달라졌던 범위를 확인해 보세요.'
        : '아직 내 자산의 과거 반응을 계산할 표본이 부족합니다.',
    watchMessage: `이번 발표 결과가 직전 ${previous}와 어떻게 달라졌는지 확인하세요.`,
    previousLabel: '직전',
    sourceLabel: event.source.name,
    latestResult: '',
    latestAssetReaction: '',
  };
}

function CalendarGrid({
  year,
  month,
  events,
  selectedEventId,
  onSelect,
}: {
  year: number;
  month: number;
  events: InvestmentCalendarEvent[];
  selectedEventId: number | null;
  onSelect: (eventId: number) => void;
}) {
  const firstWeekday = new Date(year, month - 1, 1).getDay();
  const daysInMonth = new Date(year, month, 0).getDate();
  const eventByDay = new Map(events.map((event) => [eventDateParts(event.announcedAt).day, event]));
  const cells = Array.from({ length: Math.ceil((firstWeekday + daysInMonth) / 7) * 7 });

  return (
    <div className="investment-calendar-grid" aria-label={`${year}년 ${month}월 일정`}>
      <div className="investment-calendar-weekdays" aria-hidden="true">
        {WEEKDAYS.map((weekday) => (
          <span key={weekday}>{weekday}</span>
        ))}
      </div>
      <div className="investment-calendar-days">
        {cells.map((_, index) => {
          const day = index - firstWeekday + 1;
          if (day < 1 || day > daysInMonth) {
            return <span className="investment-calendar-day empty" key={`empty-${index}`} />;
          }
          const event = eventByDay.get(day);
          return (
            <button
              className={event ? 'investment-calendar-day has-event' : 'investment-calendar-day'}
              type="button"
              key={day}
              disabled={!event}
              aria-pressed={event ? event.id === selectedEventId : undefined}
              aria-label={event ? `${month}월 ${day}일 ${event.title}` : `${month}월 ${day}일`}
              onClick={() => event && onSelect(event.id)}
            >
              {day}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function EventDetail({ event }: { event: InvestmentCalendarEvent }) {
  const date = eventDateParts(event.announcedAt);
  const analysis = event.portfolioAnalysis;
  const copy = event.demoCopy ?? defaultCopy(event);
  const totalSamples = analysis.historicalReactions.reduce(
    (sum, statistics) => sum + statistics.sampleCount,
    0,
  );

  return (
    <article className="investment-calendar-detail" aria-live="polite">
      <div className="investment-calendar-event-status">
        <span>{date.label}</span>
        <span className="investment-calendar-dday">{dDayLabel(event.daysUntil)}</span>
      </div>
      <h2>{event.title}</h2>
      <div className="investment-calendar-relevance">
        <span>
          내 자산 영향 <strong>{impactLabel(analysis.impactLevel)}</strong>
        </span>
        <span>{copy.relatedLabel}</span>
      </div>

      <section className="investment-calendar-watch" aria-label="이번 일정에서 확인할 내용">
        <h3>{copy.takeaway}</h3>
        <p>{copy.watchMessage}</p>
        <div className="investment-calendar-reference">
          <span>{copy.previousLabel}</span>
          <strong>{formatValue(event.value.previousValue, event.value.unit)}</strong>
          <small>{copy.sourceLabel}</small>
        </div>
      </section>

      {analysis.status === 'NO_PORTFOLIO' ? (
        <div className="investment-calendar-analysis-state">
          포트폴리오에 자산을 추가하면 과거 반응을 확인할 수 있어요.
        </div>
      ) : analysis.status === 'INSUFFICIENT_DATA' ? (
        <div className="investment-calendar-analysis-state">
          <strong>아직 비교할 과거 사례가 부족해요.</strong>
          <span>표본이 3회 이상 쌓이면 내 자산의 반응 범위를 보여드릴게요.</span>
        </div>
      ) : (
        <>
          <div className="investment-calendar-impact-head">
            <h3>과거 내 자산 반응</h3>
            <span>{analysis.totalEvaluationAmount.toLocaleString('ko-KR')}원 기준</span>
          </div>
          <div className="investment-calendar-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>{resultColumnTitle(event.type)}</th>
                  <th>중앙값</th>
                  <th>내 자산 변화</th>
                </tr>
              </thead>
              <tbody>
                {analysis.historicalReactions.map((statistics) => (
                  <tr key={statistics.direction}>
                    <td>{directionLabel(event.type, statistics.direction)}</td>
                    <td className={rateTone(statistics.medianReturnRate)}>
                      {formatRate(statistics.medianReturnRate)}
                    </td>
                    <td className={rateTone(statistics.medianReturnRate)}>
                      {formatRange(statistics)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="investment-calendar-basis">
            {totalSamples}회 표본 · 다음 거래일 · 예측 아님
          </p>
        </>
      )}

      {copy.latestResult && copy.latestAssetReaction ? (
        <section className="investment-calendar-last-result" aria-label="지난 발표 결과">
          <h3>지난 발표 결과</h3>
          <div>
            <span>
              발표 결과<strong>{copy.latestResult}</strong>
            </span>
            <span>
              내 자산<strong>{copy.latestAssetReaction}</strong>
            </span>
          </div>
        </section>
      ) : null}
    </article>
  );
}

export function InvestmentCalendarPage() {
  const initial = initialMonth();
  const [year, setYear] = useState(initial.year);
  const [month, setMonth] = useState(initial.month);
  const [events, setEvents] = useState<InvestmentCalendarEvent[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [calendarView, setCalendarView] = useState(false);
  const [isDemo, setIsDemo] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(false);
    void getInvestmentCalendar(year, month, controller.signal).then(
      (result) => {
        setEvents(result.calendar.events);
        setIsDemo(result.isDemo);
        setSelectedEventId(result.calendar.events[0]?.id ?? null);
        setLoading(false);
      },
      (requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return;
        setEvents([]);
        setSelectedEventId(null);
        setError(true);
        setLoading(false);
      },
    );
    return () => controller.abort();
  }, [month, reloadKey, year]);

  const selectedEvent = useMemo(
    () => events.find((event) => event.id === selectedEventId) ?? null,
    [events, selectedEventId],
  );

  const changeMonth = useCallback(
    (amount: number) => {
      const next = moveMonth(year, month, amount);
      setYear(next.year);
      setMonth(next.month);
    },
    [month, year],
  );

  return (
    <main className="investment-calendar-page">
      <header className="investment-calendar-heading">
        <div>
          <h1>투자 캘린더</h1>
          {isDemo ? <span className="investment-calendar-demo-badge">데모 데이터</span> : null}
        </div>
        <p>다가오는 발표 전, 내 자산이 과거에 어떻게 움직였는지 확인하세요.</p>
      </header>

      <section className="investment-calendar-workspace" aria-label="투자 일정과 내 자산 영향">
        <aside className="investment-calendar-schedule">
          <div className="investment-calendar-panel-head">
            <div className="investment-calendar-month-control">
              <button type="button" aria-label="이전 달" onClick={() => changeMonth(-1)}>
                <ChevronIcon direction="left" />
              </button>
              <strong>
                {year}년 {month}월
              </strong>
              <button type="button" aria-label="다음 달" onClick={() => changeMonth(1)}>
                <ChevronIcon direction="right" />
              </button>
            </div>
            <button
              className="investment-calendar-view-button"
              type="button"
              aria-pressed={calendarView}
              onClick={() => setCalendarView((current) => !current)}
            >
              {calendarView ? '목록으로 보기' : '달력으로 보기'}
            </button>
          </div>

          {loading ? (
            <div className="investment-calendar-side-state" aria-busy="true">
              일정을 불러오는 중입니다.
            </div>
          ) : error ? (
            <div className="investment-calendar-side-state">
              <p>일정을 불러오지 못했어요.</p>
              <button type="button" onClick={() => setReloadKey((key) => key + 1)}>
                다시 시도
              </button>
            </div>
          ) : events.length === 0 ? (
            <div className="investment-calendar-side-state">
              <strong>이번 달 예정된 일정이 없어요.</strong>
              <p>다른 달을 확인해 보세요.</p>
            </div>
          ) : calendarView ? (
            <CalendarGrid
              year={year}
              month={month}
              events={events}
              selectedEventId={selectedEventId}
              onSelect={setSelectedEventId}
            />
          ) : (
            <div className="investment-calendar-event-list">
              {events.map((event) => {
                const date = eventDateParts(event.announcedAt);
                const copy = event.demoCopy ?? defaultCopy(event);
                return (
                  <button
                    className="investment-calendar-event-button"
                    type="button"
                    key={event.id}
                    aria-pressed={event.id === selectedEventId}
                    onClick={() => setSelectedEventId(event.id)}
                  >
                    <span className="investment-calendar-date">
                      <strong>{date.day}</strong>
                      {date.month}월
                    </span>
                    <span>
                      <span className="investment-calendar-event-name">{copy.shortTitle}</span>
                      <span className="investment-calendar-event-meta">
                        영향 {impactLabel(event.portfolioAnalysis.impactLevel)} ·{' '}
                        {copy.relatedLabel}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>
          )}
        </aside>

        {loading ? (
          <div className="investment-calendar-detail-state" aria-busy="true" />
        ) : selectedEvent ? (
          <EventDetail event={selectedEvent} />
        ) : (
          <div className="investment-calendar-detail-state">
            <p>일정을 선택하면 내 자산의 과거 반응을 보여드려요.</p>
          </div>
        )}
      </section>
      <p className="investment-calendar-disclaimer">
        과거 반응은 미래 수익률을 보장하지 않습니다.
        {isDemo ? ' · 현재 화면의 수치는 데모 데이터입니다.' : ''}
      </p>
    </main>
  );
}
