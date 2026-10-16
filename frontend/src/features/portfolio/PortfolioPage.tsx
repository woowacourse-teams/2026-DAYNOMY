import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { PortfolioNews } from './components/PortfolioNews';
import { calculatePortfolio } from './api';
import { portfolioErrorType, trackPortfolioEvent } from './analytics';
import { PortfolioAnalysis } from './components/PortfolioAnalysis';
import { PortfolioEditor } from './components/PortfolioEditor';
import { usePortfolioHoldings } from './hooks/usePortfolioHoldings';
import { usePortfolioPerformance } from './hooks/usePortfolioPerformance';
import { toPortfolioAnalysisAssets } from './portfolioAnalysisAssets';
import type {
  AssetCategory,
  PortfolioCalculation,
  PortfolioHoldingInput,
  PortfolioHoldingResult,
  PortfolioPerformancePoint,
  PortfolioTrendPeriod,
} from './types';
import './portfolio.css';
import './portfolioLedger.css';
import { PortfolioTransactionEditor } from './components/PortfolioTransactionEditor';
import { PortfolioHistory } from './components/PortfolioHistory';
import { PortfolioDialog } from './components/PortfolioDialog';
import { PortfolioHoldingDetail } from './components/PortfolioHoldingDetail';
import { PortfolioConfirmDialog } from './components/PortfolioConfirmDialog';
import { todayInSeoul } from './transactionForm';
import type { PortfolioHoldingHistory, PortfolioTransactionType, StockSearchItem } from './types';

const numberFormatter = new Intl.NumberFormat('ko-KR');
const percentFormatter = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const compactWonFormatter = new Intl.NumberFormat('ko-KR', {
  notation: 'compact',
  maximumFractionDigits: 1,
});
const HIDDEN_AMOUNT = '••••••원';
const DESKTOP_CANVAS_WIDTH = 1324.8;
const EMPTY_PORTFOLIO_CALCULATION: PortfolioCalculation = {
  baseDate: null,
  pricingStatus: 'COMPLETE',
  pricedHoldingCount: 0,
  unpricedHoldingCount: 0,
  totalPurchaseAmount: 0,
  totalEvaluationAmount: 0,
  dailyProfitLoss: null,
  dailyReturnRate: null,
  totalProfitLoss: 0,
  totalReturnRate: 0,
  holdings: [],
  marketAllocations: [],
};

type PortfolioSort = 'DEFAULT' | 'EVALUATION' | 'PROFIT' | 'RETURN' | 'WEIGHT';

function formatWon(value: number | null) {
  if (value === null) return '—';
  return `${numberFormatter.format(Math.round(value))}원`;
}

function formatSignedWon(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '−' : '';
  return `${sign}${numberFormatter.format(Math.abs(Math.round(value)))}원`;
}

function formatPercent(value: number | null, signed = false) {
  if (value === null) return '—';
  const sign = signed && value > 0 ? '+' : value < 0 ? '−' : '';
  return `${sign}${percentFormatter.format(Math.abs(value))}%`;
}

function formatCompactWon(value: number) {
  return `${compactWonFormatter.format(Math.round(value))}원`;
}

function formatTrendDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return `${year}년 ${month}월 ${day}일`;
}

function profitClass(value: number | null) {
  if (value === null) return '';
  return value > 0 ? 'portfolio-gain' : value < 0 ? 'portfolio-loss' : '';
}

function trendTone(difference: number) {
  return difference > 0 ? 'gain' : difference < 0 ? 'loss' : 'neutral';
}

function getCategoryLabel(category: AssetCategory) {
  return category === 'ETF' ? 'ETF' : '주식';
}

function getCategoryWeight(calculation: PortfolioCalculation, category: AssetCategory) {
  const evaluationAmount = calculation.holdings
    .filter((holding) => holding.category === category)
    .reduce((sum, holding) => sum + (holding.evaluationAmount ?? 0), 0);
  if (calculation.totalEvaluationAmount === 0) return 0;
  return (evaluationAmount / calculation.totalEvaluationAmount) * 100;
}

function CompositionChart({ calculation }: { calculation: PortfolioCalculation }) {
  const stockWeight = getCategoryWeight(calculation, 'STOCK');
  const etfWeight = getCategoryWeight(calculation, 'ETF');

  return (
    <div className="portfolio-composition-content">
      <div className="portfolio-donut-wrap">
        <svg
          className="portfolio-donut"
          viewBox="0 0 180 180"
          role="img"
          aria-label={`주식 ${formatPercent(stockWeight)}, ETF ${formatPercent(etfWeight)}`}
        >
          <g transform="rotate(-90 90 90)" fill="none" strokeWidth="24">
            <circle cx="90" cy="90" r="66" stroke="#f0f2f5" />
            <circle
              cx="90"
              cy="90"
              r="66"
              pathLength="100"
              stroke="#2463b5"
              strokeDasharray={`${stockWeight} ${100 - stockWeight}`}
            />
            <circle
              cx="90"
              cy="90"
              r="66"
              pathLength="100"
              stroke="#4f9ba8"
              strokeDasharray={`${etfWeight} ${100 - etfWeight}`}
              strokeDashoffset={-stockWeight}
            />
          </g>
        </svg>
        <div className="portfolio-donut-caption">
          <span>보유 자산</span>
          <strong>
            {calculation.holdings.length}
            <small>개</small>
          </strong>
        </div>
      </div>
      <div className="portfolio-composition-breakdown">
        <dl className="portfolio-allocation-list portfolio-category-list">
          {(['STOCK', 'ETF'] as AssetCategory[]).map((category) => {
            const weight = category === 'STOCK' ? stockWeight : etfWeight;
            return (
              <div key={category}>
                <div>
                  <dt>
                    <i className={`portfolio-dot ${category.toLowerCase()}`} />
                    {getCategoryLabel(category)}
                  </dt>
                  <dd>{formatPercent(weight)}</dd>
                </div>
                <span className="portfolio-allocation-bar" aria-hidden="true">
                  <i style={{ width: `${weight}%` }} />
                </span>
              </div>
            );
          })}
        </dl>
        <div className="portfolio-market-allocation" aria-label="시장별 자산 비중">
          {calculation.marketAllocations.map((allocation) => (
            <span key={allocation.market}>
              {allocation.market} · {formatPercent(allocation.weight)}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}

type PortfolioAssetTrendChartProps = {
  points: PortfolioPerformancePoint[];
  loading: boolean;
  error: string;
  onRetry: () => void;
  amountsHidden: boolean;
  period: TrendPeriod;
  onPeriodChange: (period: TrendPeriod) => void;
  mode?: 'compact' | 'expanded';
  onExpand?: (trigger: HTMLButtonElement) => void;
};

const TREND_PERIODS = [
  { value: 'YTD', label: '올해' },
  { value: '1M', label: '1달' },
  { value: '6M', label: '6달' },
  { value: '1Y', label: '1년' },
  { value: '5Y', label: '5년' },
] as const;
type TrendPeriod = PortfolioTrendPeriod;
type TrendMetric = 'evaluation' | 'profit' | 'return';
const TREND_METRICS = [
  { value: 'evaluation', label: '평가금액' },
  { value: 'profit', label: '평가손익' },
  { value: 'return', label: '평가수익률' },
] as const;

function getTrendPeriodStart(baseDate: string, period: TrendPeriod) {
  const date = new Date(`${baseDate}T00:00:00Z`);
  if (period === 'YTD') {
    date.setUTCMonth(0, 1);
  } else {
    const months = period === '1M' ? 1 : period === '6M' ? 6 : period === '1Y' ? 12 : 60;
    const day = date.getUTCDate();
    date.setUTCDate(1);
    date.setUTCMonth(date.getUTCMonth() - months);
    const lastDay = new Date(
      Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0),
    ).getUTCDate();
    date.setUTCDate(Math.min(day, lastDay));
  }
  return date.toISOString().slice(0, 10);
}

function formatTrendAxisDate(value: string, period: TrendPeriod) {
  return period === '1Y' || period === '5Y'
    ? value.slice(0, 7).replace('-', '.')
    : value.slice(5).replace('-', '.');
}

function PortfolioAssetTrendChart({
  points,
  loading,
  error,
  onRetry,
  amountsHidden,
  period,
  onPeriodChange,
  mode = 'compact',
  onExpand,
}: PortfolioAssetTrendChartProps) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);
  const [metric, setMetric] = useState<TrendMetric>('evaluation');
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const chartRef = useRef<HTMLDivElement>(null);
  const [chartWidth, setChartWidth] = useState(720);
  useEffect(() => {
    const chart = chartRef.current;
    if (!chart || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry.contentRect.width > 0) setChartWidth(entry.contentRect.width);
    });
    observer.observe(chart);
    return () => observer.disconnect();
  }, [loading, error, points.length]);
  useEffect(() => {
    setHoveredIndex(null);
    setSelectedDate(null);
  }, [period]);
  const expanded = mode === 'expanded';
  const titleId = expanded ? 'expanded-asset-trend-title' : 'asset-trend-title';
  const latestAvailablePoint = points.at(-1);
  const visiblePoints = useMemo(() => {
    if (!latestAvailablePoint) return [];
    const cutoffDate = getTrendPeriodStart(latestAvailablePoint.baseDate, period);
    return points.filter((point) => point.baseDate >= cutoffDate);
  }, [latestAvailablePoint, period, points]);
  const cutoffDate = getTrendPeriodStart(latestAvailablePoint?.baseDate ?? todayInSeoul(), period);
  const firstDate = visiblePoints.at(0)?.baseDate;
  const lastDate = visiblePoints.at(-1)?.baseDate;
  const dateSpan = firstDate && lastDate ? Date.parse(lastDate) - Date.parse(firstDate) : 0;
  const dateX = (date: string) =>
    padding.left +
    (dateSpan && firstDate
      ? ((Date.parse(date) - Date.parse(firstDate)) / dateSpan) *
        (width - padding.left - padding.right)
      : 0);
  const pointValue = (point: PortfolioPerformancePoint) =>
    metric === 'evaluation'
      ? point.totalEvaluationAmount
      : metric === 'profit'
        ? point.totalProfitLoss
        : point.totalReturnRate;
  const baselineValue = (point: PortfolioPerformancePoint) =>
    metric === 'evaluation' ? point.totalPurchaseAmount : 0;
  const width = chartWidth;
  const hasSinglePoint = visiblePoints.length === 1;
  const height = expanded ? 300 : 190;
  const padding = { top: 14, right: 12, bottom: 28, left: 58 };
  const values = visiblePoints.flatMap((point) => [baselineValue(point), pointValue(point)]);
  const rawMinimum = values.length ? Math.min(...values) : 0;
  const rawMaximum = values.length ? Math.max(...values) : 0;
  const amountPadding = Math.max(
    (rawMaximum - rawMinimum) * 0.16,
    Math.abs(rawMaximum) * 0.015,
    metric === 'return' ? 0.1 : 1,
  );
  const minimum =
    metric === 'evaluation' ? Math.max(0, rawMinimum - amountPadding) : rawMinimum - amountPadding;
  const maximum = rawMaximum + amountPadding;
  const range = maximum - minimum || 1;
  const x = (index: number) => dateX(visiblePoints[index].baseDate);
  const y = (value: number) =>
    padding.top + ((maximum - value) / range) * (height - padding.top - padding.bottom);
  const chartPoints = visiblePoints.map((point, index) => ({
    x: x(index),
    evaluationY: y(pointValue(point)),
    purchaseY: y(baselineValue(point)),
    difference: pointValue(point) - baselineValue(point),
  }));
  const purchaseLinePoints = chartPoints.map((point) => `${point.x},${point.purchaseY}`);
  const segments = chartPoints.slice(1).flatMap((to, index) => {
    const from = chartPoints[index];
    if (from.difference * to.difference < 0) {
      const ratio = from.difference / (from.difference - to.difference);
      const crossingY = from.evaluationY + (to.evaluationY - from.evaluationY) * ratio;
      const crossing = {
        x: from.x + (to.x - from.x) * ratio,
        evaluationY: crossingY,
        purchaseY: crossingY,
        difference: 0,
      };
      return [
        { from, to: crossing, tone: trendTone(from.difference) },
        { from: crossing, to, tone: trendTone(to.difference) },
      ];
    }
    return [{ from, to, tone: trendTone(from.difference || to.difference) }];
  });
  const latest = visiblePoints.at(-1);
  const activeIndex =
    hoveredIndex === null
      ? selectedDate !== null
        ? visiblePoints.findIndex((point) => point.baseDate === selectedDate)
        : expanded
          ? visiblePoints.length - 1
          : null
      : Math.min(hoveredIndex, visiblePoints.length - 1);
  const activePoint = activeIndex === null || activeIndex < 0 ? null : visiblePoints[activeIndex];
  const activeX = activePoint && activeIndex !== null ? x(activeIndex) : null;
  const detailDate = selectedDate ?? activePoint?.baseDate ?? latest?.baseDate;
  const detailPoint = visiblePoints.find((point) => point.baseDate === detailDate);
  const guideValues = Array.from(new Set([minimum, (minimum + maximum) / 2, maximum]));
  const middlePoint = visiblePoints.at(Math.floor((visiblePoints.length - 1) / 2));
  const dateLabels = Array.from(
    new Set(
      [visiblePoints.at(0), middlePoint, visiblePoints.at(-1)]
        .filter((point): point is PortfolioPerformancePoint => Boolean(point))
        .map((point) => point.baseDate),
    ),
  );

  function selectNearestPoint(clientX: number, chart: SVGSVGElement, clearSelection = false) {
    if (visiblePoints.length === 0) return;
    const bounds = chart.getBoundingClientRect();
    const chartX = ((clientX - bounds.left) / bounds.width) * width;
    const index = visiblePoints.reduce(
      (nearest, _point, candidate) =>
        Math.abs(x(candidate) - chartX) < Math.abs(x(nearest) - chartX) ? candidate : nearest,
      0,
    );
    if (clearSelection) setSelectedDate(null);
    setHoveredIndex(index);
  }

  function moveActivePoint(direction: -1 | 1) {
    if (visiblePoints.length === 0) return;
    const currentIndex =
      activeIndex === null || activeIndex < 0 ? visiblePoints.length - 1 : activeIndex;
    setSelectedDate(null);
    setHoveredIndex(Math.max(0, Math.min(visiblePoints.length - 1, currentIndex + direction)));
  }

  const singlePointClass = visiblePoints.length === 1 ? ' has-single-point' : '';

  return (
    <section
      className={`portfolio-return-dashboard is-${mode}${singlePointClass}`}
      aria-labelledby={titleId}
    >
      <div className="portfolio-dashboard-heading">
        <div className="portfolio-trend-title">
          <h2 id={titleId}>
            {!expanded && onExpand ? (
              <button
                className="portfolio-trend-title-button"
                type="button"
                onClick={(event) => onExpand(event.currentTarget)}
              >
                자산 추이
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m9 5 7 7-7 7" />
                </svg>
              </button>
            ) : (
              '자산 추이 상세'
            )}
          </h2>
        </div>
        <div className="portfolio-trend-controls">
          <label className="portfolio-trend-period-select">
            <span className="portfolio-visually-hidden">자산 추이 기간</span>
            <select
              aria-label="자산 추이 기간"
              value={period}
              onChange={(event) => onPeriodChange(event.currentTarget.value as TrendPeriod)}
            >
              {TREND_PERIODS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <path d="m4 6 4 4 4-4" />
            </svg>
          </label>
          <button
            type="button"
            className="portfolio-trend-expand portfolio-trend-refresh"
            aria-label="자산 추이 새로고침"
            title="자산 추이 새로고침"
            disabled={loading}
            onClick={onRetry}
          >
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M20 11a8 8 0 1 0-2 6M20 4v7h-7" />
            </svg>
          </button>
          {!expanded && onExpand ? (
            <button
              type="button"
              className="portfolio-trend-expand"
              aria-label="자산 추이 크게 보기"
              onClick={(event) => onExpand(event.currentTarget)}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M14 5h5v5M10 19H5v-5M19 5l-6 6M5 19l6-6" />
              </svg>
            </button>
          ) : null}
        </div>
      </div>
      {!loading && !error && latest && expanded ? (
        <div className="portfolio-trend-summary" aria-label="자산 추이 요약">
          <span>평가금액 · {latest.baseDate.replaceAll('-', '.')}</span>
          <strong>{amountsHidden ? HIDDEN_AMOUNT : formatWon(latest.totalEvaluationAmount)}</strong>
          <p className={profitClass(latest.totalProfitLoss)}>
            평가손익{' '}
            {amountsHidden
              ? '••••••원 · •••%'
              : `${formatSignedWon(latest.totalProfitLoss)} · ${formatPercent(latest.totalReturnRate, true)}`}
          </p>
        </div>
      ) : null}
      {expanded ? (
        <div className="portfolio-trend-metric-tabs" role="group" aria-label="자산 추이 지표">
          {TREND_METRICS.map((item) => (
            <button
              type="button"
              key={item.value}
              aria-pressed={metric === item.value}
              onClick={() => setMetric(item.value)}
            >
              {item.label}
            </button>
          ))}
        </div>
      ) : null}
      <div className="portfolio-trend-legend" aria-label="자산 추이 범례">
        <span className="evaluation" title="평가이익은 빨강, 평가손실은 파랑">
          {TREND_METRICS.find((item) => item.value === metric)?.label}
        </span>
        <span className="principal">{metric === 'evaluation' ? '매입금액' : '0 기준선'}</span>
      </div>
      <div className="portfolio-trend-plot">
        {loading ? (
          <p className="portfolio-tracking-state" role="status">
            자산 추이를 불러오고 있습니다.
          </p>
        ) : null}
        {!loading && error ? (
          <div className="portfolio-tracking-state" role="alert">
            <p>{error}</p>
            <button type="button" className="portfolio-secondary-button" onClick={onRetry}>
              다시 시도
            </button>
          </div>
        ) : null}
        {!loading && !error && points.length === 0 ? (
          <div className="portfolio-tracking-state is-empty" role="status">
            <strong>선택한 기간에 표시할 추이가 없어요</strong>
            <p>기간을 바꾸거나 거래를 기록해 주세요. 가격 데이터가 준비되면 추이가 표시됩니다.</p>
          </div>
        ) : null}
        {!loading && !error && visiblePoints.length >= 1 && latest ? (
          <>
            <div
              ref={chartRef}
              className="portfolio-return-chart-wrap"
              tabIndex={0}
              aria-label="자산 추이 그래프. 좌우 방향키로 날짜를 이동할 수 있습니다."
              onBlur={() => setHoveredIndex(null)}
              onKeyDown={(event) => {
                if (event.key === 'ArrowLeft') {
                  event.preventDefault();
                  moveActivePoint(-1);
                }
                if (event.key === 'ArrowRight') {
                  event.preventDefault();
                  moveActivePoint(1);
                }
              }}
            >
              <svg
                className="portfolio-return-chart"
                viewBox={`0 0 ${width} ${height}`}
                preserveAspectRatio="none"
                role="img"
                aria-label={
                  amountsHidden
                    ? '포트폴리오 자산 추이, 금액 숨김'
                    : metric === 'evaluation'
                      ? `포트폴리오 자산 추이, 평가금액 ${formatWon(latest.totalEvaluationAmount)}, 매입원금 ${formatWon(latest.totalPurchaseAmount)}`
                      : metric === 'profit'
                        ? `포트폴리오 자산 추이, 평가손익 ${formatSignedWon(latest.totalProfitLoss)}`
                        : `포트폴리오 자산 추이, 평가수익률 ${formatPercent(latest.totalReturnRate, true)}`
                }
                onPointerDown={(event) =>
                  selectNearestPoint(event.clientX, event.currentTarget, true)
                }
                onPointerMove={(event) => selectNearestPoint(event.clientX, event.currentTarget)}
                onPointerLeave={() => setHoveredIndex(null)}
              >
                {guideValues.map((value) => (
                  <g key={value} className="portfolio-return-guide">
                    <line
                      x1={padding.left}
                      y1={y(value)}
                      x2={width - padding.right}
                      y2={y(value)}
                    />
                    <text x={padding.left - 7} y={y(value) + 4} textAnchor="end">
                      {amountsHidden
                        ? '—'
                        : metric === 'return'
                          ? formatPercent(value)
                          : formatCompactWon(value)}
                    </text>
                  </g>
                ))}
                {visiblePoints.length >= 1 ? (
                  <>
                    {segments.map(({ from, to, tone }, index) => (
                      <g key={index}>
                        {tone !== 'neutral' ? (
                          <polygon
                            className={`portfolio-return-area is-${tone}`}
                            points={`${from.x},${from.evaluationY} ${to.x},${to.evaluationY} ${to.x},${to.purchaseY} ${from.x},${from.purchaseY}`}
                            fill={`var(--portfolio-trend-${tone})`}
                            fillOpacity="0.1"
                          />
                        ) : null}
                        <polyline
                          className={`portfolio-return-line evaluation is-${tone}`}
                          points={`${from.x},${from.evaluationY} ${to.x},${to.evaluationY}`}
                          style={{ stroke: `var(--portfolio-trend-${tone})` }}
                        />
                      </g>
                    ))}
                    {hasSinglePoint ? (
                      <polyline
                        className="portfolio-return-line evaluation"
                        points={`${chartPoints[0].x},${chartPoints[0].evaluationY}`}
                        style={{
                          stroke: `var(--portfolio-trend-${trendTone(chartPoints[0].difference)})`,
                        }}
                      />
                    ) : null}
                    <polyline
                      className="portfolio-return-line principal"
                      points={purchaseLinePoints.join(' ')}
                    />
                    {hasSinglePoint && !activePoint ? (
                      <g className="portfolio-return-single-point">
                        <circle
                          className="portfolio-return-point principal"
                          cx={x(0)}
                          cy={y(baselineValue(visiblePoints[0]))}
                          r="3.5"
                        />
                        <circle
                          className="portfolio-return-point evaluation"
                          style={{
                            fill: `var(--portfolio-trend-${trendTone(chartPoints[0].difference)})`,
                          }}
                          cx={x(0)}
                          cy={y(pointValue(visiblePoints[0]))}
                          r="4.5"
                        />
                      </g>
                    ) : null}
                  </>
                ) : null}
                {activePoint && activeX !== null ? (
                  <g className="portfolio-return-active-point">
                    <line
                      className="portfolio-return-cursor"
                      x1={activeX}
                      y1={padding.top}
                      x2={activeX}
                      y2={height - padding.bottom}
                    />
                    <circle
                      className="portfolio-return-point evaluation"
                      style={{
                        fill: `var(--portfolio-trend-${trendTone(activePoint.totalProfitLoss)})`,
                      }}
                      cx={activeX}
                      cy={y(pointValue(activePoint))}
                      r="4.5"
                    />
                    <circle
                      className="portfolio-return-point principal"
                      cx={activeX}
                      cy={y(baselineValue(activePoint))}
                      r="3.5"
                    />
                  </g>
                ) : null}
              </svg>
              {hoveredIndex !== null && activePoint && activeX !== null ? (
                <div
                  className="portfolio-return-tooltip"
                  style={{
                    left: `clamp(min(158px, 50%), ${(activeX / width) * 100}%, max(calc(100% - 158px), 50%))`,
                    top: `max(140px, ${(y(pointValue(activePoint)) / height) * 100}%)`,
                  }}
                  role={expanded ? 'tooltip' : 'status'}
                  aria-live={expanded ? 'off' : 'polite'}
                >
                  <time dateTime={activePoint.baseDate}>
                    {formatTrendDate(activePoint.baseDate)}
                  </time>
                  <dl>
                    <div className="principal">
                      <dt>매입원금</dt>
                      <dd>
                        {amountsHidden ? HIDDEN_AMOUNT : formatWon(activePoint.totalPurchaseAmount)}
                      </dd>
                    </div>
                    <div
                      className="evaluation"
                      style={
                        {
                          '--portfolio-trend-accent': `var(--portfolio-trend-${trendTone(activePoint.totalProfitLoss)})`,
                        } as CSSProperties
                      }
                    >
                      <dt>평가금액</dt>
                      <dd>
                        {amountsHidden
                          ? HIDDEN_AMOUNT
                          : formatWon(activePoint.totalEvaluationAmount)}
                        <small className={profitClass(activePoint.totalProfitLoss)}>
                          {amountsHidden
                            ? '•••%'
                            : formatPercent(activePoint.totalReturnRate, true)}
                        </small>
                      </dd>
                    </div>
                  </dl>
                </div>
              ) : null}
            </div>
            <div
              className="portfolio-return-dates"
              aria-hidden="true"
              style={{
                paddingLeft: `${(padding.left / width) * 100}%`,
                paddingRight: `${(padding.right / width) * 100}%`,
              }}
            >
              {dateLabels.map((baseDate) => (
                <span key={baseDate}>{formatTrendAxisDate(baseDate, period)}</span>
              ))}
            </div>
            {visiblePoints.length === 1 && (
              <p className="portfolio-trend-note">
                표시할 평가 데이터가 하루뿐이에요. 기록이 쌓이면 선으로 연결됩니다.
              </p>
            )}
          </>
        ) : null}
      </div>
      {expanded ? (
        <section className="portfolio-trend-date-detail" aria-label="선택 날짜의 자산">
          <div className="portfolio-trend-detail-heading">
            <h3>날짜별 자산</h3>
            <label>
              조회 날짜
              <input
                type="date"
                aria-label="자산 추이 조회 날짜"
                value={detailDate ?? ''}
                min={cutoffDate}
                max={todayInSeoul()}
                onChange={(event) => {
                  setHoveredIndex(null);
                  setSelectedDate(event.target.value || null);
                }}
              />
            </label>
          </div>
          <div role="status" aria-live="polite">
            {!loading && !error && detailDate && (
              <span className="portfolio-visually-hidden">{formatTrendDate(detailDate)}</span>
            )}
            {!loading && !error && detailPoint ? (
              <dl className="portfolio-trend-date-values">
                <div>
                  <dt>매입원금</dt>
                  <dd>
                    {amountsHidden ? HIDDEN_AMOUNT : formatWon(detailPoint.totalPurchaseAmount)}
                  </dd>
                </div>
                <div>
                  <dt>평가금액</dt>
                  <dd>
                    {amountsHidden ? HIDDEN_AMOUNT : formatWon(detailPoint.totalEvaluationAmount)}
                  </dd>
                </div>
                <div>
                  <dt>평가손익</dt>
                  <dd className={profitClass(detailPoint.totalProfitLoss)}>
                    {amountsHidden ? HIDDEN_AMOUNT : formatSignedWon(detailPoint.totalProfitLoss)}
                  </dd>
                </div>
                <div>
                  <dt>평가수익률</dt>
                  <dd className={profitClass(detailPoint.totalProfitLoss)}>
                    {amountsHidden ? '•••%' : formatPercent(detailPoint.totalReturnRate, true)}
                  </dd>
                </div>
              </dl>
            ) : !loading && !error ? (
              <p className="portfolio-trend-note">평가 데이터 없음</p>
            ) : null}
          </div>
        </section>
      ) : null}
    </section>
  );
}

type PortfolioTrendDialogProps = Omit<PortfolioAssetTrendChartProps, 'mode' | 'onExpand'> & {
  onClose: () => void;
};

function PortfolioTrendDialog({ onClose, ...chartProps }: PortfolioTrendDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    dialogRef.current?.showModal();
  }, []);

  function closeDialog() {
    dialogRef.current?.close();
  }

  return (
    <dialog
      ref={dialogRef}
      className="portfolio-trend-dialog"
      aria-labelledby="expanded-asset-trend-title"
      onCancel={(event) => {
        event.preventDefault();
        closeDialog();
      }}
      onClose={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) closeDialog();
      }}
    >
      <button
        type="button"
        className="portfolio-trend-dialog-close"
        aria-label="자산 추이 상세 닫기"
        autoFocus
        onClick={closeDialog}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="m6 6 12 12M18 6 6 18" />
        </svg>
      </button>
      <PortfolioAssetTrendChart {...chartProps} mode="expanded" />
    </dialog>
  );
}

function compareHoldings(
  first: PortfolioHoldingResult,
  second: PortfolioHoldingResult,
  sort: PortfolioSort,
) {
  const sortValue: Record<Exclude<PortfolioSort, 'DEFAULT'>, keyof PortfolioHoldingResult> = {
    EVALUATION: 'evaluationAmount',
    PROFIT: 'profitLoss',
    RETURN: 'returnRate',
    WEIGHT: 'weight',
  };
  if (sort === 'DEFAULT') return 0;
  const key = sortValue[sort];
  const firstValue = first[key] as number | null;
  const secondValue = second[key] as number | null;
  if (firstValue === null) return secondValue === null ? 0 : 1;
  if (secondValue === null) return -1;
  return secondValue - firstValue;
}

type PortfolioPageProps = {
  isLoggedIn?: boolean;
};

export function PortfolioPage({ isLoggedIn = true }: PortfolioPageProps) {
  const pageRef = useRef<HTMLElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [desktopScale, setDesktopScale] = useState(1);
  useEffect(() => {
    const previousTitle = document.title;
    document.title = '내 포트폴리오 | DAYNOMY';
    headingRef.current?.focus({ preventScroll: true });
    return () => {
      document.title = previousTitle;
    };
  }, []);
  useEffect(() => {
    const page = pageRef.current;
    if (!page || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry.contentRect.width > 0)
        setDesktopScale(Math.min(1, entry.contentRect.width / DESKTOP_CANVAS_WIDTH));
    });
    observer.observe(page);
    return () => observer.disconnect();
  }, []);
  const {
    holdings,
    guestHistories,
    loading: holdingsLoading,
    saving: holdingsSaving,
    error: holdingsError,
    retry: retryHoldings,
    saveHolding,
    removeHolding,
    totalRealizedProfitLoss,
    revision,
    createTransaction,
    updateTransaction,
    deleteTransaction,
  } = usePortfolioHoldings(isLoggedIn);
  const [calculation, setCalculation] = useState<PortfolioCalculation | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [editor, setEditor] = useState<{ holding: PortfolioHoldingInput } | null>(null);
  const [activeTab, setActiveTab] = useState<'holdings' | 'history'>('holdings');
  const [transactionEditor, setTransactionEditor] = useState<{
    stock?: StockSearchItem;
    type?: PortfolioTransactionType;
    transaction?: PortfolioHoldingHistory;
  } | null>(null);
  const [removing, setRemoving] = useState<{
    holding?: PortfolioHoldingInput;
    transaction?: PortfolioHoldingHistory;
  } | null>(null);
  const [notice, setNotice] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<'ALL' | AssetCategory>('ALL');
  const [sort, setSort] = useState<PortfolioSort>('DEFAULT');
  const [amountsHidden, setAmountsHidden] = useState(false);
  const [analysisExpanded, setAnalysisExpanded] = useState(true);
  const [trendPeriod, setTrendPeriod] = useState<TrendPeriod>('YTD');
  const performance = usePortfolioPerformance(trendPeriod, holdings, revision, isLoggedIn);
  const [newsOpen, setNewsOpen] = useState(true);
  const newsButtonRef = useRef<HTMLButtonElement>(null);
  const [trendDialogOpen, setTrendDialogOpen] = useState(false);
  const trendDialogTriggerRef = useRef<HTMLButtonElement | null>(null);
  const [selectedStock, setSelectedStock] = useState<StockSearchItem | null>(null);
  const viewedRef = useRef(false);
  const loadedRef = useRef(false);

  useEffect(() => {
    if (viewedRef.current) return;
    viewedRef.current = true;
    trackPortfolioEvent('portfolio_dashboard_viewed', { is_logged_in: isLoggedIn });
  }, [isLoggedIn]);

  useEffect(() => {
    if (holdingsLoading) return;
    if (holdings.length === 0) {
      setCalculation(null);
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    setLoading(true);
    setError('');
    calculatePortfolio(holdings, controller.signal)
      .then((response) => {
        if (!controller.signal.aborted) setCalculation(response);
      })
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        setCalculation(null);
        trackPortfolioEvent('portfolio_dashboard_load_failed', {
          stage: 'calculation',
          error_type: portfolioErrorType(caughtError),
        });
        setError(
          caughtError instanceof Error ? caughtError.message : '포트폴리오를 계산하지 못했습니다.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [holdings, holdingsLoading, retryCount]);

  const dashboardCalculation = useMemo<PortfolioCalculation | null>(
    () =>
      calculation ??
      (!holdingsLoading && !holdingsError
        ? {
            ...EMPTY_PORTFOLIO_CALCULATION,
            pricingStatus: holdings.length ? ('PARTIAL' as const) : ('COMPLETE' as const),
            unpricedHoldingCount: holdings.length,
            totalPurchaseAmount: holdings.reduce(
              (sum, holding) => sum + holding.quantity * holding.averagePurchasePrice,
              0,
            ),
            totalProfitLoss: holdings.length ? null : 0,
            totalReturnRate: holdings.length ? null : 0,
            holdings: holdings.map((holding) => ({
              ...holding,
              priceAvailable: false,
              baseDate: null,
              closePrice: null,
              purchaseAmount: holding.quantity * holding.averagePurchasePrice,
              evaluationAmount: null,
              profitLoss: null,
              returnRate: null,
              weight: null,
            })),
          }
        : null),
    [calculation, holdingsLoading, holdingsError, holdings],
  );

  const visibleHoldings = useMemo(() => {
    const filtered =
      dashboardCalculation?.holdings.filter(
        (holding) => categoryFilter === 'ALL' || holding.category === categoryFilter,
      ) ?? [];
    return [...filtered].sort((first, second) => compareHoldings(first, second, sort));
  }, [dashboardCalculation, categoryFilter, sort]);
  const analysisAssets = useMemo(
    () => toPortfolioAnalysisAssets(calculation?.holdings ?? []),
    [calculation],
  );
  const analysisHoldingsMatch =
    holdings.length === (calculation?.holdings.length ?? 0) &&
    holdings.every((holding) =>
      calculation?.holdings.some(
        (calculated) =>
          calculated.assetId === holding.assetId && calculated.quantity === holding.quantity,
      ),
    );
  const trendPoints = performance.points;

  useEffect(() => {
    if (
      loadedRef.current ||
      holdingsLoading ||
      loading ||
      holdingsError ||
      error ||
      (holdings.length > 0 && !calculation) ||
      !dashboardCalculation
    )
      return;
    loadedRef.current = true;
    trackPortfolioEvent('portfolio_dashboard_loaded', {
      is_logged_in: isLoggedIn,
      has_holdings: holdings.length > 0,
      pricing_status: dashboardCalculation.pricingStatus,
    });
  }, [
    calculation,
    dashboardCalculation,
    error,
    holdings.length,
    holdingsError,
    holdingsLoading,
    isLoggedIn,
    loading,
  ]);

  function openHoldingEditor(
    holding: PortfolioHoldingInput,
    entryPoint: 'header' | 'empty_state' | 'holding_detail' | 'editor_switch',
  ) {
    trackPortfolioEvent('portfolio_editor_opened', {
      mode: 'holding_update',
      entry_point: entryPoint,
      is_logged_in: isLoggedIn,
    });
    setEditor({ holding });
  }

  function openTransactionEditor(
    next: NonNullable<typeof transactionEditor>,
    entryPoint: 'header' | 'empty_state' | 'history' | 'holding_detail' | 'editor_switch',
  ) {
    trackPortfolioEvent('portfolio_editor_opened', {
      mode: next.transaction ? 'transaction_update' : 'transaction_create',
      entry_point: entryPoint,
      is_logged_in: isLoggedIn,
    });
    setTransactionEditor(next);
  }

  function openAdd(entryPoint: 'header' | 'empty_state') {
    openTransactionEditor({}, entryPoint);
  }

  function openHoldingDetail(holding: PortfolioHoldingResult) {
    trackPortfolioEvent('portfolio_holding_detail_opened', {
      category: holding.category,
      market: holding.market,
    });
    setSelectedStock(holding);
  }

  function changeTab(tab: typeof activeTab) {
    if (tab === activeTab) return;
    trackPortfolioEvent('portfolio_tab_changed', { tab });
    setActiveTab(tab);
  }

  function changePeriod(period: TrendPeriod, view: 'inline' | 'dialog') {
    if (period === trendPeriod) return;
    trackPortfolioEvent('portfolio_period_changed', { period, view });
    setTrendPeriod(period);
  }

  function retryChart() {
    trackPortfolioEvent('portfolio_retry_clicked', { target: 'chart' });
    performance.retry();
  }

  async function save(nextHolding: PortfolioHoldingInput) {
    const operation = 'holding_update';
    const properties = { operation, storage: isLoggedIn ? 'server' : 'temporary' } as const;
    trackPortfolioEvent('portfolio_save_submitted', properties);
    try {
      await saveHolding(nextHolding);
      trackPortfolioEvent('portfolio_save_succeeded', properties);
      setEditor(null);
      setNotice(isLoggedIn ? '보유정보를 저장했어요.' : '보유정보를 현재 탭에 임시 보관했어요.');
    } catch (caughtError) {
      trackPortfolioEvent('portfolio_save_failed', {
        ...properties,
        error_type: portfolioErrorType(caughtError),
      });
      throw caughtError;
    }
  }

  function displayWon(value: number | null, signed = false) {
    if (amountsHidden) return HIDDEN_AMOUNT;
    if (value === null) return '—';
    return signed ? formatSignedWon(value) : formatWon(value);
  }

  return (
    <main
      className="portfolio-page"
      ref={pageRef}
      style={{ '--portfolio-desktop-scale': desktopScale } as CSSProperties}
    >
      <header className="portfolio-page-title">
        <div>
          <h1 ref={headingRef} tabIndex={-1}>
            내 포트폴리오
          </h1>
          {!isLoggedIn && (
            <p className="portfolio-save-notice is-guest" role="status">
              {notice || '비회원 자산은 이 탭에만 임시 저장돼요.'}
            </p>
          )}
        </div>
        <div className="portfolio-page-actions">
          <button
            type="button"
            className="portfolio-secondary-button portfolio-news-tab"
            ref={newsButtonRef}
            aria-controls="portfolio-news-panel"
            aria-expanded={newsOpen}
            title={newsOpen ? '소식 접기' : '소식 펼치기'}
            disabled={holdingsLoading || !!holdingsError}
            onClick={() => setNewsOpen((open) => !open)}
          >
            소식
            <svg aria-hidden="true" viewBox="0 0 24 24">
              <path d="m9 6 6 6-6 6" />
            </svg>
          </button>
          <button
            type="button"
            className="portfolio-primary-button"
            disabled={holdingsLoading || !!holdingsError}
            onClick={() => openAdd('header')}
          >
            <span aria-hidden="true">＋</span> 거래 기록
          </button>
        </div>
      </header>
      <div className="portfolio-canvas">
        {isLoggedIn && notice && (
          <p className="portfolio-save-notice" role="status">
            {notice}
          </p>
        )}

        {holdingsLoading || loading ? (
          <p className="portfolio-state" aria-live="polite">
            {holdingsLoading
              ? '포트폴리오를 불러오고 있습니다.'
              : '포트폴리오를 계산하고 있습니다.'}
          </p>
        ) : null}

        {!holdingsLoading && (holdingsError || error) ? (
          <section className="portfolio-state" role="alert">
            <p>{holdingsError || error}</p>
            <button
              type="button"
              className="portfolio-secondary-button"
              onClick={() => {
                trackPortfolioEvent('portfolio_retry_clicked', { target: 'dashboard' });
                if (holdingsError) retryHoldings();
                else setRetryCount((count) => count + 1);
              }}
            >
              다시 시도
            </button>
          </section>
        ) : null}

        {!holdingsLoading &&
        (!loading || calculation !== null) &&
        !holdingsError &&
        dashboardCalculation ? (
          <div
            aria-busy={loading}
            className={`portfolio-dashboard-layout${newsOpen ? ' has-news' : ''}`}
          >
            <div className="portfolio-dashboard-main">
              <dl className="portfolio-overview" aria-label="자산 요약">
                <div className="portfolio-summary-total">
                  <div className="portfolio-balance-label">
                    <dt>
                      {dashboardCalculation.pricingStatus === 'PARTIAL'
                        ? '시세 확인된 자산 평가금액'
                        : '전체 자산 평가금액'}
                    </dt>
                    <button
                      type="button"
                      className="portfolio-privacy-button"
                      aria-pressed={amountsHidden}
                      onClick={() => {
                        trackPortfolioEvent('portfolio_amount_visibility_changed', {
                          hidden: !amountsHidden,
                        });
                        setAmountsHidden(!amountsHidden);
                      }}
                    >
                      {amountsHidden ? '금액 보기' : '금액 숨기기'}
                    </button>
                  </div>
                  <dd className="portfolio-balance-value">
                    {displayWon(
                      dashboardCalculation.pricingStatus === 'PARTIAL' &&
                        dashboardCalculation.pricedHoldingCount === 0
                        ? null
                        : dashboardCalculation.totalEvaluationAmount,
                    )}
                  </dd>
                  <dd className="portfolio-daily-performance">
                    <span>최근 거래일 손익</span>
                    <strong className={profitClass(dashboardCalculation.dailyProfitLoss)}>
                      {displayWon(dashboardCalculation.dailyProfitLoss, true)}{' '}
                      <small>{formatPercent(dashboardCalculation.dailyReturnRate, true)}</small>
                    </strong>
                  </dd>
                </div>
                <div>
                  <dt>보유자산 매입금액</dt>
                  <dd>{displayWon(dashboardCalculation.totalPurchaseAmount)}</dd>
                  <small>현재 보유 수량 기준</small>
                </div>
                <div>
                  <dt>평가손익</dt>
                  <dd className={profitClass(dashboardCalculation.totalProfitLoss)}>
                    {displayWon(dashboardCalculation.totalProfitLoss, true)}
                  </dd>
                  <small className={profitClass(dashboardCalculation.totalReturnRate)}>
                    {formatPercent(dashboardCalculation.totalReturnRate, true)}
                  </small>
                </div>
                <div>
                  <dt>누적 실현손익</dt>
                  {isLoggedIn ? (
                    <dd className={profitClass(totalRealizedProfitLoss)}>
                      {displayWon(totalRealizedProfitLoss, true)}
                    </dd>
                  ) : (
                    <dd className="portfolio-summary-locked">로그인 후 확인할 수 있어요</dd>
                  )}
                  <small>매도로 확정된 손익</small>
                </div>
              </dl>
              <div className="portfolio-price-status" role="status">
                {!analysisHoldingsMatch ? (
                  <p className="portfolio-field-note" role="status">
                    현재 보유 자산의 비중을 확인하고 있어요.
                  </p>
                ) : dashboardCalculation.pricingStatus === 'PARTIAL' ? (
                  <p>
                    <span className="portfolio-status-dot is-pending" />
                    {dashboardCalculation.unpricedHoldingCount}개 종목의 시세를 확인하지 못했어요.
                    평가금액과 비중은 시세가 있는 자산만 반영하며, 전체 손익은 표시하지 않아요.
                  </p>
                ) : dashboardCalculation.pricingStatus === 'MIXED_DATES' ? (
                  <p>
                    <span className="portfolio-status-dot is-pending" />
                    종목별 가격 기준일이 달라요. 최근 거래일 손익은 표시하지 않아요.
                  </p>
                ) : (
                  <p>
                    <span className="portfolio-status-dot" />
                    {dashboardCalculation.baseDate
                      ? `${dashboardCalculation.baseDate.replaceAll('-', '.')} 종가 기준`
                      : '매수·매도 거래를 기록해 시작하세요.'}
                  </p>
                )}
              </div>

              <section className="portfolio-analysis" aria-labelledby="portfolio-analysis-title">
                <div className="portfolio-analysis-toolbar">
                  <button
                    type="button"
                    aria-label={`자산 분석 ${analysisExpanded ? '접기' : '펼치기'}`}
                    aria-expanded={analysisExpanded}
                    aria-controls="portfolio-analysis-content"
                    onClick={() => {
                      trackPortfolioEvent('portfolio_asset_section_toggled', {
                        expanded: !analysisExpanded,
                      });
                      setAnalysisExpanded(!analysisExpanded);
                    }}
                  >
                    <span id="portfolio-analysis-title" className="portfolio-analysis-title">
                      자산 분석
                    </span>
                    <span className="portfolio-analysis-action">
                      <svg viewBox="0 0 16 16" aria-hidden="true">
                        <path d="m4 6 4 4 4-4" />
                      </svg>
                    </span>
                  </button>
                </div>
                {analysisExpanded ? (
                  <div id="portfolio-analysis-content" className="portfolio-dashboard-grid">
                    {isLoggedIn ? (
                      <PortfolioAssetTrendChart
                        points={trendPoints}
                        loading={performance.loading}
                        error={performance.error}
                        onRetry={retryChart}
                        amountsHidden={amountsHidden}
                        period={trendPeriod}
                        onPeriodChange={(period) => changePeriod(period, 'inline')}
                        onExpand={(trigger) => {
                          trackPortfolioEvent('portfolio_chart_expanded');
                          trendDialogTriggerRef.current = trigger;
                          setTrendDialogOpen(true);
                        }}
                      />
                    ) : (
                      <section
                        className="portfolio-return-dashboard is-compact"
                        aria-labelledby="asset-trend-title"
                      >
                        <div className="portfolio-dashboard-heading">
                          <h2 id="asset-trend-title">자산 추이</h2>
                          <span className="portfolio-trend-lock-badge">잠금</span>
                        </div>
                        <div className="portfolio-trend-locked">
                          <svg viewBox="0 0 24 24" aria-hidden="true">
                            <rect x="5" y="10" width="14" height="11" rx="2" />
                            <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
                          </svg>
                          <strong>로그인 후 자산 추이를 확인할 수 있어요</strong>
                          <a
                            className="portfolio-primary-button portfolio-login-button"
                            href="/login?returnTo=%2F"
                          >
                            로그인하기
                          </a>
                        </div>
                      </section>
                    )}
                    <section className="portfolio-composition" aria-labelledby="composition-title">
                      <div className="portfolio-dashboard-heading">
                        <h2 id="composition-title">자산 구성</h2>
                      </div>
                      {dashboardCalculation.holdings.length === 0 ||
                      dashboardCalculation.pricedHoldingCount === 0 ? (
                        <p className="portfolio-module-empty">표시할 자산이 없습니다</p>
                      ) : (
                        <>
                          <CompositionChart calculation={dashboardCalculation} />
                          {dashboardCalculation.pricingStatus === 'PARTIAL' && (
                            <p className="portfolio-field-note">시세 확인된 자산 기준</p>
                          )}
                        </>
                      )}
                    </section>
                  </div>
                ) : null}
              </section>

              <div className="portfolio-ledger-tabs" role="group" aria-label="포트폴리오 내역 보기">
                <button
                  type="button"
                  aria-pressed={activeTab === 'holdings'}
                  onClick={() => changeTab('holdings')}
                >
                  보유 자산 <span>{holdings.length}</span>
                </button>
                <button
                  type="button"
                  aria-pressed={activeTab === 'history'}
                  onClick={() => changeTab('history')}
                >
                  거래 내역
                </button>
              </div>
              {activeTab === 'history' ? (
                <PortfolioHistory
                  revision={revision}
                  localHistories={isLoggedIn ? undefined : guestHistories}
                  amountsHidden={amountsHidden}
                  onCreate={() => openTransactionEditor({}, 'history')}
                  onEdit={(transaction) => openTransactionEditor({ transaction }, 'history')}
                />
              ) : (
                <section className="portfolio-holdings" aria-label="보유 자산">
                  {holdings.length === 0 ? (
                    <div className="portfolio-ledger-empty">
                      <h3>보유 중인 자산이 없어요</h3>
                      <p>
                        {isLoggedIn
                          ? '매수·매도를 기록해 보세요. 전량 매도한 종목의 거래 내역과 실현손익도 유지돼요.'
                          : '매수·매도를 기록하고 보유 자산의 평가금액을 확인해 보세요.'}
                      </p>
                      <button
                        type="button"
                        className="portfolio-secondary-button"
                        onClick={() => openAdd('empty_state')}
                      >
                        거래 기록하기
                      </button>
                    </div>
                  ) : (
                    <div className="portfolio-holdings-layout">
                      <div className="portfolio-holdings-table-column">
                        <div className="portfolio-holdings-toolbar">
                          <div className="portfolio-filters" role="group" aria-label="자산 유형">
                            {(['ALL', 'STOCK', 'ETF'] as const).map((category) => (
                              <button
                                key={category}
                                type="button"
                                aria-pressed={categoryFilter === category}
                                onClick={() => {
                                  if (category === categoryFilter) return;
                                  trackPortfolioEvent('portfolio_filter_changed', { category });
                                  setCategoryFilter(category);
                                }}
                              >
                                {category === 'ALL' ? '전체' : getCategoryLabel(category)}
                              </button>
                            ))}
                          </div>
                          <div className="portfolio-holdings-actions">
                            <label className="portfolio-sort-control">
                              <span className="sr-only">정렬 기준</span>
                              <select
                                aria-label="보유 자산 정렬"
                                value={sort}
                                onChange={(event) => {
                                  const next = event.target.value as PortfolioSort;
                                  if (next === sort) return;
                                  trackPortfolioEvent('portfolio_sort_changed', { sort: next });
                                  setSort(next);
                                }}
                              >
                                <option value="DEFAULT">기본 순서</option>
                                <option value="EVALUATION">평가금액 높은 순</option>
                                <option value="PROFIT">손익 높은 순</option>
                                <option value="RETURN">수익률 높은 순</option>
                                <option value="WEIGHT">비중 높은 순</option>
                              </select>
                            </label>
                          </div>
                        </div>
                        <div
                          className="portfolio-table-wrap"
                          tabIndex={0}
                          role="region"
                          aria-label="보유 자산 표, 좁은 화면에서 가로 스크롤"
                        >
                          <table>
                            <thead>
                              <tr>
                                <th scope="col">종목</th>
                                <th scope="col">보유수량</th>
                                <th scope="col">평균 매수가</th>
                                <th scope="col">최근 종가</th>
                                <th scope="col">평가금액</th>
                                <th scope="col">손익 · 수익률</th>
                                <th scope="col">비중</th>
                              </tr>
                            </thead>
                            <tbody>
                              {visibleHoldings.map((holding) => (
                                <tr
                                  key={holding.assetId}
                                  className="portfolio-holding-row"
                                  onClick={(event) => {
                                    event.currentTarget
                                      .querySelector<HTMLButtonElement>('.portfolio-asset-trigger')
                                      ?.focus();
                                    openHoldingDetail(holding);
                                  }}
                                >
                                  <td>
                                    <div className="portfolio-asset">
                                      <span className="portfolio-monogram">
                                        {holding.name.slice(0, 1)}
                                      </span>
                                      <div>
                                        <button
                                          type="button"
                                          className="portfolio-asset-link portfolio-asset-trigger"
                                          aria-label={`${holding.name} 보유 상세`}
                                          aria-haspopup="dialog"
                                          onClick={(event) => {
                                            event.stopPropagation();
                                            openHoldingDetail(holding);
                                          }}
                                        >
                                          {holding.name}
                                          <svg viewBox="0 0 16 16" aria-hidden="true">
                                            <path d="m6 4 4 4-4 4" />
                                          </svg>
                                        </button>
                                        <small>
                                          {getCategoryLabel(holding.category)} · {holding.assetCode}{' '}
                                          · {holding.market}
                                        </small>
                                      </div>
                                    </div>
                                  </td>
                                  <td data-label="보유수량">
                                    {numberFormatter.format(holding.quantity)}주
                                  </td>
                                  <td data-label="평균 매수가">
                                    {displayWon(holding.averagePurchasePrice)}
                                  </td>
                                  <td data-label="최근 종가">
                                    <span>
                                      {holding.closePrice === null
                                        ? '-'
                                        : formatWon(holding.closePrice)}
                                    </span>
                                    {holding.priceAvailable && holding.baseDate && (
                                      <small className="portfolio-price-date">
                                        {holding.baseDate.replaceAll('-', '.')}
                                      </small>
                                    )}
                                  </td>
                                  <td data-label="평가금액" className="portfolio-value">
                                    {holding.evaluationAmount === null
                                      ? '-'
                                      : displayWon(holding.evaluationAmount)}
                                  </td>
                                  <td
                                    data-label="손익 · 수익률"
                                    className={`portfolio-profit-cell ${profitClass(holding.profitLoss)}`}
                                  >
                                    <strong>
                                      {holding.profitLoss === null
                                        ? '-'
                                        : displayWon(holding.profitLoss, true)}
                                    </strong>
                                    {holding.returnRate !== null && (
                                      <small>{formatPercent(holding.returnRate, true)}</small>
                                    )}
                                  </td>
                                  <td data-label="비중">
                                    {holding.weight === null ? '-' : formatPercent(holding.weight)}
                                  </td>
                                </tr>
                              ))}
                              {visibleHoldings.length === 0 ? (
                                <tr>
                                  <td className="portfolio-no-results" colSpan={7}>
                                    {dashboardCalculation.holdings.length === 0
                                      ? '표시할 자산이 없습니다'
                                      : '선택한 유형의 보유 자산이 없습니다.'}
                                  </td>
                                </tr>
                              ) : null}
                            </tbody>
                          </table>
                        </div>
                        <span className="sr-only" role="status">
                          {categoryFilter === 'ALL' ? '전체' : getCategoryLabel(categoryFilter)}{' '}
                          {visibleHoldings.length}개 자산 표시 중
                        </span>
                      </div>
                    </div>
                  )}
                </section>
              )}
              {!analysisHoldingsMatch ? (
                <p className="portfolio-field-note" role="status">
                  현재 보유 자산의 비중을 확인하고 있어요.
                </p>
              ) : dashboardCalculation.pricingStatus === 'PARTIAL' ? (
                <p className="portfolio-field-note">
                  전체 자산의 시세가 확인되면 포트폴리오 영향 분석을 이용할 수 있어요.
                </p>
              ) : (
                <PortfolioAnalysis
                  assets={analysisAssets}
                  isLoggedIn={isLoggedIn}
                  holdingsKey={JSON.stringify(
                    holdings
                      .map(({ assetId, quantity }) => [assetId, quantity])
                      .sort((a, b) => a[0] - b[0]),
                  )}
                />
              )}
            </div>
            <PortfolioNews
              holdings={holdings}
              hidden={!newsOpen}
              onClose={() => {
                setNewsOpen(false);
                newsButtonRef.current?.focus();
              }}
            />
          </div>
        ) : null}
      </div>
      {isLoggedIn && trendDialogOpen ? (
        <PortfolioTrendDialog
          points={trendPoints}
          loading={performance.loading}
          error={performance.error}
          onRetry={retryChart}
          amountsHidden={amountsHidden}
          period={trendPeriod}
          onPeriodChange={(period) => changePeriod(period, 'dialog')}
          onClose={() => {
            setTrendDialogOpen(false);
            trendDialogTriggerRef.current?.focus();
          }}
        />
      ) : null}

      {(selectedStock || transactionEditor || editor || removing) && (
        <PortfolioDialog
          titleId={
            removing
              ? 'portfolio-confirm-title'
              : editor
                ? 'portfolio-dialog-title'
                : transactionEditor
                  ? 'transaction-title'
                  : 'holding-detail-title'
          }
          busy={holdingsSaving}
          onClose={() => {
            if (removing) setRemoving(null);
            else if (editor) setEditor(null);
            else if (transactionEditor) setTransactionEditor(null);
            else setSelectedStock(null);
          }}
          className={
            removing
              ? 'portfolio-confirm'
              : selectedStock && !transactionEditor && !editor
                ? 'portfolio-holding-detail'
                : editor
                  ? 'portfolio-holding-sheet'
                  : transactionEditor
                    ? `portfolio-transaction-sheet${transactionEditor.transaction ? ' portfolio-transaction-edit' : ''}`
                    : ''
          }
        >
          {selectedStock && (
            <div
              className="portfolio-detail-view"
              hidden={!!(transactionEditor || editor || removing)}
            >
              <PortfolioHoldingDetail
                stock={selectedStock}
                holding={holdings.find((item) => item.assetId === selectedStock.assetId)}
                valuation={dashboardCalculation?.holdings.find(
                  (item) => item.assetId === selectedStock.assetId,
                )}
                revision={revision}
                localHistories={isLoggedIn ? undefined : guestHistories}
                amountsHidden={amountsHidden}
                busy={holdingsSaving || loading}
                onClose={() => setSelectedStock(null)}
                onTrade={(type) =>
                  openTransactionEditor({ stock: selectedStock, type }, 'holding_detail')
                }
                onAdjust={(holding) => openHoldingEditor(holding, 'holding_detail')}
                onRemove={(holding) => setRemoving({ holding })}
                onEditTransaction={(transaction) =>
                  openTransactionEditor({ transaction }, 'holding_detail')
                }
              />
            </div>
          )}

          {transactionEditor && (
            <div hidden={!!removing}>
              <PortfolioTransactionEditor
                {...transactionEditor}
                onClose={() => setTransactionEditor(null)}
                onDelete={
                  transactionEditor.transaction
                    ? () => setRemoving({ transaction: transactionEditor.transaction })
                    : undefined
                }
                onSave={async (request, stock) => {
                  const transaction = transactionEditor.transaction;
                  const properties = {
                    operation: transaction ? 'transaction_update' : 'transaction_create',
                    storage: isLoggedIn ? 'server' : 'temporary',
                    transaction_type: request.type,
                  } as const;
                  trackPortfolioEvent('portfolio_save_submitted', properties);
                  try {
                    if (transaction) {
                      const { assetId: _assetId, type: _type, ...update } = request;
                      await updateTransaction(transaction.historyId, transaction.assetId, update);
                    } else await createTransaction(request, stock);
                    trackPortfolioEvent('portfolio_save_succeeded', properties);
                    setTransactionEditor(null);
                    setNotice(
                      transaction
                        ? '거래를 수정하고 자산을 다시 계산했어요.'
                        : '거래를 기록했어요.',
                    );
                  } catch (caughtError) {
                    trackPortfolioEvent('portfolio_save_failed', {
                      ...properties,
                      error_type: portfolioErrorType(caughtError),
                    });
                    throw caughtError;
                  }
                }}
              />
            </div>
          )}
          {removing && (
            <PortfolioConfirmDialog
              title={removing.transaction ? '거래 삭제' : '보유자산 제거'}
              onClose={() => setRemoving(null)}
              onConfirm={async () => {
                const properties = {
                  target: removing.transaction ? 'transaction' : 'holding',
                  storage: isLoggedIn ? 'server' : 'temporary',
                } as const;
                try {
                  if (removing.transaction)
                    await deleteTransaction(
                      removing.transaction.historyId,
                      removing.transaction.assetId,
                    );
                  else if (removing.holding) await removeHolding(removing.holding.assetId);
                  trackPortfolioEvent('portfolio_delete_succeeded', properties);
                  if (removing.transaction) setTransactionEditor(null);
                  setRemoving(null);
                  setNotice('삭제 내용을 반영했어요.');
                } catch (caughtError) {
                  trackPortfolioEvent('portfolio_delete_failed', {
                    ...properties,
                    error_type: portfolioErrorType(caughtError),
                  });
                  throw caughtError;
                }
              }}
            >
              <strong>{removing.transaction?.name ?? removing.holding?.name}</strong>
              {removing.transaction ? (
                <>
                  <p>
                    {removing.transaction.effectiveDate} ·{' '}
                    {removing.transaction.changeType === 'BUY' ? '매수' : '매도'} ·{' '}
                    {removing.transaction.transactionQuantity}주 ·{' '}
                    {displayWon(removing.transaction.unitPrice)}
                  </p>
                  <p>
                    이 거래를 삭제하면 이후 보유 수량, 평균 매입가, 실현손익과 자산 추이가 다시
                    계산됩니다.
                  </p>
                </>
              ) : (
                <p>
                  보유 목록에서 제거합니다. 매도로 처리되지 않으며 실현손익이 발생하지 않습니다.
                  {isLoggedIn ? '기존 거래 기록은 유지됩니다.' : '브라우저 세션에서 제거됩니다.'}
                </p>
              )}
            </PortfolioConfirmDialog>
          )}

          {editor ? (
            <PortfolioEditor
              isLoggedIn={isLoggedIn}
              holding={editor.holding}
              onClose={() => setEditor(null)}
              onSave={save}
            />
          ) : null}
        </PortfolioDialog>
      )}
    </main>
  );
}
