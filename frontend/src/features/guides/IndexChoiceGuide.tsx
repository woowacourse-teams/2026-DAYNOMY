import { useState } from 'react';
import { Link } from 'react-router-dom';

export const INDEX_CHOICE_STORAGE_KEY = 'daynomy:guide-index-choice-plan:v1';

type SavedAllocation = {
  monthlyAmount: number;
  marketPercent: number;
  growthPercent: number;
};

type IndexChoiceGuideProps = {
  onComplete: () => void;
};

const ANNUAL_RETURNS = [
  { year: 1986, growth: 6.9, market: 14.62 },
  { year: 1987, growth: 10.5, market: 2.03 },
  { year: 1988, growth: 13.5, market: 12.4 },
  { year: 1989, growth: 26.2, market: 27.25 },
  { year: 1990, growth: -10.4, market: -6.56 },
  { year: 1991, growth: 65, market: 26.31 },
  { year: 1992, growth: 8.9, market: 4.46 },
  { year: 1993, growth: 10.6, market: 7.06 },
  { year: 1994, growth: 1.5, market: -1.54 },
  { year: 1995, growth: 42.5, market: 34.11 },
  { year: 1996, growth: 42.5, market: 20.26 },
  { year: 1997, growth: 20.6, market: 31.01 },
  { year: 1998, growth: 85.3, market: 26.67 },
  { year: 1999, growth: 102, market: 19.53 },
  { year: 2000, growth: -36.8, market: -10.14 },
  { year: 2001, growth: -32.7, market: -13.04 },
  { year: 2002, growth: -37.6, market: -23.37 },
  { year: 2003, growth: 49.1, market: 26.38 },
  { year: 2004, growth: 10.4, market: 8.99 },
  { year: 2005, growth: 1.5, market: 3 },
  { year: 2006, growth: 6.8, market: 13.62 },
  { year: 2007, growth: 18.7, market: 3.53 },
  { year: 2008, growth: -41.9, market: -38.49 },
  { year: 2009, growth: 53.5, market: 23.45 },
  { year: 2010, growth: 19.2, market: 12.78 },
  { year: 2011, growth: 2.7, market: 0 },
  { year: 2012, growth: 16.8, market: 13.4 },
  { year: 2013, growth: 35, market: 29.6 },
  { year: 2014, growth: 17.9, market: 11.39 },
  { year: 2015, growth: 8.4, market: -0.73 },
  { year: 2016, growth: 5.9, market: 9.54 },
  { year: 2017, growth: 31.5, market: 19.42 },
  { year: 2018, growth: -1, market: -6.24 },
  { year: 2019, growth: 38, market: 28.88 },
  { year: 2020, growth: 47.6, market: 16.26 },
  { year: 2021, growth: 26.6, market: 26.89 },
  { year: 2022, growth: -33, market: -19.44 },
  { year: 2023, growth: 53.8, market: 24.23 },
  { year: 2024, growth: 24.9, market: 23.31 },
  { year: 2025, growth: 20.2, market: 16.39 },
] as const;

const STRATEGIES = [
  {
    label: '안정 중심',
    marketPercent: 80,
    description: '큰 하락이 부담스럽고 S&P500을 중심으로 시작하고 싶을 때',
  },
  {
    label: '반반',
    marketPercent: 50,
    description: '넓은 시장과 기술주의 움직임을 같은 비중으로 경험하고 싶을 때',
  },
  {
    label: '성장 중심',
    marketPercent: 20,
    description: '10년 이상 투자하며 큰 하락에도 계속 납입할 수 있을 때',
  },
] as const;

function normalizeAmount(value: number) {
  if (!Number.isFinite(value)) return 30;
  return Math.min(300, Math.max(5, Math.round(value)));
}

function loadSavedAllocation(): SavedAllocation | null {
  try {
    const saved = localStorage.getItem(INDEX_CHOICE_STORAGE_KEY);
    if (!saved) return null;
    if (saved === 'steady') return { monthlyAmount: 30, marketPercent: 80, growthPercent: 20 };
    if (saved === 'balanced') return { monthlyAmount: 30, marketPercent: 50, growthPercent: 50 };
    if (saved === 'growth') return { monthlyAmount: 30, marketPercent: 20, growthPercent: 80 };

    const parsed = JSON.parse(saved) as Partial<SavedAllocation>;
    if (
      typeof parsed.monthlyAmount !== 'number' ||
      typeof parsed.marketPercent !== 'number' ||
      typeof parsed.growthPercent !== 'number'
    ) {
      return null;
    }

    return {
      monthlyAmount: normalizeAmount(parsed.monthlyAmount),
      marketPercent: Math.min(100, Math.max(0, parsed.marketPercent)),
      growthPercent: Math.min(100, Math.max(0, parsed.growthPercent)),
    };
  } catch {
    return null;
  }
}

const RETURN_CHART = { left: 58, top: 38, width: 684, height: 174, max: 110, min: -50 } as const;

function getReturnChartPoint(value: number, index: number) {
  const x = RETURN_CHART.left + (index / (ANNUAL_RETURNS.length - 1)) * RETURN_CHART.width;
  const y =
    RETURN_CHART.top +
    ((RETURN_CHART.max - value) / (RETURN_CHART.max - RETURN_CHART.min)) * RETURN_CHART.height;
  return { x, y };
}

function getReturnChartPoints(key: 'growth' | 'market') {
  return ANNUAL_RETURNS.map((item, index) => {
    const point = getReturnChartPoint(item[key], index);
    return `${point.x.toFixed(1)},${point.y.toFixed(1)}`;
  }).join(' ');
}

export function IndexChoiceGuide({ onComplete }: IndexChoiceGuideProps) {
  const initialAllocation = loadSavedAllocation();
  const [monthlyAmount, setMonthlyAmount] = useState(initialAllocation?.monthlyAmount ?? 30);
  const [marketPercent, setMarketPercent] = useState(initialAllocation?.marketPercent ?? 80);
  const [savedAllocation, setSavedAllocation] = useState<SavedAllocation | null>(initialAllocation);

  const growthPercent = 100 - marketPercent;
  const marketAmount = Math.round((monthlyAmount * marketPercent) / 100);
  const growthAmount = monthlyAmount - marketAmount;
  const currentAllocation: SavedAllocation = { monthlyAmount, marketPercent, growthPercent };
  const isSaved =
    savedAllocation?.monthlyAmount === monthlyAmount &&
    savedAllocation.marketPercent === marketPercent &&
    savedAllocation.growthPercent === growthPercent;

  function saveAllocation() {
    localStorage.setItem(INDEX_CHOICE_STORAGE_KEY, JSON.stringify(currentAllocation));
    setSavedAllocation(currentAllocation);
    onComplete();
  }

  return (
    <main className="guide-page index-choice-page index-research-page">
      <nav className="guide-breadcrumb" aria-label="현재 위치">
        <Link to="/guides">가이드</Link>
        <span aria-hidden="true">/</span>
        <strong>나스닥100 vs S&P500</strong>
      </nav>

      <header className="index-research-header">
        <h1>S&amp;P500과 나스닥100, 뭘 사야 할까?</h1>
      </header>

      <section className="index-buying-guide" aria-labelledby="index-buying-title">
        <header>
          <h2 id="index-buying-title">처음이라면 S&amp;P500을 중심으로</h2>
        </header>

        <p className="index-buying-story">
          둘 다 미국 대표 기업에 투자하지만 담는 범위가 달라요. <strong>S&amp;P500 추종 ETF</strong>
          는 미국 대표 기업 약 500개를 여러 산업에 걸쳐 담기 때문에, 처음 한 종목만 고르거나 비교적
          넓게 나눠 투자하고 싶을 때 이해하기 쉬운 선택입니다.
        </p>

        <p className="index-buying-story index-buying-story-secondary">
          반면 <strong>나스닥100 추종 ETF</strong>는 기술주 중심 100개를 담아 움직임이 더 큽니다.
          10년 이상 투자할 수 있고 큰 하락에도 계속 납입할 수 있다면, S&amp;P500에 나스닥100을 조금
          섞어 성장 비중을 높여볼 수 있어요.
        </p>
      </section>

      <section className="index-history index-cycle" aria-labelledby="index-history-title">
        <header>
          <div>
            <h2 id="index-history-title">연간 수익률 비교</h2>
            <p className="index-section-copy">
              수익이 더 컸던 지수는 하락도 더 컸습니다. <strong>오른 폭과 떨어진 폭</strong>을 함께
              보세요.
            </p>
          </div>
          <div className="index-cycle-legend" aria-label="차트 범례">
            <span>
              <i className="is-growth" /> 나스닥100
            </span>
            <span>
              <i className="is-market" /> S&P500
            </span>
          </div>
        </header>

        <div className="index-cycle-summary" aria-label="40년 핵심 수치">
          <div>
            <strong>7년</strong>
            <span>나스닥100 하락 마감</span>
          </div>
          <div>
            <strong>9년</strong>
            <span>S&P500 하락 마감</span>
          </div>
          <div>
            <strong>2008</strong>
            <span>둘 다 -30% 아래</span>
          </div>
        </div>

        <div
          className="index-cycle-chart"
          role="img"
          aria-label="1986년부터 2025년까지 나스닥100과 S&P500 연간 가격 수익률 비교 그래프"
        >
          <svg viewBox="0 0 800 280" aria-hidden="true">
            {[100, 50, 0, -40].map((value) => {
              const { y } = getReturnChartPoint(value, 0);
              return (
                <g key={value}>
                  <line
                    x1={RETURN_CHART.left}
                    x2={RETURN_CHART.left + RETURN_CHART.width}
                    y1={y}
                    y2={y}
                    className={value === 0 ? 'return-zero-line' : 'return-grid-line'}
                  />
                  <text x="8" y={y + 4} className="return-axis-label">
                    {value > 0 ? `+${value}` : value}%
                  </text>
                </g>
              );
            })}

            <polyline
              points={getReturnChartPoints('market')}
              className="return-chart-line is-market"
            />
            <polyline
              points={getReturnChartPoints('growth')}
              className="return-chart-line is-growth"
            />

            {ANNUAL_RETURNS.filter((item) => item.growth < 0).map((item) => {
              const index = ANNUAL_RETURNS.findIndex((candidate) => candidate.year === item.year);
              const point = getReturnChartPoint(item.growth, index);
              return (
                <circle
                  key={`growth-${item.year}`}
                  cx={point.x}
                  cy={point.y}
                  r="4"
                  className="return-loss-point is-growth"
                >
                  <title>
                    {item.year}년 나스닥100 {item.growth}%
                  </title>
                </circle>
              );
            })}

            {ANNUAL_RETURNS.filter((item) => item.market < 0).map((item) => {
              const index = ANNUAL_RETURNS.findIndex((candidate) => candidate.year === item.year);
              const point = getReturnChartPoint(item.market, index);
              return (
                <circle
                  key={`market-${item.year}`}
                  cx={point.x}
                  cy={point.y}
                  r="3"
                  className="return-loss-point is-market"
                >
                  <title>
                    {item.year}년 S&P500 {item.market}%
                  </title>
                </circle>
              );
            })}

            {[
              { year: 2000, label: '닷컴버블' },
              { year: 2008, label: '금융위기' },
              { year: 2022, label: '금리 충격' },
            ].map((event) => {
              const index = ANNUAL_RETURNS.findIndex((item) => item.year === event.year);
              const point = getReturnChartPoint(
                Math.min(ANNUAL_RETURNS[index].growth, ANNUAL_RETURNS[index].market),
                index,
              );
              return (
                <g key={event.year}>
                  <line
                    x1={point.x}
                    x2={point.x}
                    y1="26"
                    y2={point.y - 9}
                    className="return-event-line"
                  />
                  <text x={point.x} y="17" textAnchor="middle" className="return-event-label">
                    {event.label}
                  </text>
                </g>
              );
            })}

            {ANNUAL_RETURNS.map((item, index) => {
              const showYear =
                index === 0 || item.year % 5 === 0 || index === ANNUAL_RETURNS.length - 1;
              if (!showYear) return null;
              const point = getReturnChartPoint(item.growth, index);
              return (
                <text
                  key={item.year}
                  x={point.x}
                  y="260"
                  textAnchor="middle"
                  className="return-year-label"
                >
                  {item.year}
                </text>
              );
            })}
          </svg>
        </div>

        <div className="index-cycle-intervals" aria-label="주요 큰 하락 사이의 간격">
          <span>
            <strong>닷컴버블 2000~02</strong>
            <small>나스닥 -36.8~-37.6% · S&amp;P -10.1~-23.4%</small>
          </span>
          <b>6년</b>
          <span>
            <strong>금융위기 2008</strong>
            <small>나스닥 -41.9% · S&amp;P -38.5%</small>
          </span>
          <b>14년</b>
          <span>
            <strong>금리 충격 2022</strong>
            <small>나스닥 -33.0% · S&amp;P -19.4%</small>
          </span>
        </div>

        <p className="index-cycle-takeaway">
          <strong>큰 하락 사이의 간격은 6년, 14년으로 일정하지 않았습니다.</strong> 다음 하락을
          기다리기보다, -30%에도 유지할 수 있는 비중을 정하는 편이 현실적이에요.{' '}
          <small className="index-data-source">
            (1985~2025 가격 수익률 자료:{' '}
            <a
              href="https://indexes.nasdaq.com/docs/NDX%20Extended%20Presentation.pdf"
              target="_blank"
              rel="noreferrer"
            >
              Nasdaq Global Indexes
            </a>
            ,{' '}
            <a
              href="https://www.sec.gov/Archives/edgar/data/19617/000121390026060669/ea0292075-01_424b3.htm"
              target="_blank"
              rel="noreferrer"
            >
              SEC 공시
            </a>
            )
          </small>
        </p>
      </section>

      <section className="index-decision" aria-labelledby="index-decision-title">
        <header>
          <h2 id="index-decision-title">어떤 방식으로 시작할까요?</h2>
          <p className="index-section-copy">
            큰 하락이 부담스럽다면 S&amp;P500을 중심으로, 10년 이상 투자하며 하락에도 계속 납입할 수
            있다면 나스닥100 비중을 높여보세요.
          </p>
        </header>
        <div className="index-strategy-options" role="group" aria-label="투자 방식 선택">
          {STRATEGIES.map((strategy) => (
            <button
              type="button"
              key={strategy.label}
              className={marketPercent === strategy.marketPercent ? 'is-active' : ''}
              aria-pressed={marketPercent === strategy.marketPercent}
              onClick={() => setMarketPercent(strategy.marketPercent)}
            >
              <span>{strategy.label}</span>
              <strong>
                S&amp;P500 {strategy.marketPercent}% · 나스닥100 {100 - strategy.marketPercent}%
              </strong>
              <p>{strategy.description}</p>
            </button>
          ))}
        </div>
      </section>

      <section className="index-plan" aria-labelledby="allocation-title">
        <header>
          <div>
            <h2 id="allocation-title">월 {monthlyAmount}만 원을 나누면</h2>
          </div>
          <label htmlFor="monthly-investment">
            월 투자금
            <input
              id="monthly-investment"
              type="number"
              min="5"
              max="300"
              step="5"
              value={monthlyAmount}
              onChange={(event) => setMonthlyAmount(normalizeAmount(Number(event.target.value)))}
            />
            만원
          </label>
        </header>

        <div
          className="index-plan-result"
          aria-label={`S&P500 ${marketAmount}만 원, 나스닥100 ${growthAmount}만 원`}
          aria-live="polite"
        >
          <article>
            <span>S&amp;P500 추종 ETF</span>
            <strong>{marketAmount}만 원</strong>
            <small>{marketPercent}%</small>
          </article>
          <i aria-hidden="true" />
          <article>
            <span>나스닥100 추종 ETF</span>
            <strong>{growthAmount}만 원</strong>
            <small>{growthPercent}%</small>
          </article>
        </div>

        <footer>
          <button type="button" className={isSaved ? 'is-saved' : ''} onClick={saveAllocation}>
            {isSaved ? '월 투자안 저장됨' : '월 투자안 저장하기'}
          </button>
        </footer>
      </section>

      <footer className="guide-detail-footer">
        <Link to="/guides">← 다른 가이드 보기</Link>
        <Link to="/">포트폴리오 보기</Link>
      </footer>
    </main>
  );
}
