import { useEffect, useMemo, useState } from 'react';
import { calculatePortfolio } from './api';
import { PortfolioEditor } from './components/PortfolioEditor';
import { usePortfolioHoldings } from './hooks/usePortfolioHoldings';
import { usePortfolioPerformance } from './hooks/usePortfolioPerformance';
import { getStockRelatedContents } from '../stock-content/api';
import { STOCK_CONTENT_SOURCE_LABELS, type StockRelatedContent } from '../stock-content/types';
import type {
  AssetCategory,
  PortfolioCalculation,
  PortfolioHoldingHistory,
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
const HIDDEN_AMOUNT = '••••••원';
const RELATED_CONTENT_PREVIEW_COUNT = 10;

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

function profitClass(value: number) {
  return value > 0 ? 'portfolio-gain' : value < 0 ? 'portfolio-loss' : '';
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

type PortfolioReturnChartProps = {
  points: PortfolioPerformancePoint[];
  histories: PortfolioHoldingHistory[];
  loading: boolean;
  error: string;
  onRetry: () => void;
};

const HISTORY_LABEL = { ADDED: '추가', UPDATED: '수정', REMOVED: '삭제' } as const;

function PortfolioReturnChart({
  points,
  histories,
  loading,
  error,
  onRetry,
}: PortfolioReturnChartProps) {
  const width = 720;
  const height = 190;
  const padding = { top: 12, right: 12, bottom: 8, left: 38 };
  const values = points.map((point) => point.totalReturnRate);
  const rawMinimum = Math.min(0, ...values);
  const rawMaximum = Math.max(0, ...values);
  const minimum = rawMinimum === rawMaximum ? rawMinimum - 1 : rawMinimum;
  const maximum = rawMinimum === rawMaximum ? rawMaximum + 1 : rawMaximum;
  const range = maximum - minimum || 1;
  const x = (index: number) =>
    points.length === 1
      ? width / 2
      : padding.left + (index / (points.length - 1)) * (width - padding.left - padding.right);
  const y = (value: number) =>
    padding.top + ((maximum - value) / range) * (height - padding.top - padding.bottom);
  const chartPoints = points.map((point, index) => `${x(index)},${y(point.totalReturnRate)}`);
  const areaPoints = points.length
    ? `${x(0)},${y(0)} ${chartPoints.join(' ')} ${x(points.length - 1)},${y(0)}`
    : '';
  const latest = points.at(-1);
  const guideValues = Array.from(new Set([minimum, (minimum + maximum) / 2, maximum]));
  const firstDate = points.at(0)?.baseDate;
  const visibleHistories = firstDate
    ? histories.filter((history) => history.occurredAt.slice(0, 10) >= firstDate)
    : [];

  return (
    <section className="portfolio-return-dashboard" aria-labelledby="return-dashboard-title">
      <div className="portfolio-dashboard-heading">
        <h2 id="return-dashboard-title">수익률 추적</h2>
      </div>
      {loading ? (
        <p className="portfolio-tracking-state" role="status">
          수익률을 불러오고 있습니다.
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
        <div className="portfolio-tracking-state">
          <strong>수익률 데이터를 준비하고 있어요</strong>
          <p>자산을 등록하거나 종가가 갱신되면 바로 기록합니다.</p>
        </div>
      ) : null}
      {!loading && !error && points.length >= 1 && latest ? (
        <>
          <div className="portfolio-return-summary">
            <strong>{formatPercent(latest.totalReturnRate, true)}</strong>
            <span>포트폴리오 누적 수익률</span>
          </div>
          <svg
            className="portfolio-return-chart"
            viewBox={`0 0 ${width} ${height}`}
            role="img"
            aria-label={`포트폴리오 수익률 변동, 현재 ${formatPercent(latest.totalReturnRate, true)}`}
          >
            <defs>
              <linearGradient id="portfolio-return-area-gradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#2474d2" stopOpacity="0.22" />
                <stop offset="100%" stopColor="#2474d2" stopOpacity="0.02" />
              </linearGradient>
            </defs>
            {guideValues.map((value) => (
              <g key={value} className="portfolio-return-guide">
                <line x1={padding.left} y1={y(value)} x2={width - padding.right} y2={y(value)} />
                <text x={padding.left - 7} y={y(value) + 4} textAnchor="end">
                  {percentFormatter.format(value)}%
                </text>
              </g>
            ))}
            {points.length >= 2 ? (
              <>
                <polygon
                  className="portfolio-return-area"
                  points={areaPoints}
                  fill="url(#portfolio-return-area-gradient)"
                />
                <polyline className="portfolio-return-line" points={chartPoints.join(' ')} />
              </>
            ) : null}
            {points.map((point, index) => (
              <circle
                key={`${point.recordedAt}-${index}`}
                className="portfolio-return-point"
                cx={x(index)}
                cy={y(point.totalReturnRate)}
                r="5"
              />
            ))}
          </svg>
          <div
            className="portfolio-return-dates"
            style={{ gridTemplateColumns: `repeat(${points.length}, minmax(0, 1fr))` }}
            aria-hidden="true"
          >
            {points.map((point, index) => (
              <span key={`${point.recordedAt}-${index}`}>
                {point.baseDate.slice(5).replace('-', '.')}
              </span>
            ))}
          </div>
          <span className="portfolio-events-label">자산 변경 이력</span>
          <ul className="portfolio-return-events" aria-label="자산 변경 이력">
            {visibleHistories.length === 0 ? (
              <li className="portfolio-return-events-empty">최근 변경 이력이 없습니다.</li>
            ) : (
              visibleHistories.map((history) => {
                const holding = history.holding ?? history.previousHolding;
                return (
                  <li key={`${history.occurredAt}-${holding?.assetId}-${history.changeType}`}>
                    <i aria-hidden="true" />
                    <time dateTime={history.occurredAt}>
                      {history.occurredAt.slice(5, 10).replace('-', '.')}
                    </time>
                    <span>
                      {holding?.name} {HISTORY_LABEL[history.changeType]}
                    </span>
                    <strong>{numberFormatter.format(holding?.quantity ?? 0)}주</strong>
                  </li>
                );
              })
            )}
          </ul>
        </>
      ) : null}
    </section>
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
    histories,
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

  const visibleHoldings = useMemo(() => {
    const filtered =
      calculation?.holdings.filter(
        (holding) => categoryFilter === 'ALL' || holding.category === categoryFilter,
      ) ?? [];
    return [...filtered].sort((first, second) => compareHoldings(first, second, sort));
  }, [calculation, categoryFilter, sort]);
  const expandedHolding = calculation?.holdings.find(
    (holding) => holding.assetId === expandedContentAssetId,
  );

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

      {!holdingsLoading && !holdingsError && holdings.length === 0 ? (
        <section className="portfolio-empty" aria-labelledby="portfolio-empty-title">
          <span className="portfolio-empty-mark">₩</span>
          <h2 id="portfolio-empty-title">첫 자산을 추가해 보세요</h2>
          <p>보유 자산과 평균 매수가를 입력하면 수익률과 자산 비중을 한눈에 보여드려요.</p>
          <button type="button" className="portfolio-primary-button" onClick={() => setEditor({})}>
            ＋ 자산 추가
          </button>
        </section>
      ) : null}

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

      {!holdingsLoading && !loading && !holdingsError && calculation ? (
        <div
          className={`portfolio-dashboard-layout${expandedHolding ? ' has-related-content' : ''}`}
        >
          <div className="portfolio-dashboard-main">
            <div className="portfolio-freshness">
              <i aria-hidden="true" />
              <span>
                {calculation.baseDate.replaceAll('-', '.')} 종가 기준 ·{' '}
                {calculation.holdings.length}개 자산 정상 반영
              </span>
            </div>
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
                  {displayWon(calculation.totalEvaluationAmount)}
                </dd>
                <dd className="portfolio-daily-performance">
                  <span>오늘 손익</span>
                  {calculation.dailyProfitLoss !== null && calculation.dailyReturnRate !== null ? (
                    <strong className={profitClass(calculation.dailyProfitLoss)}>
                      {displayWon(calculation.dailyProfitLoss, true)}
                      <small>{formatPercent(calculation.dailyReturnRate, true)}</small>
                    </strong>
                  ) : (
                    <strong className="portfolio-pending-daily">계산 준비 중</strong>
                  )}
                </dd>
              </div>
              <div>
                <dt>투자원금</dt>
                <dd>{displayWon(calculation.totalPurchaseAmount)}</dd>
              </div>
              <div>
                <dt>누적 손익</dt>
                <dd className={profitClass(calculation.totalProfitLoss)}>
                  {displayWon(calculation.totalProfitLoss, true)}
                </dd>
                <small className={profitClass(calculation.totalReturnRate)}>
                  {formatPercent(calculation.totalReturnRate, true)}
                </small>
              </div>
              <div>
                <dt>보유 자산</dt>
                <dd>{calculation.holdings.length}개</dd>
                <small>
                  주식{' '}
                  {calculation.holdings.filter((holding) => holding.category === 'STOCK').length} ·
                  ETF {calculation.holdings.filter((holding) => holding.category === 'ETF').length}
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
                  <PortfolioReturnChart
                    points={performance.points}
                    histories={histories}
                    loading={performance.loading}
                    error={performance.error}
                    onRetry={performance.retry}
                  />
                  <section className="portfolio-composition" aria-labelledby="composition-title">
                    <div className="portfolio-dashboard-heading">
                      <h2 id="composition-title">자산 구성</h2>
                    </div>
                    <CompositionChart calculation={calculation} />
                  </section>
                </div>
              ) : null}
            </section>

            <section className="portfolio-holdings" aria-labelledby="holdings-title">
              <div className="portfolio-section-title">
                <h2 id="holdings-title">
                  보유 자산 <span>{calculation.holdings.length}</span>
                </h2>
                <button
                  type="button"
                  className="portfolio-primary-button"
                  onClick={() => setEditor({})}
                >
                  ＋ 자산 추가
                </button>
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
                                  disabled={relatedContentLoadingAssetId === holding.assetId}
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
                              선택한 유형의 보유 자산이 없습니다.
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
          </div>
          {renderRelatedContentPanel()}
        </div>
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
