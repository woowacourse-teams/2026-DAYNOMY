import { getCategoryLabel } from '../../newslist/types.ts';
import type {
  AssetImpactResponse,
  ImpactDirection,
  ImpactLevel,
  MarketAnalysisResponse,
  ScenarioResponse,
  TimeHorizon,
} from '../types.ts';

const directionLabel: Record<ImpactDirection, string> = {
  POSITIVE: '긍정',
  NEGATIVE: '부정',
};

const directionClassName: Record<ImpactDirection, string> = {
  POSITIVE: 'positive',
  NEGATIVE: 'negative',
};

const impactLevelScore: Record<ImpactLevel, number> = {
  HIGH: 78,
  MEDIUM: 52,
  LOW: 34,
};

const timeHorizonLabel: Record<TimeHorizon, string> = {
  SHORT_TERM: '단기',
  MID_TERM: '중기',
  LONG_TERM: '장기',
};

function getAssetLabel(asset: AssetImpactResponse['asset']) {
  return asset === 'MOCK' ? '기타' : getCategoryLabel(asset);
}

function getScenarioTitle(scenario: ScenarioResponse) {
  return `${timeHorizonLabel[scenario.timeHorizon]} 시나리오`;
}

export function MarketAnalysis({ marketAnalysis }: { marketAnalysis: MarketAnalysisResponse }) {
  return (
    <section className="section">
      <h2>시장 분석</h2>
      <div className="market-analysis">
        <article className="analysis-card">
          <h3>1. 이슈의 핵심 내용</h3>
          <p>{marketAnalysis.cause}</p>
        </article>

        <article className="analysis-card">
          <h3>2. 이슈가 중요한 이유</h3>
          <p>
            이 뉴스는 부동산 거래량, 은행 대출 성장, 건설사 분양 심리, 채권 금리 기대에 동시에
            연결됩니다. 단일 업종 이슈가 아니라 자산 포트폴리오 전반의 위험 선호도를 바꿀 수
            있습니다.
          </p>
        </article>

        <div className="impact-chart">
          {marketAnalysis.assets.map((impact) => (
            <div className="impact-row" key={impact.asset}>
              <span>{getAssetLabel(impact.asset)}</span>
              <div className="impact-track">
                <i
                  className={directionClassName[impact.direction]}
                  style={{ width: `${impactLevelScore[impact.impactLevel]}%` }}
                />
              </div>
              <strong className={directionClassName[impact.direction]}>
                {directionLabel[impact.direction]}
              </strong>
            </div>
          ))}
        </div>

        <article className="analysis-card">
          <h3>3. 단기·중기·장기 시나리오</h3>
          <div className="scenario-list">
            {marketAnalysis.scenarios.map((scenario) => (
              <div className="scenario-card" key={scenario.timeHorizon}>
                <div>
                  <strong>{getScenarioTitle(scenario)}</strong>
                  <span>가능성 {scenario.probability}%</span>
                </div>
                <p>{scenario.prediction}</p>
              </div>
            ))}
          </div>
        </article>
      </div>
    </section>
  );
}
