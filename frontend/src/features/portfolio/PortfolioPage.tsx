import { useEffect, useMemo, useState } from 'react';
import { calculatePortfolio } from './api';
import { PortfolioEditor } from './components/PortfolioEditor';
import { usePortfolioHoldings } from './hooks/usePortfolioHoldings';
import type { PortfolioCalculation, PortfolioHoldingInput, StockMarket } from './types';
import './portfolio.css';

const numberFormatter = new Intl.NumberFormat('ko-KR');
const percentFormatter = new Intl.NumberFormat('ko-KR', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

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

function ReturnsChart({ calculation }: { calculation: PortfolioCalculation }) {
  const values = calculation.holdings.map((holding) => holding.returnRate);
  const labels = calculation.holdings.map((holding) => holding.name);
  const min = Math.min(0, ...values);
  const max = Math.max(0, ...values);
  const range = max - min || 1;
  const usableWidth = 540;
  const xStep = values.length > 1 ? usableWidth / (values.length - 1) : 0;
  const toY = (value: number) => 20 + ((max - value) / range) * 160;
  const points = values
    .map((value, index) => `${values.length === 1 ? 280 : 10 + index * xStep},${toY(value)}`)
    .join(' ');
  const zeroY = toY(0);
  const firstX = values.length === 1 ? 280 : 10;
  const lastX = values.length === 1 ? 280 : 550;

  return (
    <svg
      className="portfolio-line-chart"
      viewBox="0 0 630 212"
      role="img"
      aria-label="종목별 수익률"
    >
      <g className="portfolio-grid-lines">
        <path d="M10 20H550 M10 60H550 M10 100H550 M10 140H550 M10 180H550" />
        <path className="portfolio-zero-line" d={`M10 ${zeroY}H550`} />
      </g>
      <g className="portfolio-chart-labels">
        <text x="570" y="24">
          {formatPercent(max)}
        </text>
        <text x="570" y="104">
          {formatPercent((max + min) / 2)}
        </text>
        <text x="570" y="184">
          {formatPercent(min)}
        </text>
      </g>
      <polygon
        className="portfolio-chart-area"
        points={`${firstX},${zeroY} ${points} ${lastX},${zeroY}`}
      />
      <polyline className="portfolio-chart-line" points={points} />
      {values.map((value, index) => {
        const x = values.length === 1 ? 280 : 10 + index * xStep;
        return (
          <circle
            key={`${labels[index]}-${index}`}
            className="portfolio-chart-point"
            cx={x}
            cy={toY(value)}
            r="3.5"
          />
        );
      })}
      <g className="portfolio-chart-labels">
        {labels.map((label, index) => {
          const x = values.length === 1 ? 280 : 10 + index * xStep;
          return (
            <text key={`${label}-${index}`} x={x} y="208" textAnchor="middle">
              {label.length > 6 ? `${label.slice(0, 6)}…` : label}
            </text>
          );
        })}
      </g>
    </svg>
  );
}

function AllocationChart({ calculation }: { calculation: PortfolioCalculation }) {
  const kospi = calculation.marketAllocations.find((item) => item.market === 'KOSPI')?.weight ?? 0;
  const kosdaq =
    calculation.marketAllocations.find((item) => item.market === 'KOSDAQ')?.weight ?? 0;

  return (
    <>
      <div className="portfolio-donut-wrap">
        <svg
          className="portfolio-donut"
          viewBox="0 0 180 180"
          role="img"
          aria-label={`KOSPI ${formatPercent(kospi)}, KOSDAQ ${formatPercent(kosdaq)}`}
        >
          <g transform="rotate(-90 90 90)" fill="none" strokeWidth="19">
            <circle cx="90" cy="90" r="66" stroke="#f0f2f5" />
            <circle
              cx="90"
              cy="90"
              r="66"
              pathLength="100"
              stroke="#5075bb"
              strokeDasharray={`${kospi} ${100 - kospi}`}
            />
            <circle
              cx="90"
              cy="90"
              r="66"
              pathLength="100"
              stroke="#9fb2c8"
              strokeDasharray={`${kosdaq} ${100 - kosdaq}`}
              strokeDashoffset={-kospi}
            />
          </g>
        </svg>
        <div className="portfolio-donut-caption">
          <span>보유 종목</span>
          <strong>
            {calculation.holdings.length}
            <small>개</small>
          </strong>
        </div>
      </div>
      <dl className="portfolio-allocation-list">
        {(['KOSPI', 'KOSDAQ'] as StockMarket[]).map((market) => {
          const allocation = calculation.marketAllocations.find((item) => item.market === market);
          return (
            <div key={market}>
              <dt>
                <i className={`portfolio-dot ${market.toLowerCase()}`} />
                {market}
              </dt>
              <dd>{formatPercent(allocation?.weight ?? 0)}</dd>
            </div>
          );
        })}
      </dl>
    </>
  );
}

export function PortfolioPage() {
  const { holdings, saveHolding, removeHolding } = usePortfolioHoldings();
  const [calculation, setCalculation] = useState<PortfolioCalculation | null>(null);
  const [loading, setLoading] = useState(holdings.length > 0);
  const [error, setError] = useState('');
  const [retryCount, setRetryCount] = useState(0);
  const [editor, setEditor] = useState<{ holding?: PortfolioHoldingInput } | null>(null);
  const [marketFilter, setMarketFilter] = useState<'ALL' | StockMarket>('ALL');

  useEffect(() => {
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
  }, [holdings, retryCount]);

  const filteredHoldings = useMemo(
    () =>
      calculation?.holdings.filter(
        (holding) => marketFilter === 'ALL' || holding.market === marketFilter,
      ) ?? [],
    [calculation, marketFilter],
  );

  function save(nextHolding: PortfolioHoldingInput) {
    saveHolding(nextHolding);
    setEditor(null);
  }

  return (
    <main className="portfolio-page">
      <div className="portfolio-page-title">
        <h1>내 포트폴리오</h1>
        <span>국내 주식 · 전일 종가 기준</span>
      </div>

      {holdings.length === 0 ? (
        <section className="portfolio-empty" aria-labelledby="portfolio-empty-title">
          <span className="portfolio-empty-mark">₩</span>
          <h2 id="portfolio-empty-title">첫 자산을 추가해 보세요</h2>
          <p>보유 종목과 평균 매수가를 입력하면 수익률과 자산 비중을 한눈에 보여드려요.</p>
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
          <section className="portfolio-overview" aria-label="자산 요약">
            <div className="portfolio-balance">
              <p>전체 자산 평가금액</p>
              <p className="portfolio-balance-value">
                {numberFormatter.format(Math.round(calculation.totalEvaluationAmount))}
                <span>원</span>
              </p>
              <p className={profitClass(calculation.totalProfitLoss)}>
                {formatSignedWon(calculation.totalProfitLoss)}
                <span>{formatPercent(calculation.totalReturnRate, true)}</span>
                <small>누적 수익</small>
              </p>
            </div>
            <dl className="portfolio-balance-details">
              <div>
                <dt>투자원금</dt>
                <dd>
                  {numberFormatter.format(Math.round(calculation.totalPurchaseAmount))}
                  <span>원</span>
                </dd>
              </div>
              <div>
                <dt>기준일</dt>
                <dd className="portfolio-base-date">{calculation.baseDate.replaceAll('-', '.')}</dd>
                <small>전일 종가 기준</small>
              </div>
            </dl>
          </section>

          <div className="portfolio-charts">
            <section className="portfolio-performance" aria-labelledby="returns-title">
              <div className="portfolio-chart-heading">
                <h2 id="returns-title">종목별 수익률</h2>
                <span>평균 매수가 대비</span>
              </div>
              <p className="portfolio-period-summary">
                <strong className={profitClass(calculation.totalReturnRate)}>
                  {formatPercent(calculation.totalReturnRate, true)}
                </strong>
                <span>전체 수익률</span>
              </p>
              <ReturnsChart calculation={calculation} />
            </section>
            <section className="portfolio-allocation" aria-labelledby="allocation-title">
              <div className="portfolio-chart-heading">
                <h2 id="allocation-title">시장 구성</h2>
                <span>평가금액 기준</span>
              </div>
              <AllocationChart calculation={calculation} />
            </section>
          </div>

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
            <div className="portfolio-filters" role="group" aria-label="시장 구분">
              {(['ALL', 'KOSPI', 'KOSDAQ'] as const).map((market) => {
                const count =
                  market === 'ALL'
                    ? calculation.holdings.length
                    : calculation.holdings.filter((holding) => holding.market === market).length;
                return (
                  <button
                    key={market}
                    type="button"
                    aria-pressed={marketFilter === market}
                    onClick={() => setMarketFilter(market)}
                  >
                    {market === 'ALL' ? '전체' : market} <span>{count}</span>
                  </button>
                );
              })}
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
                    <th scope="col">평가손익</th>
                    <th scope="col">비중</th>
                    <th scope="col">
                      <span className="sr-only">관리</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filteredHoldings.map((holding) => (
                    <tr key={holding.assetId}>
                      <td>
                        <div className="portfolio-asset">
                          <span className="portfolio-monogram">{holding.name.slice(0, 1)}</span>
                          <div>
                            <strong>{holding.name}</strong>
                            <small>
                              {holding.assetCode} · {holding.market}
                            </small>
                          </div>
                        </div>
                      </td>
                      <td>{numberFormatter.format(holding.quantity)}주</td>
                      <td>{formatWon(holding.averagePurchasePrice)}</td>
                      <td>{formatWon(holding.closePrice)}</td>
                      <td className="portfolio-value">{formatWon(holding.evaluationAmount)}</td>
                      <td className={profitClass(holding.profitLoss)}>
                        {formatSignedWon(holding.profitLoss)}
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
                </tbody>
              </table>
            </div>
            <div className="portfolio-table-note">
              <span role="status">
                {marketFilter === 'ALL' ? '전체' : marketFilter} {filteredHoldings.length}개 자산
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
