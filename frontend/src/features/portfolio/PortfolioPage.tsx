import { useEffect, useMemo, useState } from 'react';
import { calculatePortfolio } from './api';
import { PortfolioEditor } from './components/PortfolioEditor';
import { usePortfolioHoldings } from './hooks/usePortfolioHoldings';
import type {
  AssetCategory,
  PortfolioCalculation,
  PortfolioHoldingInput,
  PortfolioHoldingResult,
} from './types';
import './portfolio.css';

const numberFormatter = new Intl.NumberFormat('ko-KR');
const percentFormatter = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const HIDDEN_AMOUNT = '••••••원';

type PortfolioSort = 'DEFAULT' | 'EVALUATION' | 'PROFIT' | 'RETURN' | 'WEIGHT';

type PortfolioPerformancePoint = {
  date: string;
  returnRate: number;
  event?: string;
  eventChange?: number;
};

const MOCK_PORTFOLIO_PERFORMANCE: PortfolioPerformancePoint[] = [
  { date: '2026-09-24', returnRate: 0 },
  { date: '2026-09-25', returnRate: 2.1 },
  { date: '2026-09-26', returnRate: 4, event: '삼성전자 추가', eventChange: 0.67 },
  { date: '2026-09-27', returnRate: 1 },
  { date: '2026-09-28', returnRate: 3 },
  { date: '2026-09-29', returnRate: 5, event: '보유수량 수정', eventChange: -0.43 },
  { date: '2026-09-30', returnRate: 3.08 },
];

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

function PortfolioReturnChart() {
  const width = 720;
  const height = 190;
  const padding = { top: 12, right: 12, bottom: 8, left: 38 };
  const values = MOCK_PORTFOLIO_PERFORMANCE.map((point) => point.returnRate);
  const minimum = 0;
  const maximum = Math.max(6, ...values);
  const range = maximum - minimum || 1;
  const x = (index: number) =>
    MOCK_PORTFOLIO_PERFORMANCE.length === 1
      ? width / 2
      : padding.left +
        (index / (MOCK_PORTFOLIO_PERFORMANCE.length - 1)) * (width - padding.left - padding.right);
  const y = (value: number) =>
    padding.top + ((maximum - value) / range) * (height - padding.top - padding.bottom);
  const points = MOCK_PORTFOLIO_PERFORMANCE.map(
    (point, index) => `${x(index)},${y(point.returnRate)}`,
  );
  const areaPoints = `${x(0)},${y(0)} ${points.join(' ')} ${x(
    MOCK_PORTFOLIO_PERFORMANCE.length - 1,
  )},${y(0)}`;
  const latest = MOCK_PORTFOLIO_PERFORMANCE.at(-1);
  const events = MOCK_PORTFOLIO_PERFORMANCE.filter(
    (point): point is PortfolioPerformancePoint & { event: string; eventChange: number } =>
      Boolean(point.event) && point.eventChange !== undefined,
  );
  const turningPoints = MOCK_PORTFOLIO_PERFORMANCE.flatMap((point, index, allPoints) => {
    if (index === 0 || index === allPoints.length - 1) return [];
    const previous = allPoints[index - 1].returnRate;
    const next = allPoints[index + 1].returnRate;
    const isPeak = point.returnRate > previous && point.returnRate > next;
    const isTrough = point.returnRate < previous && point.returnRate < next;
    return isPeak || isTrough ? [{ point, index }] : [];
  });
  const guideValues = [0, 2, 4, 6];

  return (
    <section className="portfolio-return-dashboard" aria-labelledby="return-dashboard-title">
      <div className="portfolio-dashboard-heading">
        <h2 id="return-dashboard-title">수익률 추적</h2>
      </div>
      <div className="portfolio-return-summary">
        <strong>{formatPercent(latest?.returnRate ?? 0, true)}</strong>
        <span>최근 7일 누적 수익률</span>
      </div>
      <svg
        className="portfolio-return-chart"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label={`일별 포트폴리오 수익률 추이, 현재 ${formatPercent(latest?.returnRate ?? 0, true)}`}
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
              {value}%
            </text>
          </g>
        ))}
        <polygon
          className="portfolio-return-area"
          points={areaPoints}
          fill="url(#portfolio-return-area-gradient)"
        />
        <polyline className="portfolio-return-line" points={points.join(' ')} />
        {turningPoints.map(({ point, index }) => (
          <circle
            key={point.date}
            className="portfolio-return-point"
            cx={x(index)}
            cy={y(point.returnRate)}
            r={5}
          >
            <title>
              방향 전환 {point.date.replaceAll('-', '.')} {formatPercent(point.returnRate, true)}
            </title>
          </circle>
        ))}
      </svg>
      <div className="portfolio-return-dates" aria-hidden="true">
        {MOCK_PORTFOLIO_PERFORMANCE.map((point) => (
          <span key={point.date}>{point.date.slice(5).replace('-', '.')}</span>
        ))}
      </div>
      <span className="portfolio-events-label">자산 변경 이력</span>
      <ul className="portfolio-return-events" aria-label="자산 변경 이력">
        {events.map((event) => (
          <li key={`${event.date}-${event.event}`}>
            <i aria-hidden="true" />
            <time dateTime={event.date}>{event.date.slice(5).replace('-', '.')}</time>
            <span>{event.event}</span>
            <strong className={event.eventChange < 0 ? 'is-negative' : undefined}>
              {formatPercent(event.eventChange, true)}p
            </strong>
          </li>
        ))}
      </ul>
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
  const { holdings, saveHolding, removeHolding } = usePortfolioHoldings();
  const [calculation, setCalculation] = useState<PortfolioCalculation | null>(null);
  const [loading, setLoading] = useState(holdings.length > 0);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [editor, setEditor] = useState<{ holding?: PortfolioHoldingInput } | null>(null);
  const [categoryFilter, setCategoryFilter] = useState<'ALL' | AssetCategory>('ALL');
  const [sort, setSort] = useState<PortfolioSort>('DEFAULT');
  const [amountsHidden, setAmountsHidden] = useState(false);
  const [analysisExpanded, setAnalysisExpanded] = useState(true);

  useEffect(() => {
    if (holdings.length === 0) {
      setCalculation(null);
      setLoading(false);
      setError('');
      return;
    }

    const controller = new AbortController();
    setLoading(true);
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
  }, [holdings, retryCount]);

  const visibleHoldings = useMemo(() => {
    const filtered =
      calculation?.holdings.filter(
        (holding) => categoryFilter === 'ALL' || holding.category === categoryFilter,
      ) ?? [];
    return [...filtered].sort((first, second) => compareHoldings(first, second, sort));
  }, [calculation, categoryFilter, sort]);

  function save(nextHolding: PortfolioHoldingInput) {
    saveHolding(nextHolding);
    setEditor(null);
  }

  function displayWon(value: number, signed = false) {
    if (amountsHidden) return HIDDEN_AMOUNT;
    return signed ? formatSignedWon(value) : formatWon(value);
  }

  return (
    <main className="portfolio-page">
      <div className="portfolio-page-title">
        <h1>내 포트폴리오</h1>
      </div>

      {holdings.length === 0 ? (
        <section className="portfolio-empty" aria-labelledby="portfolio-empty-title">
          <span className="portfolio-empty-mark">₩</span>
          <h2 id="portfolio-empty-title">첫 자산을 추가해 보세요</h2>
          <p>보유 자산과 평균 매수가를 입력하면 수익률과 자산 비중을 한눈에 보여드려요.</p>
          <button type="button" className="portfolio-primary-button" onClick={() => setEditor({})}>
            ＋ 자산 추가
          </button>
        </section>
      ) : null}

      {loading ? (
        <p className="portfolio-state" aria-live="polite">
          포트폴리오를 계산하고 있습니다.
        </p>
      ) : null}

      {!loading && error ? (
        <section className="portfolio-state" role="alert">
          <p>{error}</p>
          <button
            type="button"
            className="portfolio-secondary-button"
            onClick={() => setRetryCount((count) => count + 1)}
          >
            다시 시도
          </button>
        </section>
      ) : null}

      {!loading && calculation ? (
        <>
          <div className="portfolio-freshness">
            <i aria-hidden="true" />
            <span>
              {calculation.baseDate.replaceAll('-', '.')} 종가 기준 · {calculation.holdings.length}
              개 자산 정상 반영
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
                주식 {calculation.holdings.filter((holding) => holding.category === 'STOCK').length}{' '}
                · ETF {calculation.holdings.filter((holding) => holding.category === 'ETF').length}
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
                <PortfolioReturnChart />
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
                          <span className="portfolio-monogram">{holding.name.slice(0, 1)}</span>
                          <div>
                            <strong>{holding.name}</strong>
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
                      <td className="portfolio-value">{displayWon(holding.evaluationAmount)}</td>
                      <td className={`portfolio-profit-cell ${profitClass(holding.profitLoss)}`}>
                        <strong>{displayWon(holding.profitLoss, true)}</strong>
                        <small>{formatPercent(holding.returnRate, true)}</small>
                      </td>
                      <td>{formatPercent(holding.weight)}</td>
                      <td>
                        <div className="portfolio-row-actions">
                          <button type="button" onClick={() => setEditor({ holding })}>
                            수정
                          </button>
                          <button type="button" onClick={() => removeHolding(holding.assetId)}>
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
          </section>
        </>
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
