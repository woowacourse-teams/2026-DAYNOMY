import { useEffect, useMemo, useState } from 'react';
import { getPortfolioAnalysis, retryPortfolioAnalysis } from '../api';
import type {
  PortfolioAnalysisResponse,
  PortfolioAsset,
  PortfolioAssetImpactResponse,
  PortfolioImpactDirection,
  PortfolioImpactLevel,
} from '../types';
import '../portfolio.css';

const DIRECTION_LABELS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '긍정',
  NEGATIVE: '부정',
  NEUTRAL: '중립',
};

const DIRECTION_IMPACT_LABELS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '긍정 영향',
  NEGATIVE: '부정 영향',
  NEUTRAL: '중립 영향',
};

const IMPACT_LEVEL_LABELS: Record<PortfolioImpactLevel, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
};

const DIRECTION_COLORS: Record<PortfolioImpactDirection, string> = {
  POSITIVE: '#ef464d',
  NEGATIVE: '#3e83d7',
  NEUTRAL: '#98a2b3',
};

const INACTIVE_ASSET_COLOR = '#e8eef8';

function normalizeAssetName(assetName: string) {
  return assetName.trim().toLocaleLowerCase();
}

function DirectionIcon({ direction }: { direction: PortfolioImpactDirection }) {
  if (direction === 'NEUTRAL') {
    return (
      <svg aria-hidden="true" viewBox="0 0 20 20">
        <path d="M3 10h13m-4-4 4 4-4 4" />
      </svg>
    );
  }

  return (
    <svg
      aria-hidden="true"
      className={direction === 'NEGATIVE' ? 'is-negative' : undefined}
      viewBox="0 0 20 20"
    >
      <path d="M10 17V3m-5 5 5-5 5 5" />
    </svg>
  );
}

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

type PortfolioDonutProps = {
  assets: PortfolioAsset[];
  impactByAssetName: Map<string, PortfolioAssetImpactResponse>;
  selectedImpact: PortfolioAssetImpactResponse;
  onSelect: (assetName: string) => void;
};

function PortfolioDonut({
  assets,
  impactByAssetName,
  selectedImpact,
  onSelect,
}: PortfolioDonutProps) {
  const totalWeight = assets.reduce((sum, asset) => sum + asset.weight, 0);
  let offset = 0;

  return (
    <div className="portfolio-donut-wrap">
      <svg
        className="portfolio-donut"
        viewBox="0 0 220 220"
        role="group"
        aria-label="전체 포트폴리오의 자산별 보유 비중"
      >
        {assets.map((asset) => {
          const impact = impactByAssetName.get(normalizeAssetName(asset.assetName));
          const percentage = totalWeight > 0 ? (asset.weight / totalWeight) * 100 : 0;
          const segmentOffset = offset;
          const segmentGap = Math.min(0.9, percentage * 0.12);
          const segmentLength = Math.max(percentage - segmentGap, 0);
          const isSelected = impact?.assetName === selectedImpact.assetName;
          offset += percentage;

          return (
            <circle
              aria-label={
                impact
                  ? `${asset.assetName}, 보유 비중 ${asset.weight}%, ${DIRECTION_LABELS[impact.direction]} 영향`
                  : `${asset.assetName}, 보유 비중 ${asset.weight}%, 상세 분석 제외`
              }
              aria-pressed={impact ? isSelected : undefined}
              className={`portfolio-donut-segment${impact ? ' is-interactive' : ''}${isSelected ? ' is-selected' : ''}`}
              cx="110"
              cy="110"
              fill="none"
              key={asset.assetName}
              onClick={impact ? () => onSelect(impact.assetName) : undefined}
              onKeyDown={
                impact
                  ? (event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onSelect(impact.assetName);
                      }
                    }
                  : undefined
              }
              pathLength="100"
              r="78"
              role={impact ? 'button' : undefined}
              stroke={impact ? DIRECTION_COLORS[impact.direction] : INACTIVE_ASSET_COLOR}
              strokeDasharray={`${segmentLength} ${100 - segmentLength}`}
              strokeDashoffset={-segmentOffset}
              strokeWidth={isSelected ? 38 : 35}
              tabIndex={impact ? 0 : -1}
              transform="rotate(-90 110 110)"
            />
          );
        })}
      </svg>

      <div
        className={`portfolio-donut-center ${selectedImpact.direction.toLowerCase()}`}
        aria-live="polite"
      >
        <span>{`뉴스 영향 TOP ${selectedImpact.rank}`}</span>
        <strong>{selectedImpact.assetName}</strong>
        <b>{`${selectedImpact.weight}%`}</b>
        <small>현재 보유 비중</small>
      </div>
    </div>
  );
}

type PortfolioAssetListProps = {
  assets: PortfolioAsset[];
  impactByAssetName: Map<string, PortfolioAssetImpactResponse>;
  selectedImpact: PortfolioAssetImpactResponse;
  onSelect: (assetName: string) => void;
};

function PortfolioAssetList({
  assets,
  impactByAssetName,
  selectedImpact,
  onSelect,
}: PortfolioAssetListProps) {
  return (
    <ul className="portfolio-asset-list" aria-label="포트폴리오 보유 자산">
      {assets.map((asset) => {
        const impact = impactByAssetName.get(normalizeAssetName(asset.assetName));
        const isSelected = impact?.assetName === selectedImpact.assetName;
        const rowContent = (
          <>
            <span
              className="portfolio-asset-dot"
              style={{
                backgroundColor: impact ? DIRECTION_COLORS[impact.direction] : INACTIVE_ASSET_COLOR,
              }}
              aria-hidden="true"
            />
            <span className="portfolio-asset-name">{asset.assetName}</span>
            <span
              className={`portfolio-asset-analysis${impact ? ` ${impact.direction.toLowerCase()}` : ''}`}
            >
              {impact
                ? `TOP ${impact.rank} · ${DIRECTION_IMPACT_LABELS[impact.direction]}`
                : '상세 분석 제외'}
            </span>
            <strong>{`${asset.weight}%`}</strong>
          </>
        );

        return (
          <li key={asset.assetName}>
            {impact ? (
              <button
                className={`portfolio-asset-row ${impact.direction.toLowerCase()}${isSelected ? ' is-selected' : ''}`}
                type="button"
                aria-pressed={isSelected}
                onClick={() => onSelect(impact.assetName)}
              >
                {rowContent}
              </button>
            ) : (
              <div className="portfolio-asset-row is-disabled">{rowContent}</div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function PortfolioImpactDetail({
  impact,
  analyzedAssetCount,
}: {
  impact: PortfolioAssetImpactResponse;
  analyzedAssetCount: number;
}) {
  return (
    <article className={`portfolio-impact-detail ${impact.direction.toLowerCase()}`}>
      <div className="portfolio-detail-header">
        <div>
          <span>{`이 뉴스의 영향 TOP ${impact.rank} / ${analyzedAssetCount}`}</span>
          <h3>{impact.assetName}</h3>
        </div>
        <div className="portfolio-detail-badge-group">
          <span className="portfolio-direction-badge">
            <DirectionIcon direction={impact.direction} />
            {DIRECTION_IMPACT_LABELS[impact.direction]}
          </span>
          <small>{`영향 수준 ${IMPACT_LEVEL_LABELS[impact.impactLevel]}`}</small>
        </div>
      </div>

      <div className="portfolio-detail-copy">
        <p>
          {impact.summary} 현재 보유 비중은 <strong>{`${impact.weight}%`}</strong>예요.
        </p>
        <p>{impact.reason}</p>
      </div>

      <details className="portfolio-evidence">
        <summary>
          판단에 사용한 뉴스 문장
          <span aria-hidden="true">+</span>
        </summary>
        <blockquote>{impact.evidenceSentence}</blockquote>
      </details>
    </article>
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
  const [selectedAssetName, setSelectedAssetName] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;

    setAnalysis(null);
    setError(null);
    setSelectedAssetName(null);

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

  const sortedImpacts = useMemo(
    () => [...(analysis?.impacts ?? [])].sort((left, right) => left.rank - right.rank),
    [analysis],
  );
  const impactByAssetName = useMemo(
    () => new Map(sortedImpacts.map((impact) => [normalizeAssetName(impact.assetName), impact])),
    [sortedImpacts],
  );
  const selectedImpact =
    sortedImpacts.find((impact) => impact.assetName === selectedAssetName) ?? sortedImpacts[0];

  const handleRetry = () => {
    setRetryCount((count) => count + 1);
  };

  const hasAnalysis = Boolean(analysis && selectedImpact);

  return (
    <section className="section portfolio-section" aria-labelledby="portfolio-analysis-title">
      <div className={`portfolio-heading${hasAnalysis ? ' has-analysis' : ''}`}>
        <div>
          <h2 id="portfolio-analysis-title">
            {hasAnalysis ? '내 포트폴리오에 미치는 영향' : '포트폴리오 분석'}
          </h2>
          <p>
            {analysis && selectedImpact
              ? `보유 종목 ${analysis.totalAssetCount}개 중 이 뉴스의 영향을 크게 받는 상위 ${analysis.analyzedAssetCount}개를 살펴봤어요.`
              : '내 포트폴리오에 미칠 영향을 핵심만 정리했어요.'}
          </p>
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

      {assets.length > 0 && !loading && !error && analysis && selectedImpact ? (
        <div className="portfolio-analysis-layout">
          <div className="portfolio-overview">
            <div className="portfolio-overview-heading">
              <h3>내 포트폴리오</h3>
              <span className={selectedImpact.direction.toLowerCase()}>
                {`${DIRECTION_LABELS[selectedImpact.direction]} 영향`}
              </span>
            </div>

            <PortfolioDonut
              assets={assets}
              impactByAssetName={impactByAssetName}
              selectedImpact={selectedImpact}
              onSelect={setSelectedAssetName}
            />
            <PortfolioAssetList
              assets={assets}
              impactByAssetName={impactByAssetName}
              selectedImpact={selectedImpact}
              onSelect={setSelectedAssetName}
            />
            <p className="portfolio-weight-caption">
              전체 포트폴리오에서 차지하는 현재 비중이에요.
            </p>
          </div>

          <PortfolioImpactDetail
            impact={selectedImpact}
            analyzedAssetCount={analysis.analyzedAssetCount}
          />
        </div>
      ) : null}
    </section>
  );
}
