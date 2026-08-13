import type { Direction, Impact, RelatedIssue } from '../types'

const directionLabel: Record<Direction, string> = {
  positive: '긍정',
  negative: '부정',
  neutral: '중립',
}

const directionScore: Record<Direction, number> = {
  positive: 78,
  neutral: 52,
  negative: 34,
}

export function MarketAnalysis({
  summary,
  impacts,
  issues,
}: {
  summary: string
  impacts: Impact[]
  issues: RelatedIssue[]
}) {
  return (
    <section className="section">
      <h2>시장 분석</h2>
      <div className="market-analysis">
        <article className="analysis-card">
          <h3>1. 이슈의 핵심 내용</h3>
          <p>{summary}</p>
        </article>

        <article className="analysis-card">
          <h3>2. 이슈가 중요한 이유</h3>
          <p>
            이 뉴스는 부동산 거래량, 은행 대출 성장, 건설사 분양 심리, 채권
            금리 기대에 동시에 연결됩니다. 단일 업종 이슈가 아니라 자산
            포트폴리오 전반의 위험 선호도를 바꿀 수 있습니다.
          </p>
        </article>

        <div className="impact-chart">
          {impacts.map((impact) => (
            <div className="impact-row" key={impact.asset}>
              <span>{impact.asset}</span>
              <div className="impact-track">
                <i
                  className={impact.direction}
                  style={{ width: `${directionScore[impact.direction]}%` }}
                />
              </div>
              <strong className={impact.direction}>
                {directionLabel[impact.direction]}
              </strong>
            </div>
          ))}
        </div>

        <article className="analysis-card">
          <h3>3. 단기·중기·장기 시나리오</h3>
          <div className="scenario-list">
            {issues.map((issue) => (
              <div className="scenario-card" key={issue.title}>
                <div>
                  <strong>{issue.title}</strong>
                  {issue.probability && <span>가능성 {issue.probability}%</span>}
                </div>
                <p>{issue.description}</p>
              </div>
            ))}
          </div>
        </article>
      </div>
    </section>
  )
}
