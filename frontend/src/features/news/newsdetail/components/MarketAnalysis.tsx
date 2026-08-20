import type { Direction, Impact, MarketAnalysis as MarketAnalysisData } from '../types.ts';

const directionLabel: Record<Direction, string> = {
  positive: '긍정',
  negative: '부정',
};

const impactLevelScore: Record<Impact['impactLevel'], number> = {
  HIGH: 78,
  MEDIUM: 52,
  LOW: 34,
};

export function MarketAnalysis({ marketAnalysis }: { marketAnalysis: MarketAnalysisData }) {
  return (
    <section className="section">
      <h2>시장 분석</h2>
      <div className="market-analysis">
        <article className="analysis-card">
          <h3>1. 이슈의 핵심 내용</h3>
          <p>{marketAnalysis.cause}</p>
        </article>

        <div className="impact-chart">
          {marketAnalysis.impacts.map((impact) => (
            <div className="impact-row" key={impact.asset}>
              <span>{impact.asset}</span>
              <div className="impact-track">
                <i
                  className={impact.direction}
                  style={{ width: `${impactLevelScore[impact.impactLevel]}%` }}
                />
              </div>
              <strong className={impact.direction}>{directionLabel[impact.direction]}</strong>
            </div>
          ))}
        </div>

        <article className="analysis-card">
          <h3>2. 단기·중기·장기 시나리오</h3>
          <div className="scenario-list">
            {marketAnalysis.scenarios.map((scenario) => (
              <div className="scenario-card" key={scenario.title}>
                <div>
                  <strong>{scenario.title}</strong>
                  {scenario.probability && <span>가능성 {scenario.probability}%</span>}
                </div>
                <p>{scenario.description}</p>
              </div>
            ))}
          </div>
        </article>
      </div>
    </section>
  );
}
