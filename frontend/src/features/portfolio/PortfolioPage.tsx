import { useEffect, useMemo, useState } from 'react';
import { calculatePortfolio } from './api';
import { PortfolioAnalysis } from './components/PortfolioAnalysis';
import { PortfolioEditor } from './components/PortfolioEditor';
import { usePortfolioHoldings } from './hooks/usePortfolioHoldings';
import { usePortfolioPerformance } from './hooks/usePortfolioPerformance';
import { toPortfolioAnalysisAssets } from './portfolioAnalysisAssets';
import { getStockRelatedContents } from '../stock-content/api';
import { STOCK_CONTENT_SOURCE_LABELS, type StockRelatedContent } from '../stock-content/types';
import type {
  AssetCategory,
  PortfolioCalculation,
  PortfolioHoldingInput,
  PortfolioHoldingResult,
  PortfolioPerformancePoint,
} from './types';
import './portfolio.css';

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
const RELATED_CONTENT_PREVIEW_COUNT = 10;
const EMPTY_PORTFOLIO_CALCULATION: PortfolioCalculation = {
  baseDate: '',
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

function formatWon(value: number) {
  return `${numberFormatter.format(Math.round(value))}원`;
}

function formatSignedWon(value: number) {
  const sign = value > 0 ? '+' : value < 0 ? '−' : '';
  return `${sign}${numberFormatter.format(Math.abs(Math.round(value)))}원`;
}

function formatPercent(value: number, signed = false) {
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

function profitClass(value: number) {
  return value > 0 ? 'portfolio-gain' : value < 0 ? 'portfolio-loss' : '';
}

function createCurrentPerformancePoint(
  calculation: PortfolioCalculation,
): PortfolioPerformancePoint {
  return {
    baseDate: calculation.baseDate,
    recordedAt: `${calculation.baseDate}T15:30:00.000Z`,
    source: 'CLOSE',
    totalPurchaseAmount: calculation.totalPurchaseAmount,
    totalEvaluationAmount: calculation.totalEvaluationAmount,
    totalProfitLoss: calculation.totalProfitLoss,
    totalReturnRate: calculation.totalReturnRate,
  };
}

function getCategoryLabel(category: AssetCategory) {
  return category === 'ETF' ? 'ETF' : '주식';
}

function getCategoryWeight(calculation: PortfolioCalculation, category: AssetCategory) {
  const evaluationAmount = calculation.holdings
    .filter((holding) => holding.category === category)
    .reduce((sum, holding) => sum + holding.evaluationAmount, 0);
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
  onExpand?: () => void;
};

const TREND_PERIODS = [
  { value: 'YTD', label: '올해' },
  { value: '1M', label: '1달' },
  { value: '6M', label: '6달' },
  { value: '1Y', label: '1년' },
  { value: '5Y', label: '5년' },
] as const;
type TrendPeriod = (typeof TREND_PERIODS)[number]['value'];

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

function isFiveYearTrendDemoEnabled() {
  return (
    import.meta.env.DEV &&
    new URLSearchParams(window.location.search).get('portfolioTrendDemo') === '5y'
  );
}

function createFiveYearTrendDemo(latest: PortfolioPerformancePoint) {
  const endDate = new Date(`${latest.baseDate}T00:00:00Z`);
  const pointCount = 61;

  return Array.from({ length: pointCount }, (_, index): PortfolioPerformancePoint => {
    if (index === pointCount - 1) return latest;

    const progress = index / (pointCount - 1);
    const date = new Date(endDate);
    date.setUTCDate(1);
    date.setUTCMonth(date.getUTCMonth() - (pointCount - 1 - index));
    const totalPurchaseAmount = latest.totalPurchaseAmount * (0.56 + progress * 0.44);
    const marketMovement =
      0.96 + progress * 0.12 + Math.sin(index * 0.58) * 0.035 + Math.sin(index * 0.19) * 0.02;
    const totalEvaluationAmount = totalPurchaseAmount * marketMovement;
    const totalProfitLoss = totalEvaluationAmount - totalPurchaseAmount;

    return {
      baseDate: date.toISOString().slice(0, 10),
      recordedAt: date.toISOString(),
      source: 'CLOSE',
      totalPurchaseAmount,
      totalEvaluationAmount,
      totalProfitLoss,
      totalReturnRate:
        totalPurchaseAmount === 0 ? 0 : (totalProfitLoss / totalPurchaseAmount) * 100,
    };
  });
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
  const expanded = mode === 'expanded';
  const titleId = expanded ? 'expanded-asset-trend-title' : 'asset-trend-title';
  const gradientId = expanded
    ? 'expanded-portfolio-return-area-gradient'
    : 'portfolio-return-area-gradient';
  const latestAvailablePoint = points.at(-1);
  const visiblePoints = useMemo(() => {
    if (!latestAvailablePoint) return [];
    const cutoffDate = getTrendPeriodStart(latestAvailablePoint.baseDate, period);
    return points.filter((point) => point.baseDate >= cutoffDate);
  }, [latestAvailablePoint, period, points]);
  const width = 720;
  const hasSinglePoint = visiblePoints.length === 1;
  const height = expanded ? 300 : hasSinglePoint ? 100 : 190;
  const padding = { top: 14, right: 12, bottom: 8, left: 58 };
  const values = visiblePoints.flatMap((point) => [
    point.totalPurchaseAmount,
    point.totalEvaluationAmount,
  ]);
  const rawMinimum = Math.min(...values);
  const rawMaximum = Math.max(...values);
  const amountPadding = Math.max((rawMaximum - rawMinimum) * 0.16, rawMaximum * 0.015, 1);
  const minimum = Math.max(0, rawMinimum - amountPadding);
  const maximum = rawMaximum + amountPadding;
  const range = maximum - minimum || 1;
  const x = (index: number) =>
    hasSinglePoint
      ? padding.left
      : padding.left +
        (index / (visiblePoints.length - 1)) * (width - padding.left - padding.right);
  const y = (value: number) =>
    padding.top + ((maximum - value) / range) * (height - padding.top - padding.bottom);
  const evaluationPoints = visiblePoints.map(
    (point, index) => `${x(index)},${y(point.totalEvaluationAmount)}`,
  );
  const purchasePoints = visiblePoints.map(
    (point, index) => `${x(index)},${y(point.totalPurchaseAmount)}`,
  );
  const evaluationLinePoints = hasSinglePoint
    ? [`${padding.left},${y(visiblePoints[0].totalEvaluationAmount)}`]
    : evaluationPoints;
  const purchaseLinePoints = hasSinglePoint
    ? [`${padding.left},${y(visiblePoints[0].totalPurchaseAmount)}`]
    : purchasePoints;
  const areaPoints =
    visiblePoints.length > 1
      ? `${padding.left},${y(minimum)} ${evaluationLinePoints.join(' ')} ${width - padding.right},${y(minimum)}`
      : '';
  const latest = visiblePoints.at(-1);
  const trendColor = '#f04452';
  const activeIndex =
    hoveredIndex === null
      ? expanded
        ? visiblePoints.length - 1
        : null
      : Math.min(hoveredIndex, visiblePoints.length - 1);
  const activePoint = activeIndex === null ? null : visiblePoints[activeIndex];
  const activeX = activeIndex === null ? null : x(activeIndex);
  const tooltipAlignment =
    activeX === null
      ? ''
      : activeX > width * 0.72
        ? ' align-right'
        : activeX < width * 0.28
          ? ' align-left'
          : '';
  const guideValues = Array.from(new Set([minimum, (minimum + maximum) / 2, maximum]));
  const middlePoint = visiblePoints.at(Math.floor((visiblePoints.length - 1) / 2));
  const dateLabels = Array.from(
    new Set(
      [visiblePoints.at(0), middlePoint, visiblePoints.at(-1)]
        .filter((point): point is PortfolioPerformancePoint => Boolean(point))
        .map((point) => point.baseDate),
    ),
  );

  function selectNearestPoint(clientX: number, chart: SVGSVGElement) {
    if (visiblePoints.length === 0) return;
    const bounds = chart.getBoundingClientRect();
    const chartX = ((clientX - bounds.left) / bounds.width) * width;
    const ratio = (chartX - padding.left) / (width - padding.left - padding.right);
    const index =
      visiblePoints.length === 1
        ? 0
        : Math.round(Math.max(0, Math.min(1, ratio)) * (visiblePoints.length - 1));
    setHoveredIndex(index);
  }

  function moveActivePoint(direction: -1 | 1) {
    if (visiblePoints.length === 0) return;
    const currentIndex = activeIndex ?? visiblePoints.length - 1;
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
          <h2 id={titleId}>{expanded ? '자산 추이 상세' : '자산 추이'}</h2>
        </div>
        <div className="portfolio-trend-controls">
          {points.length > 0 || expanded ? (
            <>
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
              <div className="portfolio-trend-legend" aria-label="자산 추이 범례">
                <span className="evaluation">자산</span>
                <span className="principal">원금</span>
              </div>
            </>
          ) : null}
          {!expanded && onExpand ? (
            <button
              type="button"
              className="portfolio-trend-expand"
              aria-label="자산 추이 크게 보기"
              onClick={onExpand}
            >
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M14 5h5v5M10 19H5v-5M19 5l-6 6M5 19l6-6" />
              </svg>
            </button>
          ) : null}
        </div>
      </div>
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
          <strong>표시할 자산이 없습니다</strong>
        </div>
      ) : null}
      {!loading && !error && visiblePoints.length >= 1 && latest ? (
        <>
          <div
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
              role="img"
              aria-label={
                amountsHidden
                  ? '포트폴리오 자산 추이, 금액 숨김'
                  : `포트폴리오 자산 추이, 평가금액 ${formatWon(latest.totalEvaluationAmount)}, 매입원금 ${formatWon(latest.totalPurchaseAmount)}`
              }
              onPointerDown={(event) => selectNearestPoint(event.clientX, event.currentTarget)}
              onPointerMove={(event) => selectNearestPoint(event.clientX, event.currentTarget)}
              onPointerLeave={() => setHoveredIndex(null)}
            >
              <defs>
                <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor={trendColor} stopOpacity="0.12" />
                  <stop offset="100%" stopColor={trendColor} stopOpacity="0" />
                </linearGradient>
              </defs>
              {guideValues.map((value) => (
                <g key={value} className="portfolio-return-guide">
                  <line x1={padding.left} y1={y(value)} x2={width - padding.right} y2={y(value)} />
                  <text x={padding.left - 7} y={y(value) + 4} textAnchor="end">
                    {amountsHidden ? '—' : formatCompactWon(value)}
                  </text>
                </g>
              ))}
              {visiblePoints.length >= 1 ? (
                <>
                  {areaPoints ? (
                    <polygon
                      className="portfolio-return-area"
                      points={areaPoints}
                      fill={`url(#${gradientId})`}
                    />
                  ) : null}
                  <polyline
                    className="portfolio-return-line evaluation"
                    points={evaluationLinePoints.join(' ')}
                  />
                  <polyline
                    className="portfolio-return-line principal"
                    points={purchaseLinePoints.join(' ')}
                  />
                  {hasSinglePoint && !activePoint ? (
                    <g className="portfolio-return-single-point">
                      <circle
                        className="portfolio-return-point principal"
                        cx={padding.left}
                        cy={y(visiblePoints[0].totalPurchaseAmount)}
                        r="3.5"
                      />
                      <circle
                        className="portfolio-return-point evaluation"
                        cx={padding.left}
                        cy={y(visiblePoints[0].totalEvaluationAmount)}
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
                    cx={activeX}
                    cy={y(activePoint.totalEvaluationAmount)}
                    r="4.5"
                  />
                  <circle
                    className="portfolio-return-point principal"
                    cx={activeX}
                    cy={y(activePoint.totalPurchaseAmount)}
                    r="3.5"
                  />
                </g>
              ) : null}
            </svg>
            {activePoint && activeX !== null ? (
              <div
                className={`portfolio-return-tooltip${tooltipAlignment}`}
                style={{
                  left: `${(activeX / width) * 100}%`,
                  top: `${(y(activePoint.totalEvaluationAmount) / height) * 100}%`,
                }}
                role="status"
                aria-live="polite"
              >
                <time dateTime={activePoint.baseDate}>{formatTrendDate(activePoint.baseDate)}</time>
                <dl>
                  <div className="principal">
                    <dt>매입원금</dt>
                    <dd>
                      {amountsHidden ? HIDDEN_AMOUNT : formatWon(activePoint.totalPurchaseAmount)}
                    </dd>
                  </div>
                  <div className="evaluation">
                    <dt>평가금액</dt>
                    <dd>
                      {amountsHidden ? HIDDEN_AMOUNT : formatWon(activePoint.totalEvaluationAmount)}
                      <small className={profitClass(activePoint.totalProfitLoss)}>
                        {formatPercent(activePoint.totalReturnRate, true)}
                      </small>
                    </dd>
                  </div>
                </dl>
              </div>
            ) : null}
          </div>
          <div className="portfolio-return-dates" aria-hidden="true">
            {dateLabels.map((baseDate) => (
              <span key={baseDate}>{formatTrendAxisDate(baseDate, period)}</span>
            ))}
          </div>
        </>
      ) : null}
    </section>
  );
}

type PortfolioTrendDialogProps = Omit<PortfolioAssetTrendChartProps, 'mode' | 'onExpand'> & {
  onClose: () => void;
};

function PortfolioTrendDialog({ onClose, ...chartProps }: PortfolioTrendDialogProps) {
  useEffect(() => {
    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [onClose]);

  return (
    <div className="portfolio-trend-dialog-backdrop" onMouseDown={onClose}>
      <section
        className="portfolio-trend-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="expanded-asset-trend-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <button
          type="button"
          className="portfolio-trend-dialog-close"
          aria-label="자산 추이 상세 닫기"
          autoFocus
          onClick={onClose}
        >
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m6 6 12 12M18 6 6 18" />
          </svg>
        </button>
        <PortfolioAssetTrendChart {...chartProps} mode="expanded" />
      </section>
    </div>
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
  return (second[key] as number) - (first[key] as number);
}

export function PortfolioPage() {
  const {
    holdings,
    loading: holdingsLoading,
    saving: holdingsSaving,
    error: holdingsError,
    retry: retryHoldings,
    saveHolding,
    removeHolding,
  } = usePortfolioHoldings();
  const [calculation, setCalculation] = useState<PortfolioCalculation | null>(null);
  const performance = usePortfolioPerformance(holdings);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [editor, setEditor] = useState<{ holding?: PortfolioHoldingInput } | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<'ALL' | AssetCategory>('ALL');
  const [sort, setSort] = useState<PortfolioSort>('DEFAULT');
  const [amountsHidden, setAmountsHidden] = useState(false);
  const [analysisExpanded, setAnalysisExpanded] = useState(true);
  const fiveYearTrendDemoEnabled = isFiveYearTrendDemoEnabled();
  const [trendPeriod, setTrendPeriod] = useState<TrendPeriod>(
    fiveYearTrendDemoEnabled ? '5Y' : 'YTD',
  );
  const [trendDialogOpen, setTrendDialogOpen] = useState(false);
  const [expandedContentAssetId, setExpandedContentAssetId] = useState<number | null>(null);
  const [relatedContents, setRelatedContents] = useState<Record<number, StockRelatedContent[]>>({});
  const [relatedContentLoadingAssetId, setRelatedContentLoadingAssetId] = useState<number | null>(
    null,
  );
  const [relatedContentErrors, setRelatedContentErrors] = useState<Record<number, string>>({});

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
      .then(setCalculation)
      .catch((caughtError: unknown) => {
        if (controller.signal.aborted) return;
        setCalculation(null);
        setError(
          caughtError instanceof Error ? caughtError.message : '포트폴리오를 계산하지 못했습니다.',
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [holdings, holdingsLoading, retryCount]);

  const dashboardCalculation =
    calculation ??
    (!holdingsLoading && !holdingsError && holdings.length === 0
      ? EMPTY_PORTFOLIO_CALCULATION
      : null);

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
  const expandedHolding = dashboardCalculation?.holdings.find(
    (holding) => holding.assetId === expandedContentAssetId,
  );
  const trendPoints = useMemo(() => {
    if (holdings.length === 0) return [];
    if (!calculation) return performance.points;
    const currentPoint = createCurrentPerformancePoint(calculation);
    const accumulatedPoints = [
      ...performance.points.filter((point) => point.baseDate !== currentPoint.baseDate),
      currentPoint,
    ]
      .sort(
        (first, second) =>
          first.baseDate.localeCompare(second.baseDate) ||
          first.recordedAt.localeCompare(second.recordedAt),
      )
      .slice(-1500);
    return fiveYearTrendDemoEnabled ? createFiveYearTrendDemo(currentPoint) : accumulatedPoints;
  }, [calculation, fiveYearTrendDemoEnabled, holdings.length, performance.points]);

  async function save(nextHolding: PortfolioHoldingInput) {
    await saveHolding(nextHolding);
    setEditor(null);
  }

  async function toggleRelatedContents(assetId: number) {
    if (expandedContentAssetId === assetId) {
      setExpandedContentAssetId(null);
      return;
    }

    setExpandedContentAssetId(assetId);
    if (Object.prototype.hasOwnProperty.call(relatedContents, assetId)) return;

    setRelatedContentLoadingAssetId(assetId);
    setRelatedContentErrors((current) => ({ ...current, [assetId]: '' }));
    try {
      const response = await getStockRelatedContents(assetId);
      setRelatedContents((current) => ({ ...current, [assetId]: response.contents }));
    } catch (caughtError) {
      setRelatedContentErrors((current) => ({
        ...current,
        [assetId]:
          caughtError instanceof Error ? caughtError.message : '소식을 불러오지 못했습니다.',
      }));
    } finally {
      setRelatedContentLoadingAssetId((current) => (current === assetId ? null : current));
    }
  }

  function displayWon(value: number, signed = false) {
    if (amountsHidden) return HIDDEN_AMOUNT;
    return signed ? formatSignedWon(value) : formatWon(value);
  }

  function renderRelatedContentPanel() {
    if (!expandedHolding) return null;

    const contents = relatedContents[expandedHolding.assetId] ?? [];
    const visibleContents = contents.slice(0, RELATED_CONTENT_PREVIEW_COUNT);

    return (
      <aside className="portfolio-related-content-panel" aria-labelledby="related-content-title">
        <div className="portfolio-related-content-heading">
          <strong id="related-content-title">소식</strong>
          {contents.length > 0 ? <span>{contents.length}건</span> : null}
        </div>
        {relatedContentLoadingAssetId === expandedHolding.assetId ? (
          <p role="status">소식을 불러오는 중입니다.</p>
        ) : null}
        {relatedContentErrors[expandedHolding.assetId] ? (
          <p role="alert">{relatedContentErrors[expandedHolding.assetId]}</p>
        ) : null}
        {relatedContentLoadingAssetId !== expandedHolding.assetId &&
        !relatedContentErrors[expandedHolding.assetId] &&
        (relatedContents[expandedHolding.assetId] ?? []).length === 0 ? (
          <p>등록된 소식이 없습니다.</p>
        ) : null}
        {contents.length > 0 ? (
          <ul>
            {visibleContents.map((content) => (
              <li key={content.id}>
                {content.imageUrl ? <img src={content.imageUrl} alt="" loading="lazy" /> : null}
                <div>
                  <span>{STOCK_CONTENT_SOURCE_LABELS[content.sourceType]}</span>
                  <a
                    href={content.url}
                    target={content.sourceType === 'INTERNAL_NEWS' ? undefined : '_blank'}
                    rel={content.sourceType === 'INTERNAL_NEWS' ? undefined : 'noreferrer'}
                  >
                    {content.title}
                  </a>
                </div>
              </li>
            ))}
          </ul>
        ) : null}
        {contents.length > RELATED_CONTENT_PREVIEW_COUNT ? (
          <a className="portfolio-related-content-more" href={`/stocks/${expandedHolding.assetId}`}>
            더보기
          </a>
        ) : null}
      </aside>
    );
  }

  return (
    <main className="portfolio-page">
      <div className="portfolio-page-title">
        <h1>내 포트폴리오</h1>
      </div>

      {holdingsLoading || loading ? (
        <p className="portfolio-state" aria-live="polite">
          {holdingsLoading ? '포트폴리오를 불러오고 있습니다.' : '포트폴리오를 계산하고 있습니다.'}
        </p>
      ) : null}

      {!holdingsLoading && (holdingsError || error) ? (
        <section className="portfolio-state" role="alert">
          <p>{holdingsError || error}</p>
          <button
            type="button"
            className="portfolio-secondary-button"
            onClick={() => (holdingsError ? retryHoldings() : setRetryCount((count) => count + 1))}
          >
            다시 시도
          </button>
        </section>
      ) : null}

      {!holdingsLoading && !loading && !holdingsError && dashboardCalculation ? (
        <div
          className={`portfolio-dashboard-layout${expandedHolding ? ' has-related-content' : ''}`}
        >
          <div className="portfolio-dashboard-main">
            <dl className="portfolio-overview" aria-label="자산 요약">
              <div className="portfolio-summary-total">
                <div className="portfolio-balance-label">
                  <dt>전체 자산 평가금액</dt>
                  <button
                    type="button"
                    className="portfolio-privacy-button"
                    aria-pressed={amountsHidden}
                    onClick={() => setAmountsHidden((hidden) => !hidden)}
                  >
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
                      <circle cx="12" cy="12" r="2.75" />
                    </svg>
                    {amountsHidden ? '금액 보기' : '금액 숨기기'}
                  </button>
                </div>
                <dd className="portfolio-balance-value">
                  {displayWon(dashboardCalculation.totalEvaluationAmount)}
                </dd>
                <dd className="portfolio-daily-performance">
                  <span>오늘 손익</span>
                  {dashboardCalculation.dailyProfitLoss !== null &&
                  dashboardCalculation.dailyReturnRate !== null ? (
                    <strong className={profitClass(dashboardCalculation.dailyProfitLoss)}>
                      {displayWon(dashboardCalculation.dailyProfitLoss, true)}
                      <small>{formatPercent(dashboardCalculation.dailyReturnRate, true)}</small>
                    </strong>
                  ) : (
                    <strong
                      className="portfolio-pending-daily"
                      aria-label={
                        dashboardCalculation.holdings.length === 0 ? '표시할 자산 없음' : undefined
                      }
                    >
                      {dashboardCalculation.holdings.length === 0 ? '-' : '계산 준비 중'}
                    </strong>
                  )}
                </dd>
              </div>
              <div>
                <dt>투자원금</dt>
                <dd>{displayWon(dashboardCalculation.totalPurchaseAmount)}</dd>
              </div>
              <div>
                <dt>누적 손익</dt>
                <dd className={profitClass(dashboardCalculation.totalProfitLoss)}>
                  {displayWon(dashboardCalculation.totalProfitLoss, true)}
                </dd>
                <small className={profitClass(dashboardCalculation.totalReturnRate)}>
                  {formatPercent(dashboardCalculation.totalReturnRate, true)}
                </small>
              </div>
              <div>
                <dt>보유 자산</dt>
                <dd>{dashboardCalculation.holdings.length}개</dd>
                <small>
                  주식{' '}
                  {
                    dashboardCalculation.holdings.filter((holding) => holding.category === 'STOCK')
                      .length
                  }{' '}
                  · ETF{' '}
                  {
                    dashboardCalculation.holdings.filter((holding) => holding.category === 'ETF')
                      .length
                  }
                </small>
              </div>
            </dl>

            <section className="portfolio-analysis" aria-labelledby="portfolio-analysis-title">
              <div className="portfolio-analysis-toolbar">
                <button
                  type="button"
                  aria-label={`자산 분석 ${analysisExpanded ? '접기' : '펼치기'}`}
                  aria-expanded={analysisExpanded}
                  aria-controls="portfolio-analysis-content"
                  onClick={() => setAnalysisExpanded((expanded) => !expanded)}
                >
                  <span id="portfolio-analysis-title" className="portfolio-analysis-title">
                    자산 분석
                  </span>
                  <span className="portfolio-analysis-action">
                    {analysisExpanded ? '접기' : '펼치기'}
                    <svg viewBox="0 0 16 16" aria-hidden="true">
                      <path d="m4 6 4 4 4-4" />
                    </svg>
                  </span>
                </button>
              </div>
              {analysisExpanded ? (
                <div id="portfolio-analysis-content" className="portfolio-dashboard-grid">
                  <PortfolioAssetTrendChart
                    points={trendPoints}
                    loading={performance.loading && !dashboardCalculation}
                    error={dashboardCalculation ? '' : performance.error}
                    onRetry={performance.retry}
                    amountsHidden={amountsHidden}
                    period={trendPeriod}
                    onPeriodChange={setTrendPeriod}
                    onExpand={() => setTrendDialogOpen(true)}
                  />
                  <section className="portfolio-composition" aria-labelledby="composition-title">
                    <div className="portfolio-dashboard-heading">
                      <h2 id="composition-title">자산 구성</h2>
                    </div>
                    {dashboardCalculation.holdings.length === 0 ? (
                      <p className="portfolio-module-empty">표시할 자산이 없습니다</p>
                    ) : (
                      <CompositionChart calculation={dashboardCalculation} />
                    )}
                  </section>
                </div>
              ) : null}
            </section>

            <section className="portfolio-holdings" aria-labelledby="holdings-title">
              <div className="portfolio-section-title">
                <h2 id="holdings-title">
                  보유 자산 <span>{dashboardCalculation.holdings.length}</span>
                </h2>
                <div className="portfolio-holdings-actions">
                  <label className="portfolio-sort-control">
                    <span className="sr-only">정렬 기준</span>
                    <select
                      aria-label="보유 자산 정렬"
                      value={sort}
                      onChange={(event) => setSort(event.target.value as PortfolioSort)}
                    >
                      <option value="DEFAULT">기본 순서</option>
                      <option value="EVALUATION">평가금액 높은 순</option>
                      <option value="PROFIT">손익 높은 순</option>
                      <option value="RETURN">수익률 높은 순</option>
                      <option value="WEIGHT">비중 높은 순</option>
                    </select>
                  </label>
                  <button
                    type="button"
                    className="portfolio-primary-button"
                    onClick={() => setEditor({})}
                  >
                    <svg viewBox="0 0 20 20" aria-hidden="true">
                      <path d="M10 4.5v11M4.5 10h11" />
                    </svg>
                    자산 추가
                  </button>
                </div>
              </div>
              <div className="portfolio-holdings-layout">
                <div className="portfolio-holdings-table-column">
                  <div className="portfolio-holdings-toolbar">
                    <div className="portfolio-filters" role="group" aria-label="자산 유형">
                      {(['ALL', 'STOCK', 'ETF'] as const).map((category) => (
                        <button
                          key={category}
                          type="button"
                          aria-pressed={categoryFilter === category}
                          onClick={() => setCategoryFilter(category)}
                        >
                          {category === 'ALL' ? '전체' : getCategoryLabel(category)}
                        </button>
                      ))}
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
                          <th scope="col">현재가</th>
                          <th scope="col">평가금액</th>
                          <th scope="col">손익 · 수익률</th>
                          <th scope="col">비중</th>
                          <th scope="col">
                            <span className="sr-only">관리</span>
                          </th>
                        </tr>
                      </thead>
                      <tbody>
                        {visibleHoldings.map((holding) => (
                          <tr key={holding.assetId}>
                            <td>
                              <div className="portfolio-asset">
                                <span className="portfolio-monogram">
                                  {holding.name.slice(0, 1)}
                                </span>
                                <div>
                                  <a
                                    className="portfolio-asset-link"
                                    href={`/stocks/${holding.assetId}`}
                                  >
                                    {holding.name}
                                  </a>
                                  <small>
                                    {getCategoryLabel(holding.category)} · {holding.assetCode} ·{' '}
                                    {holding.market}
                                  </small>
                                </div>
                              </div>
                            </td>
                            <td>{numberFormatter.format(holding.quantity)}주</td>
                            <td>{displayWon(holding.averagePurchasePrice)}</td>
                            <td>{formatWon(holding.closePrice)}</td>
                            <td className="portfolio-value">
                              {displayWon(holding.evaluationAmount)}
                            </td>
                            <td
                              className={`portfolio-profit-cell ${profitClass(holding.profitLoss)}`}
                            >
                              <strong>{displayWon(holding.profitLoss, true)}</strong>
                              <small>{formatPercent(holding.returnRate, true)}</small>
                            </td>
                            <td>{formatPercent(holding.weight)}</td>
                            <td>
                              <div className="portfolio-row-actions">
                                <button
                                  type="button"
                                  disabled
                                  aria-expanded={expandedContentAssetId === holding.assetId}
                                  onClick={() => void toggleRelatedContents(holding.assetId)}
                                >
                                  {expandedContentAssetId === holding.assetId
                                    ? '소식 닫기'
                                    : '소식'}
                                </button>
                                <button
                                  type="button"
                                  disabled={holdingsSaving}
                                  onClick={() => setEditor({ holding })}
                                >
                                  수정
                                </button>
                                <button
                                  type="button"
                                  disabled={holdingsSaving}
                                  onClick={() => void removeHolding(holding.assetId)}
                                >
                                  삭제
                                </button>
                              </div>
                            </td>
                          </tr>
                        ))}
                        {visibleHoldings.length === 0 ? (
                          <tr>
                            <td className="portfolio-no-results" colSpan={8}>
                              {dashboardCalculation.holdings.length === 0
                                ? '표시할 자산이 없습니다'
                                : '선택한 유형의 보유 자산이 없습니다.'}
                            </td>
                          </tr>
                        ) : null}
                      </tbody>
                    </table>
                  </div>
                  <div className="portfolio-table-note">
                    <span className="sr-only" role="status">
                      {categoryFilter === 'ALL' ? '전체' : getCategoryLabel(categoryFilter)}{' '}
                      {visibleHoldings.length}개 자산 표시 중
                    </span>
                    <span>수수료·세금 미반영</span>
                  </div>
                </div>
              </div>
            </section>
            <PortfolioAnalysis assets={analysisAssets} />
          </div>
          {renderRelatedContentPanel()}
        </div>
      ) : null}

      {trendDialogOpen ? (
        <PortfolioTrendDialog
          points={trendPoints}
          loading={performance.loading && !calculation}
          error={calculation ? '' : performance.error}
          onRetry={performance.retry}
          amountsHidden={amountsHidden}
          period={trendPeriod}
          onPeriodChange={setTrendPeriod}
          onClose={() => setTrendDialogOpen(false)}
        />
      ) : null}

      {editor ? (
        <PortfolioEditor
          holding={editor.holding}
          savedAssetIds={holdings.map((holding) => holding.assetId)}
          onClose={() => setEditor(null)}
          onSave={save}
        />
      ) : null}
    </main>
  );
}
