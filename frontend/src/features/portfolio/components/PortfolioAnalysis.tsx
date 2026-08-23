import { useEffect, useState } from 'react';
import { getCategoryLabel } from '../../news/newslist/types';
import { getPortfolioAnalysis, retryPortfolioAnalysis } from '../api';
import type {
  PortfolioAnalysisResponse,
  PortfolioImpactDirection,
  PortfolioImpactLevel,
} from '../types';
import '../portfolio.css';

const DIRECTION_LABELS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '긍정',
  NEGATIVE: '부정',
};

const IMPACT_LEVEL_LABELS: Record<PortfolioImpactLevel, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
};

type AnalysisState =
  | { status: 'loading' }
  | { status: 'success'; analysis: PortfolioAnalysisResponse }
  | { status: 'error'; message: string };

export function PortfolioAnalysis({ newsId }: { newsId: string }) {
  const [state, setState] = useState<AnalysisState>({ status: 'loading' });
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let ignore = false;
    setState({ status: 'loading' });

    const request =
      retryCount === 0 ? getPortfolioAnalysis(newsId) : retryPortfolioAnalysis(newsId);

    request
      .then((analysis) => {
        if (!ignore) setState({ status: 'success', analysis });
      })
      .catch((error: unknown) => {
        if (ignore) return;

        setState({
          status: 'error',
          message:
            error instanceof Error ? error.message : '포트폴리오 분석을 불러오지 못했습니다.',
        });
      });

    return () => {
      ignore = true;
    };
  }, [newsId, retryCount]);

  return (
    <section className="section portfolio-section" aria-labelledby="portfolio-analysis-title">
      <div className="portfolio-heading">
        <div>
          <h2 id="portfolio-analysis-title">내 포트폴리오 영향</h2>
          <p>북마크한 자산을 기준으로 분석한 결과입니다.</p>
        </div>
      </div>

      {state.status === 'loading' ? (
        <div className="portfolio-state" role="status" aria-live="polite">
          <span className="portfolio-spinner" aria-hidden="true" />
          <p>포트폴리오 영향을 분석하고 있습니다.</p>
        </div>
      ) : null}

      {state.status === 'error' ? (
        <div className="portfolio-state portfolio-error" role="alert">
          <p>{state.message}</p>
          <button type="button" onClick={() => setRetryCount((count) => count + 1)}>
            다시 시도
          </button>
        </div>
      ) : null}

      {state.status === 'success' && state.analysis.impacts.length === 0 ? (
        <div className="portfolio-state portfolio-empty">
          <p>북마크한 자산이 없거나 이 뉴스와 관련된 자산 영향이 없습니다.</p>
        </div>
      ) : null}

      {state.status === 'success' && state.analysis.impacts.length > 0 ? (
        <div className="portfolio-impact-list">
          {state.analysis.impacts.map((impact) => (
            <article className="portfolio-impact-card" key={impact.bookmarkId}>
              <div className="portfolio-asset-heading">
                <div>
                  <span className="portfolio-category">{getCategoryLabel(impact.category)}</span>
                  <h3>{impact.name}</h3>
                  {impact.assetCode ? <small>{impact.assetCode}</small> : null}
                </div>
                <div className="portfolio-badges" aria-label="영향 분석 요약">
                  <span className={`portfolio-direction ${impact.direction.toLowerCase()}`}>
                    {DIRECTION_LABELS[impact.direction]}
                  </span>
                  <span className={`portfolio-level ${impact.impactLevel.toLowerCase()}`}>
                    영향 {IMPACT_LEVEL_LABELS[impact.impactLevel]}
                  </span>
                </div>
              </div>

              <dl className="portfolio-explanation">
                <div>
                  <dt>예상 반응</dt>
                  <dd>{impact.expectedReaction}</dd>
                </div>
                <div>
                  <dt>판단 근거</dt>
                  <dd>{impact.reason}</dd>
                </div>
              </dl>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}
