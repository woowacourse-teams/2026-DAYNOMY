import { useEffect, useState } from 'react';
import { getPortfolioAnalysis, retryPortfolioAnalysis } from '../api';
import type {
  PortfolioAnalysisResponse,
  PortfolioAsset,
  PortfolioImpactDirection,
  PortfolioImpactLevel,
} from '../types';
import '../portfolio.css';

const DIRECTION_LABELS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '긍정',
  NEGATIVE: '부정',
  NEUTRAL: '중립',
};

const DIRECTION_ICONS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '↑',
  NEGATIVE: '↓',
  NEUTRAL: '→',
};

const IMPACT_LEVEL_LABELS: Record<PortfolioImpactLevel, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
};

function PortfolioEmpty() {
  return (
    <div className="portfolio-state portfolio-empty">
      <span className="portfolio-state-icon" aria-hidden="true">
        −
      </span>
      <p>포트폴리오에 자산을 등록하면 이 뉴스가 내 자산에 미치는 영향을 확인할 수 있어요.</p>
    </div>
  );
}

function PortfolioAnalysisEmpty() {
  return (
    <div className="portfolio-state portfolio-empty">
      <span className="portfolio-state-icon" aria-hidden="true">
        −
      </span>
      <strong>이 뉴스와 직접 관련된 보유 자산이 없어요.</strong>
      <p>현재 포트폴리오에서 분석할 수 있는 직접적인 영향이 확인되지 않았어요.</p>
    </div>
  );
}

function PortfolioAnalysisLoading() {
  return (
    <div className="portfolio-state portfolio-empty" role="status" aria-live="polite">
      <strong>내 포트폴리오에 미치는 영향을 분석하고 있어요.</strong>
    </div>
  );
}

function PortfolioAnalysisError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="portfolio-state portfolio-empty" role="alert">
      <strong>포트폴리오 분석을 완료하지 못했어요.</strong>
      <button className="portfolio-retry-button" type="button" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  );
}

type PortfolioAnalysisProps = {
  newsId: string;
  assets: PortfolioAsset[];
};

export function PortfolioAnalysis({ newsId, assets }: PortfolioAnalysisProps) {
  const [analysis, setAnalysis] = useState<PortfolioAnalysisResponse | null>(null);
  const [loading, setLoading] = useState(assets.length > 0);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let ignore = false;

    setAnalysis(null);
    setError(null);

    if (assets.length === 0) {
      setLoading(false);
      return;
    }

    setLoading(true);

    const request =
      retryCount === 0
        ? getPortfolioAnalysis(newsId, assets)
        : retryPortfolioAnalysis(newsId, assets);

    request
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
  }, [assets, newsId, retryCount]);

  const handleRetry = () => {
    setRetryCount((count) => count + 1);
  };

  return (
    <section className="section portfolio-section" aria-labelledby="portfolio-analysis-title">
      <div className="portfolio-heading">
        <div>
          <h2 id="portfolio-analysis-title">포트폴리오 분석</h2>
          <p>내 포트폴리오에 미칠 영향을 핵심만 정리했어요.</p>
        </div>
      </div>

      {assets.length === 0 ? <PortfolioEmpty /> : null}

      {assets.length > 0 && loading ? <PortfolioAnalysisLoading /> : null}

      {assets.length > 0 && !loading && error ? (
        <PortfolioAnalysisError onRetry={handleRetry} />
      ) : null}

      {assets.length > 0 && !loading && !error && analysis?.impacts.length === 0 ? (
        <PortfolioAnalysisEmpty />
      ) : null}

      {assets.length > 0 && !loading && !error && analysis && analysis.impacts.length > 0 ? (
        <div className="portfolio-impact-list">
          {analysis.impacts.map((impact) => (
            <article
              className={`portfolio-impact-card ${impact.direction.toLowerCase()} ${impact.impactLevel.toLowerCase()}`}
              key={impact.assetName}
            >
              <div className="portfolio-card-header">
                <div className="portfolio-asset-heading">
                  <span className="portfolio-rank">{`TOP ${impact.rank}`}</span>
                  <h3>{impact.assetName}</h3>
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
                  <strong>요약:</strong> {impact.summary}
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
