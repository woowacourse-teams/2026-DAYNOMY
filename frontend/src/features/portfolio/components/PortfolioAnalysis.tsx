import { useEffect, useState } from 'react';
import { getPortfolioAnalysis } from '../api';
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

const DIRECTION_ICONS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '↑',
  NEGATIVE: '↓',
};

const IMPACT_LEVEL_LABELS: Record<PortfolioImpactLevel, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
};

function PortfolioAnalysisEmpty() {
  return (
    <div className="portfolio-state portfolio-empty">
      <span className="portfolio-state-icon" aria-hidden="true">
        −
      </span>
      <strong>분석할 자산 영향이 없습니다</strong>
      <p>북마크한 자산이 없거나 이 뉴스와 관련된 자산이 없습니다.</p>
    </div>
  );
}

function PortfolioAnalysisLoading() {
  return (
    <div className="portfolio-state portfolio-empty" aria-busy="true">
      <strong>포트폴리오 분석을 불러오는 중입니다.</strong>
    </div>
  );
}

function PortfolioAnalysisError({ message }: { message: string }) {
  return (
    <div className="portfolio-state portfolio-empty" role="alert">
      <strong>포트폴리오 분석을 불러오지 못했습니다.</strong>
      <p>{message}</p>
    </div>
  );
}

export function PortfolioAnalysis({ newsId }: { newsId: string }) {
  const [analysis, setAnalysis] = useState<PortfolioAnalysisResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    setAnalysis(null);
    setLoading(true);
    setError(null);

    getPortfolioAnalysis(newsId)
      .then((response) => {
        if (!ignore) {
          setAnalysis(response);
        }
      })
      .catch((caughtError) => {
        if (!ignore) {
          setError(
            caughtError instanceof Error
              ? caughtError.message
              : '포트폴리오 분석을 불러오지 못했습니다.',
          );
        }
      })
      .finally(() => {
        if (!ignore) {
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [newsId]);

  return (
    <section className="section portfolio-section" aria-labelledby="portfolio-analysis-title">
      <div className="portfolio-heading">
        <div>
          <h2 id="portfolio-analysis-title">포트폴리오 분석</h2>
          <p>내 관심 자산에 미칠 영향을 핵심만 정리했어요.</p>
        </div>
      </div>

      {loading ? <PortfolioAnalysisLoading /> : null}

      {!loading && error ? <PortfolioAnalysisError message={error} /> : null}

      {!loading && !error && analysis?.impacts.length === 0 ? <PortfolioAnalysisEmpty /> : null}

      {!loading && !error && analysis && analysis.impacts.length > 0 ? (
        <div className="portfolio-impact-list">
          {analysis.impacts.map((impact, index) => (
            <article
              className={`portfolio-impact-card ${impact.direction.toLowerCase()} ${impact.impactLevel.toLowerCase()}`}
              key={impact.bookmarkId}
            >
              <div className="portfolio-card-header">
                <div className="portfolio-asset-heading">
                  <span className="portfolio-rank">{`TOP ${index + 1}`}</span>
                  <h3>{impact.name}</h3>
                </div>
                <span
                  className={`portfolio-impact-badge ${impact.direction.toLowerCase()} ${impact.impactLevel.toLowerCase()}`}
                  aria-label="영향 분석 요약"
                >
                  <b aria-hidden="true">{DIRECTION_ICONS[impact.direction]}</b>
                  {`${DIRECTION_LABELS[impact.direction]} · 영향 ${IMPACT_LEVEL_LABELS[impact.impactLevel]}`}
                </span>
              </div>

              <div className="portfolio-impact-copy">
                <p>
                  <strong>반응:</strong> {impact.expectedReaction}
                </p>
                <p>
                  <span>근거:</span> {impact.reason}
                </p>
              </div>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}
